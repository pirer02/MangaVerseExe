package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.models.Manga;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
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

    private MainController mainController;
    private List<String> listaNombresCapitulos;
    private int indiceActual;
    private Manga mangaActual;
    private String mangaId;
    private volatile boolean cargando = false;

    // CONFIGURACIÓN DE VELOCIDAD
    // Ratón: Multiplicador de la velocidad base del sistema
    private final double VELOCIDAD_SCROLL_RATON = 4.0;

    // Teclado: Píxeles exactos a mover por pulsación (150px es suave, similar al ratón)
    private final double PIXELES_POR_TECLA = 150.0;

    public void inicializarLector(List<String> capitulos, int indice, Manga manga, MainController main) {
        this.listaNombresCapitulos = capitulos;
        this.indiceActual = indice;
        this.mangaActual = manga;
        this.mangaId = manga.getTitulo().replace(" ", "_");
        this.mainController = main;

        configurarInputUsuario();
        cargarCapituloActual();
    }

    private void configurarInputUsuario() {
        // 1. Scroll del Ratón (Por Píxeles Dinámicos)
        scrollLector.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() != 0) {
                event.consume(); // Anulamos el scroll por defecto

                double contenidoAlto = contenedorPaginas.getBoundsInLocal().getHeight();
                double visorAlto = scrollLector.getViewportBounds().getHeight();
                double maxScroll = contenidoAlto - visorAlto;

                if (maxScroll > 0) {
                    // DeltaY suele ser ~40. Multiplicado por 4.0 da ~160px.
                    double desplazamiento = -event.getDeltaY() * VELOCIDAD_SCROLL_RATON;
                    double cambioVvalue = desplazamiento / maxScroll;
                    scrollLector.setVvalue(scrollLector.getVvalue() + cambioVvalue);
                }
            }
        });

        // 2. Scroll con Teclas (Ahora también por Píxeles Dinámicos)
        scrollLector.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Calculamos cuánto representa 150px en porcentaje para este capítulo específico
            double contenidoAlto = contenedorPaginas.getBoundsInLocal().getHeight();
            double visorAlto = scrollLector.getViewportBounds().getHeight();
            double maxScroll = contenidoAlto - visorAlto;

            if (maxScroll <= 0) return; // Si no hay nada que scrollear, salimos

            // Convertimos los píxeles fijos a porcentaje relativo (0.0 a 1.0)
            double cambioVvalue = PIXELES_POR_TECLA / maxScroll;

            if (event.getCode() == KeyCode.UP) {
                scrollLector.setVvalue(scrollLector.getVvalue() - cambioVvalue);
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                scrollLector.setVvalue(scrollLector.getVvalue() + cambioVvalue);
                event.consume();
            } else if (event.getCode() == KeyCode.SPACE) {
                // Espacio baja el doble de rápido (300px)
                scrollLector.setVvalue(scrollLector.getVvalue() + (cambioVvalue * 2));
                event.consume();
            }
        });

        // Recuperar el foco al hacer clic para que el teclado siga funcionando
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
        detenerCarga();
        mainController.irACapitulos(mangaActual);
    }
}