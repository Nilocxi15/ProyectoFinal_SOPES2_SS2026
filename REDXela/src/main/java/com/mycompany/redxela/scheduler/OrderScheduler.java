package com.mycompany.redxela.scheduler;

import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.models.OrderState;
import com.mycompany.redxela.models.Warehouse;
import com.mycompany.redxela.resources.ResourceManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * proceso según la política y realiza el traspaso de control (Context Switch)
 * hacia un hilo trabajador.
 *
 * 3. POLÍTICAS INTERCAMBIABLES EN CALIENTE: Permite alternar entre Prioridad
 * Estricta, Envejecimiento (Aging) y SJF en tiempo de ejecución para contrastar
 * su rendimiento empírico.
 */
public class OrderScheduler implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(OrderScheduler.class.getName());

    /**
     * Cola de Procesos Listos (Ready Queue) protegida para acceso concurrente.
     */
    private final List<Order> readyQueue;

    /**
     * Lista de pedidos en ejecución activa.
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
        // Permite procesar múltiples pedidos en paralelo hasta el límite de los semáforos
        this.workerPool = Executors.newFixedThreadPool(12);
        this.running = false;
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

        synchronized (readyQueue) {
            readyQueue.add(order);
            sortReadyQueue();
        }

        notifyEnqueued(order);
        LOGGER.log(Level.INFO, "[PLANIFICADOR] Pedido #{0} admitido a la cola. Política activa: {1}",
                new Object[]{order.getId().toString().substring(0, 8), currentPolicy});
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
     * Bucle continuo del despachador (Dispatcher Loop). Toma el pedido en
     * cabeza de la cola de listos y delega su ejecución a un Worker.
     */
    @Override
    public void run() {
        while (running) {
            try {
                Order nextOrder = null;

                synchronized (readyQueue) {
                    // Si estamos en política de envejecimiento, reordenamos dinámicamente según el paso del tiempo
                    if (currentPolicy == SchedulingPolicy.ENVEJECIMIENTO && !readyQueue.isEmpty()) {
                        sortReadyQueue();
                    }

                    if (!readyQueue.isEmpty()) {
                        nextOrder = readyQueue.remove(0);
                    }
                }

                if (nextOrder != null) {
                    final Order orderToDispatch = nextOrder;
                    activeOrders.add(orderToDispatch);

                    // Lanzamiento asíncrono en la piscina de trabajadores
                    workerPool.submit(new OrderWorker(
                            orderToDispatch,
                            resourceManager,
                            warehouse,
                            metrics,
                            createInternalListener(orderToDispatch)
                    ));
                } else {
                    // Espera pasiva para no sobrecargar CPU cuando la cola esté vacía
                    Thread.sleep(300);
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
    private List<SchedulerListener> createInternalListener(Order order) {
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
