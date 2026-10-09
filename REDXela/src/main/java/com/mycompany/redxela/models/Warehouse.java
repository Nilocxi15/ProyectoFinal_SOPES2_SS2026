package com.mycompany.redxela.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Administra el espacio físico de almacenamiento del centro de distribución
 * REDXela.
 *
 * Esta clase implementa el subsistema de GESTIÓN DE ARCHIVOS / ASIGNACIÓN NO
 * CONTIGUA DE ALMACENAMIENTO:
 *
 * - El almacén actúa como el medio de almacenamiento físico (análogo a un disco
 * secundario o memoria RAM).
 *
 * - Cuando ingresa un lote de mercadería por el área de Recepción, el almacén
 * busca bloques libres disponibles sin exigir que sean consecutivos (Asignación
 * Enlazada / Indexada).
 *
 * - Mantiene el control exacto de qué bloques físicos (Pasillo, Nivel, Bahía)
 * fueron asignados a cada producto, permitiendo fragmentar lotes grandes a
 * través de la instalación física.
 *
 * - Sincroniza el acceso concurrente entre el hilo de Recepción (asignación de
 * bloques) y los hilos de Despacho (liberación de bloques), previniendo
 * condiciones de carrera sobre el mapa de ocupación.
 */
public class Warehouse {

    /**
     * Pasillos físicos del almacén.
     */
    public static final String[] AISLES = {"A", "B", "C"};

    /**
     * Niveles de altura por estante (1, 2, 3).
     */
    public static final int LEVELS = 3;

    /**
     * Número de bahías o casillas por nivel (1 a 8).
     */
    public static final int SLOTS_PER_LEVEL = 8;

    /**
     * Total de bloques físicos disponibles (3 pasillos * 3 niveles * 8 bahías =
     * 72).
     */
    public static final int TOTAL_CAPACITY_BLOCKS = AISLES.length * LEVELS * SLOTS_PER_LEVEL;

    /**
     * Matriz tridimensional que representa el espacio físico del almacén.
     */
    private final WarehouseBlock[][][] grid;

    /**
     * Inicializa la estructura física del almacén creando todos sus bloques
     * vacíos.
     */
    public Warehouse() {
        this.grid = new WarehouseBlock[AISLES.length][LEVELS][SLOTS_PER_LEVEL];

        for (int a = 0; a < AISLES.length; a++) {
            for (int l = 0; l < LEVELS; l++) {
                for (int s = 0; s < SLOTS_PER_LEVEL; s++) {
                    grid[a][l][s] = new WarehouseBlock(AISLES[a], l + 1, s + 1);
                }
            }
        }
    }

    /**
     * Asigna almacenamiento para un lote de unidades de un producto utilizando
     * la política de ASIGNACIÓN NO CONTIGUA:
     *
     * 1. Primero aprovecha bloques ya asignados a este producto que tengan
     * espacio disponible.
     *
     * 2. Si aún quedan unidades, busca y reserva los primeros bloques libres
     * disponibles en cualquier pasillo/nivel.
     *
     * @param product Producto que se está recibiendo.
     * @param unitsToStore Cantidad total de unidades a almacenar.
     * @return Lista de bloques físicos donde quedó distribuido el lote (tabla
     * de asignación del lote).
     * @throws IllegalStateException Si el almacén no tiene suficientes bloques
     * libres para guardar el lote.
     */
    public synchronized List<WarehouseBlock> allocateUnits(Product product, int unitsToStore) {
        if (unitsToStore <= 0) {
            return Collections.emptyList();
        }

        // 1. Verificar si hay capacidad suficiente antes de modificar
        int availableCapacity = calculateAvailableUnitsCapacity(product);
        if (unitsToStore > availableCapacity) {
            throw new IllegalStateException("Capacidad insuficiente en el almacén. Espacio disponible: "
                    + availableCapacity + " unidades, requeridas: " + unitsToStore);
        }

        List<WarehouseBlock> allocatedBlocks = new ArrayList<>();
        int remaining = unitsToStore;

        // Fase A: Llenar bloques parcialmente ocupados por este mismo producto
        for (int a = 0; a < AISLES.length && remaining > 0; a++) {
            for (int l = 0; l < LEVELS && remaining > 0; l++) {
                for (int s = 0; s < SLOTS_PER_LEVEL && remaining > 0; s++) {
                    WarehouseBlock block = grid[a][l][s];
                    if (!block.isFree() && block.getStoredProduct().equals(product) && !block.isFull()) {
                        int stored = block.storeUnits(product, remaining);
                        if (stored > 0) {
                            remaining -= stored;
                            if (!allocatedBlocks.contains(block)) {
                                allocatedBlocks.add(block);
                            }
                        }
                    }
                }
            }
        }

        // Fase B: Buscar bloques completamente libres (asignación no contigua)
        for (int a = 0; a < AISLES.length && remaining > 0; a++) {
            for (int l = 0; l < LEVELS && remaining > 0; l++) {
                for (int s = 0; s < SLOTS_PER_LEVEL && remaining > 0; s++) {
                    WarehouseBlock block = grid[a][l][s];
                    if (block.isFree()) {
                        int stored = block.storeUnits(product, remaining);
                        if (stored > 0) {
                            remaining -= stored;
                            allocatedBlocks.add(block);
                        }
                    }
                }
            }
        }

        return allocatedBlocks;
    }

