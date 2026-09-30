package com.mycompany.redxela.models;

/**
 * Representa los estados posibles en el ciclo de vida de un pedido.
 *
 * - PENDIENTE: Estado "Nuevo/Listo" en la cola de admitidos, a la espera de ser
 * seleccionado por el planificador.
 *
 * - ESPERANDO_RECURSOS: Estado "Bloqueado / Esperando" (Waiting) por falta de
 * stock o recursos físicos.
 *
 * - EN_PROCESO: Estado "En Ejecución" (Running), utilizando la CPU/estaciones
 * de trabajo asignadas.
 *
 * - BLOQUEADO: Estado "Interbloqueado" (Deadlock detectado / Espera circular de
 * recursos).
 *
 * - COMPLETADO: Estado "Terminado" (Terminated / Finished), liberando todos los
 * recursos adquiridos.
 */
public enum OrderState {

    /**
     * Pedido registrado en el sistema, en espera de turno según la política del
     * planificador.
     */
    PENDIENTE,
    /**
     * Pedido que ha sido despachado por el planificador pero compite por
     * recursos físicos no disponibles.
     */
    ESPERANDO_RECURSOS,
    /**
     * Pedido activamente siendo atendido en las estaciones con todos sus
     * recursos asignados.
     */
    EN_PROCESO,
    /**
     * Pedido involucrado en un interbloqueo (Deadlock) esperando resolución
     * manual o automática.
     */
    BLOQUEADO,
    /**
     * Pedido finalizado exitosamente; métricas registradas y recursos devueltos
     * al centro.
     */
    COMPLETADO
}
