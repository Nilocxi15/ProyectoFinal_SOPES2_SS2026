package com.mycompany.redxela.simulation;

import com.mycompany.redxela.models.Client;
import com.mycompany.redxela.models.Merchandise;
import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.models.Product;
import com.mycompany.redxela.models.ServiceTier;
import com.mycompany.redxela.scheduler.OrderScheduler;
import com.mycompany.redxela.util.ProductCatalog;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generador automático de pedidos continuos para la simulación de REDXela.
 *
 * - Simula la tasa de arribo continuo de procesos hacia la Cola de Listos
 * (Ready Queue).
 *
 * - Genera pedidos con variabilidad en niveles de servicio (1 a 5) y tiempos de
 * ráfaga (Burst Time), lo que somete al planificador y a los semáforos del
 * sistema a condiciones de estrés y contención real.
 *
 * - Opera en un hilo secundario independiente (Background Daemon Thread),
 * permitiendo ser iniciado, pausado o ajustado en su frecuencia en caliente
 * desde la interfaz gráfica.
 */
public class OrderTrafficGenerator implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(OrderTrafficGenerator.class.getName());

    /**
     * Nombres predefinidos para simular clientes realistas en las cuentas
     * comerciales.
     */
    private static final String[] CLIENT_NAMES = {
        "Distribuidora Los Altos", "Comercial Quetzal", "Supermercados La Unión",
        "Tecnología Xela", "Farmacias del Sur", "Librería Central", "Importadora Maya",
        "Electrónica Valle", "Suministros de Occidente", "Industrias Quetzaltecas"
    };

    /**
     * Planificador al cual se enviarán los pedidos generados.
     */
    private final OrderScheduler scheduler;

    /**
     * Generador de números pseudoaleatorios.
     */
    private final Random random;

    /**
     * Intervalo mínimo entre llegadas de pedidos en milisegundos.
     */
    private volatile int minIntervalMs;

    /**
     * Intervalo máximo entre llegadas de pedidos en milisegundos.
     */
    private volatile int maxIntervalMs;

    /**
     * Hilo de ejecución continua en segundo plano.
     */
    private Thread generatorThread;

    /**
     * Bandera de control del ciclo de vida del generador.
     */
    private volatile boolean running;

    /**
     * Contador total de pedidos generados automáticamente.
     */
    private int generatedCount;

    /**
     * Construye el generador automático de carga vinculado al planificador
     * central.
     *
     * @param scheduler Instancia del planificador de procesos de REDXela.
     */
    public OrderTrafficGenerator(OrderScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "El planificador no puede ser nulo");
        this.random = new Random();
        this.minIntervalMs = 2000; // 2 segundos
        this.maxIntervalMs = 4000; // 4 segundos
        this.running = false;
        this.generatedCount = 0;
    }

    /**
     * Inicia la generación automática continua en un hilo secundario.
     */
    public synchronized void start() {
        if (!running) {
            this.running = true;
            this.generatorThread = new Thread(this, "Thread-Generador-Trafico-Pedidos");
            this.generatorThread.setDaemon(true);
            this.generatorThread.start();
            LOGGER.log(Level.INFO, "Generador automático de pedidos INICIADO (Intervalo: {0}-{1} ms)",
                    new Object[]{minIntervalMs, maxIntervalMs});
        }
    }

    /**
     * Detiene la generación automática continua.
     */
    public synchronized void stop() {
        if (running) {
            this.running = false;
            if (generatorThread != null) {
                generatorThread.interrupt();
            }
            LOGGER.log(Level.INFO, "Generador automático de pedidos DETENIDO. Total generados: {0}", generatedCount);
        }
    }

    /**
     * Bucle continuo del hilo generador de tráfico.
     */
    @Override
    public void run() {
        while (running) {
            try {
                // Cálculo de tiempo de espera estocástico entre arribos
                int delay = minIntervalMs + random.nextInt(Math.max(1, maxIntervalMs - minIntervalMs + 1));
                Thread.sleep(delay);

                if (!running) {
                    break;
                }

                // Crear y despachar un pedido generado aleatoriamente
                Order order = generateSingleRandomOrder();
                scheduler.submitOrder(order);

                synchronized (this) {
                    generatedCount++;
                }

                LOGGER.log(Level.INFO, "[GENERADOR] Pedido autogenerado #{0} - Nivel: {1} ({2} artículos)",
                        new Object[]{order.getId().toString().substring(0, 8),
                            order.getServiceTier().getDisplayName(),
                            order.getTotalUnits()});

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Error durante la generación automática de pedidos: " + ex.getMessage(), ex);
            }
        }
        running = false;
    }

    /**
     * Genera un pedido individual aleatorio con productos y cantidades válidas
     * del catálogo.
     *
     * @return Pedido construido y listo para ser admitido por el planificador.
     */
    public Order generateSingleRandomOrder() {
        // 1. Cliente aleatorio
        String clientName = CLIENT_NAMES[random.nextInt(CLIENT_NAMES.length)];
        String phone = "502-" + (20000000 + random.nextInt(70000000));
        Client client = new Client(UUID.randomUUID(), clientName, phone, "Quetzaltenango, Guatemala");

        // 2. Nivel de servicio equiprobable
        ServiceTier[] tiers = ServiceTier.values();
        ServiceTier tier = tiers[random.nextInt(tiers.length)];

        // 3. Selección de producto del catálogo maestro
        List<Product> catalog = ProductCatalog.getAll();
        Product mainProduct = catalog.get(random.nextInt(catalog.size()));
        Merchandise merchandise = mainProduct.getMerchandise();

        // 4. Crear instancia de pedido
        Order order = new Order(UUID.randomUUID(), client, tier, merchandise);

        // 5. Agregar unidades (1 a 5 unidades del producto principal)
        int unitsMain = 1 + random.nextInt(5);
        order.addProduct(mainProduct, unitsMain);

        // Con 40% de probabilidad, agregar un segundo producto del mismo tipo de mercancía si existe
        if (random.nextDouble() < 0.40) {
            for (Product other : catalog) {
                if (!other.equals(mainProduct) && other.getMerchandise().equals(merchandise)) {
                    int extraUnits = 1 + random.nextInt(3);
                    order.addProduct(other, extraUnits);
                    break;
                }
            }
        }

        return order;
    }

    // Configuración y Getters
    /**
     * Configura el rango de tiempo de espera entre arribos de pedidos.
     *
     * @param minMs Tiempo mínimo en milisegundos.
     * @param maxMs Tiempo máximo en milisegundos.
     */
    public void setIntervalRange(int minMs, int maxMs) {
        if (minMs > 0 && maxMs >= minMs) {
            this.minIntervalMs = minMs;
            this.maxIntervalMs = maxMs;
        }
    }

    /**
     * @return true si el generador está produciendo tráfico activamente.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * @return Total acumulado de pedidos generados automáticamente.
     */
    public synchronized int getGeneratedCount() {
        return generatedCount;
    }

    /**
     * @return Intervalo mínimo de llegada en milisegundos.
     */
    public int getMinIntervalMs() {
        return minIntervalMs;
    }

    /**
     * @return Intervalo máximo de llegada en milisegundos.
     */
    public int getMaxIntervalMs() {
        return maxIntervalMs;
    }
}
