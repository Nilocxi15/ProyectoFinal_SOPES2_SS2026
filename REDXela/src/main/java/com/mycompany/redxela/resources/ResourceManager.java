package com.mycompany.redxela.resources;

import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.models.OrderState;
import com.mycompany.redxela.models.ResourceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administrador Central de Recursos Compartidos del Centro de Distribución.
 *
 *
 * 1. SINCRONIZACIÓN Y EXCLUSIÓN MUTUA MEDIANTE SEMÁFOROS:
 *
 * - Cada uno de los 6 tipos de recursos finitos está protegido por una
 * instancia de {@link Semaphore}.
 *
 * - Los recursos múltiples (Empaque, Carga, Bodegueros, Montacargas, Calidad)
 * operan como Semáforos Contadores.
 *
 * - El Escáner de Códigos opera con capacidad 1, actuando como un Semáforo
 * Binario (Mutex).
 *
 *
 * 2. GRAFO DE ASIGNACIÓN DE RECURSOS (RAG - Resource Allocation Graph):
 *
 * - Mantiene la matriz de asignación (qué procesos tienen qué recursos) y la
 * matriz de peticiones (qué procesos están esperando qué recursos).
 *
 *
 * 3. DETECCIÓN Y RESOLUCIÓN MANUAL DE INTERBLOQUEOS (Deadlocks):
 *
 * - Detecta en tiempo real ciclos de Espera Circular (Condición de Coffman).
 *
 * - Implementa la técnica de Desapropiación con Retroceso (Preemption with
 * Rollback): interrumpe el hilo del proceso seleccionado como perdedor, libera
 * sus recursos, y lo reencola en la lista de espera del planificador,
 * permitiendo que el proceso ganador continúe sin bloqueos.
 */
public class ResourceManager {

    private static final Logger LOGGER = Logger.getLogger(ResourceManager.class.getName());

    /**
     * Instancias reales de semáforos por cada tipo de recurso del centro.
     */
    private final Map<ResourceType, Semaphore> semaphores;

    /**
     * Matriz RAG - Recursos Asignados: Recurso -> Conjunto de órdenes que lo
     * retienen.
     */
    private final Map<ResourceType, Set<Order>> resourceHolders;

    /**
     * Matriz RAG - Solicitudes Pendientes: Recurso -> Conjunto de órdenes en
     * espera.
     */
    private final Map<ResourceType, Set<Order>> resourceWaiters;

    /**
     * Contabilidad por Proceso: ID de Orden -> Mapa de (Recurso, Cantidad
     * Asignada).
     */
    private final Map<UUID, Map<ResourceType, Integer>> allocations;

    /**
     * Solicitud Activa por Proceso: ID de Orden -> Recurso por el cual está
     * bloqueado actualmente.
     */
    private final Map<UUID, ResourceType> waitingFor;

    /**
     * Registro de Hilos por Proceso: ID de Orden -> Hilo trabajador que ejecuta
     * la orden (para Preemption).
     */
    private final Map<UUID, Thread> activeOrderThreads;

    /**
     * Lista de oyentes para notificación inmediata de interbloqueos detectados.
     */
    private final List<DeadlockListener> deadlockListeners;

    /**
     * Callback hacia el planificador para reencolar órdenes expulsadas tras un
     * Rollback de Deadlock.
     */
    private Consumer<Order> rollbackHandler;

    /**
     * Último evento de Deadlock activo pendiente de resolución manual.
     */
    private volatile DeadlockEvent activeDeadlock;

    /**
     * Inicializa los semáforos del centro de distribución con sus capacidades
     * reales.
     */
    public ResourceManager() {
        this.semaphores = new EnumMap<>(ResourceType.class);
        this.resourceHolders = new EnumMap<>(ResourceType.class);
        this.resourceWaiters = new EnumMap<>(ResourceType.class);
        this.allocations = new HashMap<>();
        this.waitingFor = new HashMap<>();
        this.activeOrderThreads = new HashMap<>();
        this.deadlockListeners = Collections.synchronizedList(new ArrayList<>());

        // Inicialización de semáforos con política FIFO (fairness = true)
        for (ResourceType type : ResourceType.values()) {
            semaphores.put(type, new Semaphore(type.getTotalCapacity(), true));
            resourceHolders.put(type, Collections.synchronizedSet(new HashSet<>()));
            resourceWaiters.put(type, Collections.synchronizedSet(new HashSet<>()));
        }
    }

