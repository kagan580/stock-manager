package com.stockapp.controller;

import com.stockapp.dao.ProductDAO;
import com.stockapp.model.Product;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.util.List;

public class CriticalStockDialogController {

    @FXML private TableView<Product> table;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, String> colBarcode;
    @FXML private TableColumn<Product, Integer> colStock;
    @FXML private TableColumn<Product, Double> colPrice;

    private volatile boolean loading = false;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colBarcode.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        colStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));

        refresh();
    }

    @FXML
    public void refresh() {
        if (loading) return;
        loading = true;

        table.setDisable(true);
        table.setItems(FXCollections.observableArrayList());

        new Thread(() -> {
            List<Product> list;
            try {
                list = ProductDAO.getCriticalProducts();
            } catch (Exception e) {
                list = List.of();
            }

            List<Product> finalList = list;
            Platform.runLater(() -> {
                table.setItems(FXCollections.observableArrayList(finalList));
                table.setDisable(false);
                loading = false;
            });
        }, "critical-products-thread").start();
    }

    @FXML
    public void close() {
        Stage stage = (Stage) table.getScene().getWindow();
        stage.close();
    }
}