    /**
     * Libera unidades del almacén tras el despacho de un pedido. Descuenta las
     * unidades de los bloques donde esté almacenado el producto. Si un bloque
     * queda con 0 unidades, se libera automáticamente.
     *
     * @param product Producto que se retiró del almacén.
     * @param unitsToRemove Cantidad de unidades despachadas.
     * @return Cantidad de unidades efectivamente retiradas.
     */
    public synchronized int deallocateUnits(Product product, int unitsToRemove) {
        if (unitsToRemove <= 0) {
            return 0;
        }

        int remainingToRemove = unitsToRemove;

        for (int a = 0; a < AISLES.length && remainingToRemove > 0; a++) {
            for (int l = 0; l < LEVELS && remainingToRemove > 0; l++) {
                for (int s = 0; s < SLOTS_PER_LEVEL && remainingToRemove > 0; s++) {
                    WarehouseBlock block = grid[a][l][s];
                    if (!block.isFree() && block.getStoredProduct().equals(product)) {
                        int removed = block.removeUnits(remainingToRemove);
                        remainingToRemove -= removed;
                    }
                }
            }
        }

        return unitsToRemove - remainingToRemove;
    }

    /**
     * Calcula la cantidad máxima de unidades de un producto que pueden ser
     * almacenadas sumando el espacio en bloques parciales del producto más
     * todos los bloques completamente libres.
     *
     * @param product Producto a evaluar.
     * @return Capacidad disponible en unidades.
     */
    public synchronized int calculateAvailableUnitsCapacity(Product product) {
        int capacity = 0;
        for (int a = 0; a < AISLES.length; a++) {
            for (int l = 0; l < LEVELS; l++) {
                for (int s = 0; s < SLOTS_PER_LEVEL; s++) {
                    WarehouseBlock block = grid[a][l][s];
                    if (block.isFree()) {
                        capacity += product.getUnitsPerBlock();
                    } else if (block.getStoredProduct().equals(product)) {
                        capacity += block.getAvailableSpace();
                    }
                }
            }
        }
        return capacity;
    }

    /**
     * Cuenta cuántos bloques físicos se encuentran actualmente libres.
     *
     * @return Cantidad de bloques vacíos.
     */
    public synchronized int getFreeBlocksCount() {
        int count = 0;
        for (int a = 0; a < AISLES.length; a++) {
            for (int l = 0; l < LEVELS; l++) {
                for (int s = 0; s < SLOTS_PER_LEVEL; s++) {
                    if (grid[a][l][s].isFree()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Cuenta cuántos bloques físicos se encuentran actualmente ocupados.
     *
     * @return Cantidad de bloques en uso.
     */
    public synchronized int getOccupiedBlocksCount() {
        return TOTAL_CAPACITY_BLOCKS - getFreeBlocksCount();
    }

    /**
     * Retorna el porcentaje de ocupación global del almacén.
     *
     * @return Porcentaje entre 0.0 y 100.0.
     */
    public synchronized double getOccupancyPercentage() {
        return ((double) getOccupiedBlocksCount() / TOTAL_CAPACITY_BLOCKS) * 100.0;
    }

    /**
     * Retorna todos los bloques donde un producto específico está guardado. Es
     * el análogo al recorrido de la cadena de bloques / inodo de un archivo.
     *
     * @param product Producto a buscar.
     * @return Lista de bloques físicos ocupados por el producto.
     */
    public synchronized List<WarehouseBlock> getBlocksByProduct(Product product) {
        List<WarehouseBlock> result = new ArrayList<>();
        for (int a = 0; a < AISLES.length; a++) {
            for (int l = 0; l < LEVELS; l++) {
                for (int s = 0; s < SLOTS_PER_LEVEL; s++) {
                    WarehouseBlock block = grid[a][l][s];
                    if (!block.isFree() && block.getStoredProduct().equals(product)) {
                        result.add(block);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Retorna una lista plana con los 72 bloques del almacén
     *
     * @return Lista de todos los bloques en orden.
     */
    public synchronized List<WarehouseBlock> getAllBlocks() {
        List<WarehouseBlock> all = new ArrayList<>(TOTAL_CAPACITY_BLOCKS);
        for (int a = 0; a < AISLES.length; a++) {
            for (int l = 0; l < LEVELS; l++) {
                for (int s = 0; s < SLOTS_PER_LEVEL; s++) {
                    all.add(grid[a][l][s]);
                }
            }
        }
        return all;
    }

    /**
     * Accede directamente a un bloque físico por sus índices de matriz.
     *
     * @param aisleIndex Índice de pasillo (0 a 2).
     * @param levelIndex Índice de nivel (0 a 2).
     * @param slotIndex Índice de bahía (0 a 7).
     * @return Instancia del bloque físico correspondiente.
     */
    public WarehouseBlock getBlock(int aisleIndex, int levelIndex, int slotIndex) {
        return grid[aisleIndex][levelIndex][slotIndex];
    }
}
