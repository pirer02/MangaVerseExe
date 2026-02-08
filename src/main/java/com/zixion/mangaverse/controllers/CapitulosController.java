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
    @FXML private StackPane contenedorPrincipal; // Asegúrate de que el StackPane raíz en el FXML tenga fx:id="contenedorPrincipal"
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
        // SOLUCIÓN AL PROBLEMA DE PANTALLA:

        // 1. Managed=false: Hace que el layout ignore el tamaño real de la imagen
        // para cálculos, evitando que "empuje" otros elementos.
        bgImage.setManaged(false);

        // 2. Binding: La imagen se estira manualmente al tamaño del panel.
        bgImage.fitWidthProperty().bind(contenedorPrincipal.widthProperty());
        bgImage.fitHeightProperty().bind(contenedorPrincipal.heightProperty());

        // 3. CLIPPING (CRÍTICO): Creamos una máscara de recorte.
        // Esto obliga a que cualquier pixel de la imagen que se salga del
        // tamaño del panel sea invisible y no interfiera con otros elementos.
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
                    abrirElLector(itemSeleccionado + ".cbz");
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

            int indice = listaArchivosCbz.indexOf(nombreCapituloSeleccionado);

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
                lblTipo.setText(info.tipo != null && !info.tipo.isEmpty() ? info.tipo.toUpperCase() : "MANGA");
                txtSinopsis.setText(info.sinopsis != null ? info.sinopsis : "Sin sinopsis disponible.");
                String estadoNormalizado = info.estado != null ? info.estado.toLowerCase() : "";

                if (estadoNormalizado.contains("terminado") || estadoNormalizado.contains("finalizado")) {
                    lblEstado.setText("TERMINADO");
                    lblEstado.setStyle("-fx-background-color: #e50914; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 4; -fx-font-weight: bold;");
                } else {
                    lblEstado.setText("EN CURSO");
                    lblEstado.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 4; -fx-font-weight: bold;");
                }

                contenedorGeneros.getChildren().clear();
                if (info.generos != null) {
                    for (String genero : info.generos) {
                        Label tag = new Label(genero);
                        tag.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 15; -fx-font-size: 12px;");
                        contenedorGeneros.getChildren().add(tag);
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

                    Label btnLeer = new Label("LEER");
                    btnLeer.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px; -fx-border-color: #555; -fx-border-radius: 3; -fx-padding: 2 8;");

                    contenedor.getChildren().addAll(icono, nombre, spacer, btnLeer);
                    setGraphic(contenedor);
                    setStyle("-fx-background-color: transparent; -fx-padding: 5 0; -fx-cursor: hand;");
                }
            }
        });
    }
}