    /**
     * Configura el manejador de Rollback que devuelve una orden expulsada a la
     * cola de espera del planificador.
     *
     * @param rollbackHandler Acción de reencolado en el planificador.
     */
    public void setRollbackHandler(Consumer<Order> rollbackHandler) {
        this.rollbackHandler = rollbackHandler;
    }

    /**
     * Solicita la asignación de un recurso para una orden. Operación bloqueante
     * regulada por el semáforo correspondiente.
     *
     * @param order Pedido que solicita el recurso.
     * @param resource Tipo de recurso físico.
     * @param count Cantidad de permisos requeridos.
     * @return true si el recurso fue adquirido con éxito; false si el hilo fue
     * interrumpido o expulsado por Rollback.
     */
    public boolean acquireResource(Order order, ResourceType resource, int count) {
        if (order == null || resource == null || count <= 0) {
            return false;
        }

        Semaphore sem = semaphores.get(resource);
        if (sem == null) {
            return false;
        }

        // Registro de intención en el Grafo de Asignación (RAG)
        synchronized (this) {
            activeOrderThreads.put(order.getId(), Thread.currentThread());
            waitingFor.put(order.getId(), resource);
            resourceWaiters.get(resource).add(order);
            order.setState(OrderState.ESPERANDO_RECURSOS);

            // Si no hay permisos suficientes disponibles inmediatamente, verificar si se cerró un ciclo de Deadlock
            if (sem.availablePermits() < count) {
                DeadlockEvent deadlock = checkDeadlockCycle();
                if (deadlock != null) {
                    this.activeDeadlock = deadlock;
                    deadlock.getOrderA().setState(OrderState.BLOQUEADO);
                    deadlock.getOrderB().setState(OrderState.BLOQUEADO);
                    notifyDeadlock(deadlock);
                }
            }
        }

        try {
            LOGGER.log(Level.INFO, "[RECURSOS] Pedido #{0} solicitando {1} unidad(es) de [{2}]...",
                    new Object[]{order.getId().toString().substring(0, 8), count, resource.getDisplayName()});

            // Llamada bloqueante al semáforo real (espera pasiva si no hay permisos)
            sem.acquire(count);

            // Transición a estado Poseedor tras adquirir el permiso
            synchronized (this) {
                waitingFor.remove(order.getId());
                resourceWaiters.get(resource).remove(order);

                resourceHolders.get(resource).add(order);
                allocations.computeIfAbsent(order.getId(), k -> new EnumMap<>(ResourceType.class))
                        .merge(resource, count, Integer::sum);
            }

            LOGGER.log(Level.INFO, "[RECURSOS] Pedido #{0} ADQUIRIÓ [{1}]. Permisos restantes: {2}",
                    new Object[]{order.getId().toString().substring(0, 8), resource.getDisplayName(), sem.availablePermits()});

            return true;

        } catch (InterruptedException ex) {
            // Manejo de expulsión / preemption por resolución de Deadlock
            synchronized (this) {
                waitingFor.remove(order.getId());
                resourceWaiters.get(resource).remove(order);
            }
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "[RECURSOS] Adquisición interrumpida (Preemption) para pedido #{0}",
                    order.getId().toString().substring(0, 8));
            return false;
        }
    }

    /**
     * Libera una cantidad de permisos de un recurso asignado a una orden.
     *
     * @param order Pedido que devuelve el recurso.
     * @param resource Tipo de recurso.
     * @param count Cantidad de permisos a liberar.
     */
    public synchronized void releaseResource(Order order, ResourceType resource, int count) {
        if (order == null || resource == null || count <= 0) {
            return;
        }

        Map<ResourceType, Integer> orderAlloc = allocations.get(order.getId());
        if (orderAlloc == null || !orderAlloc.containsKey(resource)) {
            return;
        }

        int currentCount = orderAlloc.get(resource);
        int toRelease = Math.min(count, currentCount);

        if (toRelease >= currentCount) {
            orderAlloc.remove(resource);
            resourceHolders.get(resource).remove(order);
        } else {
            orderAlloc.put(resource, currentCount - toRelease);
        }

        if (orderAlloc.isEmpty()) {
            allocations.remove(order.getId());
        }

        // Liberación física en el semáforo (despierta hilos en espera)
        Semaphore sem = semaphores.get(resource);
        if (sem != null) {
            sem.release(toRelease);
            LOGGER.log(Level.INFO, "[RECURSOS] Pedido #{0} LIBERÓ {1} de [{2}]. Disponibles: {3}",
                    new Object[]{order.getId().toString().substring(0, 8), toRelease, resource.getDisplayName(), sem.availablePermits()});
        }
    }

    /**
     * Libera de forma total e inmediata todos los recursos retenidos por una
     * orden. Utilizado al concluir el despacho de un pedido o al realizar un
     * rollback por Deadlock.
     *
     * @param order Pedido cuyos recursos se devolverán al centro.
     */
    public synchronized void releaseAllResources(Order order) {
        if (order == null) {
            return;
        }

        Map<ResourceType, Integer> orderAlloc = allocations.remove(order.getId());
        waitingFor.remove(order.getId());
        activeOrderThreads.remove(order.getId());

        for (Set<Order> waiters : resourceWaiters.values()) {
            waiters.remove(order);
        }

        if (orderAlloc != null) {
            for (Map.Entry<ResourceType, Integer> entry : orderAlloc.entrySet()) {
                ResourceType type = entry.getKey();
                int count = entry.getValue();

                resourceHolders.get(type).remove(order);
                Semaphore sem = semaphores.get(type);
                if (sem != null) {
                    sem.release(count);
                }
            }
            LOGGER.log(Level.INFO, "[RECURSOS] Todos los recursos liberados para pedido #{0}",
                    order.getId().toString().substring(0, 8));
        }
    }

    /**
     * Algoritmo de Detección de Deadlock basado en ciclos del Grafo de
     * Asignación de Recursos (RAG).
     *
     * Busca la condición de Espera Circular entre pares de procesos:
     *
     * - Orden A retiene R_A y espera R_B.
     *
     * - Orden B retiene R_B y espera R_A.
     *
     * @return Evento de interbloqueo con los detalles del ciclo, o null si no
     * hay deadlock.
     */
    public synchronized DeadlockEvent checkDeadlockCycle() {
        for (Map.Entry<UUID, ResourceType> entryA : waitingFor.entrySet()) {
            UUID idA = entryA.getKey();
            ResourceType resEsperadoPorA = entryA.getValue();

            // Buscar qué procesos retienen el recurso que A está esperando
            Set<Order> poseedoresDeResB = resourceHolders.get(resEsperadoPorA);
            if (poseedoresDeResB == null) {
                continue;
            }

            for (Order orderB : poseedoresDeResB) {
                UUID idB = orderB.getId();
                if (idA.equals(idB)) {
                    continue;
                }

                // Verificar si B está a su vez esperando algún recurso
                ResourceType resEsperadoPorB = waitingFor.get(idB);
                if (resEsperadoPorB != null) {
                    // Verificar si A retiene el recurso que B está esperando (Ciclo cerrado A -> B -> A)
                    Map<ResourceType, Integer> recursosDeA = allocations.get(idA);
                    if (recursosDeA != null && recursosDeA.containsKey(resEsperadoPorB)) {
                        Order orderA = findOrderById(idA);
                        if (orderA != null) {
                            return new DeadlockEvent(orderA, resEsperadoPorB, resEsperadoPorA,
                                    orderB, resEsperadoPorA, resEsperadoPorB);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Resuelve manualmente un interbloqueo aplicando Desapropiación con
     * Retroceso (Preemption & Rollback):
     *
     * 1. El hilo del pedido perdedor (loser) es interrumpido para sacarlo del
     * bloqueo del semáforo.
     *
     * 2. El pedido perdedor es despojado de todos los recursos que tenía
     * asignados y se liberan al semáforo.
     *
     * 3. El pedido ganador (winner) despierta inmediatamente al liberarse el
     * recurso disputado y continúa.
     *
     * 4. El pedido perdedor cambia a estado PENDIENTE y regresa a la cola de
     * espera del planificador.
     *
     * @param winner Pedido que tiene autorización para continuar.
     * @param loser Pedido que será expulsado temporalmente y revertido a la
     * cola.
     */
    public void resolveDeadlock(Order winner, Order loser) {
        if (winner == null || loser == null) {
            return;
        }

        Thread loserThread;
        synchronized (this) {
            LOGGER.log(Level.WARNING, "[DEADLOCK RESUELTO] Ganador: Pedido #{0} | Expulsado con Rollback: Pedido #{1}",
                    new Object[]{winner.getId().toString().substring(0, 8), loser.getId().toString().substring(0, 8)});

            this.activeDeadlock = null;
            loserThread = activeOrderThreads.get(loser.getId());

            // Liberar todos los recursos que retenía el perdedor (desbloquea al ganador)
            releaseAllResources(loser);
            loser.setState(OrderState.PENDIENTE);

            // Interrumpir el hilo del perdedor que estaba esperando en sem.acquire()
            if (loserThread != null) {
                loserThread.interrupt();
            }

            // Actualizar estado del ganador para continuar su adquisición
            winner.setState(OrderState.ESPERANDO_RECURSOS);
        }

        // Reencolar el pedido expulsado en la lista de espera del planificador
        if (rollbackHandler != null) {
            rollbackHandler.accept(loser);
        }
    }

    /**
     * Obtiene el interbloqueo activo actualmente si existe uno sin resolver.
     *
     * @return Evento de interbloqueo activo, o null si el sistema opera sin
     * bloqueo circular.
     */
    public DeadlockEvent getActiveDeadlock() {
        return activeDeadlock;
    }

    /**
     * Genera una captura del estado de los 6 recursos compartidos para la
     * interfaz gráfica.
     *
     * @return Lista con el estado de cada recurso, su capacidad, en uso y qué
     * pedidos los retienen.
     */
    public synchronized List<ResourceState> getResourceStates() {
        List<ResourceState> states = new ArrayList<>();

        for (ResourceType type : ResourceType.values()) {
            Semaphore sem = semaphores.get(type);
            int total = type.getTotalCapacity();
            int available = sem.availablePermits();
            int inUse = total - available;

            List<String> holdersList = new ArrayList<>();
            for (Order o : resourceHolders.get(type)) {
                holdersList.add("Pedido #" + o.getId().toString().substring(0, 8) + " (" + o.getMerchandiseType().getName() + ")");
            }

            List<String> waitersList = new ArrayList<>();
            for (Order o : resourceWaiters.get(type)) {
                waitersList.add("Pedido #" + o.getId().toString().substring(0, 8) + " (" + o.getMerchandiseType().getName() + ")");
            }

            states.add(new ResourceState(type, total, available, inUse, holdersList, waitersList));
        }

        return states;
    }

    /**
     * Calcula el porcentaje de ocupación global promedio de todos los recursos
     * del centro.
     *
     * @return Porcentaje entre 0.0 y 100.0.
     */
    public synchronized double getOverallResourceUtilization() {
        int totalCap = 0;
        int totalInUse = 0;
        for (ResourceType type : ResourceType.values()) {
            int cap = type.getTotalCapacity();
            int avail = semaphores.get(type).availablePermits();
            totalCap += cap;
            totalInUse += (cap - avail);
        }
        return totalCap == 0 ? 0.0 : ((double) totalInUse / totalCap) * 100.0;
    }

    /**
     * Registra un oyente para ser notificado de eventos de Deadlock.
     *
     * @param listener Observador a registrar.
     */
    public void addDeadlockListener(DeadlockListener listener) {
        if (listener != null) {
            deadlockListeners.add(listener);
        }
    }

    /**
     * Notifica a todos los oyentes de la presencia de un Deadlock.
     *
     * @param event Información del interbloqueo detectado.
     */
    private void notifyDeadlock(DeadlockEvent event) {
        synchronized (deadlockListeners) {
            for (DeadlockListener listener : deadlockListeners) {
                try {
                    listener.onDeadlockDetected(event);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error al notificar Deadlock: " + ex.getMessage(), ex);
                }
            }
        }
    }

    /**
     * Busca una orden por su identificador entre las que están activas en el
     * administrador.
     */
    private Order findOrderById(UUID id) {
        for (Set<Order> holders : resourceHolders.values()) {
            for (Order o : holders) {
                if (o.getId().equals(id)) {
                    return o;
                }
            }
        }
        for (Set<Order> waiters : resourceWaiters.values()) {
            for (Order o : waiters) {
                if (o.getId().equals(id)) {
                    return o;
                }
            }
        }
        return null;
    }

    /**
     * @return Semáforo subyacente para un tipo de recurso.
     */
    public Semaphore getSemaphore(ResourceType type) {
        return semaphores.get(type);
    }
}
