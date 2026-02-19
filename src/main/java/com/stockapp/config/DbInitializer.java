package com.stockapp.config;

import java.sql.Connection;
import java.sql.Statement;

public class DbInitializer {

    public static void init() {
        ensureTablesExist();
        System.out.println("DB INIT ✅");

    }

    private static void ensureTablesExist() {
        try (Connection conn = DatabaseConfig.getAppConnection();
             Statement st = conn.createStatement()) {


            st.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(100) UNIQUE NOT NULL
                );
            """);

            st.execute("""
                INSERT INTO categories(name)
                VALUES ('Genel')
                ON CONFLICT (name) DO NOTHING;
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS products (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(150) NOT NULL,
                    barcode VARCHAR(100) UNIQUE NOT NULL,
                    category_id INT,
                    stock INT NOT NULL DEFAULT 0,
                    price NUMERIC(10,2) DEFAULT 0
                );
            """);

            st.execute("""
                ALTER TABLE products
                ADD COLUMN IF NOT EXISTS category_id INT;
            """);

            st.execute("""
                DO $$
                BEGIN
                  IF NOT EXISTS (
                    SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_category'
                  ) THEN
                    ALTER TABLE products
                    ADD CONSTRAINT fk_products_category
                    FOREIGN KEY (category_id) REFERENCES categories(id);
                  END IF;
                END $$;
            """);

            st.execute("""
                DO $$
                BEGIN
                  IF EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema='public' AND table_name='sales'
                  ) THEN

                    IF EXISTS (
                      SELECT 1
                      FROM information_schema.columns
                      WHERE table_schema='public' AND table_name='sales' AND column_name='product_id'
                    ) THEN
                      IF NOT EXISTS (
                        SELECT 1 FROM information_schema.tables
                        WHERE table_schema='public' AND table_name='sales_old'
                      ) THEN
                        ALTER TABLE sales RENAME TO sales_old;
                      END IF;
                    END IF;

                  END IF;
                END $$;
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS sales (
                    id SERIAL PRIMARY KEY,
                    sale_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS sale_items (
                    id SERIAL PRIMARY KEY,
                    sale_id INT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
                    product_id INT NOT NULL REFERENCES products(id),
                    quantity INT NOT NULL,
                    unit_price NUMERIC(10,2) NOT NULL,
                    line_total NUMERIC(12,2) NOT NULL
                );
            """);

            // ✅ Performans indexleri
            st.execute("CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_products_stock ON products(stock);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_products_category_id ON products(category_id);");
            // barcode zaten UNIQUE -> index var

            st.execute("CREATE INDEX IF NOT EXISTS idx_sales_sale_date ON sales(sale_date);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON sale_items(sale_id);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_sale_items_product_id ON sale_items(product_id);");

            st.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;");
            st.execute("CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products USING gin (name gin_trgm_ops);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_products_barcode_trgm ON products USING gin (barcode gin_trgm_ops);");
        } catch (Exception e) {
            throw new RuntimeException("Tablo init başarısız", e);
        }
    }
}
