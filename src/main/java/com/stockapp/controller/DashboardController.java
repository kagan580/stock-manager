package com.stockapp.controller;

import com.stockapp.dao.ProductDAO;
import com.stockapp.dao.SalesDAO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class DashboardController {

    @FXML private Label totalProductsLabel;
    @FXML private Label monthlySalesLabel;
    @FXML private Label criticalCountLabel;

    private volatile boolean loading = false;

    @FXML
    public void initialize() {
        refreshDashboard();
    }

    public void refreshDashboard() {
        if (loading) return;
        loading = true;

        // UI: loading state
        totalProductsLabel.setText("...");
        monthlySalesLabel.setText("...");
        criticalCountLabel.setText("...");

        new Thread(() -> {
            try {
                int total = ProductDAO.countAllProducts();
                int monthly = SalesDAO.countMonthlySalesItems();
                int critical = ProductDAO.countCriticalProducts();

                Platform.runLater(() -> {
                    totalProductsLabel.setText(String.valueOf(total));
                    monthlySalesLabel.setText(String.valueOf(monthly));
                    criticalCountLabel.setText(String.valueOf(critical));
                    loading = false;
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    totalProductsLabel.setText("-");
                    monthlySalesLabel.setText("-");
                    criticalCountLabel.setText("-");
                    loading = false;
                });
            }
        }, "dashboard-thread").start();
    }
}
