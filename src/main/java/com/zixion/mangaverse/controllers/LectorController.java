package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.models.Musica;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

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

    // --- CONTROLES DE MÚSICA ---
    @FXML private ComboBox<Musica> comboMusica;
    @FXML private Button btnPlayPause;

    private MainController mainController;
    private List<String> listaNombresCapitulos;
    private int indiceActual;
    private Manga mangaActual;
    private String mangaId;
    private volatile boolean cargando = false;

    // VARIABLES DE MEDIA
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    // CONFIGURACIÓN DE VELOCIDAD
    private final double VELOCIDAD_SCROLL_RATON = 4.0;
    private final double PIXELES_POR_TECLA = 150.0;

    public void inicializarLector(List<String> capitulos, int indice, Manga manga, MainController main) {
        this.listaNombresCapitulos = capitulos;
        this.indiceActual = indice;
        this.mangaActual = manga;
        this.mangaId = manga.getTitulo().replace(" ", "_");
        this.mainController = main;

        configurarInputUsuario();
        cargarMusicaDisponible(); // <--- INICIALIZAR MÚSICA
        cargarCapituloActual();
    }

    // --- LÓGICA DE MÚSICA ---

    private void cargarMusicaDisponible() {
        MainController.DatosUsuarioManga datos = mainController.getDatosManga(mangaActual.getTitulo());

        if (datos != null && !datos.canciones.isEmpty()) {
            comboMusica.getItems().setAll(datos.canciones);
            comboMusica.setVisible(true);
            btnPlayPause.setVisible(true);

            // Al seleccionar, cargamos pero no reproducimos automáticamente hasta dar play (o puedes cambiarlo)
            comboMusica.setOnAction(e -> {
                Musica seleccionada = comboMusica.getValue();
                if (seleccionada != null) {
                    prepararCancion(seleccionada);
                }
            });
        } else {
            comboMusica.setVisible(false);
            btnPlayPause.setVisible(false);
        }
    }

    private void prepararCancion(Musica musica) {
        detenerMusica(); // Parar la anterior

        try {
            String mangaId = mangaActual.getTitulo().replace(" ", "_");
            File file = new File(Main.MUSICA_FOLDER + File.separator + mangaId, musica.getNombreArchivo());

            if (file.exists()) {
                Media media = new Media(file.toURI().toString());
                mediaPlayer = new MediaPlayer(media);

                // Loop infinito para ambiente
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                mediaPlayer.setVolume(0.5); // 50% volumen inicial

                // Si ya estábamos reproduciendo, arrancamos la nueva automáticamente
                if (isPlaying) {
                    mediaPlayer.play();
                } else {
                    // Si estaba pausado, preparamos el botón para que diga Play
                    btnPlayPause.setText("▶");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void toggleMusic() {
        // Si no hay player cargado pero hay selección, cargamos la primera
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
            mediaPlayer.dispose(); // IMPORTANTE: Liberar memoria
            mediaPlayer = null;
            // No reseteamos isPlaying aquí para recordar el estado si cambia de canción
        }
    }

    // --- FIN LÓGICA MÚSICA ---

    private void configurarInputUsuario() {
        // 1. Scroll del Ratón
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
                }
            }
        });

        // 2. Scroll con Teclas
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
        });

        scrollLector.setOnMouseClicked(e -> scrollLector.requestFocus());
    }

    private void cargarCapituloActual() {
        detenerCarga();
        String nombreArchivo = listaNombresCapitulos.get(indiceActual);

        lblCapitulo.setText("Leyendo: " + nombreArchivo.replace(".cbz", ""));
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

        if (mainController != null) {
            mainController.registrarLectura(mangaActual.getTitulo(), capituloLeido, siguienteCapitulo);
        }

        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                return mainController.getMangaService().descargarArchivo(mangaId, nombreArchivo);
            }
        };

        downloadTask.setOnSucceeded(e -> {
            File archivoCbz = downloadTask.getValue();
            procesarCbz(archivoCbz);
            btnAnterior.setDisable(indiceActual == 0);
            btnSiguiente.setDisable(indiceActual == listaNombresCapitulos.size() - 1);
            scrollLector.requestFocus();
        });

        new Thread(downloadTask).start();
    }

    private void procesarCbz(File archivoCbz) {
        cargando = true;
        new Thread(() -> {
            try (ZipFile zipFile = new ZipFile(archivoCbz)) {
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
        detenerMusica(); // <--- IMPORTANTE: Paramos la música al salir
        detenerCarga();
        mainController.irACapitulos(mangaActual);
    }
}