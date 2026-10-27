package com.mycompany.redxela.scheduler;

import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.models.OrderState;
import com.mycompany.redxela.models.Product;
import com.mycompany.redxela.models.Warehouse;
import com.mycompany.redxela.resources.ResourceManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Planificador Central de Procesos (CPU / Process Scheduler) de REDXela.
 *
 * 1. COLA DE LISTOS (Ready Queue): Mantiene todos los procesos listos para
 * ejecución, ordenados en tiempo real según el comparador de la política
 * activa.
 *
 * 2. DESPACHADOR (Dispatcher): Hilo independiente que selecciona el siguiente
 * proceso elegible (que cuente con existencias disponibles en inventario) y
 * realiza el traspaso de control (Context Switch) hacia un hilo trabajador.
 *
 * 3. REEVALUACIÓN POR EVENTOS IPC: Cuando el Área de Inventario procesa nueva
 * mercadería por la tubería (Pipe), notifica al planificador para reevaluar
 * inmediatamente los pedidos que esperaban stock.
 *
 * 4. SOPORTE DE ROLLBACK POR DEADLOCK: Reintegra pedidos expulsados tras un
 * interbloqueo de vuelta a la cola de espera restableciendo sus reservas.
 */
public class OrderScheduler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(OrderScheduler.class.getName());

    /**
     * Cola de Procesos Listos (Ready Queue) protegida para acceso concurrente.
     */
    private final List<Order> readyQueue;

    /**
     * Lista de pedidos en ejecución activa o adquiriendo recursos.
     */
    private final List<Order> activeOrders;

    /**
     * Administrador de semáforos y recursos físicos compartidos.
     */
    private final ResourceManager resourceManager;

    /**
     * Almacén físico donde se descuentan las existencias.
     */
    private final Warehouse warehouse;

    /**
     * Métricas acumuladas del planificador.
     */
    private final SchedulerMetrics metrics;

    /**
     * Lista de observadores para actualización de la interfaz gráfica.
     */
    private final List<SchedulerListener> listeners;

    /**
     * Piscina de hilos (Thread Pool) para ejecutar pedidos concurrentemente.
     */
    private final ExecutorService workerPool;

    /**
     * Política de planificación seleccionada actualmente.
     */
    private volatile SchedulingPolicy currentPolicy;

    /**
     * Hilo del despachador continuo.
     */
    private Thread dispatcherThread;

    /**
     * Bandera de control de vida del despachador.
     */
    private volatile boolean running;

    /**
     * Construye el planificador de procesos con sus dependencias principales.
     *
     * @param resourceManager Administrador de recursos compartidos.
     * @param warehouse Almacén físico.
     */
    public OrderScheduler(ResourceManager resourceManager, Warehouse warehouse) {
        this.resourceManager = Objects.requireNonNull(resourceManager);
        this.warehouse = Objects.requireNonNull(warehouse);
        this.readyQueue = Collections.synchronizedList(new ArrayList<>());
        this.activeOrders = Collections.synchronizedList(new ArrayList<>());
        this.metrics = new SchedulerMetrics();
        this.listeners = Collections.synchronizedList(new ArrayList<>());
        this.currentPolicy = SchedulingPolicy.PRIORIDAD_ESTRICTA;
        this.workerPool = Executors.newFixedThreadPool(12);
        this.running = false;

        // Registrar callback de Rollback para reencolar órdenes expulsadas por Deadlock
        this.resourceManager.setRollbackHandler(this::handleRollbackOrder);
    }

    /**
     * Inicia el hilo despachador del planificador.
     */
    public synchronized void start() {
        if (!running) {
            this.running = true;
            this.dispatcherThread = new Thread(this, "Thread-Despachador-Scheduler");
            this.dispatcherThread.setDaemon(true);
            this.dispatcherThread.start();
            LOGGER.log(Level.INFO, "Planificador de Procesos iniciado bajo política: {0}", currentPolicy);
        }
    }

    /**
     * Detiene el planificador y su piscina de trabajadores.
     */
    public synchronized void stop() {
        if (running) {
            this.running = false;
            if (dispatcherThread != null) {
                dispatcherThread.interrupt();
            }
            workerPool.shutdownNow();
            LOGGER.log(Level.INFO, "Planificador de Procesos detenido.");
        }
    }

    /**
     * Ingresa un nuevo pedido a la Cola de Listos (Ready Queue). El pedido se
     * posiciona automáticamente según el criterio de la política activa.
     *
     * @param order Pedido a encolar.
     */
    public void submitOrder(Order order) {
        if (order == null) {
            return;
        }

        order.setState(OrderState.PENDIENTE);
        order.setProgressPercentage(0);

        synchronized (readyQueue) {
            if (!readyQueue.contains(order)) {
                readyQueue.add(order);
            }
            sortReadyQueue();
        }

        notifyEnqueued(order);
        LOGGER.log(Level.INFO, "[PLANIFICADOR] Pedido #{0} admitido a la cola. Política activa: {1}",
                new Object[]{order.getId().toString().substring(0, 8), currentPolicy});
    }

    /**
     * Maneja la restitución de un pedido que fue expulsado manualmente para
     * resolver un Deadlock. Restaura las existencias reservadas y devuelve el
     * pedido a la Cola de Listos.
     *
     * @param rolledBackOrder Pedido expulsado que regresa a espera.
     */
    private void handleRollbackOrder(Order rolledBackOrder) {
        if (rolledBackOrder == null) {
            return;
        }

        activeOrders.remove(rolledBackOrder);

        // Restituir el stock reservado lógicamente ya que no llegó a despacharse
        for (Map.Entry<Product, Integer> item : rolledBackOrder.getItems().entrySet()) {
            item.getKey().addStock(item.getValue());
        }

        submitOrder(rolledBackOrder);
        LOGGER.log(Level.INFO, "[PLANIFICADOR] Pedido #{0} reintegrado a la cola de espera tras Rollback.",
                rolledBackOrder.getId().toString().substring(0, 8));
    }

    /**
     * Notifica al planificador que ha ingresado nuevo stock al inventario (vía
     * Tubería IPC), para reevaluar inmediatamente los pedidos que se
     * encontraban esperando disponibilidad.
     */
    public void reevaluateWaitingOrders() {
        synchronized (readyQueue) {
            for (Order order : readyQueue) {
                if (order.getState() == OrderState.ESPERANDO_RECURSOS && hasEnoughStock(order)) {
                    order.setState(OrderState.PENDIENTE);
                }
            }
            sortReadyQueue();
        }
        LOGGER.log(Level.INFO, "[PLANIFICADOR] Reevaluando cola de espera tras llegada de nuevo stock.");
    }

    /**
     * Cambia la política de planificación en caliente (Hot Swap) y reordena
     * inmediatamente la cola de listos para reflejar el nuevo algoritmo.
     *
     * @param newPolicy Nueva política de atención.
     */
    public void setPolicy(SchedulingPolicy newPolicy) {
        if (newPolicy == null || this.currentPolicy == newPolicy) {
            return;
        }

        this.currentPolicy = newPolicy;

        synchronized (readyQueue) {
            sortReadyQueue();
        }

        notifyPolicyChanged(newPolicy);
        LOGGER.log(Level.INFO, "[PLANIFICADOR] Política de atención cambiada a: {0}", newPolicy);
    }

    /**
     * Ordena la cola de listos utilizando la regla matemática de la política
     * actual.
     */
    private void sortReadyQueue() {
        readyQueue.sort(OrderComparatorFactory.getComparator(currentPolicy));
    }

    /**
     * Verifica si existen suficientes unidades disponibles en inventario para
     * todos los productos del pedido.
     *
     * @param order Pedido a verificar.
     * @return true si hay existencias suficientes; false si falta algún
     * artículo.
     */
    private synchronized boolean hasEnoughStock(Order order) {
        for (Map.Entry<Product, Integer> entry : order.getItems().entrySet()) {
            if (entry.getKey().getCurrentStock() < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verifica y descuenta atómicamente el stock requerido por un pedido antes
     * de despacharlo.
     *
     * @param order Pedido a despachar.
     * @return true si se pudo reservar el stock; false si no había
     * disponibilidad.
     */
    private synchronized boolean checkAndReserveStock(Order order) {
        if (!hasEnoughStock(order)) {
            return false;
        }
        for (Map.Entry<Product, Integer> entry : order.getItems().entrySet()) {
            entry.getKey().reduceStock(entry.getValue());
        }
        return true;
    }

    /**
     * Bucle continuo del despachador (Dispatcher Loop). Toma el primer pedido
     * en la cola de listos que cuente con stock disponible y delega su
     * ejecución concurrente a un OrderWorker.
     */
    @Override
    public void run() {
        while (running) {
            try {
                // Si existe un Deadlock activo sin resolver, pausar nuevos despachos hasta que el operador lo resuelva
                if (resourceManager.getActiveDeadlock() != null) {
                    Thread.sleep(250);
                    continue;
                }

                Order nextOrder = null;

                synchronized (readyQueue) {
                    if (currentPolicy == SchedulingPolicy.ENVEJECIMIENTO && !readyQueue.isEmpty()) {
                        sortReadyQueue();
                    }

                    // Buscar el primer pedido según la política que cuente con stock disponible
                    for (int i = 0; i < readyQueue.size(); i++) {
                        Order candidate = readyQueue.get(i);
                        if (checkAndReserveStock(candidate)) {
                            nextOrder = readyQueue.remove(i);
                            break;
                        } else {
                            // No hay stock suficiente todavía: queda en la cola esperando abastecimiento por el Pipe
                            candidate.setState(OrderState.ESPERANDO_RECURSOS);
                        }
                    }
                }

                if (nextOrder != null) {
                    final Order orderToDispatch = nextOrder;
                    activeOrders.add(orderToDispatch);

                    workerPool.submit(new OrderWorker(
                            orderToDispatch,
                            resourceManager,
                            warehouse,
                            metrics,
                            createInternalListener()
                    ));
                } else {
                    Thread.sleep(250);
                }

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Interceptor interno de eventos para remover pedidos terminados de
     * activeOrders.
     */
    private List<SchedulerListener> createInternalListener() {
        List<SchedulerListener> combined = new ArrayList<>(listeners);
        combined.add(new SchedulerListener() {
            @Override
            public void onOrderEnqueued(Order o) {
            }

            @Override
            public void onOrderStarted(Order o) {
            }

            @Override
            public void onOrderProgress(Order o, int p, int r) {
            }

            @Override
            public void onOrderCompleted(Order o) {
                activeOrders.remove(o);
            }

            @Override
            public void onPolicyChanged(SchedulingPolicy p) {
            }
        });
        return combined;
    }

    // Métodos de consulta y observadores
    public void addSchedulerListener(SchedulerListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeSchedulerListener(SchedulerListener listener) {
        listeners.remove(listener);
    }

    private void notifyEnqueued(Order order) {
        synchronized (listeners) {
            for (SchedulerListener l : listeners) {
                try {
                    l.onOrderEnqueued(order);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void notifyPolicyChanged(SchedulingPolicy policy) {
        synchronized (listeners) {
            for (SchedulerListener l : listeners) {
                try {
                    l.onPolicyChanged(policy);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * @return Vista inmutable de los pedidos esperando turno en la Ready Queue.
     */
    public List<Order> getReadyQueue() {
        synchronized (readyQueue) {
            return Collections.unmodifiableList(new ArrayList<>(readyQueue));
        }
    }

    /**
     * @return Vista inmutable de los pedidos procesándose actualmente.
     */
    public List<Order> getActiveOrders() {
        synchronized (activeOrders) {
            return Collections.unmodifiableList(new ArrayList<>(activeOrders));
        }
    }

    /**
     * @return Política de planificación vigente.
     */
    public SchedulingPolicy getCurrentPolicy() {
        return currentPolicy;
    }

    /**
     * @return Instancia con las métricas acumuladas de rendimiento.
     */
    public SchedulerMetrics getMetrics() {
        return metrics;
    }
}
