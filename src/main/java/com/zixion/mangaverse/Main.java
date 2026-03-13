package com.zixion.mangaverse;

// --- 1. AÑADIMOS ESTAS DOS IMPORTACIONES NUEVAS ---
import com.zixion.mangaverse.controllers.MainController;
import com.zixion.mangaverse.services.BackgroundService;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class Main extends Application {

    public static final String APP_FOLDER = System.getProperty("user.home") + File.separator + ".mangaverse";
    public static final String CAPITULOS_FOLDER = APP_FOLDER + File.separator + "capitulos-cache";
    public static final String CAPITULOS_COLOR_FOLDER = APP_FOLDER + File.separator + "capitulos-color-cache";
    public static final String LISTADO_FOLDER = APP_FOLDER + File.separator + "listado-capitulos-cache";
    public static final String MUSICA_FOLDER = APP_FOLDER + File.separator + "musica-custom";

    @Override
    public void start(Stage stage) throws IOException {
        String[] folders = {APP_FOLDER, CAPITULOS_FOLDER, CAPITULOS_COLOR_FOLDER, LISTADO_FOLDER, MUSICA_FOLDER};
        for (String path : folders) {
            File dir = new File(path);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }

        // RUTA ABSOLUTA PARA EVITAR EL ERROR "Location is not set"
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/com/zixion/mangaverse/main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);

        // --- 2. CAPTURAMOS EL CONTROLADOR PRINCIPAL ---
        MainController mainController = fxmlLoader.getController();

        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/com/zixion/mangaverse/icons/MV.png"))));
        stage.setTitle("MangaVerse");
        stage.setScene(scene);

        stage.setMaximized(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Limpiando archivos temporales...");
            limpiarCarpetaTemporal(new File(CAPITULOS_FOLDER));
            limpiarCarpetaTemporal(new File(CAPITULOS_COLOR_FOLDER));
        }));

        stage.show();

        // --- 3. INICIAMOS EL SERVICIO DE NOTIFICACIONES EN SEGUNDO PLANO ---
        new BackgroundService(mainController, stage);
    }

    public static void main(String[] args) {
        launch();
    }

    private void limpiarCarpetaTemporal(File folder) {
        if (folder.exists() && folder.isDirectory()) {
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
}