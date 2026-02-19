package com.stockapp.controller;

import com.stockapp.config.DatabaseConfig;
import com.stockapp.dao.ProductDAO;
import com.stockapp.dao.ReportsDAO;
import com.stockapp.model.Product;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;

public class MainController {

    @FXML
    private StackPane contentPane;

    @FXML
    private TextField globalSearchField;

    // 🔴 Zil üzerindeki kırmızı nokta
    @FXML
    private Label bellBadge;

    // ✅ Cache: products sayfasını her seferinde yeniden yüklemeyelim
    private Parent productsPage;
    private ProductsController productsController;

    // ✅ Debounce (kullanıcı yazmayı bırakınca çalışır) - istersen kapatırız
    private PauseTransition searchDebounce;

    @FXML
    public void initialize() {
        // Main.java artık initPool + DbInitializer + canConnect yapıyor.
        // Burada sadece sayfayı açıp UI tarafını başlatalım.
        startUI();

        // Global search: Enter ile arama zaten var.
        // Ekstra olarak yazarken debounce istersen:
        setupDebouncedSearch();
    }

    private void startUI() {
        loadPage("/view/pages/dashboard.fxml");
        refreshBellBadge();

        // ✅ otomatik temizlik (3 yıldan eski fişler) - bunu arka planda yapalım ki UI donmasın
        new Thread(() -> {
            try {
                ReportsDAO.deleteSalesOlderThanYears(3);
            } catch (Exception ignored) {}
        }, "cleanup-thread").start();
    }

    /**
     * Eğer sen bazı bilgisayarlarda internet kopması yaşıyorsan bu kontrol kalsın.
     * Ama Main.java zaten başta bağlantıyı test ediyorsa burada ikinci kez yapmaya gerek yok.
     *
     * İstersen bu metodu tamamen silebilirsin.
     */
    private void checkConnectionOrExit() {
        if (DatabaseConfig.canConnect()) return;

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Bağlantı Hatası");
        alert.setHeaderText("Sunucuya bağlanılamadı");
        alert.setContentText("""
                Uygulama internet olmadan çalışamaz.
                
                • İnterneti kontrol edin
                • VPN varsa kapatın
                • Birkaç saniye sonra tekrar deneyin
                """);

        ButtonType retryBtn = new ButtonType("Tekrar Dene");
        ButtonType exitBtn = new ButtonType("Kapat", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(retryBtn, exitBtn);

        alert.showAndWait().ifPresent(result -> {
            if (result == retryBtn) {
                checkConnectionOrExit();
            } else {
                Platform.exit();
            }
        });
    }

    @FXML
    private void goCategories() { loadPage("/view/pages/categories.fxml"); }

    @FXML
    private void goDashboard() { loadPage("/view/pages/dashboard.fxml"); }

    @FXML
    private void goSales() { loadPage("/view/pages/sales.fxml"); }

    @FXML
    private void goStockEntry() { loadPage("/view/pages/stock_entry.fxml"); }

    @FXML
    private void goProducts() {
        // ✅ cached yükle
        showProductsPageIfNeeded();
        refreshBellBadge();
    }

    @FXML
    public void goReports() { loadPage("/view/pages/reports.fxml"); }

    @FXML
    private void onBackupNow() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Yedekleme");
        a.setContentText("Yedekleme özelliğini biraz sonra bağlayacağız.");
        a.showAndWait();
    }

    private void loadPage(String fxmlPath) {
        try {
            // products özel cache
            if ("/view/pages/products.fxml".equals(fxmlPath)) {
                showProductsPageIfNeeded();
                return;
            }

            Parent page = FXMLLoader.load(getClass().getResource(fxmlPath));
            contentPane.getChildren().setAll(page);

            refreshBellBadge();

        } catch (IOException e) {
            throw new RuntimeException("Sayfa yüklenemedi: " + fxmlPath, e);
        }
    }

    private void showProductsPageIfNeeded() {
        try {
            if (productsPage == null || productsController == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/pages/products.fxml"));
                productsPage = loader.load();
                productsController = loader.getController();
            }
            contentPane.getChildren().setAll(productsPage);
        } catch (Exception e) {
            throw new RuntimeException("Products sayfası yüklenemedi", e);
        }
    }

    // ✅ Kritik stok badge
    private void refreshBellBadge() {
        try {
            int criticalCount = ProductDAO.countCriticalProducts();
            if (bellBadge != null) {
                boolean show = criticalCount > 0;
                bellBadge.setVisible(show);
                bellBadge.setManaged(show);
            }
        } catch (Exception e) {
            if (bellBadge != null) {
                bellBadge.setVisible(false);
                bellBadge.setManaged(false);
            }
        }
    }

    @FXML
    private void openCriticalStockDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dialogs/critical_stock_dialog.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Kritik Stok");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            refreshBellBadge();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ✅ Enter ile arama: products sayfasını cache’ten aç + filtre uygula
    @FXML
    private void onGlobalSearch() {
        String q = (globalSearchField.getText() == null) ? "" : globalSearchField.getText().trim();
        if (q.isEmpty()) return;

        try {
            showProductsPageIfNeeded();
            productsController.applyGlobalSearch(q);

            globalSearchField.clear();
            refreshBellBadge();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ✅ Kullanıcı yazmayı bırakınca arama (isteğe bağlı)
     * Eğer istemiyorsan bu fonksiyonu çağırma.
     */
    private void setupDebouncedSearch() {
        searchDebounce = new PauseTransition(Duration.millis(300));

        globalSearchField.textProperty().addListener((obs, oldV, newV) -> {
            String q = (newV == null) ? "" : newV.trim();
            if (q.isEmpty()) return;

            searchDebounce.stop();
            searchDebounce.setOnFinished(e -> {
                // yazarken otomatik products’a atlamasın istemiyorsan bu kısmı kaldır:
                // sadece Enter ile arama kalsın
                // showProductsPageIfNeeded();
                // productsController.applyGlobalSearch(q);
            });
            searchDebounce.playFromStart();
        });
    }

    // ✅ Barkod bulunduysa direkt ürün düzenleme popup'ı (şu an kullanılmıyor gibi)
    private void openEditDialogFor(Product p) {
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

            if (controller.isSaved()) {
                ProductDAO.updateBasics(
                        p.getId(),
                        controller.getNewName(),
                        controller.getNewPrice(),
                        controller.getNewCategoryId()
                );
                refreshBellBadge();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
