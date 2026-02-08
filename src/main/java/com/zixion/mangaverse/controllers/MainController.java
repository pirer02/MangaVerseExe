package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main; // Importamos Main para acceder a las rutas constantes
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
import org.json.JSONArray; // Necesario para manejar el JSON

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

    // Archivo donde guardaremos los favoritos
    private final File ARCHIVO_BIBLIOTECA = new File(Main.APP_FOLDER, "biblioteca.json");

    private List<Manga> listaMaestra = new ArrayList<>();
    private Set<String> bibliotecaUsuario = new HashSet<>();

    private final List<String> GENEROS_POOL = Arrays.asList(
            "Shonen", "Accion", "Aventura", "Comedia", "Drama", "Seinen", "Romance", "Isekai", "Deporte", "Chanbara"
    );

    @FXML
    public void initialize() {
        // 1. Cargamos la biblioteca del archivo JSON antes de nada
        cargarBiblioteca();

        cargarDatosYMostrarExplorar();

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

    // --- MÉTODOS DE PERSISTENCIA (JSON) ---

    private void cargarBiblioteca() {
        if (ARCHIVO_BIBLIOTECA.exists()) {
            try {
                String contenido = Files.readString(ARCHIVO_BIBLIOTECA.toPath());
                JSONArray jsonArray = new JSONArray(contenido);
                bibliotecaUsuario.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    bibliotecaUsuario.add(jsonArray.getString(i));
                }
                System.out.println("Biblioteca cargada: " + bibliotecaUsuario.size() + " mangas.");
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Error al cargar la biblioteca.");
            }
        }
    }

    private void guardarBiblioteca() {
        try {
            // Convertimos el Set a JSONArray
            JSONArray jsonArray = new JSONArray(bibliotecaUsuario);
            // Escribimos en el archivo (si no existe, se crea solo)
            Files.writeString(ARCHIVO_BIBLIOTECA.toPath(), jsonArray.toString());
            System.out.println("Biblioteca guardada correctamente.");
        } catch (IOException e) {
            e.printStackTrace();
            mostrarNotificacion("Error al guardar datos");
        }
    }

    // --- LOGICA DE DATOS ---

    private void cargarDatosYMostrarExplorar() {
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
            abrirExplorar();
        });
        new Thread(task).start();
    }

    // --- NAVEGACIÓN ---

    @FXML
    public void abrirInicio() {
        abrirExplorar();
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
            List<Manga> filtrados = listaMaestra.stream()
                    .filter(m -> m.generos != null && m.generos.stream().anyMatch(g -> g.equalsIgnoreCase(gen)))
                    .collect(Collectors.toList());

            if (!filtrados.isEmpty()) {
                mainLayout.getChildren().add(crearFilaHorizontal(gen, filtrados));
            }
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

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #141414; -fx-background-color: #141414; -fx-border-color: transparent;");

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
            for (Manga m : misMangas) {
                grid.getChildren().add(crearTarjetaManga(m));
            }
        }

        layoutBiblioteca.getChildren().add(grid);
        ScrollPane finalScroll = new ScrollPane(layoutBiblioteca);
        finalScroll.setFitToWidth(true);
        finalScroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");

        viewContainer.getChildren().setAll(finalScroll);
    }

    // --- UI GENERATORS ---

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

        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(160, 230);

        ImageView iv = new ImageView();
        iv.setFitWidth(160); iv.setFitHeight(230);
        if (m.getUrlPortada() != null) {
            iv.setImage(new Image(m.getUrlPortada(), 160, 230, true, true, true));
        }

        Rectangle clip = new Rectangle(160, 230);
        clip.setArcWidth(15); clip.setArcHeight(15);
        iv.setClip(clip);

        iv.setCursor(Cursor.HAND);
        iv.setOnMouseClicked(e -> irACapitulos(m));

        // Botón Biblioteca
        Button btnAdd = new Button();
        boolean enBiblio = bibliotecaUsuario.contains(m.getTitulo());

        configurarEstiloBotonBiblio(btnAdd, enBiblio);

        StackPane.setAlignment(btnAdd, Pos.TOP_RIGHT);
        StackPane.setMargin(btnAdd, new Insets(5));

        btnAdd.setOnAction(e -> {
            toggleBiblioteca(m, btnAdd);
            e.consume();
        });

        imageContainer.getChildren().addAll(iv, btnAdd);

        card.setOnMouseEntered(e -> card.setScaleX(1.05));
        card.setOnMouseExited(e -> card.setScaleX(1.0));

        Label lbl = new Label(m.getTitulo());
        lbl.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 13px; -fx-font-weight: bold;");
        lbl.setMaxWidth(150); lbl.setAlignment(Pos.CENTER);
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
            mostrarNotificacion("¡Añadido a tu biblioteca!");
        }
        // 2. Guardamos cambios en JSON cada vez que se toca el botón
        guardarBiblioteca();
    }

    private void mostrarNotificacion(String mensaje) {
        Label notif = new Label(mensaje);
        notif.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 20; -fx-font-size: 14px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 10, 0, 0, 0);");

        StackPane.setAlignment(notif, Pos.BOTTOM_CENTER);
        StackPane.setMargin(notif, new Insets(0, 0, 50, 0));

        notif.setOpacity(0);
        viewContainer.getChildren().add(notif);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), notif);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);

        PauseTransition pause = new PauseTransition(Duration.seconds(2));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), notif);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> viewContainer.getChildren().remove(notif));

        fadeIn.setOnFinished(e -> pause.play());
        pause.setOnFinished(e -> fadeOut.play());

        fadeIn.play();
    }

    private void irACapitulos(Manga m) {
        try {
            List<String> caps = mangaService.obtenerCapitulos(m.getTitulo(), m);
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "capitulos-view.fxml"));
            Node node = loader.load();
            CapitulosController controller = loader.getController();
            controller.setDatos(m.getTitulo(), caps, this, m);
            viewContainer.getChildren().setAll(node);
        } catch (IOException ex) { ex.printStackTrace(); }
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