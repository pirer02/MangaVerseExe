package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.services.MangaService;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
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
import org.json.JSONObject; // Necesario para el nuevo formato

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainController {

    @FXML private VBox drawerMenu;
    @FXML private StackPane viewContainer;
    @FXML private TextField searchBar;

    private boolean menuVisible = false;
    private final double MENU_WIDTH = 280.0;
    private final MangaService mangaService = new MangaService();
    private final File ARCHIVO_BIBLIOTECA = new File(Main.APP_FOLDER, "biblioteca.json");

    private List<Manga> listaMaestra = new ArrayList<>();

    // --- NUEVA ESTRUCTURA DE DATOS ---
    // Clase interna para gestionar los datos de cada manga
    public static class DatosUsuarioManga {
        public boolean enBiblioteca = false;
        public Set<String> capitulosLeidos = new HashSet<>();
        public String siguienteCapitulo = null; // El nombre del archivo del siguiente capítulo
    }

    // Mapa principal: Título del Manga -> Datos del Usuario
    private Map<String, DatosUsuarioManga> datosUsuario = new HashMap<>();

    private final List<String> GENEROS_POOL = Arrays.asList(
            "Shonen", "Accion", "Aventura", "Comedia", "Drama", "Seinen", "Romance", "Isekai", "Deporte", "Chanbara"
    );

    @FXML
    public void initialize() {
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

    // --- NUEVA LÓGICA DE PERSISTENCIA (MIGRACIÓN INCLUIDA) ---

    private void cargarBiblioteca() {
        if (ARCHIVO_BIBLIOTECA.exists()) {
            try {
                String contenido = Files.readString(ARCHIVO_BIBLIOTECA.toPath());
                datosUsuario.clear();

                // Detectamos si es el formato antiguo (JSONArray) o el nuevo (JSONObject)
                if (contenido.trim().startsWith("[")) {
                    // MIGRACIÓN: Formato antiguo (solo lista de favoritos)
                    JSONArray jsonArray = new JSONArray(contenido);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        String titulo = jsonArray.getString(i);
                        DatosUsuarioManga datos = new DatosUsuarioManga();
                        datos.enBiblioteca = true;
                        datosUsuario.put(titulo, datos);
                    }
                } else {
                    // Formato nuevo
                    JSONObject json = new JSONObject(contenido);
                    for (String titulo : json.keySet()) {
                        JSONObject dataJson = json.getJSONObject(titulo);
                        DatosUsuarioManga datos = new DatosUsuarioManga();
                        datos.enBiblioteca = dataJson.optBoolean("enBiblioteca", false);
                        datos.siguienteCapitulo = dataJson.optString("siguienteCapitulo", null);
                        if (datos.siguienteCapitulo.isEmpty()) datos.siguienteCapitulo = null;

                        JSONArray leidosArr = dataJson.optJSONArray("capitulosLeidos");
                        if (leidosArr != null) {
                            for (int i = 0; i < leidosArr.length(); i++) {
                                datos.capitulosLeidos.add(leidosArr.getString(i));
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
                // Solo guardamos si está en biblioteca o si ha leído algo
                if (entry.getValue().enBiblioteca || !entry.getValue().capitulosLeidos.isEmpty()) {
                    JSONObject dataJson = new JSONObject();
                    dataJson.put("enBiblioteca", entry.getValue().enBiblioteca);
                    dataJson.put("siguienteCapitulo", entry.getValue().siguienteCapitulo);
                    dataJson.put("capitulosLeidos", new JSONArray(entry.getValue().capitulosLeidos));
                    jsonPrincipal.put(entry.getKey(), dataJson);
                }
            }
            Files.writeString(ARCHIVO_BIBLIOTECA.toPath(), jsonPrincipal.toString());
        } catch (IOException e) { e.printStackTrace(); }
    }

    // --- MÉTODOS PÚBLICOS PARA GESTIÓN DE PROGRESO ---

    public boolean isCapituloLeido(String mangaTitulo, String capitulo) {
        DatosUsuarioManga datos = datosUsuario.get(mangaTitulo);
        return datos != null && datos.capitulosLeidos.contains(capitulo);
    }

    public void registrarLectura(String mangaTitulo, String capituloLeido, String proximoCapitulo) {
        DatosUsuarioManga datos = datosUsuario.computeIfAbsent(mangaTitulo, k -> new DatosUsuarioManga());
        datos.capitulosLeidos.add(capituloLeido);
        datos.siguienteCapitulo = proximoCapitulo; // Puede ser null si terminó
        guardarBiblioteca();
    }

    public DatosUsuarioManga getDatosManga(String titulo) {
        return datosUsuario.get(titulo);
    }

    // --- LÓGICA DE NAVEGACIÓN Y VISTAS ---

    private void cargarDatosYMostrarInicio() {
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
        task.setOnSucceeded(e -> { this.listaMaestra = task.getValue(); abrirInicio(); });
        new Thread(task).start();
    }

    @FXML
    public void abrirInicio() {
        if (menuVisible) toggleMenu();
        VBox mainLayout = new VBox(35);
        mainLayout.setPadding(new Insets(20, 0, 40, 0));
        mainLayout.setStyle("-fx-background-color: #141414;");

        ScrollPane scrollVertical = new ScrollPane(mainLayout);
        scrollVertical.setFitToWidth(true);
        scrollVertical.setStyle("-fx-background: #141414; -fx-background-color: #141414; -fx-border-color: transparent;");

        // Sección: Continuar Leyendo (Opcional, pero útil con la nueva lógica)
        List<Manga> continuar = listaMaestra.stream()
                .filter(m -> {
                    DatosUsuarioManga d = datosUsuario.get(m.getTitulo());
                    return d != null && d.siguienteCapitulo != null;
                })
                .collect(Collectors.toList());

        if (!continuar.isEmpty()) {
            mainLayout.getChildren().add(crearFilaHorizontal("Continuar Leyendo", continuar));
        }

        List<String> generosCopia = new ArrayList<>(GENEROS_POOL);
        Collections.shuffle(generosCopia);
        for (String gen : generosCopia.subList(0, Math.min(6, generosCopia.size()))) {
            List<Manga> filtrados = listaMaestra.stream()
                    .filter(m -> m.generos != null && m.generos.stream().anyMatch(g -> g.equalsIgnoreCase(gen)))
                    .collect(Collectors.toList());
            if (!filtrados.isEmpty()) mainLayout.getChildren().add(crearFilaHorizontal(gen, filtrados));
        }
        viewContainer.getChildren().setAll(scrollVertical);
    }

    @FXML
    public void abrirBiblioteca() {
        if (menuVisible) toggleMenu();
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

        List<Manga> misMangas = listaMaestra.stream()
                .filter(m -> {
                    DatosUsuarioManga d = datosUsuario.get(m.getTitulo());
                    return d != null && d.enBiblioteca;
                })
                .collect(Collectors.toList());

        if (misMangas.isEmpty()) {
            Label emptyLabel = new Label("No has añadido mangas a tu biblioteca aún.");
            emptyLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 16px;");
            grid.getChildren().add(emptyLabel);
        } else {
            for (Manga m : misMangas) grid.getChildren().add(crearTarjetaManga(m));
        }

        layoutBiblioteca.getChildren().add(grid);
        ScrollPane finalScroll = new ScrollPane(layoutBiblioteca);
        finalScroll.setFitToWidth(true);
        finalScroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");
        viewContainer.getChildren().setAll(finalScroll);
    }

    public void irACapitulos(Manga m) {
        try {
            List<String> caps = mangaService.obtenerCapitulos(m.getTitulo(), m);
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "capitulos-view.fxml"));
            Node node = loader.load();
            CapitulosController controller = loader.getController();
            controller.setDatos(m.getTitulo(), caps, this, m);
            viewContainer.getChildren().setAll(node);
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    // Método especial para abrir el lector DIRECTAMENTE desde la tarjeta
    private void abrirCapituloDirecto(Manga m, String nombreCapitulo) {
        // Necesitamos cargar la lista completa para inicializar el LectorController correctamente
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return mangaService.obtenerCapitulos(m.getTitulo(), m);
            }
        };

        task.setOnSucceeded(e -> {
            try {
                List<String> capitulos = task.getValue();
                // Aseguramos que los nombres coincidan (añadiendo .cbz si falta)
                String capBuscado = nombreCapitulo.endsWith(".cbz") ? nombreCapitulo : nombreCapitulo + ".cbz";
                // Normalizamos lista a .cbz
                List<String> listaNormalizada = capitulos.stream()
                        .map(c -> c.endsWith(".cbz") ? c : c + ".cbz")
                        .collect(Collectors.toList());

                int index = listaNormalizada.indexOf(capBuscado);

                if (index != -1) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "lector-view.fxml"));
                    Node lectorNode = loader.load();
                    LectorController controller = loader.getController();
                    setCurrentController(controller);
                    controller.inicializarLector(listaNormalizada, index, m, this);
                    viewContainer.getChildren().setAll(lectorNode);
                } else {
                    // Si no lo encuentra (ej: caché vieja), vamos a la lista normal
                    irACapitulos(m);
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        });
        new Thread(task).start();
    }

    // --- CREACIÓN DE TARJETAS (UI) ---

    private VBox crearTarjetaManga(Manga m) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(160, 230);

        ImageView iv = new ImageView();
        iv.setFitWidth(160); iv.setFitHeight(230);
        if (m.getUrlPortada() != null) iv.setImage(new Image(m.getUrlPortada(), 160, 230, true, true, true));
        Rectangle clip = new Rectangle(160, 230);
        clip.setArcWidth(15); clip.setArcHeight(15);
        iv.setClip(clip);
        iv.setCursor(Cursor.HAND);
        iv.setOnMouseClicked(e -> irACapitulos(m));

        // Botón Biblioteca (Derecha)
        Button btnAdd = new Button();
        DatosUsuarioManga datos = datosUsuario.get(m.getTitulo());
        boolean enBiblio = datos != null && datos.enBiblioteca;
        configurarEstiloBotonBiblio(btnAdd, enBiblio);
        StackPane.setAlignment(btnAdd, Pos.TOP_RIGHT);
        StackPane.setMargin(btnAdd, new Insets(5));
        btnAdd.setOnAction(e -> { toggleBiblioteca(m, btnAdd); e.consume(); });

        imageContainer.getChildren().addAll(iv, btnAdd);

        // --- NUEVO: Botón de "Continuar / Siguiente Capítulo" (Izquierda) ---
        if (datos != null && datos.siguienteCapitulo != null) {
            String capNum = extraerNumeroCapitulo(datos.siguienteCapitulo);
            Label lblNext = new Label(capNum);
            lblNext.setStyle("-fx-background-color: rgba(50, 50, 50, 0.9); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #777; -fx-border-radius: 4; -fx-border-width: 1;");

            // Efecto Hover
            lblNext.setOnMouseEntered(e -> lblNext.setStyle("-fx-background-color: #e50914; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #e50914; -fx-border-radius: 4; -fx-border-width: 1;"));
            lblNext.setOnMouseExited(e -> lblNext.setStyle("-fx-background-color: rgba(50, 50, 50, 0.9); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #777; -fx-border-radius: 4; -fx-border-width: 1;"));

            lblNext.setOnMouseClicked(e -> {
                abrirCapituloDirecto(m, datos.siguienteCapitulo);
                e.consume(); // Evita que se abra la ficha general
            });

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

    private String extraerNumeroCapitulo(String nombreArchivo) {
        // Intenta sacar el número del nombre (ej: "DanDaDan - 014" -> "14")
        try {
            Matcher m = Pattern.compile("(\\d+)").matcher(nombreArchivo);
            if (m.find()) {
                return "Cap. " + Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {}
        return "Leer"; // Fallback si no encuentra número
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

    private VBox crearFilaHorizontal(String titulo, List<Manga> mangas) {
        VBox row = new VBox(10);
        Label lbl = new Label(titulo.toUpperCase());
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 0 0 0 25;");
        HBox hb = new HBox(20);
        hb.setPadding(new Insets(10, 25, 10, 25));
        for (Manga m : mangas) hb.getChildren().add(crearTarjetaManga(m));
        ScrollPane sp = new ScrollPane(hb);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        row.getChildren().addAll(lbl, sp);
        return row;
    }

    private void ejecutarBusqueda(String query) {
        FlowPane grid = new FlowPane();
        grid.setHgap(20); grid.setVgap(25);
        grid.setPadding(new Insets(30));
        grid.setStyle("-fx-background-color: #141414;");
        for (Manga m : listaMaestra) {
            if (m.getTitulo().toLowerCase().contains(query)) {
                grid.getChildren().add(crearTarjetaManga(m));
            }
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");
        viewContainer.getChildren().setAll(scroll);
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
        // 1. Crear la alerta
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Borrar todos los datos");
        alert.setHeaderText("¿Estás seguro de que quieres reiniciar?");
        alert.setContentText("Esta acción es irreversible:\n\n" +
                "• Se borrará tu biblioteca personal.\n" +
                "• Se eliminarán todos los capítulos descargados.\n" +
                "• Se eliminará la caché de listas.\n\n" +
                "El programa quedará como recién instalado.");

        // 2. Estilar el diálogo para que el texto sea blanco
        DialogPane dialogPane = alert.getDialogPane();

        // Fondo oscuro para el panel
        dialogPane.setStyle("-fx-background-color: #2c3e50;");

        // Buscamos todos los Labels (incluyendo cabecera y contenido) y forzamos el color blanco
        // Usamos Platform.runLater para asegurar que el diálogo se ha renderizado antes de buscar los nodos
        dialogPane.getScene().getStylesheets().add(getClass().getResource(Utils.RESOURCES_PATH + "estilos-lista.css").toExternalForm());

        // Una forma infalible inline si no quieres depender del CSS externo para esto:
        alert.getDialogPane().lookupAll(".label").forEach(node -> {
            if (node instanceof Label) {
                ((Label) node).setStyle("-fx-text-fill: white;");
            }
        });

        // 3. Mostrar y esperar respuesta
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            realizarBorradoCompleto();
        }
    }

    private void realizarBorradoCompleto() {
        try {
            // A. Borrar archivo de biblioteca (JSON)
            if (ARCHIVO_BIBLIOTECA.exists()) {
                Files.delete(ARCHIVO_BIBLIOTECA.toPath());
            }

            // B. Borrar carpetas de caché (Recursivo)
            File carpetaCapitulos = new File(Main.CAPITULOS_FOLDER);
            File carpetaListas = new File(Main.LISTADO_FOLDER);

            borrarDirectorioRecursivo(carpetaCapitulos);
            borrarDirectorioRecursivo(carpetaListas);

            // C. Reiniciar memoria de la aplicación
            datosUsuario.clear();

            // Recrear carpetas vacías para evitar errores si se intenta descargar inmediatamente
            carpetaCapitulos.mkdirs();
            carpetaListas.mkdirs();

            // D. Feedback al usuario y recarga
            mostrarNotificacion("Sistema restaurado correctamente.");
            abrirInicio(); // Recargar la vista de inicio

        } catch (IOException e) {
            e.printStackTrace();
            mostrarNotificacion("Error al borrar algunos archivos.");
        }
    }

    // Método auxiliar para borrar carpetas con contenido dentro
    private void borrarDirectorioRecursivo(File archivo) {
        if (archivo.isDirectory()) {
            File[] archivos = archivo.listFiles();
            if (archivos != null) {
                for (File f : archivos) {
                    borrarDirectorioRecursivo(f);
                }
            }
        }
        // Finalmente borra el archivo o carpeta vacía
        archivo.delete();
    }

}