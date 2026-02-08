package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.services.MangaService;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class MainController {

    @FXML private VBox drawerMenu;
    @FXML private StackPane viewContainer;
    @FXML private TextField searchBar;

    private boolean menuVisible = false;
    private final double MENU_WIDTH = 280.0;
    private final MangaService mangaService = new MangaService();
    private final File ARCHIVO_BIBLIOTECA = new File(Main.APP_FOLDER, "biblioteca.json");
    private List<Manga> listaMaestra = new ArrayList<>();
    private Set<String> bibliotecaUsuario = new HashSet<>();

    private final List<String> GENEROS_POOL = Arrays.asList(
            "Shonen", "Accion", "Aventura", "Comedia", "Drama", "Seinen", "Romance", "Isekai", "Deporte", "Chanbara"
    );

    @FXML
    public void initialize() {
        cargarBiblioteca();
        cargarDatosYMostrarInicio();

        if (searchBar != null) {
            searchBar.textProperty().addListener((obs, old, newText) -> {
                if (newText == null || newText.trim().isEmpty()) {
                    abrirInicio();
                } else {
                    ejecutarBusqueda(newText.trim().toLowerCase());
                }
            });
        }
    }

    private void cargarBiblioteca() {
        if (ARCHIVO_BIBLIOTECA.exists()) {
            try {
                String contenido = Files.readString(ARCHIVO_BIBLIOTECA.toPath());
                JSONArray jsonArray = new JSONArray(contenido);
                bibliotecaUsuario.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    bibliotecaUsuario.add(jsonArray.getString(i));
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void guardarBiblioteca() {
        try {
            JSONArray jsonArray = new JSONArray(bibliotecaUsuario);
            Files.writeString(ARCHIVO_BIBLIOTECA.toPath(), jsonArray.toString());
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void cargarDatosYMostrarInicio() {
        Task<List<Manga>> task = new Task<>() {
            @Override
            protected List<Manga> call() throws Exception {
                List<Manga> mangasServidor = mangaService.obtenerMangasDesdeServidor();
                for (Manga m : mangasServidor) {
                    Manga info = mangaService.obtenerInfoManga(m.getTitulo().replace(" ", "_"));
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
            abrirInicio();
        });
        new Thread(task).start();
    }

    @FXML
    public void abrirInicio() {
        if (menuVisible) toggleMenu();
        VBox mainLayout = new VBox(35);
        mainLayout.setPadding(new Insets(20, 0, 40, 0));
        mainLayout.setStyle("-fx-background-color: #141414;");

        ScrollPane scrollVertical = new ScrollPane(mainLayout);
        scrollVertical.setFitToWidth(true);
        scrollVertical.setStyle("-fx-background: #141414; -fx-background-color: #141414; -fx-border-color: transparent;");

        List<String> generosCopia = new ArrayList<>(GENEROS_POOL);
        Collections.shuffle(generosCopia);

        for (String gen : generosCopia.subList(0, Math.min(6, generosCopia.size()))) {
            List<Manga> filtrados = listaMaestra.stream()
                    .filter(m -> m.generos != null && m.generos.stream().anyMatch(g -> g.equalsIgnoreCase(gen)))
                    .collect(Collectors.toList());
            if (!filtrados.isEmpty()) mainLayout.getChildren().add(crearFilaHorizontal(gen, filtrados));
        }
        viewContainer.getChildren().setAll(scrollVertical);
    }

    @FXML
    public void abrirBiblioteca() {
        if (menuVisible) toggleMenu();
        FlowPane grid = new FlowPane();
        grid.setHgap(20); grid.setVgap(25);
        grid.setPadding(new Insets(30));
        grid.setStyle("-fx-background-color: #141414;");

        Label titulo = new Label("Mi Biblioteca");
        titulo.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold; -fx-padding: 0 0 20 0;");

        VBox layoutBiblioteca = new VBox(10);
        layoutBiblioteca.setPadding(new Insets(20));
        layoutBiblioteca.setStyle("-fx-background-color: #141414;");
        layoutBiblioteca.getChildren().add(titulo);

        List<Manga> misMangas = listaMaestra.stream()
                .filter(m -> bibliotecaUsuario.contains(m.getTitulo()))
                .collect(Collectors.toList());

        if (misMangas.isEmpty()) {
            Label emptyLabel = new Label("No has añadido mangas a tu biblioteca aún.");
            emptyLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 16px;");
            grid.getChildren().add(emptyLabel);
        } else {
            for (Manga m : misMangas) grid.getChildren().add(crearTarjetaManga(m));
        }

        layoutBiblioteca.getChildren().add(grid);
        ScrollPane finalScroll = new ScrollPane(layoutBiblioteca);
        finalScroll.setFitToWidth(true);
        finalScroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");
        viewContainer.getChildren().setAll(finalScroll);
    }

    // CAMBIO: Ahora es PUBLIC para que LectorController pueda invocarlo
    public void irACapitulos(Manga m) {
        try {
            List<String> caps = mangaService.obtenerCapitulos(m.getTitulo(), m);
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "capitulos-view.fxml"));
            Node node = loader.load();
            CapitulosController controller = loader.getController();
            controller.setDatos(m.getTitulo(), caps, this, m);
            viewContainer.getChildren().setAll(node);
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    private void ejecutarBusqueda(String query) {
        FlowPane grid = new FlowPane();
        grid.setHgap(20); grid.setVgap(25);
        grid.setPadding(new Insets(30));
        grid.setStyle("-fx-background-color: #141414;");

        for (Manga m : listaMaestra) {
            if (m.getTitulo().toLowerCase().contains(query)) {
                grid.getChildren().add(crearTarjetaManga(m));
            }
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");
        viewContainer.getChildren().setAll(scroll);
    }

    private VBox crearFilaHorizontal(String titulo, List<Manga> mangas) {
        VBox row = new VBox(10);
        Label lbl = new Label(titulo.toUpperCase());
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 0 0 0 25;");
        HBox hb = new HBox(20);
        hb.setPadding(new Insets(10, 25, 10, 25));
        for (Manga m : mangas) hb.getChildren().add(crearTarjetaManga(m));
        ScrollPane sp = new ScrollPane(hb);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        row.getChildren().addAll(lbl, sp);
        return row;
    }

    private VBox crearTarjetaManga(Manga m) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(160, 230);
        ImageView iv = new ImageView();
        iv.setFitWidth(160); iv.setFitHeight(230);
        if (m.getUrlPortada() != null) iv.setImage(new Image(m.getUrlPortada(), 160, 230, true, true, true));
        Rectangle clip = new Rectangle(160, 230);
        clip.setArcWidth(15); clip.setArcHeight(15);
        iv.setClip(clip);
        iv.setCursor(Cursor.HAND);
        iv.setOnMouseClicked(e -> irACapitulos(m));

        Button btnAdd = new Button();
        configurarEstiloBotonBiblio(btnAdd, bibliotecaUsuario.contains(m.getTitulo()));
        StackPane.setAlignment(btnAdd, Pos.TOP_RIGHT);
        StackPane.setMargin(btnAdd, new Insets(5));
        btnAdd.setOnAction(e -> { toggleBiblioteca(m, btnAdd); e.consume(); });

        imageContainer.getChildren().addAll(iv, btnAdd);
        Label lbl = new Label(m.getTitulo());
        lbl.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 13px; -fx-font-weight: bold;");
        lbl.setCursor(Cursor.HAND);
        lbl.setOnMouseClicked(e -> irACapitulos(m));
        card.getChildren().addAll(imageContainer, lbl);
        return card;
    }

    private void configurarEstiloBotonBiblio(Button btn, boolean added) {
        if (added) {
            btn.setText("✔");
            btn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 50; -fx-min-width: 30; -fx-min-height: 30; -fx-cursor: hand;");
        } else {
            btn.setText("+");
            btn.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-background-radius: 50; -fx-min-width: 30; -fx-min-height: 30; -fx-cursor: hand;");
        }
    }

    private void toggleBiblioteca(Manga m, Button btn) {
        if (bibliotecaUsuario.contains(m.getTitulo())) {
            bibliotecaUsuario.remove(m.getTitulo());
            configurarEstiloBotonBiblio(btn, false);
        } else {
            bibliotecaUsuario.add(m.getTitulo());
            configurarEstiloBotonBiblio(btn, true);
        }
        guardarBiblioteca();
    }

    private void mostrarNotificacion(String mensaje) {
        Label notif = new Label(mensaje);
        notif.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 20;");
        StackPane.setAlignment(notif, Pos.BOTTOM_CENTER);
        StackPane.setMargin(notif, new Insets(0, 0, 50, 0));
        viewContainer.getChildren().add(notif);
        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        pause.setOnFinished(e -> viewContainer.getChildren().remove(notif));
        pause.play();
    }

    @FXML
    private void toggleMenu() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), drawerMenu);
        transition.setToX(menuVisible ? -MENU_WIDTH : 0);
        menuVisible = !menuVisible;
        transition.play();
    }

    public MangaService getMangaService() { return mangaService; }
    public StackPane getViewContainer() { return viewContainer; }
    public void setCurrentController(Object controller) { }
}