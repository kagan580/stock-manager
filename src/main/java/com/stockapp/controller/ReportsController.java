package com.stockapp.controller;

import com.stockapp.dao.ReportsDAO;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ReportsController {

    @FXML private TextField topProductSearchField;

    private ObservableList<ReportsDAO.TopProductRow> allTopProducts;

    @FXML private DatePicker fromDate;
    @FXML private DatePicker toDate;

    @FXML private Label revenueLabel;
    @FXML private Label statusLabel;

    @FXML private TabPane reportsTabs;
    @FXML private Tab tabSalesHistory;

    // Top products
    @FXML private TableView<ReportsDAO.TopProductRow> topProductsTable;
    @FXML private TableColumn<ReportsDAO.TopProductRow, String> tpNameCol, tpBarcodeCol;
    @FXML private TableColumn<ReportsDAO.TopProductRow, Number> tpQtyCol;

    // Category summary
    @FXML private TableView<ReportsDAO.CategorySummaryRow> categoryTable;
    @FXML private TableColumn<ReportsDAO.CategorySummaryRow, String> catNameCol;
    @FXML private TableColumn<ReportsDAO.CategorySummaryRow, Number> catQtyCol, catRevenueCol;

    // Chart
    @FXML private LineChart<String, Number> revenueChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    // Sales history
    @FXML private TableView<ReportsDAO.SaleRow> salesTable;
    @FXML private TableColumn<ReportsDAO.SaleRow, Number> saleIdCol;
    @FXML private TableColumn<ReportsDAO.SaleRow, String> saleDateCol;
    @FXML private TableColumn<ReportsDAO.SaleRow, Number> saleTotalCol;
    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private LocalDateTime lastFrom;
    private LocalDateTime lastToExclusive;

    // ✅ aynı anda iki load çakışmasın
    private volatile boolean loading = false;

    @FXML
    public void initialize() {
        // default: bu ay
        LocalDate now = LocalDate.now();
        LocalDate firstDay = now.withDayOfMonth(1);
        fromDate.setValue(firstDay);
        toDate.setValue(now);

        // tables
        tpNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        tpBarcodeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().barcode()));
        tpQtyCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().totalQty()));

        catNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category()));
        catQtyCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().totalQty()));
        catRevenueCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().revenue()));

        saleIdCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().saleId()));
        saleDateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().saleDate().format(DT)
        ));
        saleTotalCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().totalAmount()));

        // double click -> receipt
        salesTable.setRowFactory(tv -> {
            TableRow<ReportsDAO.SaleRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openReceipt(row.getItem().saleId());
                }
            });
            return row;
        });

        // ✅ Top products search (local filter)
        topProductSearchField.textProperty().addListener((obs, oldV, newV) -> {
            if (allTopProducts == null) return;

            String q = (newV == null) ? "" : newV.toLowerCase().trim();
            if (q.isEmpty()) {
                topProductsTable.setItems(allTopProducts);
                return;
            }

            topProductsTable.setItems(allTopProducts.filtered(p ->
                    (p.name() != null && p.name().toLowerCase().contains(q)) ||
                            (p.barcode() != null && p.barcode().toLowerCase().contains(q))
            ));
        });

        loadReports();
    }

    @FXML
    public void loadReports() {
        if (loading) return;

        if (fromDate.getValue() == null || toDate.getValue() == null) {
            statusLabel.setText("❗ Tarih seç.");
            return;
        }
        if (toDate.getValue().isBefore(fromDate.getValue())) {
            statusLabel.setText("❗ Bitiş tarihi başlangıçtan önce olamaz.");
            return;
        }

        LocalDateTime from = fromDate.getValue().atStartOfDay();
        LocalDateTime toExclusive = toDate.getValue().plusDays(1).atStartOfDay();

        lastFrom = from;
        lastToExclusive = toExclusive;

        loading = true;
        statusLabel.setText("⏳ Raporlar yükleniyor...");

        // ✅ UI donmasın: her şeyi arka thread’de çek
        new Thread(() -> {
            try {
                double revenue = ReportsDAO.getRevenue(from, toExclusive);

                List<ReportsDAO.TopProductRow> topProducts = ReportsDAO.getTopProducts(from, toExclusive);
                List<ReportsDAO.CategorySummaryRow> categories = ReportsDAO.getCategorySummary(from, toExclusive);

                List<ReportsDAO.DailyRevenueRow> daily = ReportsDAO.getDailyRevenue(from, toExclusive);
                Map<LocalDate, Integer> receiptCountMap = ReportsDAO.getReceiptCountsByDay(from, toExclusive);

                List<ReportsDAO.SaleRow> sales = ReportsDAO.getSales(from, toExclusive, 200);

                Platform.runLater(() -> {
                    revenueLabel.setText(String.format("%.2f", revenue));

                    allTopProducts = FXCollections.observableArrayList(topProducts);
                    topProductsTable.setItems(allTopProducts);

                    categoryTable.setItems(FXCollections.observableArrayList(categories));
                    salesTable.setItems(FXCollections.observableArrayList(sales));

                    fillChartWithData(daily, receiptCountMap);

                    statusLabel.setText("✅ Rapor güncellendi.");
                    loading = false;
                });

            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    statusLabel.setText("❗ Rapor alınamadı: " + e.getMessage());
                    loading = false;
                });
            }
        }, "reports-load-thread").start();
    }

    // ✅ Artık DB çağrısı yok: hazır data ile chart bas
    private void fillChartWithData(List<ReportsDAO.DailyRevenueRow> daily,
                                   Map<LocalDate, Integer> receiptCountMap) {

        revenueChart.getData().clear();

        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Ciro");

        for (ReportsDAO.DailyRevenueRow row : daily) {
            s.getData().add(new XYChart.Data<>(row.day().toString(), row.revenue()));
        }

        revenueChart.getData().add(s);

        // Node’lar sonra oluşur: runLater doğru
        Platform.runLater(() -> {
            for (XYChart.Data<String, Number> d : s.getData()) {
                if (d.getNode() == null) continue;

                String dayStr = d.getXValue();
                LocalDate day = LocalDate.parse(dayStr);
                int receiptCount = receiptCountMap.getOrDefault(day, 0);

                Tooltip t = new Tooltip(String.format("Ciro: %.2f ₺\nFiş: %d",
                        d.getYValue().doubleValue(), receiptCount));

                t.setShowDelay(javafx.util.Duration.millis(10));
                t.setHideDelay(javafx.util.Duration.millis(50));
                t.setShowDuration(javafx.util.Duration.seconds(10));

                Tooltip.install(d.getNode(), t);

                d.getNode().setOnMouseClicked(e -> loadSalesOfDayAsync(day, dayStr));
            }
        });
    }

    // ✅ Noktaya tıklayınca: sadece o günün fişlerini async yükle (UI donmasın)
    private void loadSalesOfDayAsync(LocalDate day, String dayStr) {
        statusLabel.setText("⏳ " + dayStr + " fişleri yükleniyor...");

        new Thread(() -> {
            try {
                var list = ReportsDAO.getSalesByDay(day, 200);
                Platform.runLater(() -> {
                    salesTable.setItems(FXCollections.observableArrayList(list));

                    if (reportsTabs != null && tabSalesHistory != null) {
                        reportsTabs.getSelectionModel().select(tabSalesHistory);
                    }
                    salesTable.requestFocus();

                    statusLabel.setText("📌 " + dayStr + " fişleri yüklendi. (Fiş: " + list.size() + ")");
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusLabel.setText("❗ Gün fişleri alınamadı."));
            }
        }, "sales-by-day-thread").start();
    }

    @FXML
    public void showSelectedReceipt() {
        ReportsDAO.SaleRow selected = salesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("❗ Fiş seç.");
            return;
        }
        openReceipt(selected.saleId());
    }

    private void openReceipt(int saleId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/dialogs/receipt_dialog.fxml"));
            Scene scene = new Scene(loader.load());

            ReceiptDialogController controller = loader.getController();
            controller.loadReceipt(saleId);

            Stage dialog = new Stage();
            dialog.setTitle("Fiş Detayı - #" + saleId);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(scene);

            dialog.showAndWait();
        } catch (Exception e) {
            statusLabel.setText("❗ Fiş popup açılamadı.");
        }
    }
}
