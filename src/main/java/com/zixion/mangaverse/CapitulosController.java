package com.zixion.mangaverse;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

public class CapitulosController {
    @FXML private Label mangaTituloLabel;
    @FXML private ListView<String> listaCapitulos;
    private MainController mainController;

    public void setDatos(String titulo, List<String> capitulos, MainController main) {
        this.mainController = main;
        mangaTituloLabel.setText(titulo);

        // Creamos una lista nueva con los nombres limpios
        List<String> nombresLimpios = new ArrayList<>();
        for (String cap : capitulos) {
            // Quitamos la extensión .cbz (y cualquier otra si la hubiera)
            String nombreSinExtension = cap.replace(".cbz", "");
            // Opcional: si quieres reemplazar guiones bajos por espacios para que se vea mejor
            // nombreSinExtension = nombreSinExtension.replace("_", " ");
            nombresLimpios.add(nombreSinExtension);
        }

        listaCapitulos.getItems().setAll(nombresLimpios);

        // Al hacer doble clic
        listaCapitulos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String itemSeleccionado = listaCapitulos.getSelectionModel().getSelectedItem();

                // ¡OJO! Para descargar, necesitamos el nombre REAL del archivo (con .cbz)
                // Lo recuperamos volviendo a poner la extensión
                String nombreArchivoReal = itemSeleccionado + ".cbz";

                System.out.println("Solicitando descarga de: " + nombreArchivoReal);
                descargarYAbrir(nombreArchivoReal);
            }
        });
    }

    @FXML private void volverABiblioteca() {
        mainController.abrirBiblioteca(); // Reutilizamos tu método existente
    }

    private void descargarYAbrir(String nombreArchivoReal) {
        // Bloqueamos la lista para que no pinchen mil veces mientras descarga
        listaCapitulos.setDisable(true);

        Task<File> task = new Task<>() {
            @Override
            protected File call() throws Exception {
                // Obtenemos el nombre del manga (ej: Air_gear) para la API
                String mangaId = mangaTituloLabel.getText().replace(" ", "_");
                return mainController.getMangaService().descargarArchivo(mangaId, nombreArchivoReal);
            }
        };

        task.setOnSucceeded(e -> {
            listaCapitulos.setDisable(false);
            File cbzDescargado = task.getValue();
            System.out.println("Descarga completa: " + cbzDescargado.getAbsolutePath());

            // Aquí es donde llamamos al Lector
            abrirElLector(cbzDescargado);
        });

        task.setOnFailed(e -> {
            listaCapitulos.setDisable(false);
            task.getException().printStackTrace();
            System.err.println("Error en la descarga.");
        });

        new Thread(task).start();
    }

    private void abrirElLector(File archivoDescargado) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("lector-view.fxml"));
            Node lectorNode = loader.load();

            LectorController controller = loader.getController();
            // Pasamos el archivo descargado y configuramos el visor
            controller.cargarManga(archivoDescargado, "Leyendo capítulo...", mainController);

            // Intercambiamos la vista en el StackPane principal
            mainController.getViewContainer().getChildren().setAll(lectorNode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}