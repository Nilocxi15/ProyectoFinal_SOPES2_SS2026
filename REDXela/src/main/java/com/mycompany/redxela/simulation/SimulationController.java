package com.mycompany.redxela.simulation;

import com.mycompany.redxela.ipc.InventoryArea;
import com.mycompany.redxela.ipc.ReceptionArea;
import com.mycompany.redxela.ipc.StockEventListener;
import com.mycompany.redxela.ipc.WarehousePipe;
import com.mycompany.redxela.models.Client;
import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.models.Product;
import com.mycompany.redxela.models.ServiceTier;
import com.mycompany.redxela.models.Warehouse;
import com.mycompany.redxela.resources.DeadlockListener;
import com.mycompany.redxela.resources.ResourceManager;
import com.mycompany.redxela.resources.ResourceState;
import com.mycompany.redxela.scheduler.OrderScheduler;
import com.mycompany.redxela.scheduler.SchedulerListener;
import com.mycompany.redxela.scheduler.SchedulerMetrics;
import com.mycompany.redxela.scheduler.SchedulingPolicy;
import com.mycompany.redxela.util.MerchandiseCatalog;
import com.mycompany.redxela.util.ProductCatalog;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controlador y Fachada Central (Facade / Singleton) del Sistema Operativo de
 * Simulación REDXela.
 *
 * Esta clase integra y orquesta todos los subsistemas del backend:
 *
 * 1. Gestión de Almacenamiento No Contiguo ({@link Warehouse}).
 *
 * 2. Comunicación Entre Procesos mediante Tuberías
 * ({@link WarehousePipe}, {@link ReceptionArea}, {@link InventoryArea}).
 *
 * 3. Sincronización con Semáforos y Detección de Deadlocks
 * ({@link ResourceManager}).
 *
 * 4. Planificador de Procesos y Políticas de Atención ({@link OrderScheduler}).
 *
 * 5. Generador Automático de Tráfico ({@link OrderTrafficGenerator}).
 *
 * Permite que las ventanas de la interfaz gráfica Swing (Home, CreateOrder,
 * etc.) se conecten al backend a través de un único punto de acceso limpio y
 * seguro.
 */
public class SimulationController {

    private static final Logger LOGGER = Logger.getLogger(SimulationController.class.getName());

    /**
     * Instancia única (Singleton) del controlador central.
     */
    private static SimulationController instance;

    private final Warehouse warehouse;
    private final WarehousePipe warehousePipe;
    private final ReceptionArea receptionArea;
    private final InventoryArea inventoryArea;
    private final ResourceManager resourceManager;
    private final OrderScheduler scheduler;
    private final OrderTrafficGenerator trafficGenerator;

    /**
     * Inicializa e interconecta todos los subsistemas del centro de
     * distribución.
     */
    private SimulationController() {
        this.warehouse = new Warehouse();
        this.resourceManager = new ResourceManager();
        this.scheduler = new OrderScheduler(this.resourceManager, this.warehouse);
        this.trafficGenerator = new OrderTrafficGenerator(this.scheduler);

        // Poblar el almacén físico con las existencias iniciales del catálogo predefinido
        initializeWarehouseWithCatalogStock();

        // Inicializar la tubería IPC y las áreas de Recepción e Inventario
        WarehousePipe tempPipe = null;
        ReceptionArea tempReception = null;
        InventoryArea tempInventory = null;
        try {
            tempPipe = new WarehousePipe();
            tempReception = new ReceptionArea(tempPipe);
            tempInventory = new InventoryArea(tempPipe, this.warehouse);

            // Vincular la llegada de stock por el Pipe con la reevaluación de pedidos en espera
            tempInventory.addStockEventListener(event -> scheduler.reevaluateWaitingOrders());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error crítico al inicializar la tubería IPC: " + ex.getMessage(), ex);
        }

        this.warehousePipe = tempPipe;
        this.receptionArea = tempReception;
        this.inventoryArea = tempInventory;

        // Arrancar los daemons de Inventario y del Planificador automáticamente
        startCoreServices();
    }

