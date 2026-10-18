package com.mycompany.redxela.scheduler;

import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.models.ServiceTier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registra y calcula las métricas de rendimiento del Planificador de Procesos.
 *
 * Implementa los criterios estándar de evaluación de algoritmos de
 * planificación de la CPU:
 *
 * 1. Tiempo de Espera (Waiting Time): Tiempo total que un proceso permanece en
 * la cola de listos.
 *
 * 2. Tiempo de Retorno (Turnaround Time): Tiempo transcurrido desde la llegada
 * hasta su finalización.
 *
 * 3. Tiempo de Respuesta / Procesamiento (Burst Execution Time): Tiempo real
 * utilizando recursos.
 *
 * 4. Rendimiento (Throughput): Cantidad de procesos completados por unidad de
 * tiempo.
 *
 * Permite contrastar numéricamente el desempeño entre Prioridad Estricta, Aging
 * y SJF.
 */
public class SchedulerMetrics {

    /**
     * Lista de pedidos completados registrados para cálculo estadístico.
     */
    private final List<Order> completedOrders;

    /**
     * Tiempos acumulados de espera por nivel de servicio en milisegundos.
     */
    private final Map<ServiceTier, Long> waitTimeByTier;

    /**
     * Conteo de pedidos completados por nivel de servicio.
     */
    private final Map<ServiceTier, Integer> completedCountByTier;

    public SchedulerMetrics() {
        this.completedOrders = Collections.synchronizedList(new ArrayList<>());
        this.waitTimeByTier = new EnumMap<>(ServiceTier.class);
        this.completedCountByTier = new EnumMap<>(ServiceTier.class);

        for (ServiceTier tier : ServiceTier.values()) {
            waitTimeByTier.put(tier, 0L);
            completedCountByTier.put(tier, 0);
        }
    }

    /**
     * Registra un pedido finalizado y actualiza los acumuladores estadísticos.
     *
     * @param order Pedido completado con marcas de tiempo válidas.
     */
    public synchronized void recordOrderCompletion(Order order) {
        if (order == null || order.getArrivalTime() == null || order.getCompletionTime() == null) {
            return;
        }

        completedOrders.add(order);

        // Cálculo de tiempos en milisegundos
        long waitMs = 0;
        if (order.getStartProcessingTime() != null) {
            waitMs = Math.max(0, Duration.between(order.getArrivalTime(), order.getStartProcessingTime()).toMillis());
        }

        ServiceTier tier = order.getServiceTier();
        waitTimeByTier.put(tier, waitTimeByTier.get(tier) + waitMs);
        completedCountByTier.put(tier, completedCountByTier.get(tier) + 1);
    }

    /**
     * @return Total de pedidos completados exitosamente.
     */
    public synchronized int getTotalCompletedOrders() {
        return completedOrders.size();
    }

    /**
     * Calcula el Tiempo Promedio de Espera (Average Waiting Time) en segundos.
     *
     * @return Tiempo medio en segundos.
     */
    public synchronized double getAverageWaitingTimeSeconds() {
        if (completedOrders.isEmpty()) {
            return 0.0;
        }

        long totalWaitMs = 0;
        int count = 0;
        for (Order o : completedOrders) {
            if (o.getStartProcessingTime() != null) {
                totalWaitMs += Duration.between(o.getArrivalTime(), o.getStartProcessingTime()).toMillis();
                count++;
            }
        }
        return count == 0 ? 0.0 : (totalWaitMs / 1000.0) / count;
    }

    /**
     * Calcula el Tiempo Promedio de Retorno (Average Turnaround Time) en
     * segundos.
     *
     * @return Tiempo medio en segundos.
     */
    public synchronized double getAverageTurnaroundTimeSeconds() {
        if (completedOrders.isEmpty()) {
            return 0.0;
        }

        long totalTurnaroundMs = 0;
        for (Order o : completedOrders) {
            totalTurnaroundMs += Duration.between(o.getArrivalTime(), o.getCompletionTime()).toMillis();
        }
        return (totalTurnaroundMs / 1000.0) / completedOrders.size();
    }

    /**
     * Calcula el Tiempo Promedio de Procesamiento en segundos.
     *
     * @return Tiempo medio en segundos.
     */
    public synchronized double getAverageProcessingTimeSeconds() {
        if (completedOrders.isEmpty()) {
            return 0.0;
        }

        long totalProcMs = 0;
        int count = 0;
        for (Order o : completedOrders) {
            if (o.getStartProcessingTime() != null && o.getCompletionTime() != null) {
                totalProcMs += Duration.between(o.getStartProcessingTime(), o.getCompletionTime()).toMillis();
                count++;
            }
        }
        return count == 0 ? 0.0 : (totalProcMs / 1000.0) / count;
    }

    /**
     * Obtiene el tiempo promedio de espera específico para un nivel de
     * servicio. Vital para evidenciar si pedidos de nivel 5 sufren inanición en
     * prioridad estricta.
     *
     * @param tier Nivel de servicio a evaluar.
     * @return Tiempo medio de espera en segundos.
     */
    public synchronized double getAverageWaitingByTierSeconds(ServiceTier tier) {
        int count = completedCountByTier.getOrDefault(tier, 0);
        if (count == 0) {
            return 0.0;
        }
        long totalMs = waitTimeByTier.getOrDefault(tier, 0L);
        return (totalMs / 1000.0) / count;
    }

    /**
     * Reinicia las métricas estadísticas (útil al alternar de política de
     * planificación).
     */
    public synchronized void reset() {
        completedOrders.clear();
        for (ServiceTier tier : ServiceTier.values()) {
            waitTimeByTier.put(tier, 0L);
            completedCountByTier.put(tier, 0);
        }
    }
}
