package com.mycompany.redxela.ipc;

import com.mycompany.redxela.models.Product;
import com.mycompany.redxela.models.WarehouseBlock;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Encapsula la información de un lote de mercadería recibido y procesado a
 * través del mecanismo de comunicación entre procesos (IPC).
 *
 * Este evento contiene los datos procesados por el área de Inventario tras
 * recibir el mensaje de la tubería, incluyendo la tabla de asignación física
 * (bloques del almacén) donde quedó almacenado el lote.
 */
public class StockArrivalEvent {

    /**
     * Producto recibido en el cargamento.
     */
    private final Product product;

    /**
     * Cantidad de unidades físicas recibidas.
     */
    private final int quantity;

    /**
     * Bloques del almacén (no contiguos) asignados para guardar este lote.
     */
    private final List<WarehouseBlock> allocatedBlocks;

    /**
     * Marca de tiempo exacta del procesamiento del lote.
     */
    private final LocalDateTime timestamp;

    /**
     * Construye un nuevo evento de llegada de inventario.
     *
     * @param product Producto recibido.
     * @param quantity Cantidad recibida.
     * @param allocatedBlocks Lista de bloques de almacén asignados.
     */
    public StockArrivalEvent(Product product, int quantity, List<WarehouseBlock> allocatedBlocks) {
        this.product = product;
        this.quantity = quantity;
        this.allocatedBlocks = allocatedBlocks != null ? allocatedBlocks : Collections.emptyList();
        this.timestamp = LocalDateTime.now();
    }

    /**
     * @return Producto recibido.
     */
    public Product getProduct() {
        return product;
    }

    /**
     * @return Cantidad de unidades ingresadas.
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * @return Lista inmutable de bloques físicos asignados.
     */
    public List<WarehouseBlock> getAllocatedBlocks() {
        return Collections.unmodifiableList(allocatedBlocks);
    }

    /**
     * @return Momento exacto de registro en inventario.
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("[%s] +%d unidades de '%s' almacenadas en %d bloque(s)",
                timestamp.toLocalTime().toString().substring(0, 8),
                quantity,
                product.getName(),
                allocatedBlocks.size());
    }
}
