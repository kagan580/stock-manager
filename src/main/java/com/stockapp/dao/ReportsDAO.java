package com.stockapp.dao;

import com.stockapp.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class ReportsDAO {

    public record TopProductRow(String name, String barcode, int totalQty) {}
    public record CategorySummaryRow(String category, int totalQty, double revenue) {}
    public record DailyRevenueRow(LocalDate day, double revenue) {}

    public record SaleRow(int saleId, LocalDateTime saleDate, double totalAmount) {}
    public record ReceiptItemRow(String name, String barcode, int qty, double unitPrice, double lineTotal) {}
    public record ReceiptRow(int saleId, LocalDateTime saleDate, double totalAmount, List<ReceiptItemRow> items) {}

    public static double getRevenue(LocalDateTime from, LocalDateTime toExclusive) {
        String sql = """
            SELECT COALESCE(SUM(total_amount), 0) AS revenue
            FROM sales
            WHERE sale_date >= ? AND sale_date < ?
        """;

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setObject(1, from);
            ps.setObject(2, toExclusive);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble("revenue");
            }

        } catch (Exception e) {
            throw new RuntimeException("Ciro hesaplanamadı", e);
        }
    }

    public static List<TopProductRow> getTopProducts(LocalDateTime from, LocalDateTime toExclusive) {
        String sql = """
            SELECT p.name, p.barcode, SUM(si.quantity) AS total_qty
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            JOIN products p ON p.id = si.product_id
            WHERE s.sale_date >= ? AND s.sale_date < ?
            GROUP BY p.name, p.barcode
            ORDER BY total_qty DESC
        """;

        List<TopProductRow> list = new ArrayList<>();

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setObject(1, from);
            ps.setObject(2, toExclusive);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new TopProductRow(
                            rs.getString("name"),
                            rs.getString("barcode"),
                            rs.getInt("total_qty")
                    ));
                }
            }
            return list;

        } catch (Exception e) {
            throw new RuntimeException("Top products alınamadı", e);
        }
    }

    public static List<CategorySummaryRow> getCategorySummary(LocalDateTime from, LocalDateTime toExclusive) {
        String sql = """
            SELECT COALESCE(c.name, 'Genel') AS category_name,
                   COALESCE(SUM(si.quantity), 0) AS total_qty,
                   COALESCE(SUM(si.line_total), 0) AS revenue
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            JOIN products p ON p.id = si.product_id
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE s.sale_date >= ? AND s.sale_date < ?
            GROUP BY category_name
            ORDER BY revenue DESC
        """;

        List<CategorySummaryRow> list = new ArrayList<>();

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setObject(1, from);
            ps.setObject(2, toExclusive);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new CategorySummaryRow(
                            rs.getString("category_name"),
                            rs.getInt("total_qty"),
                            rs.getDouble("revenue")
                    ));
                }
            }
            return list;

        } catch (Exception e) {
            throw new RuntimeException("Kategori raporu alınamadı", e);
        }
    }

    public static List<DailyRevenueRow> getDailyRevenue(LocalDateTime from, LocalDateTime toExclusive) {
        String sql = """
            SELECT DATE(sale_date) AS day, COALESCE(SUM(total_amount), 0) AS revenue
            FROM sales
            WHERE sale_date >= ? AND sale_date < ?
            GROUP BY DATE(sale_date)
            ORDER BY day ASC
        """;

        List<DailyRevenueRow> list = new ArrayList<>();

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setObject(1, from);
            ps.setObject(2, toExclusive);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate day = rs.getDate("day").toLocalDate();
                    double rev = rs.getDouble("revenue");
                    list.add(new DailyRevenueRow(day, rev));
                }
            }
            return list;

        } catch (Exception e) {
            throw new RuntimeException("Günlük ciro alınamadı", e);
        }
    }

    public static List<SaleRow> getSales(LocalDateTime from, LocalDateTime toExclusive, int limit) {
        String sql = """
            SELECT id, sale_date, total_amount
            FROM sales
            WHERE sale_date >= ? AND sale_date < ?
            ORDER BY sale_date DESC
            LIMIT ?
        """;

        List<SaleRow> list = new ArrayList<>();

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setObject(1, from);
            ps.setObject(2, toExclusive);
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SaleRow(
                            rs.getInt("id"),
                            rs.getTimestamp("sale_date").toLocalDateTime(),
                            rs.getDouble("total_amount")
                    ));
                }
            }
            return list;

        } catch (Exception e) {
            throw new RuntimeException("Fiş listesi alınamadı", e);
        }
    }

    public static ReceiptRow getReceipt(int saleId) {
        try (Connection c = DatabaseConfig.getAppConnection()) {

            LocalDateTime saleDate;
            double total;

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT sale_date, total_amount FROM sales WHERE id = ?"
            )) {
                ps.setInt(1, saleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new RuntimeException("Fiş bulunamadı: " + saleId);
                    saleDate = rs.getTimestamp("sale_date").toLocalDateTime();
                    total = rs.getDouble("total_amount");
                }
            }

            List<ReceiptItemRow> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                SELECT p.name, p.barcode, si.quantity, si.unit_price, si.line_total
                FROM sale_items si
                JOIN products p ON p.id = si.product_id
                WHERE si.sale_id = ?
                ORDER BY si.id ASC
            """)) {
                ps.setInt(1, saleId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new ReceiptItemRow(
                                rs.getString("name"),
                                rs.getString("barcode"),
                                rs.getInt("quantity"),
                                rs.getDouble("unit_price"),
                                rs.getDouble("line_total")
                        ));
                    }
                }
            }

            return new ReceiptRow(saleId, saleDate, total, items);

        } catch (Exception e) {
            throw new RuntimeException("Fiş detayı alınamadı", e);
        }
    }

    public static int countReceiptsByDay(LocalDate day) {
        String sql = """
            SELECT COUNT(*)
            FROM sales
            WHERE sale_date >= ? AND sale_date < ?
        """;

        LocalDateTime from = day.atStartOfDay();
        LocalDateTime toExclusive = day.plusDays(1).atStartOfDay();

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setObject(1, from);
            ps.setObject(2, toExclusive);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }

        } catch (Exception e) {
            throw new RuntimeException("Fiş sayısı alınamadı", e);
        }
    }

    public static List<SaleRow> getSalesByDay(LocalDate day, int limit) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime toExclusive = day.plusDays(1).atStartOfDay();
        return getSales(from, toExclusive, limit);
    }

    public static Map<LocalDate, Integer> getReceiptCountsByDay(LocalDateTime from, LocalDateTime toExclusive) {
        String sql = """
            SELECT DATE(sale_date) AS day, COUNT(*) AS cnt
            FROM sales
            WHERE sale_date >= ? AND sale_date < ?
            GROUP BY DATE(sale_date)
        """;

        Map<LocalDate, Integer> map = new HashMap<>();

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setObject(1, from);
            ps.setObject(2, toExclusive);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getDate("day").toLocalDate(), rs.getInt("cnt"));
                }
            }

            return map;

        } catch (Exception e) {
            throw new RuntimeException("Günlük fiş sayıları alınamadı", e);
        }
    }

    public static int deleteSalesOlderThanYears(int years) {
        String sql = "DELETE FROM sales WHERE sale_date < NOW() - (? || ' years')::interval";
        try (var c = DatabaseConfig.getAppConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setInt(1, years);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Otomatik temizlik başarısız", e);
        }
    }
}
