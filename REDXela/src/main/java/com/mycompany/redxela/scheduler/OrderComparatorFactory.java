package com.mycompany.redxela.scheduler;

import com.mycompany.redxela.models.Order;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * Fábrica de comparadores para ordenar la Cola de Listos (Ready Queue) según la
 * política de planificación activa.
 *
 * Cada comparador implementa el criterio de ordenamiento del despachador
 * (Dispatcher):
 *
 * - Prioridad Estricta: Función de prioridad estática f(p) = nivel_servicio.
 *
 * - Envejecimiento: Función de prioridad dinámica f(p, t) = (nivel * k) -
 * (tiempo_espera * tasa).
 *
 * - SJF: Función de estimación de ráfaga f(p) = totalUnits (Burst Time).
 *
 * En todos los casos se utiliza la marca de tiempo de llegada (FIFO) como regla
 * de desempate para garantizar consistencia y predictibilidad.
 */
public class OrderComparatorFactory {

    /**
     * Ponderación base por nivel de prioridad en el algoritmo de
     * envejecimiento.
     */
    private static final double AGING_PRIORITY_WEIGHT = 30.0;

    /**
     * Tasa de aceleración por segundo transcurrido en la cola de espera.
     */
    private static final double AGING_SECONDS_RATE = 2.0;

    /**
     * Retorna el comparador apropiado para una política de planificación dada.
     *
     * @param policy Política seleccionada.
     * @return Instancia de Comparator para ordenar la cola de pedidos en
     * espera.
     */
    public static Comparator<Order> getComparator(SchedulingPolicy policy) {
        switch (policy) {
            case PRIORIDAD_ESTRICTA:
                return createStrictPriorityComparator();
            case ENVEJECIMIENTO:
                return createAgingComparator();
            case MENOR_TRABAJO_PRIMERO:
                return createSjfComparator();
            default:
                return createStrictPriorityComparator();
        }
    }

    /**
     * Comparador de Prioridad Estricta: Menor nivel numérico tiene mayor
     * prioridad (Exprés=1 hasta Económico=5).
     */
    private static Comparator<Order> createStrictPriorityComparator() {
        return (o1, o2) -> {
            int p1 = o1.getServiceTier().getPriorityLevel();
            int p2 = o2.getServiceTier().getPriorityLevel();

            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
            // Desempate por orden de llegada (FIFO)
            return o1.getArrivalTime().compareTo(o2.getArrivalTime());
        };
    }

    /**
     * Comparador con Envejecimiento (Aging): Calcula una puntuación dinámica
     * donde el tiempo acumulado en espera reduce el puntaje efectivo,
     * permitiendo que pedidos de menor prioridad superen a pedidos nuevos de
     * mayor prioridad para evitar inanición (Starvation).
     */
    private static Comparator<Order> createAgingComparator() {
        return (o1, o2) -> {
            LocalDateTime now = LocalDateTime.now();

            long waitSeconds1 = Math.max(0, Duration.between(o1.getArrivalTime(), now).toSeconds());
            long waitSeconds2 = Math.max(0, Duration.between(o2.getArrivalTime(), now).toSeconds());

            double score1 = (o1.getServiceTier().getPriorityLevel() * AGING_PRIORITY_WEIGHT) - (waitSeconds1 * AGING_SECONDS_RATE);
            double score2 = (o2.getServiceTier().getPriorityLevel() * AGING_PRIORITY_WEIGHT) - (waitSeconds2 * AGING_SECONDS_RATE);

            if (Double.compare(score1, score2) != 0) {
                return Double.compare(score1, score2);
            }
            return o1.getArrivalTime().compareTo(o2.getArrivalTime());
        };
    }

    /**
     * Comparador Shortest Job First (SJF): Menor cantidad de unidades totales
     * (Burst Time) tiene mayor prioridad para desocupar estaciones y maximizar
     * el Throughput del sistema.
     */
    private static Comparator<Order> createSjfComparator() {
        return (o1, o2) -> {
            int units1 = o1.getTotalUnits();
            int units2 = o2.getTotalUnits();

            if (units1 != units2) {
                return Integer.compare(units1, units2);
            }
            return o1.getArrivalTime().compareTo(o2.getArrivalTime());
        };
    }
}
