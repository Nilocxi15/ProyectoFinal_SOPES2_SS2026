package com.mycompany.redxela.models;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Representa una orden de pedido dentro del centro de distribución REDXela.
 *
 * La orden modela un proceso o tarea (Task/PCB):
 *
 * - id: Identificador único del proceso (PID).
 *
 * - serviceTier: Nivel de prioridad base del proceso para el planificador
 * (Priority Scheduling).
 *
 * - totalUnits: Cantidad acumulada de artículos; sirve como estimación del
 * tiempo de ráfaga o trabajo (Burst Time) requerido para la política SJF
 * (Shortest Job First).
 *
 * - merchandiseType: Define los recursos compartidos que este proceso debe
 * adquirir (Semáforos).
 *
 * - state: Estado actual dentro del ciclo de vida del proceso (Nuevo, Espera,
 * Ejecución, Terminado).
 *
 * - arrivalTime: Marca de tiempo de llegada al sistema para calcular métricas
 * de rendimiento: Tiempo de Espera (Waiting Time), Tiempo de Retorno
 * (Turnaround Time) y aplicar Envejecimiento (Aging).
 */
public class Order {

    /**
     * Identificador único del pedido (PID en la simulación).
     */
    private UUID id;

    /**
     * Datos del cliente que originó la solicitud.
     */
    private Client client;

    /**
     * Nivel de servicio / prioridad contratada.
     */
    private ServiceTier serviceTier;

    /**
     * Categoría de mercancía que define los recursos requeridos.
     */
    private Merchandise merchandiseType;

    /**
     * Detalle de productos y cantidades que componen el pedido.
     */
    private Map<Product, Integer> items;

    /**
     * Total de unidades solicitadas (utilizado como Burst Time de
     * procesamiento).
     */
    private int totalUnits;

    /**
     * Estado actual en el ciclo de vida del pedido.
     */
    private OrderState state;

    /**
     * Momento exacto de llegada al sistema (para cálculo de tiempos de espera).
     */
    private LocalDateTime arrivalTime;

    /**
     * Momento en que el planificador le asignó recursos y comenzó su
     * procesamiento.
     */
    private LocalDateTime startProcessingTime;

    /**
     * Momento en que se completó el despacho.
     */
    private LocalDateTime completionTime;

    /**
     * Construye un nuevo pedido en estado inicial PENDIENTE con su marca de
     * tiempo de llegada.
     *
     * @param id Identificador único universal.
     * @param client Cliente que solicita el pedido.
     * @param serviceTier Nivel de servicio contratado.
     * @param merchandiseType Categoría de mercancía asociada.
     */
    public Order(UUID id, Client client, ServiceTier serviceTier, Merchandise merchandiseType) {
        this.id = id;
        this.client = client;
        this.serviceTier = serviceTier;
        this.merchandiseType = merchandiseType;
        this.items = new HashMap<>();
        this.totalUnits = 0;
        this.state = OrderState.PENDIENTE;
        this.arrivalTime = LocalDateTime.now();
    }

    /**
     * Agrega un producto y cantidad a la orden, recalculando el tiempo de
     * ráfaga (totalUnits).
     *
     * @param product Producto del catálogo a incluir.
     * @param quantity Cantidad de unidades solicitadas.
     */
    public void addProduct(Product product, int quantity) {
        if (quantity > 0) {
            this.items.put(product, this.items.getOrDefault(product, 0) + quantity);
            this.totalUnits += quantity;
        }
    }

    // Getters
    /**
     * @return Identificador del pedido.
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return Cliente asociado.
     */
    public Client getClient() {
        return client;
    }

    /**
     * @return Nivel de servicio / prioridad actual.
     */
    public ServiceTier getServiceTier() {
        return serviceTier;
    }

    /**
     * @return Tipo de mercancía del pedido.
     */
    public Merchandise getMerchandiseType() {
        return merchandiseType;
    }

    /**
     * @return Vista inmutable de los productos y cantidades.
     */
    public Map<Product, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    /**
     * @return Total de unidades acumuladas (Burst Time).
     */
    public int getTotalUnits() {
        return totalUnits;
    }

    /**
     * @return Estado actual del pedido.
     */
    public OrderState getState() {
        return state;
    }

    /**
     * @return Marca de tiempo de llegada a la cola de entrada.
     */
    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    /**
     * @return Marca de tiempo de inicio de atención con recursos.
     */
    public LocalDateTime getStartProcessingTime() {
        return startProcessingTime;
    }

    /**
     * @return Marca de tiempo de finalización.
     */
    public LocalDateTime getCompletionTime() {
        return completionTime;
    }

    // Setters
    /**
     * @param state Nuevo estado del ciclo de vida.
     */
    public void setState(OrderState state) {
        this.state = state;
    }

    /**
     * Modifica el nivel de servicio. Utilizado principalmente por la política
     * de Envejecimiento (Aging) del planificador para aumentar la prioridad de
     * pedidos que llevan mucho tiempo en espera.
     *
     * @param serviceTier Nuevo nivel de servicio asignado.
     */
    public void setServiceTier(ServiceTier serviceTier) {
        this.serviceTier = serviceTier;
    }

    /**
     * @param startProcessingTime Momento en que inició el procesamiento.
     */
    public void setStartProcessingTime(LocalDateTime startProcessingTime) {
        this.startProcessingTime = startProcessingTime;
    }

    /**
     * @param completionTime Momento en que concluyó el despacho.
     */
    public void setCompletionTime(LocalDateTime completionTime) {
        this.completionTime = completionTime;
    }

    @Override
    public String toString() {
        return "Pedido #" + id.toString().substring(0, 8) + " - " + serviceTier.getDisplayName() + " (" + totalUnits + " arts.)";
    }
}
