package com.stockapp.controller;

import com.stockapp.dao.ProductDAO;
import com.stockapp.dao.SalesDAO;
import com.stockapp.model.CartItem;
import com.stockapp.model.Product;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SalesController {

    @FXML private TextField barcodeField;
    @FXML private TextField qtyField;

    @FXML private Label statusLabel;
    @FXML private Label grandTotalLabel;

    @FXML private TableView<CartItem> table;
    @FXML private TableColumn<CartItem, String> nameCol, barcodeCol;
    @FXML private TableColumn<CartItem, Integer> qtyCol;
    @FXML private TableColumn<CartItem, Number> unitPriceCol, lineTotalCol;

    @FXML private Button finishSaleBtn;
    @FXML private Button clearCartBtn;
    @FXML private Button incBtn;
    @FXML private Button decBtn;
    @FXML private Button deleteBtn;
    @FXML private Button setQtyBtn;

    private final ObservableList<CartItem> cart = FXCollections.observableArrayList();
    private final Map<String, CartItem> byBarcode = new HashMap<>();

    // ✅ Barkod -> Product cache
    private final Map<String, Product> productCache = new HashMap<>();

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(c -> c.getValue().nameProperty());
        barcodeCol.setCellValueFactory(c -> c.getValue().barcodeProperty());
        qtyCol.setCellValueFactory(c -> c.getValue().qtyProperty().asObject());
        unitPriceCol.setCellValueFactory(c -> c.getValue().unitPriceProperty());
        lineTotalCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getLineTotal()));

        table.setItems(cart);

        // ✅ Barkod okutunca direkt ekle (Enter gönderiyorsa)
        barcodeField.setOnAction(e -> onScanAdd());

        // ✅ Adet alanında Enter'a basılırsa da ekleme yap (hızlı)
        if (qtyField != null) qtyField.setOnAction(e -> onScanAdd());

        // Delete tuşu ile sil
        table.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DELETE -> deleteSelected();
            }
        });

        table.setOnMouseClicked(e -> barcodeField.requestFocus());

        Platform.runLater(() -> barcodeField.requestFocus());

        updateGrandTotal();
        statusLabel.setText("Barkod okut.");
    }

    // =========================
    // ✅ BARKOD OKUT -> EKLE
    // =========================

    private void onScanAdd() {
        String barcode = barcodeField.getText() == null ? "" : barcodeField.getText().trim();
        if (barcode.isEmpty()) {
            statusLabel.setText("❗ Barkod boş olamaz.");
            barcodeField.requestFocus();
            return;
        }

        int qty = parseQtyDefaultOneOrFromField();
        if (qty < 0) {
            // sayı hatalı
            qtyField.requestFocus();
            return;
        }

        Product pr = resolveProduct(barcode);
        if (pr == null) {
            clearInputs();
            return;
        }

        addToCart(pr, qty);

        statusLabel.setText("✅ Sepete eklendi: " + pr.getName() + " (+" + qty + ")");
        clearInputs();
    }

    private Product resolveProduct(String barcode) {
        Product pr = productCache.get(barcode);
        if (pr != null) return pr;

        Optional<Product> p = ProductDAO.findByBarcode(barcode);

        if (p.isEmpty()) {
            statusLabel.setText("⚠️ Ürün yok. Tanıtma ekranı açıldı...");
            beep();

            openProductDialog(barcode, null);

            p = ProductDAO.findByBarcode(barcode);
            if (p.isEmpty()) {
                statusLabel.setText("❗ Ürün eklenmedi (iptal edilmiş olabilir).");
                return null;
            }
        }

        pr = p.get();
        productCache.put(barcode, pr);
        return pr;
    }

    private void addToCart(Product pr, int qty) {
        if (qty <= 0) qty = 1;

        CartItem existing = byBarcode.get(pr.getBarcode());
        if (existing != null) {
            existing.incQty(qty); // ✅ ÜSTÜNE EKLE
            table.refresh();
        } else {
            CartItem newItem = new CartItem(
                    pr.getId(),
                    pr.getName(),
                    pr.getBarcode(),
                    pr.getPrice(),
                    qty
            );
            cart.add(newItem);
            byBarcode.put(pr.getBarcode(), newItem);
        }

        updateGrandTotal();
    }

    private int parseQtyDefaultOneOrFromField() {
        String t = (qtyField == null) ? "" : qtyField.getText();
        t = (t == null) ? "" : t.trim();
        if (t.isEmpty()) return 1;

        try {
            int v = Integer.parseInt(t);
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (Exception e) {
            statusLabel.setText("❗ Adet sayı olmalı (örn 1, 5, 300).");
            beep();
            return -1;
        }
    }

    private void clearInputs() {
        barcodeField.clear();
        if (qtyField != null) qtyField.clear();
        barcodeField.requestFocus();
    }

    // =========================
    // ✅ SEPET DÜZENLEME
    // =========================

    @FXML
    public void incSelected() {
        CartItem selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        selected.incQty(1);
        table.refresh();
        updateGrandTotal();
        barcodeField.requestFocus();
    }

    @FXML
    public void decSelected() {
        CartItem selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        int newQty = selected.getQty() - 1;
        if (newQty <= 0) {
            cart.remove(selected);
            byBarcode.remove(selected.getBarcode());
            statusLabel.setText("🗑️ Silindi: " + selected.getName());
        } else {
            selected.incQty(-1);
        }

        table.refresh();
        updateGrandTotal();
        barcodeField.requestFocus();
    }

    @FXML
    public void deleteSelected() {
        CartItem selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("❗ Silmek için satır seç.");
            return;
        }

        // küçük onay
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Sepetten Sil");
        confirm.setHeaderText("Seçili ürünü sepetten silmek istiyor musun?");
        confirm.setContentText(selected.getName() + " | Adet: " + selected.getQty());

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        cart.remove(selected);
        byBarcode.remove(selected.getBarcode());
        table.refresh();
        updateGrandTotal();
        statusLabel.setText("🗑️ Sepetten silindi: " + selected.getName());
        barcodeField.requestFocus();
    }

    // ✅ SET adet (isteyen için ayrı dialog)
    @FXML
    public void setQtyDialog() {
        CartItem selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("❗ Adet ayarlamak için satır seç.");
            return;
        }

        TextInputDialog d = new TextInputDialog(String.valueOf(selected.getQty()));
        d.setTitle("Adet Ayarla");
        d.setHeaderText("Adet SET edilecek (üstüne eklemez)");
        d.setContentText("Yeni adet (0 yazarsan silinir):");

        Optional<String> res = d.showAndWait();
        if (res.isEmpty()) return;

        String t = res.get().trim();
        if (t.isEmpty()) return;

        int v;
        try {
            v = Integer.parseInt(t);
            if (v < 0) throw new NumberFormatException();
        } catch (Exception e) {
            statusLabel.setText("❗ Geçerli sayı gir (0,1,2...).");
            beep();
            return;
        }

        if (v == 0) {
            cart.remove(selected);
            byBarcode.remove(selected.getBarcode());
            statusLabel.setText("🗑️ Silindi: " + selected.getName());
        } else {
            // CartItem’da setQty yoksa eklemen gerekebilir.
            // Ama çoğu CartItem’da qtyProperty().set(...) yapılabilir:
            selected.qtyProperty().set(v);
            statusLabel.setText("✅ Güncellendi: " + selected.getName() + " (= " + v + ")");
        }

        table.refresh();
        updateGrandTotal();
        barcodeField.requestFocus();
    }

    @FXML
    public void clearCart() {
        cart.clear();
        byBarcode.clear();
        updateGrandTotal();
        statusLabel.setText("Sepet temizlendi.");
        clearInputs();
    }

    // =========================
    // ✅ SATIŞI BİTİR (ASYNC + POPUP)
    // =========================

    @FXML
    public void finishSale() {
        if (cart.isEmpty()) {
            statusLabel.setText("❗ Sepet boş.");
            return;
        }

        setCheckoutBusy(true);
        statusLabel.setText("⏳ Satış yapılıyor...");

        var snapshot = FXCollections.observableArrayList(cart);

        new Thread(() -> {
            try {
                int saleId = SalesDAO.createSale(snapshot);
                ProductsController.refreshIfOpen();

                Platform.runLater(() -> {
                    String totalTxt = grandTotalLabel.getText();

                    showInfo(
                            "Satış Tamamlandı",
                            "✅ Satış başarıyla gerçekleşmiştir",
                            "Fiş No: " + saleId + "\nToplam: " + totalTxt
                    );

                    statusLabel.setText("✅ Satış başarıyla gerçekleşti. Fiş No: " + saleId);
                    clearCart();
                    setCheckoutBusy(false);
                });

            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("❗ Satış başarısız: " + ex.getMessage());
                    beep();
                    setCheckoutBusy(false);
                });
            }
        }, "sale-thread").start();
    }

    private void setCheckoutBusy(boolean busy) {
        if (finishSaleBtn != null) finishSaleBtn.setDisable(busy);
        if (clearCartBtn != null) clearCartBtn.setDisable(busy);
        if (incBtn != null) incBtn.setDisable(busy);
        if (decBtn != null) decBtn.setDisable(busy);
        if (deleteBtn != null) deleteBtn.setDisable(busy);
        if (setQtyBtn != null) setQtyBtn.setDisable(busy);

        barcodeField.setDisable(busy);
        if (qtyField != null) qtyField.setDisable(busy);
        table.setDisable(busy);
    }

    private void updateGrandTotal() {
        double total = 0.0;
        for (CartItem item : cart) total += item.getLineTotal();
        grandTotalLabel.setText(String.format("%.2f", total));
    }

    private void beep() {
        try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}
    }

    private void openProductDialog(String barcode, Product existing) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dialogs/product_dialog.fxml"));
            Scene scene = new Scene(loader.load());

            ProductDialogController controller = loader.getController();
            controller.setup(barcode, existing);

            Stage dialog = new Stage();
            dialog.setTitle(existing == null ? "Yeni Ürün" : "Stok Güncelle");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(scene);
            controller.setStage(dialog);

            dialog.showAndWait();

            // Dialog sonrası cache güncelle
            ProductDAO.findByBarcode(barcode).ifPresent(p -> productCache.put(barcode, p));

        } catch (Exception e) {
            throw new RuntimeException("Popup açılamadı", e);
        }
    }

    private void showInfo(String title, String header, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(header);
        a.setContentText(content);
        a.initModality(Modality.APPLICATION_MODAL);
        a.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        a.showAndWait();
    }
}
