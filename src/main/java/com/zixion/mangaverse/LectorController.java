package com.zixion.mangaverse;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LectorController {
    @FXML private VBox contenedorPaginas;
    @FXML private Label lblCapitulo;
    @FXML private ScrollPane scrollLector;
    private MainController mainController;

    private volatile boolean cargando = false;

    public void cargarManga(File archivoCbz, String nombreCap, MainController main) {
        this.mainController = main;
        lblCapitulo.setText(nombreCap);
        contenedorPaginas.getChildren().clear();
        cargando = true; // Activamos el interruptor

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
                    // Si el interruptor se apaga, salimos del bucle inmediatamente
                    if (!cargando) {
                        System.out.println("Carga detenida por el usuario.");
                        break;
                    }

                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Image img = new Image(is);
                        javafx.application.Platform.runLater(() -> {
                            ImageView iv = new ImageView(img);
                            iv.setPreserveRatio(true);
                            iv.fitWidthProperty().bind(scrollLector.widthProperty().subtract(30));
                            contenedorPaginas.getChildren().add(iv);
                        });
                        Thread.sleep(50);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cargando = false;
            }
        }).start();
    }

    private boolean esImagen(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    public void detenerCarga() {
        this.cargando = false;
    }

    @FXML private void cerrarLector() {
        detenerCarga();
        mainController.abrirBiblioteca(); // O volver a la vista de capítulos
    }
}