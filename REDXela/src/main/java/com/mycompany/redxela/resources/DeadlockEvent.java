package com.mycompany.redxela.resources;

import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.models.ResourceType;
import java.time.LocalDateTime;

/**
 * Encapsula la información de un interbloqueo (Deadlock) detectado en el centro
 * de distribución.
 *
 * Modela el ciclo encontrado en el Grafo de Asignación de Recursos:
 *
 * Proceso 1 (Order A) retiene Recurso 1 (Held A) y solicita Recurso 2 (Waiting
 * A).
 *
 * Proceso 2 (Order B) retiene Recurso 2 (Held B) y solicita Recurso 1 (Waiting
 * B).
 */
public class DeadlockEvent {

    /**
     * Primer pedido involucrado en la espera circular.
     */
    private final Order orderA;

    /**
     * Recurso que el Pedido A tiene actualmente asignado.
     */
    private final ResourceType heldByA;

    /**
     * Recurso que el Pedido A está esperando para continuar.
     */
    private final ResourceType waitingForA;

    /**
     * Segundo pedido involucrado en la espera circular.
     */
    private final Order orderB;

    /**
     * Recurso que el Pedido B tiene actualmente asignado.
     */
    private final ResourceType heldByB;

    /**
     * Recurso que el Pedido B está esperando para continuar.
     */
    private final ResourceType waitingForB;

    /**
     * Momento de detección del ciclo.
     */
    private final LocalDateTime timestamp;

    /**
     * Construye un nuevo evento de interbloqueo.
     *
     * @param orderA Pedido A.
     * @param heldByA Recurso retenido por A.
     * @param waitingForA Recurso disputado que A espera.
     * @param orderB Pedido B.
     * @param heldByB Recurso retenido por B.
     * @param waitingForB Recurso disputado que B espera.
     */
    public DeadlockEvent(Order orderA, ResourceType heldByA, ResourceType waitingForA,
            Order orderB, ResourceType heldByB, ResourceType waitingForB) {
        this.orderA = orderA;
        this.heldByA = heldByA;
        this.waitingForA = waitingForA;
        this.orderB = orderB;
        this.heldByB = heldByB;
        this.waitingForB = waitingForB;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * @return Primer pedido del ciclo.
     */
    public Order getOrderA() {
        return orderA;
    }

    /**
     * @return Recurso retenido por A.
     */
    public ResourceType getHeldByA() {
        return heldByA;
    }

    /**
     * @return Recurso esperado por A.
     */
    public ResourceType getWaitingForA() {
        return waitingForA;
    }

    /**
     * @return Segundo pedido del ciclo.
     */
    public Order getOrderB() {
        return orderB;
    }

    /**
     * @return Recurso retenido por B.
     */
    public ResourceType getHeldByB() {
        return heldByB;
    }

    /**
     * @return Recurso esperado por B.
     */
    public ResourceType getWaitingForB() {
        return waitingForB;
    }

    /**
     * @return Momento de detección.
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Genera una explicación técnica detallada del conflicto para mostrar en la
     * interfaz de usuario.
     *
     * @return Cadena formateada explicando el ciclo de espera de Coffman.
     */
    public String getExplanation() {
        return String.format(
                "INTERBLOQUEO (DEADLOCK) DETECTADO:\n\n"
                + "• El Pedido #%s (%s) RETIENE [%s] y ESPERA [%s].\n"
                + "• El Pedido #%s (%s) RETIENE [%s] y ESPERA [%s].\n\n"
                + "Se ha configurado una condición de Espera Circular (Coffman). "
                + "Ninguna de las dos operaciones puede continuar sin que la otra libere su recurso.",
                orderA.getId().toString().substring(0, 8), orderA.getMerchandiseType().getName(),
                heldByA.getDisplayName(), waitingForA.getDisplayName(),
                orderB.getId().toString().substring(0, 8), orderB.getMerchandiseType().getName(),
                heldByB.getDisplayName(), waitingForB.getDisplayName()
        );
    }
}
