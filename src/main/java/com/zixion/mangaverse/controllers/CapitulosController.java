package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CapitulosController {
    @FXML private Label lblTitulo;
    @FXML private ListView<String> listaCapitulos;
    private MainController mainController;

    // --- ELEMENTOS VISUALES DEL FXML ---
    @FXML private ImageView bgImage;      // Fondo para efecto borroso
    @FXML private ImageView portadaImg;   // Portada nítida
    @FXML private Label lblEstado;
    @FXML private Label lblTipo;
    @FXML private Text txtSinopsis;       // Usamos Text para que se ajuste al ancho
    @FXML private FlowPane contenedorGeneros; // Contenedor para las etiquetas
    // -----------------------------------

    public void setDatos(String titulo, List<String> capitulos, MainController main, Manga manga) {
        this.mainController = main;
        lblTitulo.setText(titulo);

        // Creamos una lista nueva con los nombres limpios
        List<String> nombresLimpios = new ArrayList<>();
        for (String cap : capitulos) {
            String nombreSinExtension = cap.replace(".cbz", "");
            nombresLimpios.add(nombreSinExtension);
        }

        listaCapitulos.getItems().setAll(nombresLimpios);

        Image imagen = new Image(manga.getUrlPortada(), true);
        portadaImg.setImage(imagen);
        bgImage.setImage(imagen);
        cargarInfoExtra();

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

    private void cargarInfoExtra() {
        Task<Manga> task = new Task<>() {
            @Override
            protected Manga call() throws Exception {
                // Llama al método nuevo que creamos en MangaService
                String mangaId = lblTitulo.getText().replace(" ", "_");
                return mainController.getMangaService().obtenerInfoManga(mangaId);
            }
        };

        task.setOnSucceeded(e -> {
            Manga info = task.getValue();

            // Actualizar interfaz
            lblEstado.setText(info.estado != null ? info.estado.toUpperCase() : "DESCONOCIDO");
            lblTipo.setText(info.tipo != null && !info.tipo.isEmpty() ? info.tipo.toUpperCase() : "MANGA");
            txtSinopsis.setText(info.sinopsis);

            // Estilo dinámico para el estado
            if ("Finalizado".equalsIgnoreCase(info.estado) || "Terminado".equalsIgnoreCase(info.estado)) {
                lblEstado.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 4; -fx-font-weight: bold;");
            } else {
                lblEstado.setStyle("-fx-background-color: #e50914; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 4; -fx-font-weight: bold;");
            }

            // Crear etiquetas (tags) para los géneros
            contenedorGeneros.getChildren().clear();
            if (info.generos != null) {
                for (String genero : info.generos) {
                    Label tag = new Label(genero);
                    tag.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 15; -fx-font-size: 12px;");
                    contenedorGeneros.getChildren().add(tag);
                }
            }
        });

        task.setOnFailed(e -> {
            System.err.println("No se pudo cargar la info extra del manga.");
            txtSinopsis.setText("Descripción no disponible.");
        });

        new Thread(task).start();
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
                String mangaId = lblTitulo.getText().replace(" ", "_");
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "lector-view.fxml"));
            Node lectorNode = loader.load();

            LectorController controller = loader.getController();
            mainController.setCurrentController(controller);
            // Pasamos el archivo descargado y configuramos el visor
            controller.cargarManga(archivoDescargado, "Leyendo capítulo...", mainController);

            // Intercambiamos la vista en el StackPane principal
            mainController.getViewContainer().getChildren().setAll(lectorNode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}