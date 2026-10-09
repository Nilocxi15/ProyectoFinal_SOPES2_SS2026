package com.mycompany.redxela.ipc;

import com.mycompany.redxela.models.Product;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Representa el Área de Recepción de Mercadería de REDXela (Proceso Productor).
 *
 * - Funciona como la entidad PRODUCTORA dentro del patrón Productor-Consumidor.
 * - Desacoplamiento estricto: Esta área NO posee acceso ni modifica las
 * estructuras internas del Almacén ni del Área de Inventario.
 *
 * - Su única vía de comunicación hacia el sistema es el extremo de escritura de
 * la tubería (Pipe).
 *
 * - Cuando arriba un camión con mercancía (ya sea por ingreso manual desde la
 * interfaz o por simulación automática), serializa los datos y los envía a
 * través del canal IPC.
 */
public class ReceptionArea {

    private static final Logger LOGGER = Logger.getLogger(ReceptionArea.class.getName());

    /**
     * Canal de comunicación unidireccional hacia el área de Inventario.
     */
    private final WarehousePipe pipe;

    /**
     * Construye el área de recepción vinculada a una tubería de comunicación.
     *
     * @param pipe Instancia de la tubería compartida.
     */
    public ReceptionArea(WarehousePipe pipe) {
        this.pipe = Objects.requireNonNull(pipe, "La tubería IPC no puede ser nula");
    }

    /**
     * Registra la llegada de un lote de mercadería y lo despacha a través de la
     * tubería IPC.
     *
     * Protocolo de serialización: "UUID_PRODUCTO:CANTIDAD"
     *
     * @param product Producto recibido.
     * @param quantity Cantidad de unidades físicas recibidas.
     * @return true si el mensaje se escribió exitosamente en la tubería; false
     * si hubo un error.
     */
    public boolean receiveStock(Product product, int quantity) {
        if (product == null || quantity <= 0) {
            LOGGER.log(Level.WARNING, "Intento de recepción inválido: producto nulo o cantidad <= 0");
            return false;
        }

        if (pipe.isClosed()) {
            LOGGER.log(Level.SEVERE, "No se puede despachar: la tubería de recepción está cerrada.");
            return false;
        }

        // Serialización textual del mensaje según el protocolo establecido
        String message = product.getId().toString() + ":" + quantity;

        try {
            LOGGER.log(Level.INFO, "[RECEPCIÓN -> PIPE] Enviando lote: {0} unidades de {1} (ID: {2})",
                    new Object[]{quantity, product.getName(), product.getId()});

            // Escritura bloqueante y sincronizada en el búfer de la tubería
            pipe.writeMessage(message);
            return true;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Fallo al escribir en la tubería IPC: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Obtiene la referencia a la tubería utilizada por esta área.
     *
     * @return Tubería IPC asociada.
     */
    public WarehousePipe getPipe() {
        return pipe;
    }
}
