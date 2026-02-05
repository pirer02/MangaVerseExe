package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
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
    @FXML private TextField txtBusqueda;
    private List<String> todosLosCapitulos = new ArrayList<>(); // Para guardar el respaldo
    // -----------------------------------

    public void setDatos(String titulo, List<String> capitulos, MainController main, Manga manga) {
        this.mainController = main;
        lblTitulo.setText(titulo);

        // 1. Limpiar nombres y guardar en la lista maestra
        todosLosCapitulos.clear();
        for (String cap : capitulos) {
            todosLosCapitulos.add(cap.replace(".cbz", ""));
        }

        // 2. Cargar inicialmente todos los capítulos
        listaCapitulos.getItems().setAll(todosLosCapitulos);

        // 3. LOGICA DE BÚSQUEDA (FILTRO)
        txtBusqueda.textProperty().addListener((observable, oldValue, newValue) -> {
            filtrarCapitulos(newValue);
        });

        // Cargar imágenes y demás info
        Image imagen = new Image(manga.getUrlPortada(), true);
        portadaImg.setImage(imagen);
        bgImage.setImage(imagen);
        cargarInfoExtra();

        // Evento de doble clic (se mantiene igual)
        listaCapitulos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String itemSeleccionado = listaCapitulos.getSelectionModel().getSelectedItem();
                if (itemSeleccionado != null) {
                    descargarYAbrir(itemSeleccionado + ".cbz");
                }
            }
        });
    }

    // Método auxiliar para el filtrado
    private void filtrarCapitulos(String texto) {
        if (texto == null || texto.isEmpty()) {
            listaCapitulos.getItems().setAll(todosLosCapitulos);
        } else {
            String lowerCaseFilter = texto.toLowerCase();
            List<String> filtrados = todosLosCapitulos.stream()
                    .filter(cap -> cap.toLowerCase().contains(lowerCaseFilter))
                    .toList();
            listaCapitulos.getItems().setAll(filtrados);
        }
    }

    private void cargarInfoExtra() {
        Task<Manga> task = new Task<>() {
            @Override
            protected Manga call() throws Exception {
                // Reemplazamos espacios por guiones bajos para que coincida con la búsqueda en la API/BD
                String mangaId = lblTitulo.getText().replace(" ", "_");
                return mainController.getMangaService().obtenerInfoManga(mangaId);
            }
        };

        task.setOnSucceeded(e -> {
            Manga info = task.getValue();

            if (info != null) {
                // 1. Asignar textos básicos
                lblTipo.setText(info.tipo != null && !info.tipo.isEmpty() ? info.tipo.toUpperCase() : "MANGA");
                txtSinopsis.setText(info.sinopsis != null ? info.sinopsis : "Sin sinopsis disponible.");

                // 2. Lógica de Colores y Estado
                String estadoNormalizado = info.estado != null ? info.estado.toLowerCase() : "";

                if (estadoNormalizado.contains("terminado") || estadoNormalizado.contains("finalizado")) {
                    lblEstado.setText("TERMINADO");
                    // ROJO
                    lblEstado.setStyle("-fx-background-color: #e50914; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 4; -fx-font-weight: bold;");
                } else {
                    lblEstado.setText("EN CURSO");
                    // VERDE
                    lblEstado.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 4; -fx-font-weight: bold;");
                }

                // 3. Crear etiquetas para los géneros
                contenedorGeneros.getChildren().clear();
                if (info.generos != null) {
                    for (String genero : info.generos) {
                        Label tag = new Label(genero);
                        tag.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 15; -fx-font-size: 12px;");
                        contenedorGeneros.getChildren().add(tag);
                    }
                }
            }
        });

        task.setOnFailed(e -> {
            System.err.println("No se pudo cargar la info extra del manga.");
            txtSinopsis.setText("Error al cargar la descripción desde el servidor.");
            lblEstado.setText("ERROR");
            lblEstado.setStyle("-fx-background-color: #555555; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 4;");
        });

        // Ejecutar en un hilo separado para no congelar la UI
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