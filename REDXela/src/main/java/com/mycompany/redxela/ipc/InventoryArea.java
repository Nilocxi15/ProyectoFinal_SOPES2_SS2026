package com.mycompany.redxela.ipc;

import com.mycompany.redxela.models.Product;
import com.mycompany.redxela.models.Warehouse;
import com.mycompany.redxela.models.WarehouseBlock;
import com.mycompany.redxela.util.ProductCatalog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Representa el Área de Control de Inventario de REDXela (Proceso Consumidor).
 *
 * - Funciona como la entidad CONSUMIDORA dentro del patrón
 * Productor-Consumidor.
 *
 * - Ejecuta un hilo independiente (Worker Thread) que consume información de la
 * tubería a su propio ritmo de procesamiento.
 *
 * - Desacoplamiento total: No interactúa con Recepción ni conoce su estado
 * interno; únicamente consume bytes/caracteres del descriptor de lectura del
 * canal IPC.
 *
 * - Automatización del flujo de entrada: 1. Lee el mensaje bloqueante de la
 * tubería. 2. Deserializa el identificador del producto y la cantidad recibida.
 * 3. Actualiza el stock consolidado del producto. 4. Ejecuta el algoritmo de
 * Asignación No Contigua en el Almacén (Warehouse). 5. Dispara eventos
 * asíncronos hacia la GUI y hacia el planificador para reactivar pedidos.
 */
public class InventoryArea implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(InventoryArea.class.getName());

    /**
     * Canal de comunicación de donde se consumen los lotes enviados por
     * Recepción.
     */
    private final WarehousePipe pipe;

    /**
     * Estructura física del almacén donde se asignan los bloques físicos.
     */
    private final Warehouse warehouse;

    /**
     * Lista de observadores registrados para recibir notificaciones de nuevo
     * stock.
     */
    private final List<StockEventListener> listeners;

    /**
     * Hilo de ejecución en segundo plano para el procesamiento continuo de la
     * tubería.
     */
    private Thread workerThread;

    /**
     * Bandera atómica de control para el ciclo de vida del hilo de ejecución.
     */
    private volatile boolean running;

    /**
     * Construye el área de inventario vinculada a la tubería y al almacén
     * físico.
     *
     * @param pipe Tubería IPC compartida.
     * @param warehouse Almacén físico donde se ubican los lotes.
     */
    public InventoryArea(WarehousePipe pipe, Warehouse warehouse) {
        this.pipe = Objects.requireNonNull(pipe, "La tubería IPC no puede ser nula");
        this.warehouse = Objects.requireNonNull(warehouse, "El almacén no puede ser nulo");
        this.listeners = Collections.synchronizedList(new ArrayList<>());
        this.running = false;
    }

    /**
     * Inicia el hilo consumidor en segundo plano para procesar la tubería de
     * forma continua.
     */
    public synchronized void start() {
        if (!running) {
            this.running = true;
            this.workerThread = new Thread(this, "Thread-AreaInventario-Consumidor");
            this.workerThread.setDaemon(true); // Permite cierre limpio con la aplicación
            this.workerThread.start();
            LOGGER.log(Level.INFO, "Área de Inventario iniciada. Escuchando tubería IPC...");
        }
    }

    /**
     * Detiene la ejecución del hilo consumidor de forma controlada.
     */
    public synchronized void stop() {
        if (running) {
            this.running = false;
            if (workerThread != null) {
                workerThread.interrupt();
            }
            LOGGER.log(Level.INFO, "Área de Inventario detenida.");
        }
    }

    /**
     * Ciclo principal del hilo consumidor (Consumer Loop). Ejecuta una lectura
     * bloqueante sobre la tubería, garantizando cero consumo de CPU mientras no
     * exista mercadería por procesar.
     */
    @Override
    public void run() {
        while (running && !pipe.isClosed()) {
            try {
                // Lectura bloqueante sobre el búfer circular de la tubería
                String rawMessage = pipe.readMessage();

                if (rawMessage == null) {
                    // Fin de la tubería (EOF o extremo escritor cerrado)
                    break;
                }

                if (rawMessage.trim().isEmpty()) {
                    continue;
                }

                // Procesamiento y deserialización del mensaje recibido
                processStockMessage(rawMessage.trim());

            } catch (IOException ex) {
                if (running) {
                    LOGGER.log(Level.WARNING, "Error de lectura en la tubería de inventario: " + ex.getMessage());
                }
                break;
            }
        }
        running = false;
    }

    /**
     * Parsea el mensaje del protocolo IPC, actualiza el catálogo y reserva
     * bloques en el almacén.
     *
     * Protocolo esperado: "UUID_PRODUCTO:CANTIDAD"
     *
     * @param message Cadena textual recibida por la tubería.
     */
    private void processStockMessage(String message) {
        String[] parts = message.split(":");
        if (parts.length != 2) {
            LOGGER.log(Level.WARNING, "Mensaje malformado recibido en la tubería: {0}", message);
            return;
        }

        try {
            UUID productId = UUID.fromString(parts[0]);
            int quantity = Integer.parseInt(parts[1]);

            // 1. Localizar el producto en el catálogo central
            Product product = ProductCatalog.findById(productId);
            if (product == null) {
                LOGGER.log(Level.WARNING, "Producto no encontrado en el catálogo para ID: {0}", productId);
                return;
            }

            // 2. Actualizar el stock consolidado del producto (método sincronizado)
            product.addStock(quantity);

            // 3. Asignación física no contigua en el almacén
            List<WarehouseBlock> allocatedBlocks = warehouse.allocateUnits(product, quantity);

            LOGGER.log(Level.INFO, "[PIPE -> INVENTARIO] Lote procesado: +{0} de '{1}'. Asignado en {2} bloque(s) de almacén.",
                    new Object[]{quantity, product.getName(), allocatedBlocks.size()});

            // 4. Construir evento y emitir notificación a los suscriptores (GUI y Planificador)
            StockArrivalEvent event = new StockArrivalEvent(product, quantity, allocatedBlocks);
            notifyListeners(event);

        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.SEVERE, "Error en el formato de datos del mensaje IPC: " + ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "Fallo de capacidad en el almacén: " + ex.getMessage(), ex);
        }
    }

    /**
     * Registra un observador para ser notificado de eventos de nuevo
     * inventario.
     *
     * @param listener Instancia que implementa StockEventListener.
     */
    public void addStockEventListener(StockEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Remueve un observador previamente registrado.
     *
     * @param listener Observador a desuscribir.
     */
    public void removeStockEventListener(StockEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Emite el evento de llegada a todos los observadores registrados.
     *
     * @param event Información del lote procesado.
     */
    private void notifyListeners(StockArrivalEvent event) {
        synchronized (listeners) {
            for (StockEventListener listener : listeners) {
                try {
                    listener.onStockArrived(event);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error al notificar al oyente de inventario: " + ex.getMessage(), ex);
                }
            }
        }
    }

    /**
     * @return true si el hilo consumidor está en ejecución; false en caso
     * contrario.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return Referencia al almacén gestionado por esta área.
     */
    public Warehouse getWarehouse() {
        return warehouse;
    }
}
