package com.mycompany.redxela.ipc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;

/**
 * Encapsula una Tubería (Pipe) real de comunicación entre procesos/hilos.
 *
 * - Una tubería (Pipe) es un canal unidireccional de comunicación (IPC)
 * gestionado a través de un búfer circular en memoria.
 *
 * - Desacopla completamente las entidades comunicantes: el extremo escritor
 * (Recepción) no conoce las estructuras internas del extremo lector
 * (Inventario).
 *
 * - Sincronización implícita y control de flujo (Backpressure): ( Si la tubería
 * está vacía, el lector se bloquea automáticamente en estado WAITING sin
 * consumir ciclos de CPU. ) Si el búfer se llena, el escritor se bloquea hasta
 * que el lector libere espacio.
 *
 * - Evita la pérdida de datos cuando el productor genera información más rápido
 * de lo que el consumidor puede procesarla.
 */
public class WarehousePipe implements Closeable {

    /**
     * Tamaño del búfer interno de la tubería en caracteres (1024 caracteres).
     */
    public static final int BUFFER_SIZE = 1024;

    /**
     * Extremo de escritura de la tubería (utilizado por el área de Recepción).
     */
    private final PipedWriter pipedWriter;

    /**
     * Extremo de lectura de la tubería (utilizado por el área de Inventario).
     */
    private final PipedReader pipedReader;

    /**
     * Envoltura bufferizada para escritura de líneas completas.
     */
    private final BufferedWriter writer;

    /**
     * Envoltura bufferizada para lectura de líneas completas (bloqueante).
     */
    private final BufferedReader reader;

    /**
     * Indicador de estado de la tubería.
     */
    private volatile boolean closed;

    /**
     * Construye e interconecta los dos extremos de la tubería.
     *
     * @throws IOException Si ocurre un fallo al inicializar o interconectar los
     * canales.
     */
    public WarehousePipe() throws IOException {
        this.pipedWriter = new PipedWriter();
        // Conexión del lector al escritor con búfer circular de tamaño definido
        this.pipedReader = new PipedReader(this.pipedWriter, BUFFER_SIZE);
        this.writer = new BufferedWriter(this.pipedWriter);
        this.reader = new BufferedReader(this.pipedReader);
        this.closed = false;
    }

    /**
     * Escribe un mensaje en la tubería y vacía el búfer (flush) para
     * transmisión inmediata. Si el búfer está lleno, este método se bloquea
     * temporalmente hasta que haya espacio.
     *
     * @param message Mensaje serializado a transmitir (sin incluir salto de
     * línea).
     * @throws IOException Si la tubería está rota o cerrada.
     */
    public synchronized void writeMessage(String message) throws IOException {
        if (closed) {
            throw new IOException("No se puede escribir: la tubería está cerrada.");
        }
        writer.write(message);
        writer.newLine();
        writer.flush(); // Garantiza la emisión inmediata hacia el extremo lector
    }

    /**
     * Lee la siguiente línea de texto disponible en la tubería. Esta operación
     * es BLOQUEANTE: el hilo llamador quedará suspendido hasta que el productor
     * escriba un mensaje completo terminado en salto de línea, o la tubería se
     * cierre.
     *
     * @return El mensaje recibido, o null si la tubería fue cerrada por el
     * extremo escritor.
     * @throws IOException Si ocurre un error de E/S durante la lectura.
     */
    public String readMessage() throws IOException {
        if (closed) {
            return null;
        }
        return reader.readLine();
    }

    /**
     * Verifica si la tubería se encuentra cerrada.
     *
     * @return true si los extremos fueron cerrados; false si está operativa.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Cierra ambos extremos de la tubería de forma segura liberando los
     * descriptores de E/S.
     */
    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
    }
}
