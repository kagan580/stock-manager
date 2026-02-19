package com.stockapp.controller;

import com.stockapp.dao.ProductDAO;
import com.stockapp.model.Product;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class StockEntryController {

    @FXML private TextField barcodeField;
    @FXML private Label statusLabel;

    // ✅ barkod cache
    private final Map<String, Product> cache = new HashMap<>();
    private volatile boolean loading = false;

//    private final PauseTransition scanDelay =
//            new PauseTransition(Duration.millis(120));

    @FXML
    public void initialize() {

        // ✅ Sadece ENTER gelirse çalışır
        barcodeField.setOnAction(e -> onScan());

        Platform.runLater(() -> {
            barcodeField.requestFocus();
            barcodeField.positionCaret(barcodeField.getText().length());
        });

        statusLabel.setText("Barkod okut.");
    }

    @FXML
    public void onScan() {
        if (loading) return;

        String barcode = (barcodeField.getText() == null) ? "" : barcodeField.getText().trim();
        if (barcode.isEmpty()) {
            statusLabel.setText("❗ Barkod boş olamaz.");
            return;
        }

        // cache varsa direkt aç
        Product cached = cache.get(barcode);
        if (cached != null) {
            openProductDialog(barcode, cached);
            afterScanReset();
            return;
        }

        loading = true;
        statusLabel.setText("⏳ Ürün aranıyor...");

        // ✅ DB araması async
        new Thread(() -> {
            Optional<Product> p;
            try {
                p = ProductDAO.findByBarcode(barcode);
            } catch (Exception e) {
                p = Optional.empty();
            }

            Optional<Product> finalP = p;
            Platform.runLater(() -> {
                // bulunduysa cache’e koy
                finalP.ifPresent(prod -> cache.put(barcode, prod));

                openProductDialog(barcode, finalP.orElse(null));
                afterScanReset();

                loading = false;
            });
        }, "stock-entry-find-thread").start();
    }

    private void afterScanReset() {
        barcodeField.clear();
        barcodeField.requestFocus();
        statusLabel.setText("Barkod okut.");
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
            if (controller.isSuccess()) {
                String msg = controller.getSuccessMessage();

                // Ürünü tekrar çekip stok bilgisi de yazalım
                ProductDAO.findByBarcode(barcode).ifPresentOrElse(p -> {
                    statusLabel.setText(msg + " | Stok: " + p.getStock());
                    cache.put(barcode, p);
                }, () -> statusLabel.setText(msg));
            } else {
                // işlem yapılmadı / iptal edildi
                statusLabel.setText("İptal edildi.");
            }

            ProductsController.refreshIfOpen();

            // ✅ Dialog sonrası ürün değişmiş olabilir -> cache’i güncelle
            ProductDAO.findByBarcode(barcode).ifPresent(p -> cache.put(barcode, p));

        } catch (Exception e) {
            throw new RuntimeException("Popup açılamadı", e);
        }
    }
}
