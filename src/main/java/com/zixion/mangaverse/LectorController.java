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

    public void cargarManga(File archivoCbz, String nombreCap, MainController main) {
        this.mainController = main;
        lblCapitulo.setText(nombreCap);
        contenedorPaginas.getChildren().clear();

        try (ZipFile zipFile = new ZipFile(archivoCbz)) {
            // Usamos un TreeMap para que las páginas se ordenen alfabéticamente (01, 02, 03...)
            TreeMap<String, ZipEntry> entradasOrdenadas = new TreeMap<>();
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && esImagen(entry.getName())) {
                    entradasOrdenadas.put(entry.getName(), entry);
                }
            }

            // Creamos un ImageView por cada página
            for (ZipEntry entry : entradasOrdenadas.values()) {
                try (InputStream is = zipFile.getInputStream(entry)) {
                    Image img = new Image(is);
                    ImageView iv = new ImageView(img);

                    // Ajustar la imagen al ancho del contenedor
                    iv.setPreserveRatio(true);
                    iv.fitWidthProperty().bind(scrollLector.widthProperty().subtract(30));

                    contenedorPaginas.getChildren().add(iv);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean esImagen(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    @FXML private void cerrarLector() {
        mainController.abrirBiblioteca(); // O volver a la vista de capítulos
    }
}