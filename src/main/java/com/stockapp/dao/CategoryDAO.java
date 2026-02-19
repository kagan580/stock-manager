package com.stockapp.dao;

import com.stockapp.config.DatabaseConfig;
import com.stockapp.model.Category;

import java.sql.ResultSet;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    public static List<Category> findAll() {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT id, name FROM categories ORDER BY name ASC";

        try (Connection c = DatabaseConfig.getAppConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                list.add(new Category(rs.getInt("id"), rs.getString("name")));
            }
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Kategoriler alınamadı", e);
        }
    }

    public static void insert(String name) {
        String sql = "INSERT INTO categories(name) VALUES (?)";
        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Kategori eklenemedi (aynı isim olabilir)", e);
        }
    }

    public static void deleteById(int id) {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Kategori silinemedi (ürün bağlı olabilir)", e);
        }
    }

    public static void moveProductsToCategory(int fromCategoryId, int toCategoryId) {
        String sql = "UPDATE products SET category_id = ? WHERE category_id = ?";
        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, toCategoryId);
            ps.setInt(2, fromCategoryId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Ürünler başka kategoriye taşınamadı", e);
        }
    }

    public static int findIdByName(String name) {
        String sql = "SELECT id FROM categories WHERE LOWER(name) = LOWER(?)";
        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
            return -1;

        } catch (SQLException e) {
            throw new RuntimeException("Kategori bulunamadı: " + name, e);
        }
    }


}
