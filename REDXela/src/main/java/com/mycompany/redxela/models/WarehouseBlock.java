package com.mycompany.redxela.models;

import java.util.Objects;

/**
 * Representa una celda o bloque físico de almacenamiento dentro del almacén.
 *
 * - Es la analogía directa de un BLOQUE DE DISCO (Disk Block) en un sistema de
 * archivos o un MARCO DE PÁGINA (Page Frame) en la gestión de memoria virtual.
 *
 * - Cada bloque tiene una ubicación física discreta (Pasillo, Nivel, Bahía) y
 * almacena unidades de un producto hasta el límite 'unitsPerBlock' definido
 * para ese artículo.
 *
 * - Al igual que los bloques en disco, pueden asignarse de manera no contigua
 * para satisfacer el almacenamiento de un lote completo de mercadería.
 */
public class WarehouseBlock {

    /**
     * Pasillo físico donde se ubica el bloque
     */
    private final String aisle;

    /**
     * Nivel de altura en la estantería (1 = suelo, 2 = intermedio, 3 = alto).
     */
    private final int level;

    /**
     * Número de bahía o posición dentro del pasillo y nivel (1 a 8).
     */
    private final int slot;

    /**
     * Producto actualmente almacenado en el bloque (null si el bloque está
     * libre).
     */
    private Product storedProduct;

    /**
     * Cantidad de unidades físicas guardadas actualmente en este bloque.
     */
    private int currentUnits;

    /**
     * Construye un nuevo bloque físico de almacén en una posición específica.
     * Inicialmente el bloque se encuentra vacío y disponible.
     *
     * @param aisle Identificador del pasillo.
     * @param level Nivel de altura.
     * @param slot Número de bahía o casilla.
     */
    public WarehouseBlock(String aisle, int level, int slot) {
        this.aisle = aisle;
        this.level = level;
        this.slot = slot;
        this.storedProduct = null;
        this.currentUnits = 0;
    }

    /**
     * Determina si el bloque está completamente libre (sin producto asignado).
     *
     * @return true si el bloque no almacena ningún artículo; false si está
     * ocupado.
     */
    public boolean isFree() {
        return storedProduct == null || currentUnits == 0;
    }

    /**
     * Determina si el bloque ha alcanzado su capacidad máxima para el producto
     * asignado.
     *
     * @return true si está lleno; false en caso contrario.
     */
    public boolean isFull() {
        if (isFree()) {
            return false;
        }
        return currentUnits >= storedProduct.getUnitsPerBlock();
    }

    /**
     * Calcula el espacio remanente disponible en este bloque para el producto
     * actual.
     *
     * @return Número de unidades adicionales que pueden guardarse en este
     * bloque.
     */
    public int getAvailableSpace() {
        if (isFree()) {
            return 0;
        }
        return Math.max(0, storedProduct.getUnitsPerBlock() - currentUnits);
    }

    /**
     * Almacena unidades de un producto en este bloque.
     *
     * @param product Producto que se desea guardar.
     * @param units Cantidad de unidades que se intentan almacenar.
     * @return Cantidad de unidades que efectivamente pudieron ser guardadas.
     */
    public synchronized int storeUnits(Product product, int units) {
        if (units <= 0) {
            return 0;
        }

        // Si está libre, se inicializa con el nuevo producto
        if (isFree()) {
            this.storedProduct = product;
            int toStore = Math.min(units, product.getUnitsPerBlock());
            this.currentUnits = toStore;
            return toStore;
        }

        // Si ya contiene el mismo producto y tiene espacio disponible
        if (this.storedProduct.equals(product)) {
            int space = getAvailableSpace();
            int toStore = Math.min(units, space);
            this.currentUnits += toStore;
            return toStore;
        }

        // Bloque ocupado por otro producto diferente
        return 0;
    }

    /**
     * Descuenta unidades de este bloque al despachar un pedido. Si la cantidad
     * llega a cero, el bloque se libera automáticamente.
     *
     * @param units Cantidad de unidades a retirar.
     * @return Cantidad de unidades efectivamente retiradas.
     */
    public synchronized int removeUnits(int units) {
        if (isFree() || units <= 0) {
            return 0;
        }

        int toRemove = Math.min(units, currentUnits);
        this.currentUnits -= toRemove;

        // Si el bloque queda vacío, se libera el marco/bloque
        if (this.currentUnits == 0) {
            this.storedProduct = null;
        }

        return toRemove;
    }

    /**
     * Obtiene el código o coordenada física legible del bloque
     *
     * @return Cadena con el formato PASILLO-NIVEL-BAHIA.
     */
    public String getLocationCode() {
        return String.format("%s-%d-%02d", aisle, level, slot);
    }

    // Getters
    /**
     * @return Pasillo del bloque.
     */
    public String getAisle() {
        return aisle;
    }

    /**
     * @return Nivel de altura.
     */
    public int getLevel() {
        return level;
    }

    /**
     * @return Número de bahía.
     */
    public int getSlot() {
        return slot;
    }

    /**
     * @return Producto almacenado, o null si está libre.
     */
    public Product getStoredProduct() {
        return storedProduct;
    }

    /**
     * @return Unidades almacenadas actualmente.
     */
    public int getCurrentUnits() {
        return currentUnits;
    }

    @Override
    public String toString() {
        if (isFree()) {
            return "[" + getLocationCode() + ": LIBRE]";
        }
        return "[" + getLocationCode() + ": " + storedProduct.getName() + " (" + currentUnits + "/" + storedProduct.getUnitsPerBlock() + ")]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WarehouseBlock that = (WarehouseBlock) o;
        return level == that.level && slot == that.slot && Objects.equals(aisle, that.aisle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aisle, level, slot);
    }
}
