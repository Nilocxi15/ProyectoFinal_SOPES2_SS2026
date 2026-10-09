package com.mycompany.redxela.models;

/**
 * Niveles de servicio contratados por los clientes en REDXela.
 *
 * Estos niveles definen la prioridad base de planificación (Priority
 * Scheduling):
 *
 *
 * - A menor nivel numérico, mayor prioridad de despacho en la cola de listos.
 *
 *
 * - Los tiempos objetivos representan el SLA máximo de espera antes de
 * considerarse degradado, lo cual sirve como parámetro clave para los
 * algoritmos con Envejecimiento (Aging) que previenen la inanición (starvation)
 * de niveles inferiores.
 */
public enum ServiceTier {

    /**
     * Nivel 1: Procesamiento casi instantáneo (Máxima prioridad de despacho).
     */
    EXPRES("Exprés", 1, 0),
    /**
     * Nivel 2: Tiempo objetivo máximo de 10 minutos en espera.
     */
    PRIORITARIO("Prioritario", 2, 10),
    /**
     * Nivel 3: Tiempo objetivo máximo de 30 minutos en espera.
     */
    ESTANDAR("Estándar", 3, 30),
    /**
     * Nivel 4: Tiempo objetivo máximo de 60 minutos en espera.
     */
    PROGRAMADO("Programado", 4, 60),
    /**
     * Nivel 5: Tiempo objetivo máximo de 120 minutos en espera (Menor
     * prioridad).
     */
    ECONOMICO("Económico", 5, 120);

    /**
     * Nombre legible para la interfaz gráfica.
     */
    private final String displayName;

    /**
     * Nivel numérico de prioridad (1 = más alta, 5 = más baja).
     */
    private final int priorityLevel;

    /**
     * Tiempo objetivo máximo de espera en minutos según la especificación.
     */
    private final int targetWaitMinutes;

    private ServiceTier(String displayName, int priorityLevel, int targetWaitMinutes) {
        this.displayName = displayName;
        this.priorityLevel = priorityLevel;
        this.targetWaitMinutes = targetWaitMinutes;
    }

    /**
     * @return Nombre descriptivo del nivel de servicio.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return Nivel de prioridad numérica (1 a 5).
     */
    public int getPriorityLevel() {
        return priorityLevel;
    }

    /**
     * @return Tiempo objetivo de espera en minutos.
     */
    public int getTargetWaitMinutes() {
        return targetWaitMinutes;
    }

    @Override
    public String toString() {
        return displayName + " (Nivel " + priorityLevel + ")";
    }
}
