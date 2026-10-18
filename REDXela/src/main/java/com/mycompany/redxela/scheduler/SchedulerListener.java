package com.mycompany.redxela.scheduler;

import com.mycompany.redxela.models.Order;

/**
 * Interfaz observadora para recibir actualizaciones en tiempo real del
 * Planificador de Procesos hacia la interfaz gráfica de usuario (Swing).
 */
public interface SchedulerListener {

    /**
     * Invocado cuando un nuevo pedido ingresa a la cola de espera (Ready
     * Queue).
     */
    void onOrderEnqueued(Order order);

    /**
     * Invocado cuando un pedido obtiene sus recursos y comienza a procesarse
     * (Running).
     */
    void onOrderStarted(Order order);

    /**
     * Invocado periódicamente para actualizar la barra de progreso y tiempo
     * restante en la GUI.
     */
    void onOrderProgress(Order order, int percentage, int remainingSeconds);

    /**
     * Invocado cuando un pedido termina su procesamiento y libera los recursos.
     */
    void onOrderCompleted(Order order);

    /**
     * Invocado cuando el operador cambia la política de planificación activa.
     */
    void onPolicyChanged(SchedulingPolicy newPolicy);
}
