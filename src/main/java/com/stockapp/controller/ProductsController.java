package com.stockapp.controller;

import com.stockapp.dao.ProductDAO;
import com.stockapp.model.Product;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductsController {

    @FXML private TextField searchField;
    @FXML private Label statusLabel;

    @FXML private TableView<Product> table;
    @FXML private TableColumn<Product, String> nameCol, barcodeCol, categoryCol;
    @FXML private TableColumn<Product, Number> stockCol, priceCol;

    // ✅ Cache
    private final ObservableList<Product> masterList = FXCollections.observableArrayList();
    private final ObservableList<Product> filteredList = FXCollections.observableArrayList();
    private static ProductsController INSTANCE;  // ✅ ekle

    // ✅ Debounce
    private PauseTransition searchDebounce;

    @FXML
    public void initialize() {
        INSTANCE = this;
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        barcodeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBarcode()));
        categoryCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCategoryName() == null ? "-" : c.getValue().getCategoryName()
        ));
        stockCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getStock()));
        priceCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getPrice()));

        setupRowColors();

        // ✅ Table ilk başta filteredList’e bağlı kalsın
        table.setItems(filteredList);

        // ✅ ürünleri arka planda yükle (UI donmasın)
        loadAllProductsAsync();

        // ✅ debounce (10k listede bile rahat)
        searchDebounce = new PauseTransition(Duration.millis(250));

        searchField.textProperty().addListener((obs, oldV, newV) -> {
            searchDebounce.stop();
            searchDebounce.setOnFinished(e -> applyLocalFilter());
            searchDebounce.playFromStart();
        });

        // Enter ile anında filtre
        searchField.setOnAction(e -> {
            searchDebounce.stop();
            applyLocalFilter();
        });
    }

    private void loadAllProductsAsync() {
        statusLabel.setText("⏳ Ürünler yükleniyor...");

        new Thread(() -> {
            List<Product> all;
            try {
                all = ProductDAO.findAll(); // DB'ye 1 kere
            } catch (Exception e) {
                all = new ArrayList<>();
            }

            List<Product> finalAll = all;
            Platform.runLater(() -> {
                masterList.setAll(finalAll);
                filteredList.setAll(finalAll);
                statusLabel.setText("✅ Ürünler yüklendi: " + finalAll.size());
            });
        }, "products-load-thread").start();
    }

    private void setupRowColors() {
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setStyle("");
                    return;
                }

                if (item.getStock() < 10) {
                    if (isSelected()) {
                        setStyle("-fx-background-color: rgba(255, 80, 80, 0.35);");
                    } else {
                        setStyle("-fx-background-color: rgba(255, 80, 80, 0.18);");
                    }
                } else {
                    setStyle("");
                }
            }
        });
    }

    // ✅ Artık refresh DB’den çekmiyor: cache’i yeniden kuruyor
    @FXML
    public void refresh() {
        // Büyük listede DB’ye tekrar gitmek yerine, sadece filtreyi sıfırla

        table.refresh();
        filteredList.setAll(masterList);
        statusLabel.setText("✅ Liste yenilendi: " + masterList.size());
        searchField.clear();
    }

    // ✅ Lokal filtre (DB yok)
    private void applyLocalFilter() {
        String q = (searchField.getText() == null) ? "" : searchField.getText().trim();
        if (q.isEmpty()) {
            filteredList.setAll(masterList);
            statusLabel.setText("✅ Ürünler: " + masterList.size());
            return;
        }

        String qq = q.toLowerCase();

        // Barkod hızlı: prefix match
        // İsim: contains
        List<Product> out = new ArrayList<>(512);
        for (Product p : masterList) {
            String name = p.getName() == null ? "" : p.getName().toLowerCase();
            String barcode = p.getBarcode() == null ? "" : p.getBarcode();

            if (barcode.startsWith(q) || name.contains(qq)) {
                out.add(p);
            }
        }

        filteredList.setAll(out);
        statusLabel.setText("🔎 Arama sonucu: " + out.size());
    }

    // MainController global search burayı çağıracak
    public void applyGlobalSearch(String query) {
        if (query == null) query = "";
        String q = query.trim();

        searchField.setText(q);
        searchField.requestFocus();
        searchField.positionCaret(searchField.getText().length());

        // Global search'te bekletmeden filtre uygula:
        searchDebounce.stop();
        applyLocalFilter();
    }

    @FXML
    public void incStockSelected() {
        Product p = table.getSelectionModel().getSelectedItem();
        if (p == null) { statusLabel.setText("❗ Ürün seç."); return; }

        int delta = askInt("Stok Artır", "Kaç adet eklenecek?", "Boş=1");
        if (delta < 0) return;

        ProductDAO.increaseStock(p.getBarcode(), delta);

        // ✅ Cache güncelle
        p.setStock(p.getStock() + delta);
        table.refresh();

        statusLabel.setText("✅ Stok arttı: +" + delta);
        showSuccess("✅ Stok arttı: +" + delta + "\nÜrün: " + p.getName() + "\nYeni stok: " + p.getStock());


    }

    @FXML
    public void decStockSelected() {
        Product p = table.getSelectionModel().getSelectedItem();
        if (p == null) { statusLabel.setText("❗ Ürün seç."); return; }

        int delta = askInt("Stok Düş", "Kaç adet düşülecek?", "Boş=1");
        if (delta < 0) return;

        try {
            ProductDAO.decreaseStock(p.getBarcode(), delta);

            // ✅ Cache güncelle
            p.setStock(p.getStock() - delta);
            table.refresh();

            statusLabel.setText("✅ Stok düştü: -" + delta);
            showSuccess("✅ Stok düştü: -" + delta + "\nÜrün: " + p.getName() + "\nYeni stok: " + p.getStock());

        } catch (RuntimeException e) {
            statusLabel.setText("❗ Stok yetersiz.");
        }
    }

    @FXML
    public void setStockSelected() {
        Product p = table.getSelectionModel().getSelectedItem();
        if (p == null) { statusLabel.setText("❗ Ürün seç."); return; }

        int value = askIntExact("Stok = Yap", "Yeni stok kaç olsun?", "örn 120");
        if (value < 0) return;

        ProductDAO.setStock(p.getBarcode(), value);

        // ✅ Cache güncelle
        p.setStock(value);
        table.refresh();

        statusLabel.setText("✅ Stok güncellendi: " + value);
        showSuccess("✅ Stok güncellendi\nÜrün: " + p.getName() + "\nYeni stok: " + p.getStock());

    }

    @FXML
    public void editSelected() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { statusLabel.setText("❗ Ürün seç."); return; }

        ProductEditDialogController.Result r = openEditDialog(selected);
        if (r == null) { statusLabel.setText("İptal edildi."); return; }

        statusLabel.setText("⏳ Güncelleniyor...");

        // ✅ DB update'i UI thread'de bırakma (takılma olmasın)
        new Thread(() -> {
            try {
                // 1) DB update
                ProductDAO.updateBasics(selected.getId(), r.name, r.price, r.categoryId);

                // 2) UI update (gecikmeli)
                Platform.runLater(() -> {

                    // Model update (seçili objeyi güncelle)
                    selected.setName(r.name);
                    selected.setPrice(r.price);
                    selected.setCategoryId(r.categoryId);
                    selected.setCategoryName(r.categoryName);

                    // ✅ 1sn sonra TableView'i zorla güncelle
                    PauseTransition delay = new PauseTransition(Duration.millis(500));
                    delay.setOnFinished(ev -> {
                        replaceInListById(masterList, selected);
                        replaceInListById(filteredList, selected);

                        // TableView bazen cache tutuyor -> items resetle
                        table.setItems(null);
                        table.setItems(filteredList);

                        table.getSelectionModel().select(selected);
                        table.refresh();

                        statusLabel.setText("✅ Güncellendi.");
                    });
                    delay.play();
                });

            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("❗ Güncellenemedi: " + ex.getMessage()));
            }
        }, "product-edit-thread").start();
    }
    private void replaceInListById(ObservableList<Product> list, Product updated) {
        if (list == null || updated == null) return;

        for (int i = 0; i < list.size(); i++) {
            Product p = list.get(i);
            if (p != null && p.getId() == updated.getId()) {
                // ✅ SET ile replace: TableView kesin "change" görür
                list.set(i, updated);
                return;
            }
        }
    }


    private int askInt(String title, String header, String prompt) {
        TextInputDialog d = new TextInputDialog("");
        d.setTitle(title);
        d.setHeaderText(header);
        d.setContentText(prompt);

        Optional<String> res = d.showAndWait();
        if (res.isEmpty()) return -1;

        String t = res.get().trim();
        if (t.isEmpty()) return 1;

        try {
            int v = Integer.parseInt(t);
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (Exception e) {
            statusLabel.setText("❗ Sayı gir (örn 1,5,20).");
            return -1;
        }
    }

    private int askIntExact(String title, String header, String prompt) {
        TextInputDialog d = new TextInputDialog("");
        d.setTitle(title);
        d.setHeaderText(header);
        d.setContentText(prompt);

        Optional<String> res = d.showAndWait();
        if (res.isEmpty()) return -1;

        String t = res.get().trim();
        if (t.isEmpty()) { statusLabel.setText("❗ Boş olamaz."); return -1; }

        try {
            int v = Integer.parseInt(t);
            if (v < 0) throw new NumberFormatException();
            return v;
        } catch (Exception e) {
            statusLabel.setText("❗ Sayı gir (örn 0,10,120).");
            return -1;
        }
    }

    private ProductEditDialogController.Result openEditDialog(Product p) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dialogs/product_edit_dialog.fxml"));
            Scene scene = new Scene(loader.load());

            ProductEditDialogController controller = loader.getController();
            Stage dialog = new Stage();
            dialog.setTitle("Ürün Düzenle");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(scene);
            controller.setStage(dialog);

            controller.setup(p);

            dialog.showAndWait();

            if (!controller.isSaved()) return null;

            return new ProductEditDialogController.Result(
                    controller.getNewName(),
                    controller.getNewPrice(),
                    controller.getNewCategoryId(),
                    controller.getNewCategoryName()
            );

        } catch (Exception e) {
            throw new RuntimeException("Düzenleme popup açılamadı", e);
        }
    }

    @FXML
    public void deleteSelected() {
        Product p = table.getSelectionModel().getSelectedItem();
        if (p == null) { statusLabel.setText("❗ Silmek için ürün seç."); return; }

        if (ProductDAO.hasSales(p.getId())) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setHeaderText("Silme Engellendi");
            a.setContentText("Bu ürün daha önce satılmış.\nSatışı olan ürünler silinemez.");
            a.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            a.showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Ürünü Sil");
        confirm.setContentText(
                "Ürün: " + p.getName() + "\n\n" +
                        "Bu işlem GERİ ALINAMAZ.\n" +
                        "Silmek istiyor musun?"
        );
        confirm.getDialogPane().getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        // ✅ DB’den sil
        ProductDAO.deleteById(p.getId());

        // ✅ Her listeden güvenli kaldır (id ile)
        int id = p.getId();

        masterList.removeIf(x -> x != null && x.getId() == id);
        filteredList.removeIf(x -> x != null && x.getId() == id);

        // ✅ TableView bazen kendi items referansını tutar -> onu da temizle
        if (table.getItems() != null) {
            table.getItems().removeIf(x -> x != null && x.getId() == id);
        }

        // ✅ UI zorla yenile (bazı JavaFX cache bug’larını kırar)
        table.getSelectionModel().clearSelection();
        table.setItems(null);
        table.setItems(filteredList);
        table.refresh();

        statusLabel.setText("🗑 Ürün silindi: " + p.getName());

        // ✅ popup
        showSuccess("🗑 Ürün silindi:\n" + p.getName());
    }


    public static void refreshIfOpen() {
        if (INSTANCE != null) {
            INSTANCE.loadAllProductsAsync(); // DB'den tekrar çek
        }
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
