package com.mycompany.redxela.util;

import com.mycompany.redxela.models.Merchandise;
import com.mycompany.redxela.models.ResourceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Catálogo maestro predefinido de tipos de mercancía para REDXela.
 *
 * Implementa los requerimientos de la especificación técnica:
 *
 *
 * 1. Define un mínimo de 5 tipos de mercancía con combinaciones distintas de
 * recursos.
 *
 *
 * 2. Incorpora deliberadamente un escenario de contención cruzada entre
 * ELECTRONICO y CARGA_ESPECIAL, los cuales solicitan los recursos
 * ESCANEAR_CODIGOS y MONTACARGAS en orden inverso:
 *
 *  - ELECTRONICO: solicita ESCANEAR_CODIGOS -> MONTACARGAS -> CONTROL_CALIDAD
 *
 *  - CARGA_ESPECIAL: solicita MONTACARGAS -> ESCANEAR_CODIGOS -> AREA_CARGA Esto
 * 
 * genera una Espera Circular (Circular Wait), una de las 4 condiciones de
 * Coffman necesarias para provocar un Interbloqueo (Deadlock) de forma
 * reproducible.
 */
public class MerchandiseCatalog {

    /**
     * Mercancía 1: Artículos comunes de consumo sin requerimientos especiales.
     */
    public static final Merchandise ESTANDAR;

    /**
     * Mercancía 2: Artículos de alto tonelaje que demandan montacargas y
     * personal de apoyo.
     */
    public static final Merchandise PESADO;

    /**
     * Mercancía 3: Artículos frágiles que requieren inspección rigurosa de
     * control de calidad.
     */
    public static final Merchandise FRAGIL;

    /**
     * Mercancía 4: Dispositivos de alto valor. Escenario Deadlock A: Solicita
     * primero el escáner de códigos y luego el montacargas.
     */
    public static final Merchandise ELECTRONICO;

    /**
     * Mercancía 5: Mercancía especial o regulada. Escenario Deadlock B:
     * Solicita primero el montacargas y luego el escáner de códigos.
     */
    public static final Merchandise CARGA_ESPECIAL;

    /**
     * Lista inmutable con todos los tipos disponibles.
     */
    private static final List<Merchandise> ALL_MERCHANDISE = new ArrayList<>();

    static {
        /*
         * 1. ESTÁNDAR: Flujo básico con baja contención de recursos.
         * Recursos: 1 Encargado de Bodega, 1 Estación de Empaque.
         */
        Map<ResourceType, Integer> estandarResources = new LinkedHashMap<>();
        estandarResources.put(ResourceType.ENCARGADO_BODEGA, 1);
        estandarResources.put(ResourceType.ESTACION_EMPAQUE, 1);
        ESTANDAR = new Merchandise(
                UUID.randomUUID(),
                "Estándar",
                "Artículos comunes de consumo sin requerimientos especiales",
                estandarResources,
                List.of(ResourceType.ENCARGADO_BODEGA, ResourceType.ESTACION_EMPAQUE)
        );

        /*
         * 2. PESADO: Mercancía voluminosa.
         * Recursos: 1 Montacargas, 2 Encargados de Bodega, 1 Área de Carga.
         */
        Map<ResourceType, Integer> heavyResources = new LinkedHashMap<>();
        heavyResources.put(ResourceType.MONTACARGAS, 1);
        heavyResources.put(ResourceType.ENCARGADO_BODEGA, 2);
        heavyResources.put(ResourceType.AREA_CARGA, 1);
        PESADO = new Merchandise(
                UUID.randomUUID(),
                "Pesado",
                "Maquinaria, muebles o artículos de alto tonelaje",
                heavyResources,
                List.of(ResourceType.MONTACARGAS, ResourceType.ENCARGADO_BODEGA, ResourceType.AREA_CARGA)
        );

        /*
         * 3. FRÁGIL: Requiere revisión técnica antes de embalar.
         * Recursos: 1 Control de Calidad, 1 Estación de Empaque, 1 Encargado de Bodega.
         */
        Map<ResourceType, Integer> fragileResources = new LinkedHashMap<>();
        fragileResources.put(ResourceType.CONTROL_CALIDAD, 1);
        fragileResources.put(ResourceType.ESTACION_EMPAQUE, 1);
        fragileResources.put(ResourceType.ENCARGADO_BODEGA, 1);
        FRAGIL = new Merchandise(
                UUID.randomUUID(),
                "Frágil",
                "Cristalería, vajillas y objetos susceptibles a roturas",
                fragileResources,
                List.of(ResourceType.CONTROL_CALIDAD, ResourceType.ESTACION_EMPAQUE, ResourceType.ENCARGADO_BODEGA)
        );

        /*
         * 4. ELECTRÓNICO DE ALTO VALOR:
         * Orden de adquisición: ESCANEAR_CODIGOS -> MONTACARGAS -> CONTROL_CALIDAD.
         * Involucrado en Deadlock deliberado con CARGA_ESPECIAL.
         */
        Map<ResourceType, Integer> electronicResources = new LinkedHashMap<>();
        electronicResources.put(ResourceType.ESCANEAR_CODIGOS, 1);
        electronicResources.put(ResourceType.MONTACARGAS, 1);
        electronicResources.put(ResourceType.CONTROL_CALIDAD, 1);
        ELECTRONICO = new Merchandise(
                UUID.randomUUID(),
                "Electrónico de Alto Valor",
                "Dispositivos que requieren escaneo estricto de número de serie y montacargas",
                electronicResources,
                List.of(ResourceType.ESCANEAR_CODIGOS, ResourceType.MONTACARGAS, ResourceType.CONTROL_CALIDAD)
        );

        /*
         * 5. CARGA ESPECIAL:
         * Orden de adquisición inverso: MONTACARGAS -> ESCANEAR_CODIGOS -> AREA_CARGA.
         * Involucrado en Deadlock deliberado con ELECTRONICO.
         */
        Map<ResourceType, Integer> specialResources = new LinkedHashMap<>();
        specialResources.put(ResourceType.MONTACARGAS, 1);
        specialResources.put(ResourceType.ESCANEAR_CODIGOS, 1);
        specialResources.put(ResourceType.AREA_CARGA, 1);
        CARGA_ESPECIAL = new Merchandise(
                UUID.randomUUID(),
                "Carga Especial",
                "Material regulado que requiere montacargas y luego escaneo de seguridad",
                specialResources,
                List.of(ResourceType.MONTACARGAS, ResourceType.ESCANEAR_CODIGOS, ResourceType.AREA_CARGA)
        );

        ALL_MERCHANDISE.addAll(List.of(ESTANDAR, PESADO, FRAGIL, ELECTRONICO, CARGA_ESPECIAL));
    }

    /**
     * Obtiene una lista inmutable con los 5 tipos de mercancía registrados.
     *
     * @return Lista inmutable de mercancías.
     */
    public static List<Merchandise> getAll() {
        return Collections.unmodifiableList(ALL_MERCHANDISE);
    }
}
