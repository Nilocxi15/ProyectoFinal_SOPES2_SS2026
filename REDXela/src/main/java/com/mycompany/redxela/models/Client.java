package com.mycompany.redxela.models;

import java.util.UUID;

/**
 * Representa a un cliente del centro de distribución REDXela.
 * 
 * Modela al usuario o cuenta comercial que emite órdenes de pedido dentro
 * de la simulación, permitiendo asociar la procedencia de cada proceso/tarea en el sistema.
 */
public class Client {

    /** Identificador único universal del cliente. */
    private UUID id;

    /** Nombre completo del cliente o empresa. */
    private String name;

    /** Número telefónico de contacto. */
    private String cellphone;

    /** Dirección física de entrega. */
    private String address;

    /**
     * Crea una nueva instancia de un cliente.
     * 
     * @param id Identificador único universal.
     * @param name Nombre o razón social del cliente.
     * @param cellphone Teléfono de contacto.
     * @param address Dirección de entrega.
     */
    public Client(UUID id, String name, String cellphone, String address) {
        this.id = id;
        this.name = name;
        this.cellphone = cellphone;
        this.address = address;
    }

    // Getters y Setters

    /** @return Identificador único del cliente. */
    public UUID getId() {
        return id;
    }

    /** @return Nombre del cliente. */
    public String getName() {
        return name;
    }

    /** @return Teléfono de contacto. */
    public String getCellphone() {
        return cellphone;
    }

    /** @return Dirección de entrega. */
    public String getAddress() {
        return address;
    }

    /** @param name Nuevo nombre del cliente. */
    public void setName(String name) {
        this.name = name;
    }

    /** @param cellphone Nuevo teléfono de contacto. */
    public void setCellphone(String cellphone) {
        this.cellphone = cellphone;
    }

    /** @param address Nueva dirección de entrega. */
    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return name + " (" + cellphone + ")";
    }
}
