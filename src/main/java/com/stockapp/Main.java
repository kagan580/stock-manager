package com.stockapp;

import atlantafx.base.theme.PrimerDark;
import com.stockapp.config.DatabaseConfig;
import com.stockapp.config.DbInitializer;
import com.stockapp.controller.ProductsController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/main.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 750);

        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        stage.setTitle("Stock Manager");
        stage.setScene(scene);
        stage.show();
        stage.focusedProperty().addListener((obs, oldV, focused) -> {
            if (focused) {
                // Uykudan dönünce genelde focus geri gelir
                ProductsController.refreshIfOpen();
                // istersen raporlar / kritik stok vs.
            }
        });
    }


    @Override
    public void stop() {
        // ✅ Uygulama kapanırken pool’u kapat
        DatabaseConfig.shutdownPool();
    }

    public static void main(String[] args) {
        launch();
    }
}
