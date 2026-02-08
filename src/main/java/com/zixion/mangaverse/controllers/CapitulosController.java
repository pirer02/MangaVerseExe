package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    private MainController mainController;
    private List<String> todosLosCapitulos = new ArrayList<>();
    private Manga mangaActual;

    @FXML
    public void initialize() {
        // Fondo Responsive
        bgImage.setManaged(false);
        bgImage.fitWidthProperty().bind(contenedorPrincipal.widthProperty());
        bgImage.fitHeightProperty().bind(contenedorPrincipal.heightProperty());

        // Clipping para que no se salga de la ventana
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(contenedorPrincipal.widthProperty());
        clip.heightProperty().bind(contenedorPrincipal.heightProperty());
        contenedorPrincipal.setClip(clip);
    }

    public void setDatos(String titulo, List<String> capitulos, MainController main, Manga manga) {
        this.mainController = main;
        this.mangaActual = manga;
        lblTitulo.setText(titulo);

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

    private void abrirElLector(String nombreCapituloSeleccionado) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "lector-view.fxml"));
            Node lectorNode = loader.load();

            LectorController controller = loader.getController();
            mainController.setCurrentController(controller);

            List<String> listaArchivosCbz = todosLosCapitulos.stream()
                    .map(s -> s + ".cbz")
                    .toList();

            String nombreArchivoSeleccionado = nombreCapituloSeleccionado + ".cbz";
            int indice = listaArchivosCbz.indexOf(nombreArchivoSeleccionado);

            // Registro de progreso
            String siguienteCapitulo = null;
            if (indice + 1 < listaArchivosCbz.size()) {
                siguienteCapitulo = listaArchivosCbz.get(indice + 1).replace(".cbz", "");
            }
            mainController.registrarLectura(mangaActual.getTitulo(), nombreCapituloSeleccionado, siguienteCapitulo);

            listaCapitulos.refresh(); // Actualiza visualmente el "LEÍDO"

            controller.inicializarLector(listaArchivosCbz, indice, mangaActual, mainController);
            mainController.getViewContainer().getChildren().setAll(lectorNode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // CORRECCIÓN: El método que busca el FXML es volverAlInicio
    @FXML private void volverAlInicio() {
        mainController.abrirInicio();
    }

    private void filtrarCapitulos(String texto) {
        if (texto == null || texto.isEmpty()) {
            listaCapitulos.getItems().setAll(todosLosCapitulos);
        } else {
            String lowerCaseFilter = texto.toLowerCase();
            List<String> filtrados = todosLosCapitulos.stream()
                    .filter(cap -> cap.toLowerCase().contains(lowerCaseFilter))
                    .toList();
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