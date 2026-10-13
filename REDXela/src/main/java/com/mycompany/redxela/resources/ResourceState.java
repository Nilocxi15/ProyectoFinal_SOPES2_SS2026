package com.mycompany.redxela.resources;

import com.mycompany.redxela.models.ResourceType;
import java.util.Collections;
import java.util.List;

/**
 * Representa una instantánea (Snapshot) del estado de un recurso físico
 * compartido.
 *
 *
 * Modela el estado de un Semáforo:
 *
 * - Capacidad total (valor inicial del semáforo).
 *
 * - Permisos disponibles actualmente (valor del contador en tiempo real).
 *
 * - Instancias en uso.
 *
 * - Lista de procesos que retienen permisos y procesos bloqueados en la cola
 * del semáforo.
 *
 * Es utilizado por la interfaz gráfica Swing para mostrar en tiempo real la
 * ocupación de los recursos compartidos y "por quién" están siendo utilizados.
 */
public class ResourceState {

    /**
     * Tipo de recurso representado.
     */
    private final ResourceType type;

    /**
     * Capacidad total de instancias físicas/permisos.
     */
    private final int totalCapacity;

    /**
     * Permisos disponibles actualmente en el semáforo.
     */
    private final int availablePermits;

    /**
     * Cantidad de instancias en uso concurrente.
     */
    private final int inUse;

    /**
     * Identificadores descriptivos de los pedidos que retienen el recurso.
     */
    private final List<String> activeHoldingOrders;

    /**
     * Identificadores descriptivos de los pedidos que están en espera por este
     * recurso.
     */
    private final List<String> waitingOrders;

    /**
     * Construye un nuevo objeto con el estado instantáneo del recurso.
     *
     * @param type Tipo de recurso.
     * @param totalCapacity Capacidad máxima.
     * @param availablePermits Permisos disponibles.
     * @param inUse Permisos en uso.
     * @param activeHoldingOrders Lista de órdenes poseedoras.
     * @param waitingOrders Lista de órdenes en cola de espera.
     */
    public ResourceState(ResourceType type, int totalCapacity, int availablePermits, int inUse,
            List<String> activeHoldingOrders, List<String> waitingOrders) {
        this.type = type;
        this.totalCapacity = totalCapacity;
        this.availablePermits = availablePermits;
        this.inUse = inUse;
        this.activeHoldingOrders = activeHoldingOrders != null ? activeHoldingOrders : Collections.emptyList();
        this.waitingOrders = waitingOrders != null ? waitingOrders : Collections.emptyList();
    }

    /**
     * @return Tipo de recurso.
     */
    public ResourceType getType() {
        return type;
    }

    /**
     * @return Capacidad total.
     */
    public int getTotalCapacity() {
        return totalCapacity;
    }

    /**
     * @return Permisos libres en el semáforo.
     */
    public int getAvailablePermits() {
        return availablePermits;
    }

    /**
     * @return Permisos ocupados actualmente.
     */
    public int getInUse() {
        return inUse;
    }

    /**
     * @return Porcentaje de utilización del recurso (0.0% a 100.0%).
     */
    public double getUtilizationPercentage() {
        if (totalCapacity == 0) {
            return 0.0;
        }
        return ((double) inUse / totalCapacity) * 100.0;
    }

    /**
     * @return Lista inmutable de pedidos que poseen el recurso.
     */
    public List<String> getActiveHoldingOrders() {
        return Collections.unmodifiableList(activeHoldingOrders);
    }

    /**
     * @return Lista inmutable de pedidos esperando en la cola del semáforo.
     */
    public List<String> getWaitingOrders() {
        return Collections.unmodifiableList(waitingOrders);
    }

    @Override
    public String toString() {
        return String.format("%s: %d/%d en uso (%.1f%%)",
                type.getDisplayName(), inUse, totalCapacity, getUtilizationPercentage());
    }
}
