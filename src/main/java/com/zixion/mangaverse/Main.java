package com.zixion.mangaverse;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class Main extends Application {

    public static final String APP_FOLDER = System.getProperty("user.home") + File.separator + ".mangaverse";

    @Override
    public void start(Stage stage) throws IOException {
        File directory = new File(APP_FOLDER);
        if (!directory.exists()) {
            directory.mkdirs();
            System.out.println("Carpeta de la aplicación creada en: " + APP_FOLDER);
        }

        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);

        stage.setTitle("MangaVerse");
        stage.setScene(scene);

        stage.setMaximized(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Limpiando archivos temporales en: " + APP_FOLDER);
            limpiarCarpetaTemporal(directory);
        }));
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

    private void limpiarCarpetaTemporal(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".cbz")) {
                    f.delete();
                }
            }
        }
    }
}