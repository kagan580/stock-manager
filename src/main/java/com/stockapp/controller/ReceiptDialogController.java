package com.stockapp.controller;

import com.stockapp.dao.ReportsDAO;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;

public class ReceiptDialogController {

    @FXML private Label saleIdLabel;
    @FXML private Label saleDateLabel;
    @FXML private Label totalLabel;
    @FXML private Label msgLabel;

    @FXML private TableView<ReportsDAO.ReceiptItemRow> itemsTable;
    @FXML private TableColumn<ReportsDAO.ReceiptItemRow, String> rNameCol, rBarcodeCol;
    @FXML private TableColumn<ReportsDAO.ReceiptItemRow, Number> rQtyCol, rUnitCol, rTotalCol;

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ✅ aynı anda iki load çakışmasın
    private volatile boolean loading = false;

    @FXML
    public void initialize() {
        rNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        rBarcodeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().barcode()));
        rQtyCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().qty()));
        rUnitCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().unitPrice()));
        rTotalCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().lineTotal()));
    }

    public void loadReceipt(int saleId) {
        if (loading) return;
        loading = true;

        // ✅ UI loading state
        msgLabel.setText("⏳ Fiş yükleniyor...");
        itemsTable.setDisable(true);
        itemsTable.setItems(FXCollections.observableArrayList());

        saleIdLabel.setText(String.valueOf(saleId));
        saleDateLabel.setText("-");
        totalLabel.setText("-");

        new Thread(() -> {
            try {
                ReportsDAO.ReceiptRow receipt = ReportsDAO.getReceipt(saleId);

                Platform.runLater(() -> {
                    saleIdLabel.setText(String.valueOf(receipt.saleId()));
                    saleDateLabel.setText(receipt.saleDate().format(DT));
                    totalLabel.setText(String.format("%.2f", receipt.totalAmount()));

                    itemsTable.setItems(FXCollections.observableArrayList(receipt.items()));
                    itemsTable.setDisable(false);

                    msgLabel.setText("");
                    loading = false;
                });

            } catch (RuntimeException e) {
                Platform.runLater(() -> {
                    msgLabel.setText("❗ " + e.getMessage());
                    itemsTable.setDisable(false);
                    loading = false;
                });
            }
        }, "receipt-load-thread").start();
    }

    @FXML
    public void close() {
        Stage stage = (Stage) itemsTable.getScene().getWindow();
        stage.close();
    }
}
