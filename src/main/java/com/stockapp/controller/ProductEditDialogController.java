package com.stockapp.controller;

import com.stockapp.dao.CategoryDAO;
import com.stockapp.model.Category;
import com.stockapp.model.Product;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ProductEditDialogController {

    @FXML private TextField nameField;
    @FXML private TextField priceField;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private Label msgLabel;

    private Stage stage;
    private Product product;

    private boolean saved = false;
    private String newName;
    private double newPrice;
    private int newCategoryId;
    private String newCategoryName; // ✅ eklendi

    // ✅ cache
    private static volatile List<Category> CACHED_CATEGORIES = null;

    public void setStage(Stage stage) { this.stage = stage; }

    public void setup(Product product) {
        this.product = product;
        msgLabel.setText("");

        nameField.setText(product.getName());
        priceField.setText(String.valueOf(product.getPrice()));

        categoryBox.setDisable(true);
        msgLabel.setText("⏳ Kategoriler yükleniyor...");

        loadCategoriesAsync();
    }

    // ✅ gerçekten async + cache set
    private void loadCategoriesAsync() {
        if (CACHED_CATEGORIES != null) {
            applyCategories(CACHED_CATEGORIES);
            return;
        }

        new Thread(() -> {
            List<Category> categories;
            try {
                categories = CategoryDAO.findAll();
            } catch (Exception e) {
                categories = new ArrayList<>();
            }

            List<Category> finalCats = categories;
            Platform.runLater(() -> {
                CACHED_CATEGORIES = finalCats;  // ✅ cache’e yaz
                applyCategories(finalCats);
            });
        }, "edit-categories-thread").start();
    }

    private void applyCategories(List<Category> categories) {
        categoryBox.getItems().setAll(categories);

        // mevcut kategoriyi seç
        Category current = categories.stream()
                .filter(c -> c.getId() == product.getCategoryId())
                .findFirst()
                .orElse(categories.isEmpty() ? null : categories.get(0));

        if (current != null) categoryBox.getSelectionModel().select(current);

        categoryBox.setDisable(false);
        msgLabel.setText(categories.isEmpty() ? "⚠️ Kategori bulunamadı." : "");
    }

    @FXML
    private void onSave() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) { msgLabel.setText("❗ Ürün adı boş olamaz."); return; }

        double price = parsePrice(priceField.getText());
        if (price < 0) return;

        Category selected = categoryBox.getSelectionModel().getSelectedItem();
        if (selected == null) { msgLabel.setText("❗ Kategori seç."); return; }

        this.newName = name;
        this.newPrice = price;
        this.newCategoryId = selected.getId();
        this.newCategoryName = selected.getName();
        this.saved = true;

        // ✅ başarı popup
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Başarılı");
        a.setHeaderText(null);
        a.setContentText("✅ Ürün güncellendi.");
        a.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        a.showAndWait();

        stage.close();
    }

    @FXML
    private void onCancel() {
        saved = false;
        stage.close();
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

    // getters
    public boolean isSaved() { return saved; }
    public String getNewName() { return newName; }
    public double getNewPrice() { return newPrice; }
    public int getNewCategoryId() { return newCategoryId; }
    public String getNewCategoryName() { return newCategoryName == null ? "-" : newCategoryName; }

    public static class Result {
        public final String name;
        public final double price;
        public final int categoryId;
        public final String categoryName;

        public Result(String name, double price, int categoryId, String categoryName) {
            this.name = name;
            this.price = price;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }
}
