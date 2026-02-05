package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.services.MangaService;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MainController {

    @FXML private VBox drawerMenu;
    @FXML private StackPane viewContainer;
    @FXML private TextField searchBar;

    private boolean menuVisible = false;
    private final double MENU_WIDTH = 280.0;
    private final MangaService mangaService = new MangaService();

    // Esta lista contendrá los objetos Manga con sus datos públicos rellenos
    private List<Manga> listaMaestra = new ArrayList<>();

    private final List<String> GENEROS_POOL = Arrays.asList(
            "Shonen", "Accion", "Aventura", "Comedia", "Drama", "Seinen", "Romance", "Isekai", "Deporte", "Chanbara"
    );

    @FXML
    public void initialize() {
        // Iniciamos la carga de datos
        cargarDatosYMostrarExplorar();

        // Barra de búsqueda: si hay texto busca, si no, vuelve a Netflix
        if (searchBar != null) {
            searchBar.textProperty().addListener((obs, old, newText) -> {
                if (newText == null || newText.trim().isEmpty()) {
                    abrirExplorar();
                } else {
                    ejecutarBusqueda(newText.trim().toLowerCase());
                }
            });
        }
    }

    private void cargarDatosYMostrarExplorar() {
        Task<List<Manga>> task = new Task<>() {
            @Override
            protected List<Manga> call() throws Exception {
                // 1. Obtenemos la lista inicial del servidor
                List<Manga> mangasServidor = mangaService.obtenerMangasDesdeServidor();

                // 2. Para cada manga, obtenemos la info extra y rellenamos las variables PÚBLICAS
                for (Manga m : mangasServidor) {
                    Manga info = mangaService.obtenerInfoManga(m.getTitulo().replace(" ", "_"));
                    // Acceso directo a variables públicas de tu clase Manga
                    m.generos = info.generos;
                    m.sinopsis = info.sinopsis;
                    m.estado = info.estado;
                    m.tipo = info.tipo;
                }
                return mangasServidor;
            }
        };

        task.setOnSucceeded(e -> {
            this.listaMaestra = task.getValue();
            abrirExplorar();
        });
        new Thread(task).start();
    }

    @FXML
    public void abrirExplorar() {
        if (menuVisible) toggleMenu();

        VBox mainLayout = new VBox(35);
        mainLayout.setPadding(new Insets(20, 0, 40, 0));
        mainLayout.setStyle("-fx-background-color: #141414;");

        ScrollPane scrollVertical = new ScrollPane(mainLayout);
        scrollVertical.setFitToWidth(true);
        scrollVertical.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollVertical.setStyle("-fx-background: #141414; -fx-background-color: #141414; -fx-border-color: transparent;");

        List<String> generosCopia = new ArrayList<>(GENEROS_POOL);
        Collections.shuffle(generosCopia);

        for (String gen : generosCopia.subList(0, Math.min(6, generosCopia.size()))) {
            // Filtrado usando la variable pública .generos
            List<Manga> filtrados = listaMaestra.stream()
                    .filter(m -> m.generos != null &&
                            m.generos.stream().anyMatch(g -> g.equalsIgnoreCase(gen)))
                    .collect(Collectors.toList());

            if (!filtrados.isEmpty()) {
                mainLayout.getChildren().add(crearFilaHorizontal(gen, filtrados));
            }
        }
        viewContainer.getChildren().setAll(scrollVertical);
    }

    private void ejecutarBusqueda(String query) {
        FlowPane grid = new FlowPane();
        grid.setHgap(20); grid.setVgap(25);
        grid.setPadding(new Insets(30));
        grid.setStyle("-fx-background-color: #141414;");

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #141414; -fx-background-color: #141414; -fx-border-color: transparent;");

        for (Manga m : listaMaestra) {
            if (m.getTitulo().toLowerCase().contains(query)) {
                grid.getChildren().add(crearTarjetaManga(m));
            }
        }
        viewContainer.getChildren().setAll(scroll);
    }

    private VBox crearFilaHorizontal(String titulo, List<Manga> mangas) {
        VBox row = new VBox(10);
        Label lbl = new Label(titulo.toUpperCase());
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 0 0 0 25;");

        HBox hb = new HBox(20);
        hb.setPadding(new Insets(10, 25, 10, 25));
        for (Manga m : mangas) {
            hb.getChildren().add(crearTarjetaManga(m));
        }

        ScrollPane sp = new ScrollPane(hb);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPannable(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");

        row.getChildren().addAll(lbl, sp);
        return row;
    }

    private VBox crearTarjetaManga(Manga m) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setCursor(Cursor.HAND);

        ImageView iv = new ImageView();
        iv.setFitWidth(160); iv.setFitHeight(230);

        if (m.getUrlPortada() != null) {
            iv.setImage(new Image(m.getUrlPortada(), 160, 230, true, true, true));
        }

        Rectangle clip = new Rectangle(160, 230);
        clip.setArcWidth(15); clip.setArcHeight(15);
        iv.setClip(clip);

        card.setOnMouseEntered(e -> card.setScaleX(1.05));
        card.setOnMouseExited(e -> card.setScaleX(1.0));

        Label lbl = new Label(m.getTitulo());
        lbl.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 13px; -fx-font-weight: bold;");
        lbl.setMaxWidth(150); lbl.setAlignment(Pos.CENTER);

        card.getChildren().addAll(iv, lbl);
        card.setOnMouseClicked(e -> {
            try {
                List<String> caps = mangaService.obtenerCapitulos(m.getTitulo());
                FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "capitulos-view.fxml"));
                Node node = loader.load();
                CapitulosController controller = loader.getController();
                controller.setDatos(m.getTitulo(), caps, this, m);
                viewContainer.getChildren().setAll(node);
            } catch (IOException ex) { ex.printStackTrace(); }
        });

        return card;
    }

    @FXML
    private void toggleMenu() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), drawerMenu);
        transition.setToX(menuVisible ? -MENU_WIDTH : 0);
        menuVisible = !menuVisible;
        transition.play();
    }

    @FXML public void abrirBiblioteca() { abrirExplorar(); }
    public MangaService getMangaService() { return mangaService; }
    public StackPane getViewContainer() { return viewContainer; }
    public void setCurrentController(Object controller) { }
}