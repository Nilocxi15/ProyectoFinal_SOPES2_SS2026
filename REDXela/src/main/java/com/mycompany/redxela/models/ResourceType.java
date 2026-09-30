package com.mycompany.redxela.models;

/**
 * Enumeración que define los tipos de recursos físicos limitados disponibles
 * en el centro de distribución REDXela.
 * 
 * En el contexto de Sistemas Operativos, cada recurso representa un tipo de
 * recurso compartido administrado mediante primitivas de sincronización:
 * - Los recursos con capacidad mayor a 1 son gestionados mediante Semáforos Contadores.
 * - El sistema de escaneo de códigos cuenta con capacidad 1, actuando como un
 *   Semáforo Binario (Mutex), garantizando exclusión mutua estricta entre recepción y despacho.
 */
public enum ResourceType {

    /** 6 estaciones de empaque disponibles para pedidos listos para embalar. */
    ESTACION_EMPAQUE("Estacion de Empaque", 6),

    /** 2 áreas de carga para atender camiones de despacho. */
    AREA_CARGA("Area de Carga", 2),

    /** 10 encargados de bodega asignados al traslado y preparación de pedidos. */
    ENCARGADO_BODEGA("Encargado de Bodega", 10),

    /** 3 montacargas para transporte de mercancía pesada o traslados de altura. */
    MONTACARGAS("Montacargas", 3),

    /** 4 estaciones de control de calidad para inspección técnica y seguridad. */
    CONTROL_CALIDAD("Control de Calidad", 4),

    /** 
     * 1 sistema de escaneo de códigos compartido entre recepción y despacho.
     * Funciona como recurso crítico con exclusión mutua pura (Mutex).
     */
    ESCANEAR_CODIGOS("Sistema de Escaneo de Codigos", 1);

    /** Nombre descriptivo para su presentación en la interfaz de usuario. */
    private final String displayName;

    /** Número total de instancias físicas/permisos disponibles en el centro de distribución. */
    private final int totalCapacity;

    /**
     * Constructor del tipo de recurso.
     * 
     * @param displayName Nombre legible del recurso.
     * @param totalCapacity Capacidad máxima de instancias o permisos concurrentes.
     */
    private ResourceType(String displayName, int totalCapacity) {
        this.displayName = displayName;
        this.totalCapacity = totalCapacity;
    }

    /**
     * Obtiene el nombre legible del recurso.
     * @return Cadena con el nombre para visualización.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Obtiene la capacidad total (permisos máximos en el semáforo).
     * @return Cantidad de instancias disponibles.
     */
    public int getTotalCapacity() {
        return totalCapacity;
    }
}
