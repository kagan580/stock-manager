package com.stockapp.dao;

import com.stockapp.config.DatabaseConfig;
import com.stockapp.model.CartItem;

import java.math.BigDecimal;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalesDAO {

    private record ProductInfo(int id, BigDecimal price) {}

    /**
     * Sepeti tek satış (fiş) olarak kaydeder:
     * 1) sales kaydı oluşturur (total)
     * 2) her item için stok düşer (atomik)
     * 3) sale_items'a satırları yazar
     * Hepsi tek transaction.
     *
     * @return saleId (fiş no)
     */
    public static int createSale(List<CartItem> cart) {
        if (cart == null || cart.isEmpty()) {
            throw new RuntimeException("Sepet boş.");
        }

        try (Connection c = DatabaseConfig.getAppConnection()) {
            c.setAutoCommit(false);

            try {
                // ✅ toplam
                BigDecimal total = BigDecimal.ZERO;
                for (CartItem item : cart) {
                    total = total.add(BigDecimal.valueOf(item.getLineTotal()));
                }

                // ✅ 1) sales insert -> id
                int saleId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO sales(total_amount) VALUES (?) RETURNING id"
                )) {
                    ps.setBigDecimal(1, total);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        saleId = rs.getInt(1);
                    }
                }

                // ✅ 2) ürün bilgilerini tek seferde çek (N+1 SELECT kalktı)
                Map<String, ProductInfo> infoByBarcode = fetchProductInfoByBarcodes(c, cart);

                // ✅ 3) statement'ları bir kere hazırla
                try (PreparedStatement psUpdateStock = c.prepareStatement(
                        "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?"
                );
                     PreparedStatement psInsertItem = c.prepareStatement(
                             "INSERT INTO sale_items(sale_id, product_id, quantity, unit_price, line_total) VALUES(?,?,?,?,?)"
                     )) {

                    for (CartItem item : cart) {
                        int qty = item.getQty();
                        if (qty <= 0) qty = 1;

                        ProductInfo pi = infoByBarcode.get(item.getBarcode());
                        if (pi == null) {
                            throw new RuntimeException("Ürün bulunamadı: " + item.getBarcode());
                        }

                        // ✅ stok düş (atomik)
                        psUpdateStock.setInt(1, qty);
                        psUpdateStock.setInt(2, pi.id());
                        psUpdateStock.setInt(3, qty);

                        int updated = psUpdateStock.executeUpdate();
                        if (updated == 0) {
                            throw new RuntimeException("Stok yetersiz: " + item.getName());
                        }

                        BigDecimal unitPrice = pi.price() == null ? BigDecimal.ZERO : pi.price();
                        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

                        // ✅ sale_items insert
                        psInsertItem.setInt(1, saleId);
                        psInsertItem.setInt(2, pi.id());
                        psInsertItem.setInt(3, qty);
                        psInsertItem.setBigDecimal(4, unitPrice);
                        psInsertItem.setBigDecimal(5, lineTotal);
                        psInsertItem.executeUpdate();
                    }
                }

                c.commit();
                return saleId;

            } catch (Exception e) {
                try { c.rollback(); } catch (Exception ignored) {}
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                try { c.setAutoCommit(true); } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static Map<String, ProductInfo> fetchProductInfoByBarcodes(Connection c, List<CartItem> cart) throws SQLException {
        Map<String, ProductInfo> map = new HashMap<>();

        // IN (?, ?, ?)
        StringBuilder sb = new StringBuilder("SELECT id, barcode, price FROM products WHERE barcode IN (");
        for (int i = 0; i < cart.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        sb.append(")");

        try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
            for (int i = 0; i < cart.size(); i++) {
                ps.setString(i + 1, cart.get(i).getBarcode());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String barcode = rs.getString("barcode");
                    BigDecimal price = rs.getBigDecimal("price");
                    map.put(barcode, new ProductInfo(rs.getInt("id"), price));
                }
            }
        }

        return map;
    }

    public static int countMonthlySalesItems() {
        String sql = """
            SELECT COALESCE(SUM(si.quantity), 0)
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            WHERE date_trunc('month', s.sale_date) = date_trunc('month', CURRENT_DATE)
        """;

        try (Connection c = DatabaseConfig.getAppConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) return rs.getInt(1);
            return 0;

        } catch (Exception e) {
            throw new RuntimeException("Aylık satış adedi alınamadı", e);
        }
    }
}
