package com.stockapp.controller;

import com.stockapp.dao.CategoryDAO;
import com.stockapp.dao.ProductDAO;
import com.stockapp.model.Category;
import com.stockapp.model.Product;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ProductDialogController {

    @FXML private Label titleLabel;
    @FXML private Label msgLabel;

    @FXML private TextField barcodeField;
    @FXML private TextField nameField;
    @FXML private TextField priceField;
    @FXML private TextField qtyField;

    @FXML private ComboBox<Category> categoryBox;

    @FXML private Button primaryBtn;    // Kaydet
    @FXML private Button secondaryBtn;  // +Ekle
    @FXML private Button decreaseBtn;   // -Düş

    private Stage stage;
    private Product existing;
    private boolean success = false;
    private String successMessage = null;

    public boolean isSuccess() { return success; }
    public String getSuccessMessage() { return successMessage; }

    // ✅ Kategori cache (uygulama boyunca 1 kere DB)
    private static volatile List<Category> CACHED_CATEGORIES = null;

    public void setStage(Stage stage) { this.stage = stage; }

    public void setup(String barcode, Product existing) {
        this.existing = existing;
        msgLabel.setText("");

        barcodeField.setText(barcode);

        // ✅ kategorileri async yükle (UI donmasın)
        loadCategoriesAsync(() -> {
            // kategori geldikten sonra existing varsa seç
            if (existing != null) selectCategory(existing.getCategoryId());
        });

        if (existing == null) {
            titleLabel.setText("Yeni Ürün Oluştur");

            nameField.clear();
            priceField.clear();

            nameField.setDisable(false);
            priceField.setDisable(false);
            categoryBox.setDisable(false);

            primaryBtn.setText("Kaydet");
            primaryBtn.setVisible(true);
            primaryBtn.setManaged(true);

            secondaryBtn.setVisible(false);
            secondaryBtn.setManaged(false);

            decreaseBtn.setVisible(false);
            decreaseBtn.setManaged(false);

        } else {
            titleLabel.setText("Ürün Bulundu - Stok Güncelle");

            nameField.setText(existing.getName());
            priceField.setText(String.valueOf(existing.getPrice()));

            nameField.setDisable(true);
            priceField.setDisable(true);

            // kategori seçimini göster ama kilitle
            categoryBox.setDisable(true);

            primaryBtn.setVisible(false);
            primaryBtn.setManaged(false);

            secondaryBtn.setText("+ Ekle");
            secondaryBtn.setVisible(true);
            secondaryBtn.setManaged(true);

            decreaseBtn.setText("- Düş");
            decreaseBtn.setVisible(true);
            decreaseBtn.setManaged(true);
        }

        qtyField.clear();
        qtyField.setPromptText("Boşsa 1");
        qtyField.requestFocus();
    }

    private void loadCategoriesAsync(Runnable afterLoad) {
        msgLabel.setText("⏳ Kategoriler yükleniyor...");
        setButtonsBusy(true);

        // cache varsa direkt bas
        if (CACHED_CATEGORIES != null) {
            categoryBox.getItems().setAll(CACHED_CATEGORIES);
            if (!CACHED_CATEGORIES.isEmpty()) categoryBox.getSelectionModel().selectFirst();
            msgLabel.setText("");
            setButtonsBusy(false);
            if (afterLoad != null) afterLoad.run();
            return;
        }

        new Thread(() -> {
            List<Category> categories;
            try {
                categories = CategoryDAO.findAll();
                if (categories == null) categories = new ArrayList<>();
            } catch (Exception e) {
                categories = new ArrayList<>();
            }

            List<Category> finalCategories = categories;
            Platform.runLater(() -> {
                CACHED_CATEGORIES = finalCategories; // ✅ cache’e koy
                categoryBox.getItems().setAll(finalCategories);
                if (!finalCategories.isEmpty()) categoryBox.getSelectionModel().selectFirst();

                msgLabel.setText(finalCategories.isEmpty() ? "⚠️ Kategori bulunamadı." : "");
                setButtonsBusy(false);

                if (afterLoad != null) afterLoad.run();
            });
        }, "categories-thread").start();
    }

    private void selectCategory(int categoryId) {
        if (categoryId <= 0) return;
        for (Category c : categoryBox.getItems()) {
            if (c.getId() == categoryId) {
                categoryBox.getSelectionModel().select(c);
                return;
            }
        }
    }

    @FXML
    private void onPrimary() {
        if (existing != null) return;

        String barcode = barcodeField.getText().trim();
        if (barcode.isEmpty()) { msgLabel.setText("❗ Barkod boş olamaz."); return; }

        String name = nameField.getText().trim();
        if (name.isEmpty()) { msgLabel.setText("❗ Ürün adı zorunlu."); return; }

        double price = parsePrice(priceField.getText());
        if (price < 0) return;

        int initialStock = parseQtyDefaultOne(qtyField.getText());
        if (initialStock < 0) return;

        Category selected = categoryBox.getSelectionModel().getSelectedItem();
        if (selected == null) { msgLabel.setText("❗ Kategori seçmelisin."); return; }

        int categoryId = selected.getId();

        setButtonsBusy(true);
        msgLabel.setText("⏳ Kaydediliyor...");

        Product newP = new Product(name, barcode, categoryId, initialStock, price);

        new Thread(() -> {
            try {
                ProductDAO.insert(newP);

                Platform.runLater(() -> {
                    success = true;
                    successMessage = "✅ Ürün eklendi: " + newP.getName();

                    showInfo(successMessage);

                    // ürünler ekranı açıksa listeyi güncelle
                    ProductsController.refreshIfOpen();

                    stage.close();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    msgLabel.setText("❗ Kaydedilemedi: " + e.getMessage());
                    setButtonsBusy(false);
                });
            }
        }, "product-insert-thread").start();
    }

    @FXML
    private void onSecondary() {
        if (existing == null) return;

        String barcode = barcodeField.getText().trim();
        int delta = parseQtyDefaultOne(qtyField.getText());
        if (delta < 0) return;

        setButtonsBusy(true);
        msgLabel.setText("⏳ Güncelleniyor...");

        new Thread(() -> {
            try {
                ProductDAO.increaseStock(barcode, delta);

                Platform.runLater(() -> {
                    success = true;
                    successMessage = "✅ Stok arttı (+" + delta + ")\nÜrün: " + existing.getName();

                    showInfo(successMessage);
                    ProductsController.refreshIfOpen();
                    stage.close();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    msgLabel.setText("❗ Güncellenemedi: " + e.getMessage());
                    setButtonsBusy(false);
                });
            }
        }, "stock-inc-thread").start();
    }

    @FXML
    private void onDecrease() {
        if (existing == null) return;

        String barcode = barcodeField.getText().trim();
        int delta = parseQtyDefaultOne(qtyField.getText());
        if (delta < 0) return;

        setButtonsBusy(true);
        msgLabel.setText("⏳ Güncelleniyor...");

        new Thread(() -> {
            try {
                ProductDAO.decreaseStock(barcode, delta);

                Platform.runLater(() -> {
                    success = true;
                    successMessage = "✅ Stok düştü (-" + delta + ")\nÜrün: " + existing.getName();

                    showInfo(successMessage);
                    ProductsController.refreshIfOpen();
                    stage.close();
                });

            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    msgLabel.setText("❗ Stok yetersiz, düşülemedi.");
                    setButtonsBusy(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    msgLabel.setText("❗ Güncellenemedi: " + e.getMessage());
                    setButtonsBusy(false);
                });
            }
        }, "stock-dec-thread").start();
    }


    @FXML
    private void onCancel() {
        stage.close();
    }

    private void setButtonsBusy(boolean busy) {
        if (primaryBtn != null) primaryBtn.setDisable(busy);
        if (secondaryBtn != null) secondaryBtn.setDisable(busy);
        if (decreaseBtn != null) decreaseBtn.setDisable(busy);

        barcodeField.setDisable(busy);
        nameField.setDisable(busy);
        priceField.setDisable(busy);
        qtyField.setDisable(busy);
        categoryBox.setDisable(busy || existing != null); // existing modda zaten kilitli
    }

    private int parseQtyDefaultOne(String t) {
        t = (t == null) ? "" : t.trim();
        if (t.isEmpty()) return 1;

        try {
            int v = Integer.parseInt(t);
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (Exception e) {
            msgLabel.setText("❗ Miktar sayı olmalı (örn 1, 5, 20).");
            return -1;
        }
    }

    private double parsePrice(String t) {
        t = (t == null) ? "" : t.trim().replace(",", ".");
        if (t.isEmpty()) return 0.0;

        try {
            double v = Double.parseDouble(t);
            if (v < 0) throw new NumberFormatException();
            return v;
        } catch (Exception e) {
            msgLabel.setText("❗ Fiyat sayı olmalı (örn 49.90).");
            return -1;
        }
    }

    public static void invalidateCategoryCache() {
        CACHED_CATEGORIES = null;
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Başarılı");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        a.showAndWait();
    }
}
