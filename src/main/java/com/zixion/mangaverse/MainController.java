package com.zixion.mangaverse;

import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField; // Importación necesaria
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

public class MainController {
    @FXML private VBox drawerMenu;
    @FXML private StackPane viewContainer;
    @FXML private TextField searchBar; // Referencia al buscador

    private FlowPane mangaGrid;
    private boolean menuVisible = false;
    private final double MENU_WIDTH = 280.0; // Sincronizado con FXML

    private MangaService mangaService = new MangaService();

    @FXML
    public void initialize() {
        abrirBiblioteca();

        // Ejemplo opcional: Detectar cuando el usuario escribe en el buscador
        if (searchBar != null) {
            searchBar.textProperty().addListener((observable, oldValue, newValue) -> {
                System.out.println("Buscando: " + newValue);
            });
        }
    }

    @FXML
    private void toggleMenu() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), drawerMenu);
        if (menuVisible) {
            transition.setToX(-MENU_WIDTH);
            menuVisible = false;
        } else {
            transition.setToX(0);
            menuVisible = true;
        }
        transition.play();
    }

    @FXML
    private void abrirBiblioteca() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("biblioteca-view.fxml"));
            Node root = loader.load();

            // 1. Obtenemos el controlador de la biblioteca
            BibliotecaController biblioCtrl = loader.getController();

            // 2. Colocamos la vista en el contenedor [cite: 8]
            viewContainer.getChildren().setAll(root);

            System.out.println("HOLA 1");

            // 3. Usamos la referencia directa del controlador
            if (biblioCtrl != null && biblioCtrl.mangaGrid != null) {
                this.mangaGrid = biblioCtrl.mangaGrid; // Guardamos la referencia en el MainController

                System.out.println("HOLA 2 - ¡Grid encontrado mediante Controller!");
                mangaGrid.getChildren().clear();

                Task<List<Manga>> task = new Task<>() {
                    @Override
                    protected List<Manga> call() throws Exception {
                        return mangaService.obtenerMangasDesdeServidor();
                    }
                };

                task.setOnSucceeded(e -> {
                    List<Manga> mangas = task.getValue();
                    for (Manga m : mangas) {
                        agregarMangaAGrid(m);
                    }
                });

                new Thread(task).start();
            } else {
                System.err.println("ERROR: No se pudo vincular el BibliotecaController.");
            }
        } catch (IOException e) {
            System.err.println("Error cargando biblioteca-view.fxml");
            e.printStackTrace();
        }
    }

    private void agregarMangaAGrid(Manga manga) {
        try {
            // 1. Cargamos el archivo FXML de la tarjeta
            FXMLLoader loader = new FXMLLoader(getClass().getResource("manga-card.fxml"));

            // El controlador del FXML (VBox) se convierte en nuestro nodo raíz
            VBox card = loader.load();

            // 2. Buscamos los elementos dentro del FXML por su fx:id
            // (Asegúrate de que en manga-card.fxml tengan estos IDs)
            ImageView iv = (ImageView) card.lookup("#portadaImageView");
            Label lbl = (Label) card.lookup("#tituloLabel");

            // 3. Asignamos los datos del objeto Manga [cite: 2]
            if (iv != null) {
                iv.setImage(manga.getPortada());
            }
            if (lbl != null) {
                lbl.setText(manga.getTitulo());
            }

            // 4. Configuramos el evento de clic
            card.setOnMouseClicked(event -> {
                System.out.println("Abriendo manga: " + manga.getTitulo());
                // Aquí irá la lógica para abrir el lector
            });

            // 5. Añadimos la tarjeta al grid de la biblioteca
            mangaGrid.getChildren().add(card);

        } catch (IOException e) {
            System.err.println("No se pudo cargar manga-card.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void mostrarLector(Manga manga) {
        System.out.println("Abriendo lector para: " + manga.getTitulo());
        // Aquí cargarías una nueva vista (ej. lector-view.fxml) que lea el archivo .zip o .cbz
        // loadView("lector-view.fxml");
    }
    private void loadView(String fxml) {
        try {
            Node node = FXMLLoader.load(Objects.requireNonNull(getClass().getResource(fxml)));
            viewContainer.getChildren().setAll(node);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML private void abrirExplorar() { if (menuVisible) toggleMenu(); }
}