    /**
     * Obtiene la instancia global del controlador de simulación.
     *
     * @return Instancia única de SimulationController.
     */
    public static synchronized SimulationController getInstance() {
        if (instance == null) {
            instance = new SimulationController();
        }
        return instance;
    }

    /**
     * Asigna los bloques iniciales en el almacén para reflejar el stock inicial
     * del catálogo.
     */
    private void initializeWarehouseWithCatalogStock() {
        for (Product product : ProductCatalog.getAll()) {
            if (product.getCurrentStock() > 0) {
                try {
                    warehouse.allocateUnits(product, product.getCurrentStock());
                } catch (IllegalStateException ex) {
                    LOGGER.log(Level.WARNING, "Capacidad inicial excedida para {0}: {1}",
                            new Object[]{product.getName(), ex.getMessage()});
                }
            }
        }
    }

    /**
     * Inicia los hilos base del sistema (Consumidor de Inventario IPC y
     * Despachador del Planificador).
     */
    public final void startCoreServices() {
        if (inventoryArea != null && !inventoryArea.isRunning()) {
            inventoryArea.start();
        }
        scheduler.start();
    }

    /**
     * Detiene ordenadamente todos los hilos y cierra la tubería IPC.
     */
    public void shutdown() {
        trafficGenerator.stop();
        scheduler.stop();
        if (inventoryArea != null) {
            inventoryArea.stop();
        }
        if (warehousePipe != null) {
            warehousePipe.close();
        }
    }

    // MÉTODOS DE ACCIÓN DIRECTA PARA LA INTERFAZ GRÁFICA (FRONTEND SWING)    
    /**
     * Registra manualmente un nuevo pedido creado por el usuario desde la GUI.
     *
     * @param order Pedido construido en el formulario CreateOrder.
     */
    public void submitManualOrder(Order order) {
        scheduler.submitOrder(order);
    }

    /**
     * Genera e inyecta inmediatamente un pedido aleatorio de prueba.
     *
     * @return El pedido generado.
     */
    public Order injectSingleRandomOrder() {
        Order order = trafficGenerator.generateSingleRandomOrder();
        scheduler.submitOrder(order);
        return order;
    }

    /**
     * Inicia o detiene el generador automático continuo de pedidos.
     *
     * @param enabled true para encender la generación continua, false para
     * pausarla.
     */
    public void setAutoGeneratorEnabled(boolean enabled) {
        if (enabled) {
            trafficGenerator.start();
        } else {
            trafficGenerator.stop();
        }
    }

    /**
     * @return true si el generador automático está activo.
     */
    public boolean isAutoGeneratorRunning() {
        return trafficGenerator.isRunning();
    }

    /**
     * Registra la llegada de nueva mercadería en el área de Recepción. Envía
     * los datos por la Tubería (Pipe IPC) hacia el área de Inventario.
     *
     * @param product Producto existente seleccionado del catálogo.
     * @param quantity Cantidad de unidades recibidas en el camión.
     * @return true si se envió correctamente por la tubería.
     */
    public boolean receiveMerchandise(Product product, int quantity) {
        if (receptionArea == null) {
            return false;
        }
        return receptionArea.receiveStock(product, quantity);
    }

    /**
     * Cambia en caliente la política de planificación de pedidos.
     *
     * @param policy Nueva política (PRIORIDAD_ESTRICTA, ENVEJECIMIENTO,
     * MENOR_TRABAJO_PRIMERO).
     */
    public void changeSchedulingPolicy(SchedulingPolicy policy) {
        scheduler.setPolicy(policy);
    }

