package com.mycompany.redxela.scheduler;

import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.models.OrderState;
import com.mycompany.redxela.models.Product;
import com.mycompany.redxela.models.ResourceType;
import com.mycompany.redxela.models.Warehouse;
import com.mycompany.redxela.resources.ResourceManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hilo de trabajo (Worker Thread) que simula la ejecución física y concurrente
 * de un pedido.
 *
 * Modela la EJECUCIÓN DEL PROCESO EN LA CPU:
 *
 * 1. Transición de Listo (Ready) a Esperando Recursos / En Ejecución (Running).
 *
 * 2. Adquisición secuencial de recursos compartidos (Semáforos) según el tipo
 * de mercancía, recreando la condición de Retención y Espera (Hold and Wait).
 *
 * 3. Simulación de la Ráfaga de CPU (Burst Time) proporcional a la cantidad
 * total de artículos.
 *
 * 4. Actualización del Almacén Físico (liberación de bloques).
 *
 * 5. Liberación de todos los semáforos adquiridos y registro de métricas de
 * retorno.
 */
public class OrderWorker implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(OrderWorker.class.getName());

    /**
     * Tiempo base de procesamiento en milisegundos por cada artículo del
     * pedido.
     */
    public static final int MS_PER_UNIT = 400;

    /**
     * Lapso de transición entre la solicitud de un recurso y el siguiente (Hold
     * and Wait).
     */
    public static final int RESOURCE_STEP_DELAY_MS = 250;

    private final Order order;
    private final ResourceManager resourceManager;
    private final Warehouse warehouse;
    private final SchedulerMetrics metrics;
    private final List<SchedulerListener> listeners;

    public OrderWorker(Order order, ResourceManager resourceManager, Warehouse warehouse,
            SchedulerMetrics metrics, List<SchedulerListener> listeners) {
        this.order = order;
        this.resourceManager = resourceManager;
        this.warehouse = warehouse;
        this.metrics = metrics;
        this.listeners = listeners;
    }

    @Override
    public void run() {
        try {
            LOGGER.log(Level.INFO, "[WORKER] Iniciando trámite de recursos para Pedido #{0} ({1} unidades)",
                    new Object[]{order.getId().toString().substring(0, 8), order.getTotalUnits()});

            // Fase 1: Adquisición secuencial de recursos (Semáforos)
            List<ResourceType> acquisitionOrder = order.getMerchandiseType().getResourceAcquisitionOrder();
            Map<ResourceType, Integer> requiredResources = order.getMerchandiseType().getRequiredResources();

            for (int i = 0; i < acquisitionOrder.size(); i++) {
                ResourceType resource = acquisitionOrder.get(i);
                int count = requiredResources.getOrDefault(resource, 1);
                boolean acquired = resourceManager.acquireResource(order, resource, count);
                if (!acquired) {
                    LOGGER.log(Level.WARNING, "[WORKER] Pedido #{0} no pudo adquirir [{1}] (expulsado por Rollback de Deadlock)",
                            new Object[]{order.getId().toString().substring(0, 8), resource.getDisplayName()});
                    return;
                }

                // Breve pausa de coordinación entre recursos para evidenciar Retención y Espera (Hold & Wait)
                if (i < acquisitionOrder.size() - 1) {
                    Thread.sleep(RESOURCE_STEP_DELAY_MS);
                }
            }

            // Fase 2: Transición a EN_PROCESO
            int totalDurationMs = Math.max(1000, order.getTotalUnits() * MS_PER_UNIT);
            order.setState(OrderState.EN_PROCESO);
            order.setStartProcessingTime(LocalDateTime.now());
            order.setProgressPercentage(0);
            order.setEstimatedRemainingSeconds(totalDurationMs / 1000);
            notifyStarted();

            // Fase 3: Simulación del Burst Time de trabajo
            int intervalMs = 200;
            int elapsedMs = 0;

            while (elapsedMs < totalDurationMs) {
                Thread.sleep(intervalMs);
                elapsedMs += intervalMs;

                int percentage = Math.min(100, (elapsedMs * 100) / totalDurationMs);
                int remainingSeconds = Math.max(0, (totalDurationMs - elapsedMs) / 1000);
                order.setProgressPercentage(percentage);
                order.setEstimatedRemainingSeconds(remainingSeconds);
                notifyProgress(percentage, remainingSeconds);
            }

            // Fase 4: Despacho finalizado y desasignación de bloques del almacén físico
            for (Map.Entry<Product, Integer> item : order.getItems().entrySet()) {
                warehouse.deallocateUnits(item.getKey(), item.getValue());
            }

            // Fase 5: Liberación de recursos físicos
            resourceManager.releaseAllResources(order);

            // Fase 6: Finalización y registro de métricas
            order.setProgressPercentage(100);
            order.setEstimatedRemainingSeconds(0);
            order.setState(OrderState.COMPLETADO);
            order.setCompletionTime(LocalDateTime.now());
            metrics.recordOrderCompletion(order);
            notifyCompleted();

            LOGGER.log(Level.INFO, "[WORKER] Pedido #{0} COMPLETADO exitosamente.",
                    order.getId().toString().substring(0, 8));

        } catch (InterruptedException ex) {
            LOGGER.log(Level.WARNING, "[WORKER] Hilo interrumpido para Pedido #{0}",
                    order.getId().toString().substring(0, 8));
            resourceManager.releaseAllResources(order);
            Thread.currentThread().interrupt();
        }
    }

    private void notifyStarted() {
        synchronized (listeners) {
            for (SchedulerListener l : listeners) {
                try {
                    l.onOrderStarted(order);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void notifyProgress(int percentage, int remainingSeconds) {
        synchronized (listeners) {
            for (SchedulerListener l : listeners) {
                try {
                    l.onOrderProgress(order, percentage, remainingSeconds);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void notifyCompleted() {
        synchronized (listeners) {
            for (SchedulerListener l : listeners) {
                try {
                    l.onOrderCompleted(order);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
