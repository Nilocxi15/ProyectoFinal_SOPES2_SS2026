package com.mycompany.redxela.ipc;

/**
 * Interfaz observadora para recibir notificaciones cuando el área de Inventario
 * procesa un cargamento llegado a través de la tubería IPC.
 *
 * Permite mantener desacoplado el backend de la interfaz gráfica Swing y del
 * futuro planificador de procesos (el cual debe despertar pedidos en espera de
 * stock).
 */
@FunctionalInterface
public interface StockEventListener {

    /**
     * Invocado cuando un nuevo lote de mercadería ha sido procesado y
     * almacenado.
     *
     * @param event Información del cargamento y bloques asignados en el
     * almacén.
     */
    void onStockArrived(StockArrivalEvent event);
}