    /**
     * Provoca deliberadamente la situación de Conflicto de Recursos (Deadlock):
     * Inyecta dos pedidos EXPRES simultáneos con orden inverso de adquisición
     * de recursos:
     *
     * - Pedido 1 (Electrónico de Alto Valor): solicita ESCANEAR_CODIGOS (1) ->
     * MONTACARGAS (2).
     *
     * - Pedido 2 (Carga Especial): solicita MONTACARGAS (2) -> ESCANEAR_CODIGOS
     * (1).
     */
    public void triggerDeliberateDeadlock() {
        Product elecProduct = null;
        Product specialProduct = null;

        for (Product p : ProductCatalog.getAll()) {
            if (p.getMerchandise().equals(MerchandiseCatalog.ELECTRONICO) && elecProduct == null) {
                elecProduct = p;
            } else if (p.getMerchandise().equals(MerchandiseCatalog.CARGA_ESPECIAL) && specialProduct == null) {
                specialProduct = p;
            }
        }

        if (elecProduct == null || specialProduct == null) {
            return;
        }

        // Asegurar que haya stock suficiente para que ambos se despachen de inmediato
        if (elecProduct.getCurrentStock() < 2) {
            elecProduct.addStock(5);
            warehouse.allocateUnits(elecProduct, 5);
        }
        if (specialProduct.getCurrentStock() < 2) {
            specialProduct.addStock(5);
            warehouse.allocateUnits(specialProduct, 5);
        }

        Client clientA = new Client(UUID.randomUUID(), "Corporación TechXela (Conflicto A)", "502-77610001", "Zona 3, Xela");
        Order orderA = new Order(UUID.randomUUID(), clientA, ServiceTier.EXPRES, MerchandiseCatalog.ELECTRONICO);
        orderA.addProduct(elecProduct, 2);

        Client clientB = new Client(UUID.randomUUID(), "Químicos de Occidente (Conflicto B)", "502-77610002", "Zona 1, Xela");
        Order orderB = new Order(UUID.randomUUID(), clientB, ServiceTier.EXPRES, MerchandiseCatalog.CARGA_ESPECIAL);
        orderB.addProduct(specialProduct, 2);

        scheduler.submitOrder(orderA);
        scheduler.submitOrder(orderB);

        LOGGER.log(Level.WARNING, "[SIMULACIÓN] Escenario de Deadlock deliberado disparado: Pedido #{0} vs Pedido #{1}",
                new Object[]{orderA.getId().toString().substring(0, 8), orderB.getId().toString().substring(0, 8)});
    }

    /**
     * Resuelve manualmente el conflicto de recursos (Deadlock) seleccionando
     * cuál operación continúa con los recursos disputados y cuál regresa a la
     * cola de espera.
     *
     * @param winner Pedido que continuará su procesamiento.
     * @param loser Pedido que liberará sus recursos y volverá a la lista de
     * espera.
     */
    public void resolveDeadlock(Order winner, Order loser) {
        resourceManager.resolveDeadlock(winner, loser);
    }

    // SUSCRIPCIÓN DE EVENTOS Y CONSULTA DE ESTADO PARA LA GUI    
    public void addSchedulerListener(SchedulerListener listener) {
        scheduler.addSchedulerListener(listener);
    }

    public void addDeadlockListener(DeadlockListener listener) {
        resourceManager.addDeadlockListener(listener);
    }

    public void addStockEventListener(StockEventListener listener) {
        if (inventoryArea != null) {
            inventoryArea.addStockEventListener(listener);
        }
    }

    public List<ResourceState> getResourceStates() {
        return resourceManager.getResourceStates();
    }

    public List<Order> getReadyQueue() {
        return scheduler.getReadyQueue();
    }

    public List<Order> getActiveOrders() {
        return scheduler.getActiveOrders();
    }

    public SchedulerMetrics getMetrics() {
        return scheduler.getMetrics();
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    public OrderScheduler getScheduler() {
        return scheduler;
    }

    public OrderTrafficGenerator getTrafficGenerator() {
        return trafficGenerator;
    }
}
