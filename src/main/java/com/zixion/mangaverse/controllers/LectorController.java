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
import java.util.HashSet;
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
    @FXML private ComboBox<Musica> comboMusica;
    @FXML private Button btnPlayPause;
    @FXML private ToggleButton btnLupa;
    @FXML private Slider sliderTamanoLupa;
    @FXML private Pane lupaOverlay;
    @FXML private Circle lenteLupa;
    @FXML private Label lblPorcentajeZoom;

    // --- NUEVOS ELEMENTOS DE LA INTERFAZ ---
    @FXML private Slider sliderProgreso;
    @FXML private Label lblProgresoPagina;

    private MainController mainController;
    private List<String> listaNombresCapitulos;
    private int indiceActual;
    private Manga mangaActual;
    private String mangaId;
    private volatile boolean cargando = false;
    private boolean isColorMode = false;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private double zoomFactor = 2.0;
    private final double MAX_ZOOM = 5.0;
    private final double MIN_ZOOM = 1.5;
    private double lastSceneX = 0;
    private double lastSceneY = 0;
    private final double VELOCIDAD_SCROLL_RATON = 4.0;
    private final double PIXELES_POR_TECLA = 150.0;

    // --- VARIABLES DE PROGRESO ---
    private boolean yaMarcadoLeido = false;
    private int totalPaginas = 0;
    private int paginaActual = 0;
    private int ultimaPaginaGuardada = -1; // <--- AÑADE ESTA LÍNEA

    // Controles para que el slider y el scroll no entren en bucle infinito
    private boolean actualizandoDesdeScroll = false;
    private boolean arrastrandoSlider = false;

    public void inicializarLector(List<String> capitulos, int indice, Manga manga, MainController main, boolean isColor) {
        this.listaNombresCapitulos = capitulos;
        this.indiceActual = indice;
        this.mangaActual = manga;
        this.mangaId = manga.getTitulo().replace(" ", "_");
        this.mainController = main;
        this.isColorMode = isColor;

        configurarInputUsuario();
        configurarLupa();
        configurarSliderProgreso(); // <--- AÑADE ESTA LÍNEA AQUÍ
        // Sincroniza la página actual con el MainController y Firebase
        cargarMusicaDisponible();
        cargarCapituloActual();
        actualizarEtiquetaPorcentaje();


        // LÓGICA DE PÁGINAS EXACTAS BASADA EN EL SCROLL
        scrollLector.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (cargando) return;
            double max = scrollLector.getVmax();
            if (max > 0 && totalPaginas > 0) {
                // Traducimos el scroll en el índice de la página (0, 1, 2...)
                paginaActual = (int) Math.round((newVal.doubleValue() / max) * (totalPaginas - 1));

                // Si se está moviendo con la rueda del ratón (no arrastrando la barra) actualizamos la barra visualmente
                // Si se está moviendo con la rueda del ratón (no arrastrando la barra) actualizamos la barra visualmente
                if (!arrastrandoSlider && sliderProgreso != null && lblProgresoPagina != null) {
                    actualizandoDesdeScroll = true;
                    sliderProgreso.setValue(paginaActual);
                    lblProgresoPagina.setText((paginaActual + 1) + " / " + totalPaginas);
                    actualizandoDesdeScroll = false;

                    // OPTIMIZACIÓN: Solo guarda en Firebase si realmente cambiaste de página entera
                    if (paginaActual != ultimaPaginaGuardada) {
                        ultimaPaginaGuardada = paginaActual;
                        mainController.guardarProgresoPagina(mangaActual.getTitulo(), listaNombresCapitulos.get(indiceActual).replace(".cbz", ""), paginaActual, isColorMode);
                    }
                }

                int porcentaje = (int) ((newVal.doubleValue() / max) * 100);

                // Si llegamos al final del capítulo (98%)
                if (porcentaje >= 98 && !yaMarcadoLeido) {
                    yaMarcadoLeido = true;
                    String capLeido = listaNombresCapitulos.get(indiceActual).replace(".cbz", "");
                    String sigCap = (indiceActual + 1 < listaNombresCapitulos.size())
                            ? listaNombresCapitulos.get(indiceActual + 1).replace(".cbz", "") : null;

                    // Cambia a esto:
                    mainController.marcarCapituloComoTerminado(mangaActual.getTitulo(), capLeido, sigCap);

                }
            }
        });
    }

    private void configurarSliderProgreso() {
        if (sliderProgreso != null) {
            sliderProgreso.setMin(0);

            // Detectar inicio de arrastre
            sliderProgreso.setOnMousePressed(e -> arrastrandoSlider = true);

            // Al soltar el mouse, hacemos el salto definitivo al scroll
            sliderProgreso.setOnMouseReleased(e -> {
                arrastrandoSlider = false;
                if (totalPaginas > 1 && scrollLector.getVmax() > 0) {
                    double targetScroll = (sliderProgreso.getValue() / (totalPaginas - 1)) * scrollLector.getVmax();
                    scrollLector.setVvalue(targetScroll);
                }
            });

            // Listener para cambios de valor (clics o arrastre fluido)
            sliderProgreso.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (actualizandoDesdeScroll) return;

                int pagVisual = newVal.intValue();
                if (lblProgresoPagina != null) {
                    lblProgresoPagina.setText((pagVisual + 1) + " / " + totalPaginas);
                }

                // Si el usuario hace clic en la barra sin arrastrar, saltamos de inmediato
                if (!arrastrandoSlider && totalPaginas > 1) {
                    double targetScroll = (newVal.doubleValue() / (totalPaginas - 1)) * scrollLector.getVmax();
                    scrollLector.setVvalue(targetScroll);
                }
            });
        }
    }

    private void cargarCapituloActual() {
        if (loadingOverlay != null) loadingOverlay.setVisible(true);
        detenerCarga();

        yaMarcadoLeido = false;
        totalPaginas = 0;
        paginaActual = 0;
        ultimaPaginaGuardada = -1; // <--- AÑADE ESTA LÍNEA AQUÍ

        if (sliderProgreso != null) sliderProgreso.setValue(0);
        if (lblProgresoPagina != null) lblProgresoPagina.setText("0 / 0");

        String nombreArchivo = listaNombresCapitulos.get(indiceActual);
        String sufijo = isColorMode ? " (Color)" : "";
        lblCapitulo.setText("Leyendo: " + nombreArchivo.replace(".cbz", "") + sufijo);

        // Registra que este es el último capítulo que has abierto (Historial)
        mainController.registrarLectura(mangaActual.getTitulo(), nombreArchivo.replace(".cbz", ""), isColorMode);

        btnAnterior.setDisable(true);
        btnSiguiente.setDisable(true);
        contenedorPaginas.getChildren().clear();
        scrollLector.setVvalue(0);
        Platform.runLater(() -> scrollLector.requestFocus());

        Task<File> downloadTask = new Task<>() {
            @Override protected File call() throws Exception {
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
        downloadTask.setOnFailed(e -> { if (loadingOverlay != null) loadingOverlay.setVisible(false); });
        new Thread(downloadTask).start();
    }

    private void procesarCbz(File archivoCbz) {
        cargando = true;
        new Thread(() -> {
            // 1. Verificación inicial del archivo
            if (!archivoCbz.exists() || archivoCbz.length() == 0) {
                Platform.runLater(() -> {
                    cargando = false;
                    if (loadingOverlay != null) loadingOverlay.setVisible(false);
                    mainController.mostrarNotificacion("Error: Archivo de capítulo corrupto.");
                });
                archivoCbz.delete();
                return;
            }

            try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(archivoCbz)) {
                java.util.TreeMap<String, java.util.zip.ZipEntry> entradasOrdenadas = new java.util.TreeMap<>();
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();

                // 2. Ordenar las páginas (01.jpg, 02.jpg...)
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && esImagen(entry.getName())) {
                        entradasOrdenadas.put(entry.getName(), entry);
                    }
                }

                totalPaginas = entradasOrdenadas.size();

                // 3. Configurar el slider con el total de páginas real
                Platform.runLater(() -> {
                    if (sliderProgreso != null) sliderProgreso.setMax(totalPaginas > 1 ? totalPaginas - 1 : 1);
                    if (lblProgresoPagina != null) lblProgresoPagina.setText("1 / " + totalPaginas);
                });

                // 4. Procesamiento y carga de imágenes
                for (java.util.zip.ZipEntry entry : entradasOrdenadas.values()) {
                    if (!cargando) break;

                    Image img = null;
                    int reintentos = 0;
                    int MAX_REINTENTOS = 3;

                    while (img == null && reintentos < MAX_REINTENTOS) {
                        try (java.io.InputStream is = zipFile.getInputStream(entry)) {
                            byte[] imageBytes = is.readAllBytes();
                            img = new Image(new java.io.ByteArrayInputStream(imageBytes));

                            // Soporte para WebP o formatos que JavaFX no lee nativamente
                            if (img.isError()) {
                                java.awt.image.BufferedImage bImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
                                if (bImage != null) {
                                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                    javax.imageio.ImageIO.write(bImage, "png", baos);
                                    img = new Image(new java.io.ByteArrayInputStream(baos.toByteArray()));
                                }
                            }
                        } catch (Exception e) {
                            reintentos++;
                            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                        }
                    }

                    if (img != null && !img.isError()) {
                        final Image imagenFinal = img;
                        Platform.runLater(() -> {
                            ImageView iv = new ImageView(imagenFinal);
                            iv.setPreserveRatio(true);
                            iv.setSmooth(true);
                            // Ajuste dinámico de ancho respetando el tamaño original o la ventana
                            iv.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                                    () -> Math.min(imagenFinal.getWidth(), scrollLector.getWidth() - 30),
                                    scrollLector.widthProperty()
                            ));
                            contenedorPaginas.getChildren().add(iv);
                        });
                    }
                    Thread.sleep(20);
                }

                // 5. FINALIZACIÓN Y RETOMA DE LECTURA (Lógica Sincronizada)
                Platform.runLater(() -> {
                    cargando = false;
                    if (loadingOverlay != null) loadingOverlay.setVisible(false);

                    String nombreCap = listaNombresCapitulos.get(indiceActual).replace(".cbz", "");
                    // Recuperamos el progreso que MainController bajó de Firebase
                    int paginaGuardada = mainController.obtenerProgreso(mangaActual.getTitulo(), nombreCap, isColorMode);

                    if (paginaGuardada > 0 && totalPaginas > 1) {
                        // Pausa para asegurar que JavaFX haya renderizado y calculado el alto del ScrollPane
                        javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1200));
                        pt.setOnFinished(ev -> {
                            double max = scrollLector.getVmax();
                            double targetScroll = ((double) paginaGuardada / (totalPaginas - 1)) * max;

                            // Actualizamos la posición del scroll
                            scrollLector.setVvalue(targetScroll);

                            // Sincronizamos la variable interna y la interfaz visual
                            this.paginaActual = paginaGuardada;
                            if (sliderProgreso != null) sliderProgreso.setValue(paginaGuardada);
                            if (lblProgresoPagina != null) lblProgresoPagina.setText((paginaGuardada + 1) + " / " + totalPaginas);

                            System.out.println("[Sincro] Retomado con éxito en página: " + (paginaGuardada + 1));
                        });
                        pt.play();
                    }
                });

            } catch (Exception e) {
                System.err.println("Error procesando CBZ: " + e.getMessage());
                archivoCbz.delete();
                Platform.runLater(() -> {
                    cargando = false;
                    if (loadingOverlay != null) loadingOverlay.setVisible(false);
                    mainController.mostrarNotificacion("Ocurrió un error al cargar las páginas.");
                });
            }
        }).start();
    }
    @FXML private void capituloSiguiente() {
        guardarProgresoAntesDeSalir();
        if (indiceActual < listaNombresCapitulos.size() - 1) { indiceActual++; cargarCapituloActual(); }
    }

    @FXML private void capituloAnterior() {
        guardarProgresoAntesDeSalir();
        if (indiceActual > 0) { indiceActual--; cargarCapituloActual(); }
    }

    @FXML private void cerrarLector() {
        guardarProgresoAntesDeSalir();
        detenerMusica();
        detenerCarga();
        mainController.irACapitulos(mangaActual);
    }

    private void guardarProgresoAntesDeSalir() {
        if (!cargando && paginaActual > 0 && !yaMarcadoLeido) {
            String capActual = listaNombresCapitulos.get(indiceActual).replace(".cbz", "");
            // AÑADIDO el isColorMode al final
            mainController.guardarProgresoPagina(mangaActual.getTitulo(), capActual, paginaActual, isColorMode);
        }
        mainController.guardarDatosGlobales();
    }

    private void configurarLupa() {
        sliderTamanoLupa.valueProperty().addListener((obs, oldVal, newVal) -> { lenteLupa.setRadius(newVal.doubleValue()); if (btnLupa.isSelected()) actualizarLupa(); });
        scrollLector.setOnMouseMoved(this::guardarPosicionRaton); scrollLector.setOnMouseDragged(this::guardarPosicionRaton);
        scrollLector.setOnMouseExited(e -> { if (btnLupa.isSelected()) { lupaOverlay.setVisible(false); scrollLector.setCursor(Cursor.DEFAULT); } });
        scrollLector.setOnMouseEntered(e -> { if (btnLupa.isSelected()) { lupaOverlay.setVisible(true); scrollLector.setCursor(Cursor.NONE); } });
    }
    private void guardarPosicionRaton(MouseEvent event) { lastSceneX = event.getSceneX(); lastSceneY = event.getSceneY(); if (btnLupa.isSelected()) actualizarLupa(); }
    private void actualizarLupa() {
        try {
            Point2D localOverlayPoint = lupaOverlay.sceneToLocal(lastSceneX, lastSceneY); lenteLupa.setCenterX(localOverlayPoint.getX()); lenteLupa.setCenterY(localOverlayPoint.getY());
            Point2D contentPoint = contenedorPaginas.sceneToLocal(lastSceneX, lastSceneY);
            if (contentPoint.getY() < 0 || contentPoint.getY() > contenedorPaginas.getHeight()) return;
            double radio = lenteLupa.getRadius(); double anchoCaptura = (radio * 2) / zoomFactor; double altoCaptura = (radio * 2) / zoomFactor;
            double x = contentPoint.getX() - (anchoCaptura / 2); double y = contentPoint.getY() - (altoCaptura / 2);
            if (anchoCaptura <= 0 || altoCaptura <= 0) return;
            SnapshotParameters params = new SnapshotParameters(); params.setFill(Color.BLACK); params.setViewport(new Rectangle2D(x, y, anchoCaptura, altoCaptura));
            WritableImage snapshot = contenedorPaginas.snapshot(params, null); lenteLupa.setFill(new ImagePattern(snapshot)); lupaOverlay.setVisible(true);
        } catch (Exception e) { }
    }
    @FXML private void toggleLupa() {
        boolean activa = btnLupa.isSelected(); lupaOverlay.setVisible(activa);
        if (activa) { scrollLector.setCursor(Cursor.NONE); btnLupa.setStyle("-fx-background-color: #e50914; -fx-text-fill: white; -fx-cursor: hand;"); actualizarLupa(); }
        else { scrollLector.setCursor(Cursor.DEFAULT); btnLupa.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-cursor: hand;"); }
    }
    @FXML private void aumentarZoom() { if (zoomFactor < MAX_ZOOM) { zoomFactor += 0.5; actualizarEtiquetaPorcentaje(); if (btnLupa.isSelected()) actualizarLupa(); } }
    @FXML private void disminuirZoom() { if (zoomFactor > MIN_ZOOM) { zoomFactor -= 0.5; actualizarEtiquetaPorcentaje(); if (btnLupa.isSelected()) actualizarLupa(); } }
    private void actualizarEtiquetaPorcentaje() { int porcentaje = (int) (zoomFactor * 100); lblPorcentajeZoom.setText(porcentaje + "%"); }

    private void configurarInputUsuario() {
        scrollLector.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() != 0) {
                event.consume(); double contenidoAlto = contenedorPaginas.getBoundsInLocal().getHeight(); double visorAlto = scrollLector.getViewportBounds().getHeight();
                double maxScroll = contenidoAlto - visorAlto;
                if (maxScroll > 0) {
                    double desplazamiento = -event.getDeltaY() * VELOCIDAD_SCROLL_RATON; double cambioVvalue = desplazamiento / maxScroll;
                    scrollLector.setVvalue(scrollLector.getVvalue() + cambioVvalue);
                    if (btnLupa.isSelected()) Platform.runLater(this::actualizarLupa);
                }
            }
        });
        scrollLector.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            double maxScroll = contenedorPaginas.getBoundsInLocal().getHeight() - scrollLector.getViewportBounds().getHeight();
            if (maxScroll <= 0) return;
            double cambioVvalue = PIXELES_POR_TECLA / maxScroll;
            if (event.getCode() == KeyCode.UP) { scrollLector.setVvalue(scrollLector.getVvalue() - cambioVvalue); event.consume(); }
            else if (event.getCode() == KeyCode.DOWN) { scrollLector.setVvalue(scrollLector.getVvalue() + cambioVvalue); event.consume(); }
            else if (event.getCode() == KeyCode.SPACE) { scrollLector.setVvalue(scrollLector.getVvalue() + (cambioVvalue * 2)); event.consume(); }
            if (btnLupa.isSelected()) Platform.runLater(this::actualizarLupa);
        });
        scrollLector.setOnMouseClicked(e -> scrollLector.requestFocus());
    }
    private void cargarMusicaDisponible() {
        List<Musica> canciones = mainController.getMusicaManga(mangaActual.getTitulo());
        if (!canciones.isEmpty()) { comboMusica.getItems().setAll(canciones); comboMusica.setVisible(true); btnPlayPause.setVisible(true); comboMusica.setOnAction(e -> { Musica seleccionada = comboMusica.getValue(); if (seleccionada != null) prepararCancion(seleccionada); }); }
        else { comboMusica.setVisible(false); btnPlayPause.setVisible(false); }
    }
    private void prepararCancion(Musica musica) {
        detenerMusica();
        try {
            File file = new File(Main.MUSICA_FOLDER + File.separator + mangaActual.getTitulo().replace(" ", "_"), musica.getNombreArchivo());
            if (file.exists()) { mediaPlayer = new MediaPlayer(new Media(file.toURI().toString())); mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE); mediaPlayer.setVolume(0.5); if (isPlaying) mediaPlayer.play(); else btnPlayPause.setText("▶"); }
        } catch (Exception e) { e.printStackTrace(); }
    }
    @FXML private void toggleMusic() {
        if (mediaPlayer == null && comboMusica.getValue() == null && !comboMusica.getItems().isEmpty()) comboMusica.getSelectionModel().selectFirst();
        if (mediaPlayer != null) { if (isPlaying) { mediaPlayer.pause(); btnPlayPause.setText("▶"); isPlaying = false; } else { mediaPlayer.play(); btnPlayPause.setText("⏸"); isPlaying = true; } }
    }
    private void detenerMusica() { if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; } }
    private boolean esImagen(String name) { String n = name.toLowerCase(); return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"); }
    public void detenerCarga() { this.cargando = false; }




}