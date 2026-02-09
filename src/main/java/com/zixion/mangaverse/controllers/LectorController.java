package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.models.Musica;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;

import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LectorController {
    @FXML private VBox contenedorPaginas;
    @FXML private Label lblCapitulo;
    @FXML private ScrollPane scrollLector;
    @FXML private Button btnAnterior;
    @FXML private Button btnSiguiente;
    @FXML private StackPane loadingOverlay;

    // --- CONTROLES DE MÚSICA ---
    @FXML private ComboBox<Musica> comboMusica;
    @FXML private Button btnPlayPause;

    // --- CONTROLES DE LUPA ---
    @FXML private ToggleButton btnLupa;
    @FXML private Slider sliderTamanoLupa;
    @FXML private Pane lupaOverlay;
    @FXML private Circle lenteLupa;
    @FXML private Label lblPorcentajeZoom;

    private MainController mainController;
    private List<String> listaNombresCapitulos;
    private int indiceActual;
    private Manga mangaActual;
    private String mangaId;
    private volatile boolean cargando = false;

    // CAMBIO: Variable para controlar el modo
    private boolean isColorMode = false;

    // VARIABLES DE MEDIA
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    // VARIABLES DE ZOOM/LUPA
    private double zoomFactor = 2.0;
    private final double MAX_ZOOM = 5.0;
    private final double MIN_ZOOM = 1.5;

    private double lastSceneX = 0;
    private double lastSceneY = 0;

    private final double VELOCIDAD_SCROLL_RATON = 4.0;
    private final double PIXELES_POR_TECLA = 150.0;

    // CAMBIO: Firma actualizada para recibir isColor
    public void inicializarLector(List<String> capitulos, int indice, Manga manga, MainController main, boolean isColor) {
        this.listaNombresCapitulos = capitulos;
        this.indiceActual = indice;
        this.mangaActual = manga;
        this.mangaId = manga.getTitulo().replace(" ", "_");
        this.mainController = main;
        this.isColorMode = isColor; // Guardamos el estado

        configurarInputUsuario();
        configurarLupa();
        cargarMusicaDisponible();
        cargarCapituloActual();

        actualizarEtiquetaPorcentaje();
    }

    private void configurarLupa() {
        sliderTamanoLupa.valueProperty().addListener((obs, oldVal, newVal) -> {
            lenteLupa.setRadius(newVal.doubleValue());
            if (btnLupa.isSelected()) {
                actualizarLupa();
            }
        });

        scrollLector.setOnMouseMoved(this::guardarPosicionRaton);
        scrollLector.setOnMouseDragged(this::guardarPosicionRaton);

        scrollLector.setOnMouseExited(e -> {
            if (btnLupa.isSelected()) {
                lupaOverlay.setVisible(false);
                scrollLector.setCursor(Cursor.DEFAULT);
            }
        });

        scrollLector.setOnMouseEntered(e -> {
            if (btnLupa.isSelected()) {
                lupaOverlay.setVisible(true);
                scrollLector.setCursor(Cursor.NONE);
            }
        });
    }

    private void guardarPosicionRaton(MouseEvent event) {
        lastSceneX = event.getSceneX();
        lastSceneY = event.getSceneY();

        if (btnLupa.isSelected()) {
            actualizarLupa();
        }
    }

    private void actualizarLupa() {
        try {
            Point2D localOverlayPoint = lupaOverlay.sceneToLocal(lastSceneX, lastSceneY);
            lenteLupa.setCenterX(localOverlayPoint.getX());
            lenteLupa.setCenterY(localOverlayPoint.getY());

            Point2D contentPoint = contenedorPaginas.sceneToLocal(lastSceneX, lastSceneY);

            if (contentPoint.getY() < 0 || contentPoint.getY() > contenedorPaginas.getHeight()) {
                return;
            }

            double radio = lenteLupa.getRadius();
            double anchoCaptura = (radio * 2) / zoomFactor;
            double altoCaptura = (radio * 2) / zoomFactor;

            double x = contentPoint.getX() - (anchoCaptura / 2);
            double y = contentPoint.getY() - (altoCaptura / 2);

            if (anchoCaptura <= 0 || altoCaptura <= 0) return;

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.BLACK);
            params.setViewport(new Rectangle2D(x, y, anchoCaptura, altoCaptura));

            WritableImage snapshot = contenedorPaginas.snapshot(params, null);
            lenteLupa.setFill(new ImagePattern(snapshot));
            lupaOverlay.setVisible(true);

        } catch (Exception e) { }
    }

    @FXML
    private void toggleLupa() {
        boolean activa = btnLupa.isSelected();
        lupaOverlay.setVisible(activa);

        if (activa) {
            scrollLector.setCursor(Cursor.NONE);
            btnLupa.setStyle("-fx-background-color: #e50914; -fx-text-fill: white; -fx-cursor: hand;");
            actualizarLupa();
        } else {
            scrollLector.setCursor(Cursor.DEFAULT);
            btnLupa.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-cursor: hand;");
        }
    }

    @FXML
    private void aumentarZoom() {
        if (zoomFactor < MAX_ZOOM) {
            zoomFactor += 0.5;
            actualizarEtiquetaPorcentaje();
            if (btnLupa.isSelected()) actualizarLupa();
        }
    }

    @FXML
    private void disminuirZoom() {
        if (zoomFactor > MIN_ZOOM) {
            zoomFactor -= 0.5;
            actualizarEtiquetaPorcentaje();
            if (btnLupa.isSelected()) actualizarLupa();
        }
    }

    private void actualizarEtiquetaPorcentaje() {
        int porcentaje = (int) (zoomFactor * 100);
        lblPorcentajeZoom.setText(porcentaje + "%");
    }

    private void configurarInputUsuario() {
        scrollLector.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() != 0) {
                event.consume();
                double contenidoAlto = contenedorPaginas.getBoundsInLocal().getHeight();
                double visorAlto = scrollLector.getViewportBounds().getHeight();
                double maxScroll = contenidoAlto - visorAlto;

                if (maxScroll > 0) {
                    double desplazamiento = -event.getDeltaY() * VELOCIDAD_SCROLL_RATON;
                    double cambioVvalue = desplazamiento / maxScroll;
                    scrollLector.setVvalue(scrollLector.getVvalue() + cambioVvalue);

                    if (btnLupa.isSelected()) {
                        Platform.runLater(this::actualizarLupa);
                    }
                }
            }
        });

        scrollLector.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            double contenidoAlto = contenedorPaginas.getBoundsInLocal().getHeight();
            double visorAlto = scrollLector.getViewportBounds().getHeight();
            double maxScroll = contenidoAlto - visorAlto;
            if (maxScroll <= 0) return;
            double cambioVvalue = PIXELES_POR_TECLA / maxScroll;

            if (event.getCode() == KeyCode.UP) {
                scrollLector.setVvalue(scrollLector.getVvalue() - cambioVvalue);
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                scrollLector.setVvalue(scrollLector.getVvalue() + cambioVvalue);
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE) {
                scrollLector.setVvalue(scrollLector.getVvalue() + (cambioVvalue * 2));
                event.consume();
            }

            if (btnLupa.isSelected()) {
                Platform.runLater(this::actualizarLupa);
            }
        });

        scrollLector.setOnMouseClicked(e -> scrollLector.requestFocus());
    }

    private void cargarMusicaDisponible() {
        MainController.DatosUsuarioManga datos = mainController.getDatosManga(mangaActual.getTitulo());
        if (datos != null && !datos.canciones.isEmpty()) {
            comboMusica.getItems().setAll(datos.canciones);
            comboMusica.setVisible(true);
            btnPlayPause.setVisible(true);
            comboMusica.setOnAction(e -> {
                Musica seleccionada = comboMusica.getValue();
                if (seleccionada != null) prepararCancion(seleccionada);
            });
        } else {
            comboMusica.setVisible(false);
            btnPlayPause.setVisible(false);
        }
    }

    private void prepararCancion(Musica musica) {
        detenerMusica();
        try {
            String mangaId = mangaActual.getTitulo().replace(" ", "_");
            File file = new File(Main.MUSICA_FOLDER + File.separator + mangaId, musica.getNombreArchivo());
            if (file.exists()) {
                Media media = new Media(file.toURI().toString());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                mediaPlayer.setVolume(0.5);
                if (isPlaying) mediaPlayer.play();
                else btnPlayPause.setText("▶");
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML private void toggleMusic() {
        if (mediaPlayer == null && comboMusica.getValue() == null && !comboMusica.getItems().isEmpty()) {
            comboMusica.getSelectionModel().selectFirst();
        }
        if (mediaPlayer != null) {
            if (isPlaying) {
                mediaPlayer.pause();
                btnPlayPause.setText("▶");
                isPlaying = false;
            } else {
                mediaPlayer.play();
                btnPlayPause.setText("⏸");
                isPlaying = true;
            }
        }
    }

    private void detenerMusica() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }

    private void cargarCapituloActual() {
        if (loadingOverlay != null) loadingOverlay.setVisible(true);
        detenerCarga();
        String nombreArchivo = listaNombresCapitulos.get(indiceActual);

        // Indicador visual simple si es color
        String sufijo = isColorMode ? " (Color)" : "";
        lblCapitulo.setText("Leyendo: " + nombreArchivo.replace(".cbz", "") + sufijo);

        btnAnterior.setDisable(true);
        btnSiguiente.setDisable(true);
        contenedorPaginas.getChildren().clear();
        scrollLector.setVvalue(0);
        Platform.runLater(() -> scrollLector.requestFocus());

        String capituloLeido = nombreArchivo.replace(".cbz", "");
        String siguienteCapitulo = null;
        if (indiceActual + 1 < listaNombresCapitulos.size()) {
            siguienteCapitulo = listaNombresCapitulos.get(indiceActual + 1).replace(".cbz", "");
        }
        if (mainController != null) mainController.registrarLectura(mangaActual.getTitulo(), capituloLeido, siguienteCapitulo);

        Task<File> downloadTask = new Task<>() {
            @Override protected File call() throws Exception {
                // CAMBIO: Pasamos el flag isColorMode al servicio
                return mainController.getMangaService().descargarArchivo(mangaId, nombreArchivo, isColorMode);
            }
        };
        downloadTask.setOnSucceeded(e -> {
            File archivoCbz = downloadTask.getValue();
            procesarCbz(archivoCbz);
            btnAnterior.setDisable(indiceActual == 0);
            btnSiguiente.setDisable(indiceActual == listaNombresCapitulos.size() - 1);
            scrollLector.requestFocus();
        });
        downloadTask.setOnFailed(e -> {
            if (loadingOverlay != null) loadingOverlay.setVisible(false);
            e.getSource().getException().printStackTrace();
        });
        new Thread(downloadTask).start();
    }

    private void procesarCbz(File archivoCbz) {
        cargando = true;
        new Thread(() -> {
            try (ZipFile zipFile = new ZipFile(archivoCbz)) {
                Platform.runLater(() -> { if (loadingOverlay != null) loadingOverlay.setVisible(false); });
                TreeMap<String, ZipEntry> entradasOrdenadas = new TreeMap<>();
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && esImagen(entry.getName())) {
                        entradasOrdenadas.put(entry.getName(), entry);
                    }
                }
                for (ZipEntry entry : entradasOrdenadas.values()) {
                    if (!cargando) break;
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Image img = new Image(is);
                        Platform.runLater(() -> {
                            ImageView iv = new ImageView(img);
                            iv.setPreserveRatio(true);
                            iv.setSmooth(true);
                            iv.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                                    () -> Math.min(img.getWidth(), scrollLector.getWidth() - 30),
                                    scrollLector.widthProperty()
                            ));
                            contenedorPaginas.getChildren().add(iv);
                        });
                        Thread.sleep(40);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> { if (loadingOverlay != null) loadingOverlay.setVisible(false); });
            }
        }).start();
    }

    @FXML private void capituloSiguiente() {
        if (indiceActual < listaNombresCapitulos.size() - 1) {
            indiceActual++;
            cargarCapituloActual();
        }
    }

    @FXML private void capituloAnterior() {
        if (indiceActual > 0) {
            indiceActual--;
            cargarCapituloActual();
        }
    }

    private boolean esImagen(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    public void detenerCarga() { this.cargando = false; }

    @FXML private void cerrarLector() {
        detenerMusica();
        detenerCarga();
        // Al salir, volvemos a la vista general.
        // Nota: Por defecto volverá al modo "Original". Si quisieras persistir el modo Color,
        // tendrías que modificar irACapitulos en MainController, pero para uso básico esto funciona bien.
        mainController.irACapitulos(mangaActual);
    }
}