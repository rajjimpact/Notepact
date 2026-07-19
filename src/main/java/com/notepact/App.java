package com.notepact;
import com.notepact.controller.MainController;
import com.notepact.settings.SettingsManager;
import com.notepact.model.AppSettings;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import java.util.logging.Logger;
public class App extends Application {
    private static final Logger LOG = Logger.getLogger(App.class.getName());
    private MainController controller;
    @Override
    public void start(Stage primaryStage) {
        AppSettings s = SettingsManager.getInstance().getSettings();
        controller = new MainController(primaryStage);
        Scene scene = controller.buildScene();
        // Window setup
        primaryStage.setTitle("Notepact");
        primaryStage.setScene(scene);
        primaryStage.setWidth(s.getWindowWidth());
        primaryStage.setHeight(s.getWindowHeight());

        // App icon — load Notepact mascot logo from resources
        try {
            var iconStream = getClass().getResourceAsStream("/com/notepact/logo.png");
            if (iconStream != null) {
                primaryStage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ex) {
            LOG.warning("Could not load app icon: " + ex.getMessage());
        }

        if (s.getWindowX() >= 0) primaryStage.setX(s.getWindowX());
        if (s.getWindowY() >= 0) primaryStage.setY(s.getWindowY());
        if (s.isMaximized()) primaryStage.setMaximized(true);

        // Graceful shutdown
        primaryStage.setOnCloseRequest(this::onClose);

        primaryStage.show();

        // Run startup (session recovery, auto-save, etc.)
        Platform.runLater(controller::startup);

        LOG.info("Notepact started successfully.");
    }

    private void onClose(WindowEvent event) {
        // Check for unsaved changes is handled inside MainController via tab close events
        if (controller != null) {
            controller.onWindowClose();
        }
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
