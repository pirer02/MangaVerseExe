package com.zixion.mangaverse;

import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.ZipFile;

public class MainController {
    @FXML private FlowPane mangaGrid;
    @FXML private WebView adWebView;
    @FXML private VBox drawerMenu;
    @FXML private StackPane viewContainer;

    private boolean menuVisible = false;

    @FXML
    public void initialize() {
        // 1. Cargar el anuncio desde tu servidor/hosting
        //adWebView.getEngine().load("https://tu-sitio.com/ads-sidebar.html");

        abrirBiblioteca();
    }

    @FXML
    private void toggleMenu() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), drawerMenu);

        if (menuVisible) {
            transition.setToX(-250);
            menuVisible = false;
        } else {
            transition.setToX(0);
            menuVisible = true;
        }
        transition.play();
    }

    @FXML
    private void abrirBiblioteca() {
        loadView("biblioteca-view.fxml");

        // IMPORTANTE: Asegúrate de que mangaGrid no sea null antes de cargar
        if (mangaGrid != null) {
            mangaGrid.getChildren().clear(); // Limpiamos para no duplicar
            cargarMangasDePrueba();
        }

        // Cerramos el menú si estaba abierto (solo si menuVisible es true)
        if (menuVisible) {
            toggleMenu();
        }
    }

    @FXML
    private void abrirExplorar() {
        loadView("explorar-view.fxml");
        toggleMenu();
    }

    private void loadView(String fxml) {
        try {
            Node node = FXMLLoader.load(Objects.requireNonNull(getClass().getResource(fxml)));
            viewContainer.getChildren().setAll(node);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void cargarMangasDePrueba() {
        // Obtenemos la ruta raíz del proyecto dinámicamente
        String rutaRaiz = System.getProperty("\"C:\\Users\\javsa\\OneDrive\\Documentos\\Javier\\Proyectos\\Mangas\"");
        File carpeta = new File(rutaRaiz);

        // Filtramos solo los archivos que terminan en .cbz
        File[] archivos = carpeta.listFiles((dir, name) -> name.toLowerCase().endsWith(".cbz"));

        if (archivos != null && mangaGrid != null) {
            mangaGrid.getChildren().clear(); // Evita duplicados
            for (File f : archivos) {
                Image portada = extraerPortada(f);
                // Quitamos la extensión .cbz del nombre para el título
                VBox card = crearMangaCard(f.getName().replace(".cbz", ""), portada);
                mangaGrid.getChildren().add(card);
            }
        } else {
            System.out.println("No se encontraron archivos .cbz o mangaGrid es null");
        }
    }

    private VBox crearMangaCard(String titulo, Image portada) {
        VBox card = new VBox(10);
        card.setStyle("-fx-alignment: center; " +
                "-fx-padding: 10; " +
                "-fx-background-color: white; " +
                "-fx-background-radius: 8; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        card.setPrefWidth(180);

        ImageView iv = new ImageView(portada);
        iv.setFitWidth(160);
        iv.setFitHeight(230); // Altura estándar para portadas de manga
        iv.setPreserveRatio(true);

        Label lbl = new Label(titulo);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-text-alignment: center;");
        lbl.setMaxWidth(160);

        card.getChildren().addAll(iv, lbl);

        // Efecto visual al pasar el ratón
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle() + "-fx-background-color: #e8f4fd;"));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle() + "-fx-background-color: white;"));

        return card;
    }

    private Image extraerPortada(File archivoCbz) {
        try (ZipFile zipFile = new ZipFile(archivoCbz)) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory() && isImage(entry.getName()))
                    .findFirst()
                    .map(entry -> {
                        try {
                            return new Image(zipFile.getInputStream(entry));
                        } catch (IOException e) { return null; }
                    }).orElse(null);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isImage(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg");
    }
}