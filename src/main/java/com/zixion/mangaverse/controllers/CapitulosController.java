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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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

    // Botones de la barra de herramientas
    @FXML private Button btnModoColor;
    @FXML private Button btnOrden; // El nuevo botón de orden

    private MainController mainController;

    // Lista VISUAL (se invierte, se filtra, cambia según lo que ve el usuario)
    private List<String> todosLosCapitulos = new ArrayList<>();

    // Lista MAESTRA (SIEMPRE en orden cronológico 1, 2, 3... para la lógica interna)
    private List<String> listaOriginalAscendente = new ArrayList<>();

    private Manga mangaActual;
    private boolean modoColor = false;
    private boolean ordenDescendente = true; // Por defecto empezamos descendente (lo más nuevo arriba)

    @FXML
    public void initialize() {
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

        txtBusqueda.textProperty().addListener((observable, oldValue, newValue) -> {
            filtrarCapitulos(newValue);
        });

        Image imagen = new Image(manga.getUrlPortada(), true);
        portadaImg.setImage(imagen);
        bgImage.setImage(imagen);

        cargarInfoExtra();
        verificarOpcionColor();

        listaCapitulos.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String itemSeleccionado = listaCapitulos.getSelectionModel().getSelectedItem();
                if (itemSeleccionado != null) {
                    abrirElLector(itemSeleccionado);
                }
            }
        });
        configurarDisenoLista();
    }

    private void actualizarListaInterna(List<String> capitulos) {
        todosLosCapitulos.clear();
        listaOriginalAscendente.clear(); // Limpiamos la maestra

        for (String cap : capitulos) {
            String nombreLimpio = cap.replace(".cbz", "");
            todosLosCapitulos.add(nombreLimpio);
            listaOriginalAscendente.add(nombreLimpio); // Guardamos copia exacta cronológica
        }

        // --- CORRECCIÓN ---
        // Antes poníamos ordenDescendente = true y revertíamos.
        // Ahora lo dejamos en false (Ascendente) y NO revertimos la lista.

        ordenDescendente = false; // Empezamos en orden normal (1, 2, 3...)

        if (btnOrden != null) {
            btnOrden.setText("⬆"); // Icono indicando que estamos en Ascendente
        }

        // NO hacemos Collections.reverse(todosLosCapitulos) aquí.
        // Queremos que se muestre tal cual llegó (1, 2, 3...)

        listaCapitulos.getItems().setAll(todosLosCapitulos);
        lblTotalCapitulos.setText(String.valueOf(todosLosCapitulos.size()));
    }

    @FXML
    private void alternarOrden() {
        ordenDescendente = !ordenDescendente;

        if (btnOrden != null) {
            btnOrden.setText(ordenDescendente ? "⬇" : "⬆");
        }

        // Invertimos la lista visual
        Collections.reverse(todosLosCapitulos);

        // Refrescamos la vista manteniendo el filtro de búsqueda si existe
        filtrarCapitulos(txtBusqueda.getText());
    }

    // Método para forzar recarga (opcional, si quieres añadir un botón de "Refresh")
    public void forzarRecarga() {
        if (loadingOverlay != null) loadingOverlay.setVisible(true);
        Task<List<String>> reloadTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                // true = forzar descarga ignorando caché
                return mainController.getMangaService().obtenerCapitulos(mangaActual.getTitulo(), mangaActual, modoColor, true);
            }
        };
        reloadTask.setOnSucceeded(e -> {
            actualizarListaInterna(reloadTask.getValue());
            if (loadingOverlay != null) loadingOverlay.setVisible(false);
        });
        reloadTask.setOnFailed(e -> {
            if (loadingOverlay != null) loadingOverlay.setVisible(false);
            e.getSource().getException().printStackTrace();
        });
        new Thread(reloadTask).start();
    }

    private void abrirElLector(String nombreCapituloSeleccionado) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "lector-view.fxml"));
            Node lectorNode = loader.load();
            LectorController controller = loader.getController();
            mainController.setCurrentController(controller);

            // --- CORRECCIÓN DE LÓGICA ---
            // Usamos listaOriginalAscendente para la lógica.
            // Esta lista SIEMPRE es [Cap 1, Cap 2, ... Cap Final].
            // Así el index + 1 siempre es el futuro.

            List<String> listaArchivosCbz = listaOriginalAscendente.stream()
                    .map(s -> s + ".cbz")
                    .toList();

            String nombreArchivoSeleccionado = nombreCapituloSeleccionado + ".cbz";
            int indice = listaArchivosCbz.indexOf(nombreArchivoSeleccionado);

            String siguienteCapitulo = null;
            if (indice + 1 < listaArchivosCbz.size()) {
                siguienteCapitulo = listaArchivosCbz.get(indice + 1).replace(".cbz", "");
            }

            mainController.registrarLectura(mangaActual.getTitulo(), nombreCapituloSeleccionado, siguienteCapitulo);
            listaCapitulos.refresh();

            controller.inicializarLector(listaArchivosCbz, indice, mangaActual, mainController, modoColor);

            mainController.getViewContainer().getChildren().setAll(lectorNode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void verificarOpcionColor() {
        Task<Boolean> checkTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
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
            @Override
            protected List<String> call() throws Exception {
                // Recargamos (sin forzar update) con el nuevo modo
                return mainController.getMangaService().obtenerCapitulos(mangaActual.getTitulo(), mangaActual, modoColor, false);
            }
        };

        reloadTask.setOnSucceeded(e -> {
            actualizarListaInterna(reloadTask.getValue());
            loadingOverlay.setVisible(false);
        });

        reloadTask.setOnFailed(e -> {
            loadingOverlay.setVisible(false);
            e.getSource().getException().printStackTrace();
        });

        new Thread(reloadTask).start();
    }

    @FXML
    private void gestionarMusica() {
        MainController.DatosUsuarioManga datos = mainController.getDatosManga(mangaActual.getTitulo());
        if (datos == null) {
            mainController.registrarLectura(mangaActual.getTitulo(), "INIT", null);
            datos = mainController.getDatosManga(mangaActual.getTitulo());
            datos.capitulosLeidos.remove("INIT");
        }

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
        listaCanciones.getItems().setAll(datos.canciones);
        listaCanciones.setPrefHeight(150);
        listaCanciones.setStyle("-fx-control-inner-background: #1a1a1a; -fx-background-color: #1a1a1a;");

        Button btnAdd = new Button("Añadir MP3 (+)");
        Button btnDel = new Button("Eliminar Seleccionada");

        btnAdd.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
        btnDel.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");

        final MainController.DatosUsuarioManga finalDatos = datos;
        btnAdd.setOnAction(e -> {
            if (finalDatos.canciones.size() >= 12) {
                mostrarAlertaNegra("Límite alcanzado", "Has alcanzado el límite de 12 canciones.");
                return;
            }

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
                        finalDatos.canciones.add(nueva);
                        listaCanciones.getItems().add(nueva);
                        mainController.guardarDatosGlobales();

                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        });

        btnDel.setOnAction(e -> {
            Musica selected = listaCanciones.getSelectionModel().getSelectedItem();
            if (selected != null) {
                finalDatos.canciones.remove(selected);
                listaCanciones.getItems().remove(selected);
                String mangaId = mangaActual.getTitulo().replace(" ", "_");
                File file = new File(Main.MUSICA_FOLDER + File.separator + mangaId, selected.getNombreArchivo());
                if(file.exists()) file.delete();
                mainController.guardarDatosGlobales();
            }
        });

        HBox botones = new HBox(10, btnAdd, btnDel);
        botones.setAlignment(Pos.CENTER);

        Label lblLista = new Label("Tus canciones:");
        lblLista.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        content.getChildren().addAll(lblLista, listaCanciones, botones);

        dialogPane.setContent(content);
        ButtonType closeButton = ButtonType.CLOSE;
        dialogPane.getButtonTypes().add(closeButton);
        Node btnCerrar = dialogPane.lookupButton(closeButton);
        btnCerrar.setStyle("-fx-background-color: #444; -fx-text-fill: white;");

        dialog.showAndWait();
    }

    private void mostrarAlertaNegra(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.getDialogPane().setStyle("-fx-background-color: #000000;");
        alert.getDialogPane().lookupAll(".label").forEach(n -> n.setStyle("-fx-text-fill: white;"));
        alert.showAndWait();
    }

    @FXML private void volverAlInicio() {
        mainController.abrirInicio();
    }

    private void filtrarCapitulos(String texto) {
        if (texto == null || texto.isEmpty()) {
            listaCapitulos.getItems().setAll(todosLosCapitulos);
        } else {
            String lowerCaseFilter = texto.toLowerCase();
            List<String> filtrados = todosLosCapitulos.stream().filter(cap -> cap.toLowerCase().contains(lowerCaseFilter)).toList();
            listaCapitulos.getItems().setAll(filtrados);
        }
    }

    private void cargarInfoExtra() {
        Task<Manga> task = new Task<>() {
            @Override
            protected Manga call() throws Exception {
                String mangaId = lblTitulo.getText().replace(" ", "_");
                return mainController.getMangaService().obtenerInfoManga(mangaId);
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

        task.setOnFailed(e -> {
            if(loadingOverlay != null) loadingOverlay.setVisible(false);
            e.getSource().getException().printStackTrace();
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
                    Label btnLeer = new Label(leido ? "LEÍDO" : "LEER");
                    if (leido) {
                        btnLeer.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 10px; -fx-border-color: #2ecc71; -fx-border-radius: 3; -fx-padding: 2 8; -fx-font-weight: bold;");
                    } else {
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