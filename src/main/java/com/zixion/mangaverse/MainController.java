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

    private Object currentController;

    private MangaService mangaService = new MangaService();

    public MangaService getMangaService() {
        return mangaService;
    }
    public void setMangaService(MangaService mangaService) {
        this.mangaService = mangaService;
    }
    public StackPane getViewContainer() {
        return viewContainer;
    }
    public void setViewContainer(StackPane viewContainer) {
        this.viewContainer = viewContainer;
    }
    public Object getCurrentController() {
        return currentController;
    }
    public void setCurrentController(Object currentController) {
        this.currentController = currentController;
    }

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
    public void abrirBiblioteca() {
        if (currentController instanceof LectorController) {
            ((LectorController) currentController).detenerCarga();
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("biblioteca-view.fxml"));
            Node root = loader.load();

            // 1. Obtenemos el controlador de la biblioteca
            BibliotecaController biblioCtrl = loader.getController();

            // 2. Colocamos la vista en el contenedor [cite: 8]
            viewContainer.getChildren().setAll(root);

            // 3. Usamos la referencia directa del controlador
            if (biblioCtrl != null && biblioCtrl.mangaGrid != null) {
                this.mangaGrid = biblioCtrl.mangaGrid; // Guardamos la referencia en el MainController
                mangaGrid.getChildren().clear();

                Task<List<Manga>> task = new Task<>() {
                    @Override
                    protected List<Manga> call() throws Exception {
                        return mangaService.obtenerMangasDesdeServidor();
                    }
                };

                task.setOnSucceeded(e -> {
                    List<Manga> mangas = task.getValue();
                    if (mangaGrid != null) {
                        // 1. Limpieza total garantizada antes de empezar
                        mangaGrid.getChildren().clear();

                        for (Manga m : mangas) {
                            agregarMangaAGrid(m);
                        }
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
            // 1. Cargamos una nueva instancia del FXML para esta tarjeta
            FXMLLoader loader = new FXMLLoader(getClass().getResource("manga-card.fxml"));
            VBox card = loader.load();

            // 2. Localizamos los elementos visuales
            ImageView iv = (ImageView) card.lookup("#portadaImageView");
            Label lbl = (Label) card.lookup("#tituloLabel");

            // 3. Asignamos el texto inmediatamente
            if (lbl != null) {
                lbl.setText(manga.getTitulo());
            }

            // 4. AÑADIMOS LA TARJETA AL GRID (Solo una vez aquí)
            // Esto permite que la interfaz sea instantánea
            mangaGrid.getChildren().add(card);

            // 5. Iniciamos la carga de la imagen en segundo plano
            if (iv != null && manga.getUrlPortada() != null) {
                // Usamos el constructor de 6 parámetros para activar backgroundLoading (true)
                Image imagen = new Image(manga.getUrlPortada(), 180, 250, true, true, true);

                imagen.errorProperty().addListener((obs, old, hasError) -> {
                    if (hasError) {
                        System.err.println("Error en: " + manga.getUrlPortada());
                        // Aquí podrías cargar una imagen local genérica si la descarga falla
                    }
                });

                iv.setImage(imagen);
            }

            // 6. Configuramos el evento de clic
            card.setOnMouseClicked(event -> {
                try {
                    // Reemplazamos espacios por guiones bajos para la API
                    String mangaId = manga.getTitulo().replace(" ", "_");
                    List<String> caps = mangaService.obtenerCapitulos(mangaId);

                    FXMLLoader capLoader = new FXMLLoader(getClass().getResource("capitulos-view.fxml"));
                    Node node = capLoader.load();

                    CapitulosController controller = capLoader.getController();
                    controller.setDatos(manga.getTitulo(), caps, this);

                    viewContainer.getChildren().setAll(node);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // IMPORTANTE: Se eliminó la duplicidad de mangaGrid.getChildren().add(card) que estaba aquí.

        } catch (IOException e) {
            System.err.println("No se pudo cargar manga-card.fxml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML private void abrirExplorar() { if (menuVisible) toggleMenu(); }
}