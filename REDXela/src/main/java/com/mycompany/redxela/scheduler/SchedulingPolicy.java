package com.mycompany.redxela.scheduler;

/**
 * Define las políticas de planificación de procesos disponibles en REDXela.
 *
 * Modela los algoritmos clásicos de planificación de la CPU (CPU Scheduling):
 *
 * 1. PRIORIDAD_ESTRICTA: Priority Scheduling no apropiativo donde el Nivel de
 * Servicio (1 a 5) determina el orden absoluto de despacho. Propenso a
 * inanición (Starvation).
 *
 * 2. ENVEJECIMIENTO: Priority Scheduling con Envejecimiento (Aging). Incrementa
 * la prioridad efectiva de los procesos que acumulan tiempo en la cola de
 * listos, previniendo la inanición.
 *
 * 3. MENOR_TRABAJO_PRIMERO: Shortest Job First (SJF) donde la cantidad total de
 * artículos (totalUnits) representa el tiempo de ráfaga (Burst Time). Maximiza
 * el rendimiento (Throughput) y minimiza el tiempo promedio de espera global.
 */
public enum SchedulingPolicy {

    /**
     * Prioridad Estricta: Atiende pedidos basándose únicamente en el Nivel de
     * Servicio (Exprés = 1, Prioritario = 2, Estándar = 3, Programado = 4,
     * Económico = 5).
     */
    PRIORIDAD_ESTRICTA("Prioridad Estricta (Por Nivel de Servicio)"),
    /**
     * Prioridad con Envejecimiento (Aging): Ajusta dinámicamente la prioridad
     * en función del tiempo transcurrido en la cola de espera para evitar la
     * inanición.
     */
    ENVEJECIMIENTO("Prioridad con Envejecimiento (Aging)"),
    /**
     * SJF (Shortest Job First): Procesa primero los pedidos con menor cantidad
     * de artículos (menor Burst Time) para desocupar estaciones más
     * rápidamente.
     */
    MENOR_TRABAJO_PRIMERO("Menor Cantidad de Artículos Primero (SJF)");

    private final String displayName;

    SchedulingPolicy(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return Nombre descriptivo para la interfaz de usuario.
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
