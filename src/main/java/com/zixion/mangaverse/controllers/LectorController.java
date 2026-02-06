package com.zixion.mangaverse.controllers;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
    private List<String> listaNombresCapitulos; // Nombres de los archivos (.cbz)
    private int indiceActual;
    private String mangaId;
    private volatile boolean cargando = false;

    /**
     * Método principal para iniciar el lector
     */
    public void inicializarLector(List<String> capitulos, int indice, String mangaId, MainController main) {
        this.listaNombresCapitulos = capitulos;
        this.indiceActual = indice;
        this.mangaId = mangaId;
        this.mainController = main;

        cargarCapituloActual();
    }

    private void cargarCapituloActual() {
        detenerCarga();
        String nombreArchivo = listaNombresCapitulos.get(indiceActual);
        lblCapitulo.setText("Leyendo: " + nombreArchivo.replace(".cbz", ""));

        // Bloqueamos botones mientras descarga
        btnAnterior.setDisable(true);
        btnSiguiente.setDisable(true);
        contenedorPaginas.getChildren().clear();
        scrollLector.setVvalue(0);

        // Tarea para descargar y luego procesar el ZIP
        Task<File> downloadTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                return mainController.getMangaService().descargarArchivo(mangaId, nombreArchivo);
            }
        };

        downloadTask.setOnSucceeded(e -> {
            File archivoCbz = downloadTask.getValue();
            procesarCbz(archivoCbz);
            // Habilitar botones según posición
            btnAnterior.setDisable(indiceActual == 0);
            btnSiguiente.setDisable(indiceActual == listaNombresCapitulos.size() - 1);
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

                            // MÁXIMA CALIDAD: No estira si la imagen es pequeña,
                            // pero se adapta si la ventana es más pequeña que la imagen.
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
        mainController.abrirBiblioteca();
    }
}