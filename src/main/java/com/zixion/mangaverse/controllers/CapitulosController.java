package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.models.Musica;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CapitulosController {

    @FXML private StackPane contenedorPrincipal;
    @FXML private Label lblTitulo;
    @FXML private ListView<String> listaCapitulos;
    @FXML private ImageView bgImage;
    @FXML private ImageView portadaImg;
    @FXML private Label lblEstado;
    @FXML private Label lblTipo;
    @FXML private Text txtSinopsis;
    @FXML private FlowPane contenedorGeneros;
    @FXML private TextField txtBusqueda;
    @FXML private Label lblTotalCapitulos;
    @FXML private StackPane loadingOverlay;
    @FXML private Button btnModoColor;
    @FXML private Button btnOrden;

    private MainController mainController;
    private List<String> todosLosCapitulos = new ArrayList<>();
    private List<String> listaOriginalAscendente = new ArrayList<>();
    private Manga mangaActual;
    private boolean modoColor = false;
    private boolean ordenDescendente = true;

    @FXML
    public void initialize() {
        // Configuramos el fondo difuminado para que ocupe toda la pantalla
        bgImage.setManaged(false);
        bgImage.fitWidthProperty().bind(contenedorPrincipal.widthProperty());
        bgImage.fitHeightProperty().bind(contenedorPrincipal.heightProperty());
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(contenedorPrincipal.widthProperty());
        clip.heightProperty().bind(contenedorPrincipal.heightProperty());
        contenedorPrincipal.setClip(clip);

        if(btnModoColor != null) {
            btnModoColor.setVisible(false);
            btnModoColor.setManaged(false);
        }
    }

    public void setDatos(String titulo, List<String> capitulos, MainController main, Manga manga) {
        this.mainController = main;
        this.mangaActual = manga;
        lblTitulo.setText(titulo);
        this.modoColor = false;

        if (loadingOverlay != null) loadingOverlay.setVisible(true);

        actualizarListaInterna(capitulos);

        // Listener para el buscador en tiempo real
        txtBusqueda.textProperty().addListener((obs, old, newValue) -> filtrarCapitulos(newValue));

        // Cargar portada y fondo
        Image imagen = new Image(manga.getUrlPortada(), true);
        portadaImg.setImage(imagen);
        bgImage.setImage(imagen);

        cargarInfoExtra();
        verificarOpcionColor();

        // Doble clic para abrir el lector
        listaCapitulos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String itemSeleccionado = listaCapitulos.getSelectionModel().getSelectedItem();
                if (itemSeleccionado != null) abrirElLector(itemSeleccionado);
            }
        });

        configurarDisenoLista();
    }

    @FXML
    private void borrarProgreso() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Borrar Progreso");
        alert.setHeaderText(null);
        alert.setContentText("¿Seguro que quieres borrar todo tu progreso de lectura de este manga?");

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #000000; -fx-border-color: #e50914;");
        dialogPane.lookupAll(".label").forEach(n -> ((Label) n).setStyle("-fx-text-fill: white;"));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            mainController.borrarProgresoManga(mangaActual.getTitulo());
            listaCapitulos.refresh(); // Refrescamos la lista para que desaparezcan las etiquetas "LEÍDO"
        }
    }

    private void actualizarListaInterna(List<String> capitulos) {
        todosLosCapitulos.clear();
        listaOriginalAscendente.clear();
        for (String cap : capitulos) {
            String nombreLimpio = cap.replace(".cbz", "");
            todosLosCapitulos.add(nombreLimpio);
            listaOriginalAscendente.add(nombreLimpio); // Guardamos la original para saber siempre el orden real
        }
        ordenDescendente = false;
        if (btnOrden != null) btnOrden.setText("⬆");
        listaCapitulos.getItems().setAll(todosLosCapitulos);
        lblTotalCapitulos.setText(String.valueOf(todosLosCapitulos.size()));
    }

    @FXML
    private void alternarOrden() {
        ordenDescendente = !ordenDescendente;
        if (btnOrden != null) btnOrden.setText(ordenDescendente ? "⬇" : "⬆");
        Collections.reverse(todosLosCapitulos);
        filtrarCapitulos(txtBusqueda.getText());
    }

    @FXML
    public void forzarRecarga() {
        if (loadingOverlay != null) loadingOverlay.setVisible(true);
        Task<List<String>> reloadTask = new Task<>() {
            @Override protected List<String> call() throws Exception {
                // Forzamos la recarga en segundo plano saltándonos la caché
                return mainController.getMangaService().obtenerCapitulos(mangaActual.getTitulo(), mangaActual, modoColor, true);
            }
        };
        reloadTask.setOnSucceeded(e -> {
            actualizarListaInterna(reloadTask.getValue());
            if (loadingOverlay != null) loadingOverlay.setVisible(false);
        });
        reloadTask.setOnFailed(e -> {
            if (loadingOverlay != null) loadingOverlay.setVisible(false);
        });
        new Thread(reloadTask).start();
    }

    private void abrirElLector(String nombreCapituloSeleccionado) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "lector-view.fxml"));
            Node lectorNode = loader.load();
            LectorController controller = loader.getController();
            mainController.setCurrentController(controller);

            // Buscamos el índice correcto basándonos en la lista ascendente
            List<String> listaArchivosCbz = listaOriginalAscendente.stream().map(s -> s + ".cbz").toList();
            String nombreArchivoSeleccionado = nombreCapituloSeleccionado + ".cbz";
            int indice = listaArchivosCbz.indexOf(nombreArchivoSeleccionado);

            listaCapitulos.refresh();
            controller.inicializarLector(listaArchivosCbz, indice, mangaActual, mainController, modoColor);
            mainController.getViewContainer().getChildren().setAll(lectorNode);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void verificarOpcionColor() {
        Task<Boolean> checkTask = new Task<>() {
            @Override protected Boolean call() throws Exception {
                return mainController.getMangaService().verificarExistenciaColor(mangaActual.getTitulo());
            }
        };
        checkTask.setOnSucceeded(e -> {
            boolean existeColor = checkTask.getValue();
            if (existeColor && btnModoColor != null) {
                btnModoColor.setVisible(true);
                btnModoColor.setManaged(true);
                btnModoColor.setText("Ver a Color 🎨");
                btnModoColor.setStyle("-fx-background-color: linear-gradient(to right, #8e44ad, #c0392b); -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 10;");
            }
        });
        new Thread(checkTask).start();
    }

    @FXML
    private void toggleColorMode() {
        loadingOverlay.setVisible(true);
        modoColor = !modoColor;
        if (modoColor) {
            btnModoColor.setText("Ver Original 📄");
            btnModoColor.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 10;");
        } else {
            btnModoColor.setText("Ver a Color 🎨");
            btnModoColor.setStyle("-fx-background-color: linear-gradient(to right, #8e44ad, #c0392b); -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 10;");
        }

        Task<List<String>> reloadTask = new Task<>() {
            @Override protected List<String> call() throws Exception {
                return mainController.getMangaService().obtenerCapitulos(mangaActual.getTitulo(), mangaActual, modoColor, false);
            }
        };
        reloadTask.setOnSucceeded(e -> { actualizarListaInterna(reloadTask.getValue()); loadingOverlay.setVisible(false); });
        reloadTask.setOnFailed(e -> { loadingOverlay.setVisible(false); });
        new Thread(reloadTask).start();
    }

    @FXML
    private void gestionarMusica() {
        List<Musica> canciones = mainController.getMusicaManga(mangaActual.getTitulo());
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Ambiente Musical - " + mangaActual.getTitulo());
        dialog.setHeaderText("Añade música temática para leer este manga.\n(Máx. 12 canciones - Archivos MP3)");
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #000000; -fx-border-color: #e50914; -fx-border-width: 1;");
        dialogPane.getStylesheets().add(Objects.requireNonNull(getClass().getResource(Utils.RESOURCES_PATH + "estilos-lista.css")).toExternalForm());
        dialogPane.lookupAll(".label").forEach(node -> node.setStyle("-fx-text-fill: white; -fx-font-weight: bold;"));

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        ListView<Musica> listaCanciones = new ListView<>();
        listaCanciones.getItems().setAll(canciones);
        listaCanciones.setPrefHeight(150);
        listaCanciones.setStyle("-fx-control-inner-background: #1a1a1a; -fx-background-color: #1a1a1a;");

        Button btnAdd = new Button("Añadir MP3 (+)");
        Button btnDel = new Button("Eliminar Seleccionada");
        btnAdd.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
        btnDel.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");

        btnAdd.setOnAction(e -> {
            if (canciones.size() >= 12) return;
            TextInputDialog nameDialog = new TextInputDialog();
            nameDialog.setTitle("Nueva Canción");
            nameDialog.setHeaderText("Introduce el nombre de la canción:");
            nameDialog.getDialogPane().setStyle("-fx-background-color: #000000; -fx-border-color: #e50914;");
            nameDialog.getDialogPane().lookupAll(".label").forEach(n -> n.setStyle("-fx-text-fill: white;"));

            nameDialog.showAndWait().ifPresent(nombre -> {
                if (nombre.trim().isEmpty()) return;
                FileChooser fileChooser = new FileChooser();
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archivos MP3", "*.mp3"));
                File file = fileChooser.showOpenDialog(dialogPane.getScene().getWindow());
                if (file != null) {
                    try {
                        String mangaId = mangaActual.getTitulo().replace(" ", "_");
                        File destFolder = new File(Main.MUSICA_FOLDER, mangaId);
                        if (!destFolder.exists()) destFolder.mkdirs();
                        String safeName = System.currentTimeMillis() + "_" + file.getName().replace(" ", "_");
                        File destFile = new File(destFolder, safeName);
                        Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        Musica nueva = new Musica(nombre, safeName);
                        canciones.add(nueva);
                        listaCanciones.getItems().add(nueva);
                        mainController.guardarDatosGlobales();
                    } catch (IOException ex) { ex.printStackTrace(); }
                }
            });
        });

        btnDel.setOnAction(e -> {
            Musica selected = listaCanciones.getSelectionModel().getSelectedItem();
            if (selected != null) {
                canciones.remove(selected);
                listaCanciones.getItems().remove(selected);
                String mangaId = mangaActual.getTitulo().replace(" ", "_");
                new File(Main.MUSICA_FOLDER + File.separator + mangaId, selected.getNombreArchivo()).delete();
                mainController.guardarDatosGlobales();
            }
        });

        HBox botones = new HBox(10, btnAdd, btnDel);
        botones.setAlignment(Pos.CENTER);
        Label lblLista = new Label("Tus canciones:");
        lblLista.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        content.getChildren().addAll(lblLista, listaCanciones, botones);
        dialogPane.setContent(content);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.lookupButton(ButtonType.CLOSE).setStyle("-fx-background-color: #444; -fx-text-fill: white;");
        dialog.showAndWait();
    }

    @FXML
    private void volverAtras() {
        mainController.volverVistaAnterior();
    }

    private void filtrarCapitulos(String texto) {
        if (texto == null || texto.isEmpty()) {
            listaCapitulos.getItems().setAll(todosLosCapitulos);
        } else {
            listaCapitulos.getItems().setAll(todosLosCapitulos.stream().filter(cap -> cap.toLowerCase().contains(texto.toLowerCase())).toList());
        }
    }

    private void cargarInfoExtra() {
        Task<Manga> task = new Task<>() {
            @Override protected Manga call() throws Exception {
                return mainController.getMangaService().obtenerInfoManga(lblTitulo.getText().replace(" ", "_"));
            }
        };
        task.setOnSucceeded(e -> {
            Manga info = task.getValue();
            if (info != null) {
                lblTipo.setText(info.tipo != null ? info.tipo.toUpperCase() : "MANGA");
                txtSinopsis.setText(info.sinopsis);
                String est = info.estado != null ? info.estado.toLowerCase() : "";
                if (est.contains("terminado") || est.contains("finalizado")) {
                    lblEstado.setText("TERMINADO");
                    lblEstado.setStyle("-fx-background-color: #e50914; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 4; -fx-font-weight: bold;");
                } else {
                    lblEstado.setText("EN CURSO");
                    lblEstado.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 4; -fx-font-weight: bold;");
                }
                contenedorGeneros.getChildren().clear();
                if (info.generos != null) {
                    for (String g : info.generos) {
                        Label t = new Label(g);
                        t.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 15; -fx-font-size: 12px;");
                        contenedorGeneros.getChildren().add(t);
                    }
                }
            }
            if(loadingOverlay != null) loadingOverlay.setVisible(false);
        });
        new Thread(task).start();
    }

    private void configurarDisenoLista() {
        listaCapitulos.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox contenedor = new HBox(15);
                    contenedor.setAlignment(Pos.CENTER_LEFT);
                    contenedor.setPadding(new Insets(10, 15, 10, 15));
                    contenedor.getStyleClass().add("fila-capitulo");

                    Label icono = new Label("▶");
                    icono.setStyle("-fx-text-fill: #e50914; -fx-font-size: 16px;");

                    Label nombre = new Label(item);
                    nombre.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    boolean leido = mainController.isCapituloLeido(mangaActual.getTitulo(), item);
                    int progresoP = mainController.obtenerProgreso(mangaActual.getTitulo(), item);

                    Label btnLeer = new Label();
                    if (leido) {
                        btnLeer.setText("LEÍDO");
                        btnLeer.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 10px; -fx-border-color: #2ecc71; -fx-border-radius: 3; -fx-padding: 2 8; -fx-font-weight: bold;");
                    } else if (progresoP > 0) {
                        // AQUÍ MOSTRAMOS LA PÁGINA EXACTA (Como en la app móvil)
                        btnLeer.setText("PÁG " + progresoP);
                        btnLeer.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 10px; -fx-border-color: #f39c12; -fx-border-radius: 3; -fx-padding: 2 8; -fx-font-weight: bold;");
                    } else {
                        btnLeer.setText("LEER");
                        btnLeer.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px; -fx-border-color: #555; -fx-border-radius: 3; -fx-padding: 2 8;");
                    }

                    contenedor.getChildren().addAll(icono, nombre, spacer, btnLeer);
                    setGraphic(contenedor);
                    setStyle("-fx-background-color: transparent; -fx-padding: 5 0; -fx-cursor: hand;");
                }
            }
        });
    }
}