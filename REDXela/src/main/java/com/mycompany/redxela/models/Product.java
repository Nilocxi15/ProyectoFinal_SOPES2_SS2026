package com.mycompany.redxela.models;

import java.util.Objects;
import java.util.UUID;

/**
 * Representa un artículo específico del catálogo predefinido de REDXela.
 * 
 * En el contexto de Sistemas Operativos:
 * - Forma parte de la base de datos/catálogo de recursos consumibles.
 * - Integra el concepto de "Almacenamiento No Contiguo": el atributo 'unitsPerBlock'
 *   determina la granularidad con la que el lote se fragmenta y distribuye entre
 *   bloques/celdas del almacén, análogo a la asignación de bloques en un sistema de archivos
 *   o a la paginación en memoria virtual.
 */
public class Product {

    /** Identificador único del producto en el catálogo. */
    private UUID id;

    /** Nombre comercial del producto. */
    private String name;

    /** Categoría de mercancía que define las restricciones de manejo y recursos requeridos. */
    private Merchandise merchandise;

    /** Unidades físicas disponibles actualmente en el inventario. */
    private int currentStock;

    /**
     * Capacidad de empaque por bloque/celda de almacenamiento.
     * Define cuántas unidades caben como máximo en una única ubicación física del almacén.
     */
    private int unitsPerBlock;

    /**
     * Construye una nueva instancia de producto para el catálogo.
     * 
     * @param id Identificador único.
     * @param name Nombre comercial.
     * @param merchandise Tipo de mercancía asociado.
     * @param currentStock Existencias iniciales en inventario.
     * @param unitsPerBlock Capacidad unitaria por celda/bloque de almacén.
     */
    public Product(UUID id, String name, Merchandise merchandise, int currentStock, int unitsPerBlock) {
        this.id = id;
        this.name = name;
        this.merchandise = merchandise;
        this.currentStock = currentStock;
        this.unitsPerBlock = unitsPerBlock;
    }

    // Getters y Setters

    /** @return Identificador del producto. */
    public UUID getId() {
        return id;
    }

    /** @return Nombre comercial del producto. */
    public String getName() {
        return name;
    }

    /** @return Categoría de mercancía asociada. */
    public Merchandise getMerchandise() {
        return merchandise;
    }

    /** @return Cantidad disponible en stock. */
    public int getCurrentStock() {
        return currentStock;
    }

    /** @return Unidades máximas por celda/bloque. */
    public int getUnitsPerBlock() {
        return unitsPerBlock;
    }

    /** @param name Nuevo nombre comercial. */
    public void setName(String name) {
        this.name = name;
    }

    /** @param merchandise Nueva categoría de mercancía. */
    public void setMerchandise(Merchandise merchandise) {
        this.merchandise = merchandise;
    }

    /** @param currentStock Nuevo nivel de stock. */
    public void setCurrentStock(int currentStock) {
        this.currentStock = currentStock;
    }

    /** @param unitsPerBlock Nueva capacidad por celda. */
    public void setUnitsPerBlock(int unitsPerBlock) {
        this.unitsPerBlock = unitsPerBlock;
    }

    /**
     * Incrementa el stock disponible tras una recepción de mercadería (vía tubería IPC).
     * 
     * @param quantity Cantidad de unidades a ingresar.
     */
    public synchronized void addStock(int quantity) {
        if (quantity > 0) {
            this.currentStock += quantity;
        }
    }

    /**
     * Intenta reducir el stock al despachar un pedido.
     * 
     * @param quantity Cantidad requerida por el pedido.
     * @return true si había suficiente inventario y se descontó; false si no hay suficiente stock.
     */
    public synchronized boolean reduceStock(int quantity) {
        if (quantity > 0 && this.currentStock >= quantity) {
            this.currentStock -= quantity;
            return true;
        }
        return false;
    }

    /**
     * Representación textual adecuada para su inclusión en listas o combos de Swing.
     * 
     * @return Nombre con indicación de categoría y stock actual.
     */
    @Override
    public String toString() {
        return name + " (" + merchandise.getName() + ") - Stock: " + currentStock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
