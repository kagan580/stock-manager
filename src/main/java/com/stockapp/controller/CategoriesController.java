package com.stockapp.controller;

import com.stockapp.dao.CategoryDAO;
import com.stockapp.model.Category;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;

public class CategoriesController {

    @FXML private TextField nameField;
    @FXML private TableView<Category> table;
    @FXML private TableColumn<Category, String> nameCol;
    @FXML private Label statusLabel;

    // Eğer FXML’de butonların fx:id’si varsa bağla (yoksa null kalır, sıkıntı olmaz)
    @FXML private Button addBtn;
    @FXML private Button removeBtn;


    private volatile boolean busy = false;
    private volatile boolean loading = false;


//    private volatile boolean loading = false;

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        refreshAsync();

        // Enter ile ekleme
        nameField.setOnAction(e -> add());
    }

    @FXML
    public void add() {
        if (loading) return;

        String name = (nameField.getText() == null) ? "" : nameField.getText().trim();

        if (name.isEmpty()) { statusLabel.setText("❗ Kategori adı boş olamaz."); return; }
        if (name.length() > 100) { statusLabel.setText("❗ Kategori adı çok uzun (max 100)."); return; }

        setBusy(true);
        statusLabel.setText("⏳ Ekleniyor...");

        new Thread(() -> {
            try {
                CategoryDAO.insert(name);

                Platform.runLater(() -> {
                    statusLabel.setText("✅ Eklendi: " + name);
                    showSuccess("✅ Kategori eklendi: " + name);
                    nameField.clear();
                    nameField.requestFocus();

                    invalidateCategoryCacheSafely();

                    setBusy(false);      // ✅ önce kilidi kaldır
                    refreshAsync();      // ✅ sonra yenile
                });



            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("❗ " + e.getMessage());
                    setBusy(false);
                });
            }
        }, "category-insert-thread").start();
    }

    @FXML
    public void removeSelected() {
        if (loading) return;

        Category selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { statusLabel.setText("❗ Silmek için bir kategori seç."); return; }

        if ("Genel".equalsIgnoreCase(selected.getName())) {
            statusLabel.setText("❗ 'Genel' kategorisi silinemez.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Kategori Sil");
        alert.setHeaderText("Kategori silinecek ve bu kategoriye bağlı ürünler 'Genel'e taşınacak.");
        alert.setContentText("Silinecek kategori: " + selected.getName());

        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            statusLabel.setText("İptal edildi.");
            return;
        }

        setBusy(true);
        statusLabel.setText("⏳ Siliniyor ve ürünler taşınıyor...");

        new Thread(() -> {
            try {
                int genelId = CategoryDAO.findIdByName("Genel");
                if (genelId <= 0) throw new RuntimeException("'Genel' kategorisi bulunamadı.");

                // 1) ürünleri Genel'e taşı
                CategoryDAO.moveProductsToCategory(selected.getId(), genelId);

                // 2) kategoriyi sil
                CategoryDAO.deleteById(selected.getId());

                Platform.runLater(() -> {
                    statusLabel.setText("🗑️ Silindi: " + selected.getName() + " (Ürünler Genel'e taşındı)");
                    showSuccess("🗑️ Kategori silindi: " + selected.getName() +
                            "\nBağlı ürünler 'Genel'e taşındı.");

                    invalidateCategoryCacheSafely();

                    setBusy(false);
                    refreshAsync();
                    ProductsController.refreshIfOpen();
                });



            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("❗ Silme başarısız: " + e.getMessage());
                    setBusy(false);
                });
            }
        }, "category-delete-thread").start();
    }

    private void refreshAsync() {
        if (loading) return;

        loading = true;
        table.setDisable(true);

        new Thread(() -> {
            List<Category> list;
            try {
                list = CategoryDAO.findAll();
            } catch (Exception e) {
                list = List.of();
            }

            List<Category> finalList = list;
            Platform.runLater(() -> {
                table.setItems(FXCollections.observableArrayList(finalList));
                table.setDisable(busy);  // ✅ busy true ise tablo kilitli kalsın
                loading = false;
            });
        }, "category-refresh-thread").start();
    }


    private void setBusy(boolean b) {
        busy = b;

        if (addBtn != null) addBtn.setDisable(b);
        if (removeBtn != null) removeBtn.setDisable(b);

        nameField.setDisable(b);
        table.setDisable(b); // istersen sadece butonları kilitle, tabloyu kilitlemeyebilirsin
    }


    /**
     * ✅ 2 seçenek:
     * A) ProductDialogController.CACHED_CATEGORIES public ise direkt null'la
     * B) Daha temiz: ProductDialogController içinde public static void invalidateCategoryCache() yap
     */
    private void invalidateCategoryCacheSafely() {
        try {
            // Seçenek A (Eğer erişilebiliyorsa)
            // ProductDialogController.CACHED_CATEGORIES = null;

            // Seçenek B (Önerilen - aşağıda nasıl yapılacağını yazdım)
            ProductDialogController.invalidateCategoryCache();

        } catch (Exception ignored) { }
    }

    private void showSuccess(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Başarılı");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        a.showAndWait();
    }
}
