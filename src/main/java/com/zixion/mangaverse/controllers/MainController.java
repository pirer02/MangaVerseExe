package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.models.Musica;
import com.zixion.mangaverse.services.MangaService;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainController {

    @FXML private VBox drawerMenu;
    @FXML private StackPane viewContainer;
    @FXML private TextField searchBar;

    // Control del Spinner Global
    @FXML private StackPane loadingOverlay;
    @FXML private ProgressIndicator loadingSpinner;

    private boolean menuVisible = false;
    private final double MENU_WIDTH = 280.0;
    private final MangaService mangaService = new MangaService();
    private final File ARCHIVO_BIBLIOTECA = new File(Main.APP_FOLDER, "biblioteca.json");

    private List<Manga> listaMaestra = new ArrayList<>();

    public static class DatosUsuarioManga {
        public boolean enBiblioteca = false;
        public Set<String> capitulosLeidos = new HashSet<>();
        public String siguienteCapitulo = null;
        public List<Musica> canciones = new ArrayList<>();
    }

    private Map<String, DatosUsuarioManga> datosUsuario = new HashMap<>();

    private final List<String> GENEROS_POOL = Arrays.asList(
            "Shonen", "Accion", "Aventura", "Comedia", "Drama", "Seinen", "Romance", "Isekai", "Deporte", "Chanbara"
    );

    @FXML
    public void initialize() {
        if(loadingOverlay != null) loadingOverlay.setVisible(false);

        cargarBiblioteca();
        cargarDatosYMostrarInicio();

        if (searchBar != null) {
            searchBar.textProperty().addListener((obs, old, newText) -> {
                if (newText == null || newText.trim().isEmpty()) {
                    abrirInicio();
                } else {
                    ejecutarBusqueda(newText.trim().toLowerCase());
                }
            });
        }
    }

    public void setCargando(boolean cargando) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(cargando);
        }
    }

    // --- CARGA DE DATOS ---

    private void cargarBiblioteca() {
        if (ARCHIVO_BIBLIOTECA.exists()) {
            try {
                String contenido = Files.readString(ARCHIVO_BIBLIOTECA.toPath());
                datosUsuario.clear();
                if (contenido.trim().startsWith("[")) {
                    JSONArray jsonArray = new JSONArray(contenido);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        String titulo = jsonArray.getString(i);
                        DatosUsuarioManga datos = new DatosUsuarioManga();
                        datos.enBiblioteca = true;
                        datosUsuario.put(titulo, datos);
                    }
                } else {
                    JSONObject json = new JSONObject(contenido);
                    for (String titulo : json.keySet()) {
                        JSONObject dataJson = json.getJSONObject(titulo);
                        DatosUsuarioManga datos = new DatosUsuarioManga();
                        datos.enBiblioteca = dataJson.optBoolean("enBiblioteca", false);
                        datos.siguienteCapitulo = dataJson.optString("siguienteCapitulo", null);
                        if (datos.siguienteCapitulo != null && datos.siguienteCapitulo.isEmpty()) datos.siguienteCapitulo = null;
                        JSONArray leidosArr = dataJson.optJSONArray("capitulosLeidos");
                        if (leidosArr != null) {
                            for (int i = 0; i < leidosArr.length(); i++) datos.capitulosLeidos.add(leidosArr.getString(i));
                        }
                        JSONArray musicaArr = dataJson.optJSONArray("musica");
                        if (musicaArr != null) {
                            for (int i = 0; i < musicaArr.length(); i++) {
                                JSONObject mObj = musicaArr.getJSONObject(i);
                                datos.canciones.add(new Musica(mObj.getString("nombre"), mObj.getString("archivo")));
                            }
                        }
                        datosUsuario.put(titulo, datos);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void guardarBiblioteca() {
        try {
            JSONObject jsonPrincipal = new JSONObject();
            for (Map.Entry<String, DatosUsuarioManga> entry : datosUsuario.entrySet()) {
                if (entry.getValue().enBiblioteca || !entry.getValue().capitulosLeidos.isEmpty() || !entry.getValue().canciones.isEmpty()) {
                    JSONObject dataJson = new JSONObject();
                    dataJson.put("enBiblioteca", entry.getValue().enBiblioteca);
                    dataJson.put("siguienteCapitulo", entry.getValue().siguienteCapitulo);
                    dataJson.put("capitulosLeidos", new JSONArray(entry.getValue().capitulosLeidos));
                    if (!entry.getValue().canciones.isEmpty()) {
                        JSONArray musicaArr = new JSONArray();
                        for (Musica m : entry.getValue().canciones) {
                            JSONObject mObj = new JSONObject();
                            mObj.put("nombre", m.getNombre());
                            mObj.put("archivo", m.getNombreArchivo());
                            musicaArr.put(mObj);
                        }
                        dataJson.put("musica", musicaArr);
                    }
                    jsonPrincipal.put(entry.getKey(), dataJson);
                }
            }
            Files.writeString(ARCHIVO_BIBLIOTECA.toPath(), jsonPrincipal.toString());
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void guardarDatosGlobales() { guardarBiblioteca(); }

    public boolean isCapituloLeido(String mangaTitulo, String capitulo) {
        DatosUsuarioManga datos = datosUsuario.get(mangaTitulo);
        return datos != null && datos.capitulosLeidos.contains(capitulo);
    }

    public void registrarLectura(String mangaTitulo, String capituloLeido, String proximoCapitulo) {
        DatosUsuarioManga datos = datosUsuario.computeIfAbsent(mangaTitulo, k -> new DatosUsuarioManga());
        datos.capitulosLeidos.add(capituloLeido);
        datos.siguienteCapitulo = proximoCapitulo;
        guardarBiblioteca();
    }

    public DatosUsuarioManga getDatosManga(String titulo) { return datosUsuario.get(titulo); }

    private void cargarDatosYMostrarInicio() {
        setCargando(true);
        Task<List<Manga>> task = new Task<>() {
            @Override
            protected List<Manga> call() throws Exception {
                List<Manga> mangasServidor = mangaService.obtenerMangasDesdeServidor();
                for (Manga m : mangasServidor) {
                    Manga info = mangaService.obtenerInfoManga(m.getTitulo().replace(" ", "_"));
                    m.generos = info.generos; m.sinopsis = info.sinopsis; m.estado = info.estado; m.tipo = info.tipo;
                }
                return mangasServidor;
            }
        };
        task.setOnSucceeded(e -> {
            this.listaMaestra = task.getValue();
            abrirInicio();
        });
        task.setOnFailed(e -> {
            setCargando(false);
            e.getSource().getException().printStackTrace();
        });
        new Thread(task).start();
    }

    // --- NAVEGACIÓN Y VISTAS ---

    @FXML
    public void abrirInicio() {
        if (menuVisible) toggleMenu();

        setCargando(true);
        // Usamos PauseTransition para dar tiempo al thread de UI de mostrar el spinner
        PauseTransition pause = new PauseTransition(Duration.millis(50));
        pause.setOnFinished(e -> construirVistaInicio());
        pause.play();
    }

    private void construirVistaInicio() {
        // Lista para rastrear todas las imágenes que vamos a crear
        List<Image> imagenesPendientes = new ArrayList<>();

        VBox mainLayout = new VBox(35);
        mainLayout.setPadding(new Insets(20, 0, 40, 0));
        mainLayout.setStyle("-fx-background-color: #141414;");
        ScrollPane scrollVertical = new ScrollPane(mainLayout);
        scrollVertical.setFitToWidth(true);
        scrollVertical.setStyle("-fx-background: #141414; -fx-background-color: #141414; -fx-border-color: transparent;");

        List<Manga> continuar = listaMaestra.stream().filter(m -> {
            DatosUsuarioManga d = datosUsuario.get(m.getTitulo());
            return d != null && d.siguienteCapitulo != null;
        }).collect(Collectors.toList());
        if (!continuar.isEmpty()) {
            mainLayout.getChildren().add(crearFilaHorizontal("Continuar Leyendo", continuar, imagenesPendientes));
        }

        List<String> generosCopia = new ArrayList<>(GENEROS_POOL);
        Collections.shuffle(generosCopia);
        for (String gen : generosCopia.subList(0, Math.min(6, generosCopia.size()))) {
            List<Manga> filtrados = listaMaestra.stream().filter(m -> m.generos != null && m.generos.stream().anyMatch(g -> g.equalsIgnoreCase(gen))).collect(Collectors.toList());
            if (!filtrados.isEmpty()) {
                mainLayout.getChildren().add(crearFilaHorizontal(gen, filtrados, imagenesPendientes));
            }
        }
        viewContainer.getChildren().setAll(scrollVertical);

        // Ahora esperamos a que todas las imágenes estén cargadas (progress == 1.0)
        esperarCargaImagenes(imagenesPendientes, () -> setCargando(false));
    }

    @FXML
    public void abrirBiblioteca() {
        if (menuVisible) toggleMenu();

        setCargando(true);
        PauseTransition pause = new PauseTransition(Duration.millis(50));
        pause.setOnFinished(e -> construirVistaBiblioteca());
        pause.play();
    }

    private void construirVistaBiblioteca() {
        List<Image> imagenesPendientes = new ArrayList<>();

        FlowPane grid = new FlowPane();
        grid.setHgap(20); grid.setVgap(25);
        grid.setPadding(new Insets(30));
        grid.setStyle("-fx-background-color: #141414;");
        Label titulo = new Label("Mi Biblioteca");
        titulo.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold; -fx-padding: 0 0 20 0;");
        VBox layoutBiblioteca = new VBox(10);
        layoutBiblioteca.setPadding(new Insets(20));
        layoutBiblioteca.setStyle("-fx-background-color: #141414;");
        layoutBiblioteca.getChildren().add(titulo);

        List<Manga> mangasConMusica = listaMaestra.stream().filter(m -> {
            DatosUsuarioManga d = datosUsuario.get(m.getTitulo());
            return d != null && !d.canciones.isEmpty();
        }).collect(Collectors.toList());

        if (!mangasConMusica.isEmpty()) {
            // Pasamos la lista de rastreo
            VBox rowMusica = crearFilaHorizontal("♫ Mangas con Ambiente", mangasConMusica, imagenesPendientes);
            Label subtitulo = new Label("(Haz clic en un manga para gestionar su música)");
            subtitulo.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px; -fx-padding: 0 0 0 25;");
            layoutBiblioteca.getChildren().addAll(rowMusica, subtitulo);
        }

        List<Manga> misMangas = listaMaestra.stream().filter(m -> {
            DatosUsuarioManga d = datosUsuario.get(m.getTitulo());
            return d != null && d.enBiblioteca;
        }).collect(Collectors.toList());

        if (misMangas.isEmpty() && mangasConMusica.isEmpty()) {
            Label emptyLabel = new Label("No has añadido mangas a tu biblioteca aún.");
            emptyLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 16px;");
            grid.getChildren().add(emptyLabel);
        } else {
            for (Manga m : misMangas) grid.getChildren().add(crearTarjetaManga(m, imagenesPendientes));
        }
        layoutBiblioteca.getChildren().add(grid);
        ScrollPane finalScroll = new ScrollPane(layoutBiblioteca);
        finalScroll.setFitToWidth(true);
        finalScroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");
        viewContainer.getChildren().setAll(finalScroll);

        // Esperamos imágenes
        esperarCargaImagenes(imagenesPendientes, () -> setCargando(false));
    }

    private void ejecutarBusqueda(String query) {
        setCargando(true);
        PauseTransition pause = new PauseTransition(Duration.millis(50));
        pause.setOnFinished(e -> {
            List<Image> imagenesPendientes = new ArrayList<>();
            FlowPane grid = new FlowPane();
            grid.setHgap(20); grid.setVgap(25);
            grid.setPadding(new Insets(30));
            grid.setStyle("-fx-background-color: #141414;");
            for (Manga m : listaMaestra) {
                if (m.getTitulo().toLowerCase().contains(query)) {
                    grid.getChildren().add(crearTarjetaManga(m, imagenesPendientes));
                }
            }
            ScrollPane scroll = new ScrollPane(grid);
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");
            viewContainer.getChildren().setAll(scroll);

            esperarCargaImagenes(imagenesPendientes, () -> setCargando(false));
        });
        pause.play();
    }

    /**
     * MÉTODO CLAVE: Recibe una lista de objetos Image y un Runnable.
     * Solo ejecuta el Runnable cuando todas las imágenes han completado su carga (o fallado).
     */
    private void esperarCargaImagenes(List<Image> imagenes, Runnable alTerminar) {
        if (imagenes == null || imagenes.isEmpty()) {
            alTerminar.run();
            return;
        }

        // Contador atómico para saber cuántas faltan
        AtomicInteger pendientes = new AtomicInteger(imagenes.size());

        for (Image img : imagenes) {
            // Si ya cargó (cache) o falló, restamos uno inmediatamente
            if (img.getProgress() == 1.0 || img.isError()) {
                if (pendientes.decrementAndGet() == 0) {
                    alTerminar.run();
                }
            } else {
                // Si está cargando, ponemos un listener
                img.progressProperty().addListener(new ChangeListener<Number>() {
                    @Override
                    public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                        if (newValue.doubleValue() == 1.0) {
                            // Carga completa
                            if (pendientes.decrementAndGet() == 0) {
                                Platform.runLater(alTerminar);
                            }
                            // Importante: remover listener para no fugar memoria
                            img.progressProperty().removeListener(this);
                        }
                    }
                });

                // También escuchamos errores por si la imagen no existe (404)
                img.errorProperty().addListener((obs, old, isError) -> {
                    if (isError) {
                        if (pendientes.decrementAndGet() == 0) {
                            Platform.runLater(alTerminar);
                        }
                    }
                });
            }
        }
    }

    public void irACapitulos(Manga m) {
        setCargando(true);
        Task<List<String>> fetchTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return mangaService.obtenerCapitulos(m.getTitulo(), m);
            }
        };
        fetchTask.setOnSucceeded(evt -> {
            try {
                List<String> caps = fetchTask.getValue();
                FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "capitulos-view.fxml"));
                Node node = loader.load();
                CapitulosController controller = loader.getController();
                controller.setDatos(m.getTitulo(), caps, this, m);
                viewContainer.getChildren().setAll(node);
            } catch (IOException ex) { ex.printStackTrace(); }
            finally { setCargando(false); }
        });
        fetchTask.setOnFailed(e -> setCargando(false));
        new Thread(fetchTask).start();
    }

    private void abrirCapituloDirecto(Manga m, String nombreCapitulo) {
        setCargando(true);
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception { return mangaService.obtenerCapitulos(m.getTitulo(), m); }
        };
        task.setOnSucceeded(e -> {
            try {
                List<String> capitulos = task.getValue();
                String capBuscado = nombreCapitulo.endsWith(".cbz") ? nombreCapitulo : nombreCapitulo + ".cbz";
                List<String> listaNormalizada = capitulos.stream().map(c -> c.endsWith(".cbz") ? c : c + ".cbz").collect(Collectors.toList());
                int index = listaNormalizada.indexOf(capBuscado);
                if (index != -1) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "lector-view.fxml"));
                    Node lectorNode = loader.load();
                    LectorController controller = loader.getController();
                    setCurrentController(controller);
                    controller.inicializarLector(listaNormalizada, index, m, this);
                    viewContainer.getChildren().setAll(lectorNode);
                } else { irACapitulos(m); }
            } catch (Exception ex) { ex.printStackTrace(); }
            finally { setCargando(false); }
        });
        task.setOnFailed(e -> setCargando(false));
        new Thread(task).start();
    }

    // MODIFICADO: Ahora acepta la lista de rastreo
    private VBox crearTarjetaManga(Manga m, List<Image> trackerImagenes) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(160, 230);
        ImageView iv = new ImageView();
        iv.setFitWidth(160); iv.setFitHeight(230);

        if (m.getUrlPortada() != null) {
            // true, true, true -> backgroundLoading = true
            Image img = new Image(m.getUrlPortada(), 160, 230, true, true, true);
            iv.setImage(img);
            // Añadimos la imagen al tracker para esperar por ella
            if (trackerImagenes != null) {
                trackerImagenes.add(img);
            }
        }

        Rectangle clip = new Rectangle(160, 230);
        clip.setArcWidth(15); clip.setArcHeight(15);
        iv.setClip(clip);
        iv.setCursor(Cursor.HAND);
        iv.setOnMouseClicked(e -> irACapitulos(m));
        Button btnAdd = new Button();
        DatosUsuarioManga datos = datosUsuario.get(m.getTitulo());
        boolean enBiblio = datos != null && datos.enBiblioteca;
        configurarEstiloBotonBiblio(btnAdd, enBiblio);
        StackPane.setAlignment(btnAdd, Pos.TOP_RIGHT);
        StackPane.setMargin(btnAdd, new Insets(5));
        btnAdd.setOnAction(e -> { toggleBiblioteca(m, btnAdd); e.consume(); });
        imageContainer.getChildren().addAll(iv, btnAdd);
        if (datos != null && datos.siguienteCapitulo != null) {
            String capNum = extraerNumeroCapitulo(datos.siguienteCapitulo);
            Label lblNext = new Label(capNum);
            lblNext.setStyle("-fx-background-color: rgba(50, 50, 50, 0.9); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #777; -fx-border-radius: 4; -fx-border-width: 1;");
            lblNext.setOnMouseEntered(e -> lblNext.setStyle("-fx-background-color: #e50914; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #e50914; -fx-border-radius: 4; -fx-border-width: 1;"));
            lblNext.setOnMouseExited(e -> lblNext.setStyle("-fx-background-color: rgba(50, 50, 50, 0.9); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #777; -fx-border-radius: 4; -fx-border-width: 1;"));
            lblNext.setOnMouseClicked(e -> { abrirCapituloDirecto(m, datos.siguienteCapitulo); e.consume(); });
            StackPane.setAlignment(lblNext, Pos.TOP_LEFT);
            StackPane.setMargin(lblNext, new Insets(6));
            imageContainer.getChildren().add(lblNext);
        }
        Label lbl = new Label(m.getTitulo());
        lbl.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 13px; -fx-font-weight: bold;");
        lbl.setMaxWidth(150); lbl.setAlignment(Pos.CENTER);
        lbl.setCursor(Cursor.HAND);
        lbl.setOnMouseClicked(e -> irACapitulos(m));
        card.getChildren().addAll(imageContainer, lbl);
        card.setOnMouseEntered(e -> card.setScaleX(1.05));
        card.setOnMouseExited(e -> card.setScaleX(1.0));
        return card;
    }

    // Método de soporte para crear tarjeta SIN tracking (sobrecarga para compatibilidad si fuera necesaria)
    private VBox crearTarjetaManga(Manga m) {
        return crearTarjetaManga(m, null);
    }

    private String extraerNumeroCapitulo(String nombreArchivo) {
        try {
            Matcher m = Pattern.compile("(\\d+)").matcher(nombreArchivo);
            if (m.find()) return "Cap. " + Integer.parseInt(m.group(1));
        } catch (Exception e) {}
        return "Leer";
    }

    private void configurarEstiloBotonBiblio(Button btn, boolean added) {
        if (added) {
            btn.setText("✔");
            btn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 50; -fx-min-width: 30; -fx-min-height: 30; -fx-cursor: hand;");
        } else {
            btn.setText("+");
            btn.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-background-radius: 50; -fx-min-width: 30; -fx-min-height: 30; -fx-cursor: hand;");
        }
    }

    private void toggleBiblioteca(Manga m, Button btn) {
        DatosUsuarioManga datos = datosUsuario.computeIfAbsent(m.getTitulo(), k -> new DatosUsuarioManga());
        datos.enBiblioteca = !datos.enBiblioteca;
        configurarEstiloBotonBiblio(btn, datos.enBiblioteca);
        mostrarNotificacion(datos.enBiblioteca ? "¡Añadido a tu biblioteca!" : "Eliminado de biblioteca");
        guardarBiblioteca();
    }

    // MODIFICADO: Ahora acepta la lista de rastreo
    private VBox crearFilaHorizontal(String titulo, List<Manga> mangas, List<Image> trackerImagenes) {
        VBox row = new VBox(10);
        Label lbl = new Label(titulo.toUpperCase());
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 0 0 0 25;");
        HBox hb = new HBox(20);
        hb.setPadding(new Insets(10, 25, 10, 25));
        for (Manga m : mangas) {
            hb.getChildren().add(crearTarjetaManga(m, trackerImagenes));
        }
        ScrollPane sp = new ScrollPane(hb);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        row.getChildren().addAll(lbl, sp);
        return row;
    }

    // Sobrecarga para compatibilidad (por si se usa en otro lado sin tracking)
    private VBox crearFilaHorizontal(String titulo, List<Manga> mangas) {
        return crearFilaHorizontal(titulo, mangas, null);
    }

    private void mostrarNotificacion(String mensaje) {
        Label notif = new Label(mensaje);
        notif.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 20;");
        StackPane.setAlignment(notif, Pos.BOTTOM_CENTER);
        StackPane.setMargin(notif, new Insets(0, 0, 50, 0));
        viewContainer.getChildren().add(notif);
        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        pause.setOnFinished(e -> viewContainer.getChildren().remove(notif));
        pause.play();
    }

    @FXML private void toggleMenu() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), drawerMenu);
        transition.setToX(menuVisible ? -MENU_WIDTH : 0);
        menuVisible = !menuVisible;
        transition.play();
    }

    public MangaService getMangaService() { return mangaService; }
    public StackPane getViewContainer() { return viewContainer; }
    public void setCurrentController(Object controller) { }

    @FXML
    private void borrarDatos() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Borrar todos los datos");
        alert.setHeaderText("¿Estás seguro de que quieres reiniciar?");
        alert.setContentText("Esta acción es irreversible:\n\n" +
                "• Se borrará tu biblioteca personal.\n" +
                "• Se eliminarán todos los capítulos descargados.\n" +
                "• Se eliminará la caché de listas.\n" +
                "• Se eliminará toda la música personalizada.\n\n" +
                "El programa quedará como recién instalado.");

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #000000; -fx-border-color: #e50914;");
        dialogPane.getScene().getStylesheets().add(getClass().getResource(Utils.RESOURCES_PATH + "estilos-lista.css").toExternalForm());
        alert.getDialogPane().lookupAll(".label").forEach(node -> {
            if (node instanceof Label) ((Label) node).setStyle("-fx-text-fill: white;");
        });

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) realizarBorradoCompleto();
    }

    private void realizarBorradoCompleto() {
        try {
            if (ARCHIVO_BIBLIOTECA.exists()) Files.delete(ARCHIVO_BIBLIOTECA.toPath());
            File carpetaCapitulos = new File(Main.CAPITULOS_FOLDER);
            File carpetaListas = new File(Main.LISTADO_FOLDER);
            File carpetaMusica = new File(Main.MUSICA_FOLDER);
            borrarDirectorioRecursivo(carpetaCapitulos);
            borrarDirectorioRecursivo(carpetaListas);
            borrarDirectorioRecursivo(carpetaMusica);
            datosUsuario.clear();
            carpetaCapitulos.mkdirs();
            carpetaListas.mkdirs();
            carpetaMusica.mkdirs();
            mostrarNotificacion("Sistema restaurado correctamente.");
            abrirInicio();
        } catch (IOException e) {
            e.printStackTrace();
            mostrarNotificacion("Error al borrar algunos archivos.");
        }
    }

    private void borrarDirectorioRecursivo(File archivo) {
        if (archivo.isDirectory()) {
            File[] archivos = archivo.listFiles();
            if (archivos != null) { for (File f : archivos) borrarDirectorioRecursivo(f); }
        }
        archivo.delete();
    }
}