package com.mycompany.redxela.models;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa una categoría o tipo de mercancía en el centro de distribución.
 *
 * - Define el conjunto de recursos compartidos (Semáforos) que un proceso
 * requiere para poder completar su ejecución.
 *
 * - Incluye una secuencia de adquisición de recursos
 * (resourceAcquisitionOrder), lo cual permite simular y estudiar formalmente
 * las condiciones de Coffman (específicamente retención y espera, y espera
 * circular) que dan lugar a interbloqueos (Deadlocks).
 */
public class Merchandise {

    /**
     * Identificador único de la categoría de mercancía.
     */
    private UUID id;

    /**
     * Nombre descriptivo
     */
    private String name;

    /**
     * Descripción de las características físicas y operativas.
     */
    private String description;

    /**
     * Mapa que define la cantidad necesaria de cada recurso para procesar esta
     * mercancía.
     */
    private Map<ResourceType, Integer> requiredResources;

    /**
     * Secuencia cronológica u orden en el que se deben solicitar los recursos.
     */
    private List<ResourceType> resourceAcquisitionOrder;

    /**
     * Construye un tipo de mercancía con sus requerimientos de recursos.
     *
     * @param id Identificador único.
     * @param name Nombre de la mercancía.
     * @param description Descripción detallada.
     * @param requiredResources Mapa de Recurso -> Cantidad requerida.
     * @param resourceAcquisitionOrder Lista ordenada de solicitud de recursos.
     */
    public Merchandise(UUID id, String name, String description,
            Map<ResourceType, Integer> requiredResources,
            List<ResourceType> resourceAcquisitionOrder) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.requiredResources = requiredResources;
        this.resourceAcquisitionOrder = resourceAcquisitionOrder;
    }

    // Getters y Setters
    /**
     * @return Identificador único de la mercancía.
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return Nombre del tipo de mercancía.
     */
    public String getName() {
        return name;
    }

    /**
     * @return Descripción de sus características.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return Mapa inmutable de recursos requeridos.
     */
    public Map<ResourceType, Integer> getRequiredResources() {
        return Collections.unmodifiableMap(requiredResources);
    }

    /**
     * @return Lista inmutable con el orden de adquisición de recursos.
     */
    public List<ResourceType> getResourceAcquisitionOrder() {
        return Collections.unmodifiableList(resourceAcquisitionOrder);
    }

    /**
     * @param name Nuevo nombre de la mercancía.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param description Nueva descripción.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @param requiredResources Nuevo mapa de recursos requeridos.
     */
    public void setRequiredResources(Map<ResourceType, Integer> requiredResources) {
        this.requiredResources = requiredResources;
    }

    /**
     * @param resourceAcquisitionOrder Nueva secuencia de adquisición.
     */
    public void setResourceAcquisitionOrder(List<ResourceType> resourceAcquisitionOrder) {
        this.resourceAcquisitionOrder = resourceAcquisitionOrder;
    }

    /**
     * Retorna el nombre de la mercancía para su adecuada visualización en
     * componentes Swing como JComboBox y JList sin desconfigurar el
     * renderizado.
     *
     * @return Nombre de la mercancía.
     */
    @Override
    public String toString() {
        return this.name;
    }

    /**
     * Genera una cadena detallada para propósitos de auditoría, bitácoras o
     * logs.
     *
     * @return Ficha técnica con descripción, recursos y secuencia de solicitud.
     */
    public String getDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("MERCANCIA: ").append(name).append("\n");
        sb.append("Descripcion: ").append(description).append("\n");
        sb.append("Recursos Requeridos: ").append(requiredResources).append("\n");
        sb.append("Orden de Adquisicion: ");
        if (resourceAcquisitionOrder != null) {
            for (int i = 0; i < resourceAcquisitionOrder.size(); i++) {
                sb.append(resourceAcquisitionOrder.get(i).getDisplayName());
                if (i < resourceAcquisitionOrder.size() - 1) {
                    sb.append(" -> ");
                }
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Merchandise that = (Merchandise) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
