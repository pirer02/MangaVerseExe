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
    @FXML private Button btnAnterior, btnSiguiente;

    private MainController mainController;
    private List<String> listaNombresCapitulos;
    private int indiceActual;
    private Manga mangaActual;
    private volatile boolean cargando = false;

    public void inicializarLector(List<String> caps, int index, Manga manga, MainController main) {
        this.listaNombresCapitulos = caps;
        this.indiceActual = index;
        this.mangaActual = manga;
        this.mainController = main;
        cargarCapituloActual();
    }

    private void cargarCapituloActual() {
        detenerCarga();
        String file = listaNombresCapitulos.get(indiceActual);
        lblCapitulo.setText("Leyendo: " + file.replace(".cbz", ""));
        btnAnterior.setDisable(true); btnSiguiente.setDisable(true);
        contenedorPaginas.getChildren().clear();
        scrollLector.setVvalue(0);

        Task<File> task = new Task<>() {
            @Override protected File call() throws Exception {
                return mainController.getMangaService().descargarArchivo(mangaActual.getTitulo().replace(" ", "_"), file);
            }
        };
        task.setOnSucceeded(e -> {
            procesarCbz(task.getValue());
            btnAnterior.setDisable(indiceActual == 0);
            btnSiguiente.setDisable(indiceActual == listaNombresCapitulos.size() - 1);
        });
        new Thread(task).start();
    }

    private void procesarCbz(File cbz) {
        cargando = true;
        new Thread(() -> {
            try (ZipFile zf = new ZipFile(cbz)) {
                TreeMap<String, ZipEntry> map = new TreeMap<>();
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry ze = en.nextElement();
                    if (!ze.isDirectory() && esImg(ze.getName())) map.put(ze.getName(), ze);
                }
                for (ZipEntry ze : map.values()) {
                    if (!cargando) break;
                    try (InputStream is = zf.getInputStream(ze)) {
                        Image img = new Image(is);
                        Platform.runLater(() -> {
                            ImageView iv = new ImageView(img); iv.setPreserveRatio(true); iv.setSmooth(true);
                            iv.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                                    () -> Math.min(img.getWidth(), scrollLector.getWidth() - 30), scrollLector.widthProperty()));
                            contenedorPaginas.getChildren().add(iv);
                        });
                        Thread.sleep(40);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    @FXML private void capituloSiguiente() { if (indiceActual < listaNombresCapitulos.size() - 1) { indiceActual++; cargarCapituloActual(); } }
    @FXML private void capituloAnterior() { if (indiceActual > 0) { indiceActual--; cargarCapituloActual(); } }
    private boolean esImg(String n) { String l = n.toLowerCase(); return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp"); }
    public void detenerCarga() { this.cargando = false; }

    @FXML private void cerrarLector() {
        detenerCarga();
        // Vuelve a la vista de capítulos del manga que se estaba leyendo
        mainController.irACapitulos(mangaActual);
    }
}