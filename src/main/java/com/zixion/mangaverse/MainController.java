package com.zixion.mangaverse;

import javafx.animation.TranslateTransition;
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
import java.util.Objects;
import java.util.zip.ZipFile;

public class MainController {
    @FXML private VBox drawerMenu;
    @FXML private StackPane viewContainer;
    @FXML private TextField searchBar; // Referencia al buscador

    private FlowPane mangaGrid;
    private boolean menuVisible = false;
    private final double MENU_WIDTH = 280.0; // Sincronizado con FXML

    @FXML
    public void initialize() {
        abrirBiblioteca();

        // Ejemplo opcional: Detectar cuando el usuario escribe en el buscador
        if (searchBar != null) {
            searchBar.textProperty().addListener((observable, oldValue, newValue) -> {
                System.out.println("Buscando: " + newValue);
                // Aquí podrías filtrar los mangas en tiempo real
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
        loadView("biblioteca-view.fxml");
        Node content = viewContainer.getChildren().get(0);
        mangaGrid = (FlowPane) content.lookup("#mangaGrid");

        if (mangaGrid != null) {
            mangaGrid.getChildren().clear();
            cargarMangasDePrueba();
        }

        if (menuVisible) toggleMenu();
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
        String rutaRaiz = "C:\\Users\\javsa\\OneDrive\\Documentos\\Javier\\Proyectos\\Mangas";
        File carpeta = new File(rutaRaiz);
        File[] archivos = carpeta.listFiles((dir, name) -> name.toLowerCase().endsWith(".cbz"));

        if (archivos != null && mangaGrid != null) {
            for (File f : archivos) {
                Image portada = extraerPortada(f);
                VBox card = crearMangaCard(f.getName().replace(".cbz", ""), portada);
                mangaGrid.getChildren().add(card);
            }
        }
    }

    private VBox crearMangaCard(String titulo, Image portada) {
        VBox card = new VBox(10);
        card.setStyle("-fx-alignment: center; -fx-padding: 15; -fx-background-color: #34495e; " +
                "-fx-background-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 5);");
        card.setPrefWidth(190);

        ImageView iv = new ImageView(portada);
        iv.setFitWidth(160);
        iv.setFitHeight(230);
        iv.setPreserveRatio(true);

        Label lbl = new Label(titulo);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-text-alignment: center;");
        lbl.setMaxWidth(160);

        card.getChildren().addAll(iv, lbl);
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle().replace("#34495e", "#48627a")));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle().replace("#48627a", "#34495e")));

        return card;
    }

    private Image extraerPortada(File archivoCbz) {
        try (ZipFile zipFile = new ZipFile(archivoCbz)) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory() && isImage(entry.getName()))
                    .findFirst()
                    .map(entry -> {
                        try { return new Image(zipFile.getInputStream(entry)); }
                        catch (IOException e) { return null; }
                    }).orElse(null);
        } catch (IOException e) { return null; }
    }

    private boolean isImage(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg");
    }

    @FXML private void abrirExplorar() { if (menuVisible) toggleMenu(); }
}