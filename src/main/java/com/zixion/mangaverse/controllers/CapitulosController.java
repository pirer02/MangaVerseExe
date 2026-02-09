package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.models.Musica;
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

    // NUEVO: Control del Overlay
    @FXML private StackPane loadingOverlay;

    private MainController mainController;
    private List<String> todosLosCapitulos = new ArrayList<>();
    private Manga mangaActual;

    @FXML
    public void initialize() {
        bgImage.setManaged(false);
        bgImage.fitWidthProperty().bind(contenedorPrincipal.widthProperty());
        bgImage.fitHeightProperty().bind(contenedorPrincipal.heightProperty());

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(contenedorPrincipal.widthProperty());
        clip.heightProperty().bind(contenedorPrincipal.heightProperty());
        contenedorPrincipal.setClip(clip);
    }

    public void setDatos(String titulo, List<String> capitulos, MainController main, Manga manga) {
        this.mainController = main;
        this.mangaActual = manga;
        lblTitulo.setText(titulo);

        // Activamos la carga al iniciar la vista
        if (loadingOverlay != null) loadingOverlay.setVisible(true);

        todosLosCapitulos.clear();
        for (String cap : capitulos) {
            todosLosCapitulos.add(cap.replace(".cbz", ""));
        }

        listaCapitulos.getItems().setAll(todosLosCapitulos);
        lblTotalCapitulos.setText(String.valueOf(todosLosCapitulos.size()));

        txtBusqueda.textProperty().addListener((observable, oldValue, newValue) -> {
            filtrarCapitulos(newValue);
        });

        Image imagen = new Image(manga.getUrlPortada(), true);
        portadaImg.setImage(imagen);
        bgImage.setImage(imagen);

        // Cargamos la info asíncrona
        cargarInfoExtra();

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

    private void abrirElLector(String nombreCapituloSeleccionado) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "lector-view.fxml"));
            Node lectorNode = loader.load();
            LectorController controller = loader.getController();
            mainController.setCurrentController(controller);
            List<String> listaArchivosCbz = todosLosCapitulos.stream().map(s -> s + ".cbz").toList();
            String nombreArchivoSeleccionado = nombreCapituloSeleccionado + ".cbz";
            int indice = listaArchivosCbz.indexOf(nombreArchivoSeleccionado);
            String siguienteCapitulo = null;
            if (indice + 1 < listaArchivosCbz.size()) {
                siguienteCapitulo = listaArchivosCbz.get(indice + 1).replace(".cbz", "");
            }
            mainController.registrarLectura(mangaActual.getTitulo(), nombreCapituloSeleccionado, siguienteCapitulo);
            listaCapitulos.refresh();
            controller.inicializarLector(listaArchivosCbz, indice, mangaActual, mainController);
            mainController.getViewContainer().getChildren().setAll(lectorNode);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

        // Cuando termine (éxito), actualizamos la UI y quitamos el spinner
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

        // Si falla, también quitamos el spinner para no bloquear la app
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