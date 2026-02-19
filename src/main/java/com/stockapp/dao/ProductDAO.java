package com.stockapp.dao;

import com.stockapp.config.DatabaseConfig;
import com.stockapp.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDAO {

    public static Optional<Product> findByBarcode(String barcode) {
        String sql = """
            SELECT p.id, p.name, p.barcode, p.stock, p.price,
                   p.category_id, c.name AS category_name
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE p.barcode = ?
        """;

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, barcode);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Product(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("barcode"),
                            rs.getInt("category_id"),
                            rs.getString("category_name"),
                            rs.getInt("stock"),
                            rs.getDouble("price")
                    ));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Barkod arama hatası", e);
        }
    }

    public static List<Product> findAll() {
        String sql = """
        SELECT
          p.id, p.name, p.barcode, p.stock, p.price, p.category_id,
          c.name AS category_name
        FROM products p
        LEFT JOIN categories c ON c.id = p.category_id
        ORDER BY p.id DESC
    """;

        List<Product> list = new ArrayList<>();

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("barcode"),
                        rs.getInt("category_id"),
                        rs.getString("category_name"),
                        rs.getInt("stock"),
                        rs.getDouble("price")
                ));
            }
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Ürünler getirilemedi", e);
        }
    }


    /**
     * ✅ Daha hızlı arama:
     * - barcode araması ayrı
     * - name araması ayrı (ILIKE)
     * Böylece barcode UNIQUE index ile direkt hızlanır.
     *
     * Not: %...% içeren LIKE için en iyi çözüm pg_trgm index (aşağıda not).
     */
    public static List<Product> search(String q) {
        String trimmed = (q == null) ? "" : q.trim();
        if (trimmed.isEmpty()) {
            return findAll();
        }

        // Eğer kullanıcı sadece sayı/uzun barkod yazdıysa önce barcode araması
        boolean maybeBarcode = trimmed.length() >= 6;

        List<Product> list = new ArrayList<>();

        // 1) barcode exact / prefix araması (çok hızlı)
        if (maybeBarcode) {
            String sqlBarcode = """
                SELECT p.id, p.name, p.barcode, p.stock, p.price,
                       p.category_id, c.name AS category_name
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                WHERE p.barcode LIKE ?
                ORDER BY p.id DESC
                LIMIT 100
            """;

            try (Connection c = DatabaseConfig.getAppConnection();
                 PreparedStatement ps = c.prepareStatement(sqlBarcode)) {

                ps.setString(1, trimmed + "%");

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Product(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getString("barcode"),
                                rs.getInt("category_id"),
                                rs.getString("category_name"),
                                rs.getInt("stock"),
                                rs.getDouble("price")
                        ));
                    }
                }

                // barcode sonuç geldiyse direkt dön (UI hızlı hisseder)
                if (!list.isEmpty()) return list;

            } catch (SQLException e) {
                throw new RuntimeException("Barkod arama hatası", e);
            }
        }

        // 2) isim araması
        String sqlName = """
            SELECT p.id, p.name, p.barcode, p.stock, p.price,
                   p.category_id, c.name AS category_name
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE p.name ILIKE ?
            ORDER BY p.id DESC
            LIMIT 200
        """;

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sqlName)) {

            ps.setString(1, "%" + trimmed + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Product(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("barcode"),
                            rs.getInt("category_id"),
                            rs.getString("category_name"),
                            rs.getInt("stock"),
                            rs.getDouble("price")
                    ));
                }
            }

            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Arama hatası", e);
        }
    }

    public static void insert(Product p) {
        String sql = """
            INSERT INTO products (name, barcode, category_id, stock, price)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, p.getName());
            ps.setString(2, p.getBarcode());
            ps.setInt(3, p.getCategoryId());
            ps.setInt(4, p.getStock());
            ps.setDouble(5, p.getPrice());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Ürün eklenemedi", e);
        }
    }

    public static void updateBasics(int id, String name, double price, int categoryId) {
        String sql = """
            UPDATE products
            SET name = ?, price = ?, category_id = ?
            WHERE id = ?
        """;

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setDouble(2, price);
            ps.setInt(3, categoryId);
            ps.setInt(4, id);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Ürün güncellenemedi", e);
        }
    }

    public static void increaseStock(String barcode, int delta) {
        String sql = "UPDATE products SET stock = stock + ? WHERE barcode = ?";

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, delta);
            ps.setString(2, barcode);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Stok artırma hatası", e);
        }
    }

    public static void setStock(String barcode, int newStock) {
        String sql = "UPDATE products SET stock = ? WHERE barcode = ?";

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, newStock);
            ps.setString(2, barcode);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Stok set hatası", e);
        }
    }

    public static void decreaseStock(String barcode, int delta) {
        String sql = """
            UPDATE products
            SET stock = stock - ?
            WHERE barcode = ? AND stock >= ?
        """;

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, delta);
            ps.setString(2, barcode);
            ps.setInt(3, delta);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new RuntimeException("Stok yetersiz, düşülemedi.");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Stok düşme hatası", e);
        }
    }

    public static List<Product> getCriticalProducts() {
        String sql = """
            SELECT p.id, p.name, p.barcode, p.stock, p.price,
                   p.category_id, c.name AS category_name
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE p.stock < 10
            ORDER BY p.stock ASC
        """;

        List<Product> list = new ArrayList<>();

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("barcode"),
                        rs.getInt("category_id"),
                        rs.getString("category_name"),
                        rs.getInt("stock"),
                        rs.getDouble("price")
                ));
            }
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Kritik ürünler alınamadı", e);
        }
    }

    public static int countCriticalProducts() {
        String sql = "SELECT COUNT(*) FROM products WHERE stock < 10";

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) return rs.getInt(1);
            return 0;

        } catch (Exception e) {
            throw new RuntimeException("Kritik ürün sayısı alınamadı", e);
        }
    }

    public static int countAllProducts() {
        String sql = "SELECT COUNT(*) FROM products";

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) return rs.getInt(1);
            return 0;

        } catch (Exception e) {
            throw new RuntimeException("Toplam ürün sayısı alınamadı", e);
        }
    }

    public static void deleteById(int productId) {
        String sql = "DELETE FROM products WHERE id = ?";

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ps.executeUpdate();

        } catch (Exception e) {
            throw new RuntimeException("Ürün silinemedi", e);
        }
    }

    public static boolean hasSales(int productId) {
        String sql = """
            SELECT 1
            FROM sale_items
            WHERE product_id = ?
            LIMIT 1
        """;

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (Exception e) {
            throw new RuntimeException("Satış kontrolü yapılamadı", e);
        }
    }
}
