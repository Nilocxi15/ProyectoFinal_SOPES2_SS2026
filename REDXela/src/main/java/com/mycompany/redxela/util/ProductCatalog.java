package com.mycompany.redxela.util;

import com.mycompany.redxela.models.Product;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ProductCatalog {

    private static final List<Product> PRODUCTS = new ArrayList<>();

    static {
        /**
         * 1. ESTÁNDAR
         */
        PRODUCTS.add(new Product(UUID.randomUUID(), "Caja de Herramientas Básica", MerchandiseCatalog.ESTANDAR, 20, 10));
        PRODUCTS.add(new Product(UUID.randomUUID(), "Set de Toallas de Algodón", MerchandiseCatalog.ESTANDAR, 50, 15));

        /**
         * 2. PESADO
         */
        PRODUCTS.add(new Product(UUID.randomUUID(), "Refrigerador Industrial 2 Puertas", MerchandiseCatalog.PESADO, 5, 1));
        PRODUCTS.add(new Product(UUID.randomUUID(), "Generador Eléctrico a Gasolina", MerchandiseCatalog.PESADO, 8, 2));

        /**
         * 3. FRÁGIL
         */
        PRODUCTS.add(new Product(UUID.randomUUID(), "Juego de Vajilla de Porcelana (12 pzs)", MerchandiseCatalog.FRAGIL, 15, 4));
        PRODUCTS.add(new Product(UUID.randomUUID(), "Jarrón de Cristal Veneciano", MerchandiseCatalog.FRAGIL, 10, 3));

        /**
         * 4. ELECTRÓNICO (Deadlock A)
         */
        PRODUCTS.add(new Product(UUID.randomUUID(), "Laptop Gamer Ultra 16GB", MerchandiseCatalog.ELECTRONICO, 12, 6));
        PRODUCTS.add(new Product(UUID.randomUUID(), "Smartphone Flagship 256GB", MerchandiseCatalog.ELECTRONICO, 25, 10));

        /**
         * 5. CARGA ESPECIAL (Deadlock B)
         */
        PRODUCTS.add(new Product(UUID.randomUUID(), "Baterías Industriales de Litio", MerchandiseCatalog.CARGA_ESPECIAL, 6, 2));
        PRODUCTS.add(new Product(UUID.randomUUID(), "Contenedor de Resina Industrial", MerchandiseCatalog.CARGA_ESPECIAL, 4, 1));
    }

    public static List<Product> getAll() {
        return Collections.unmodifiableList(PRODUCTS);
    }

    public static Product findById(UUID id) {
        return PRODUCTS.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
