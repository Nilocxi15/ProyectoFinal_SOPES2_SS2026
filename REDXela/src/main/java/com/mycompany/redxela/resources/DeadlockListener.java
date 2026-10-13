package com.mycompany.redxela.resources;

/**
 * Interfaz observadora para alertar en tiempo real a la interfaz gráfica y al
 * sistema cuando el Administrador de Recursos detecta un interbloqueo
 * (Deadlock).
 */
@FunctionalInterface
public interface DeadlockListener {

    /**
     * Invocado inmediatamente tras detectar un ciclo de espera circular en el
     * sistema.
     *
     * @param event Información detallada del interbloqueo y de las operaciones
     * en conflicto.
     */
    void onDeadlockDetected(DeadlockEvent event);
}
