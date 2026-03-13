package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.Utils;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.models.Musica;
import com.zixion.mangaverse.models.UserData;
import com.zixion.mangaverse.services.AuthService;
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
import javafx.scene.input.ScrollEvent;
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

    // --- ELEMENTOS FXML ---
    @FXML private Label lblTextoCarga;
    @FXML private Label lblTemporizador;
    @FXML private Button btnCancelarCarga;

    @FXML private Button btnTopLogin;
    @FXML private VBox boxUsuarioMenu;
    @FXML private Label lblUsuarioEmail;

    @FXML private AnchorPane mainContent;
    @FXML private HBox searchBoxContainer;
    @FXML private TextField searchBar;
    @FXML private StackPane viewContainer;
    @FXML private StackPane loadingOverlay;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private VBox drawerMenu;

    // --- VARIABLES DE MOTOR ---
    private javafx.animation.Timeline timelineLogin; // El reloj del temporizador

    private boolean menuVisible = false;
    private final double MENU_WIDTH = 280.0;
    private final MangaService mangaService = new MangaService();
    private final AuthService authService = new AuthService();
    private final File ARCHIVO_BIBLIOTECA = new File(Main.APP_FOLDER, "biblioteca.json");
    private List<Manga> listaMaestra = new ArrayList<>();
    private UserData userData = new UserData();
    private Object currentController;

    private String vistaPreCapitulos = "INICIO";

    private boolean enVistaExplorar = false;
    private boolean enVistaBiblioteca = false;
    private String filtroGeneroGuardado = "Todos los Géneros";
    private String filtroEstadoGuardado = "Todos los Estados";
    private ComboBox<String> cmbGeneroExplorar;
    private ComboBox<String> cmbEstadoExplorar;
    private FlowPane gridExplorar;


    private CheckBox chkColorExplorar;
    private boolean filtroColorGuardado = false;
    private Task<List<Manga>> taskFiltroExplorar; // Para evitar tirones si escribes muy rápido


    private final List<String> GENEROS_POOL = Arrays.asList(
            "Shonen", "Accion", "Aventura", "Comedia", "Drama", "Seinen", "Romance", "Isekai", "Deporte", "Chanbara"
    );

    // =========================================================================================
    // GETTERS PARA CONTROLADORES HIJOS
    // =========================================================================================
    public AuthService getAuthService() { return authService; }
    public UserData getUserData() { return userData; }
    public MangaService getMangaService() { return mangaService; }
    public StackPane getViewContainer() { return viewContainer; }
    public void setCurrentController(Object controller) { this.currentController = controller; }

    @FXML
    public void initialize() {
        cargarBiblioteca(); // Carga rápida de lo que haya en el disco

        if(loadingOverlay != null) loadingOverlay.setVisible(false);

        if (authService.isLogueado()) {
            sincronizarConNube(); // Activa la lógica de comparación (ESTE YA HACE TODO EL TRABAJO)
        } else {
            cargarDatosYMostrarInicio();
        }

        // Configuración del buscador...
        if (searchBar != null) {
            searchBar.textProperty().addListener((obs, old, newText) -> {
                if (enVistaExplorar) filtrarExploracion();
                else if (enVistaBiblioteca) construirVistaBiblioteca();
                else {
                    if (newText == null || newText.trim().isEmpty()) abrirInicio();
                    else ejecutarBusqueda(newText.trim().toLowerCase());
                }
            });
        }
        actualizarEstadoTopBar();
    }

    public void setCargando(boolean cargando) {
        if (loadingOverlay != null) loadingOverlay.setVisible(cargando);

        // Restaurar estado normal al ocultar o al hacer una carga estándar
        if (lblTextoCarga != null) lblTextoCarga.setText("Cargando...");
        if (lblTemporizador != null) { lblTemporizador.setVisible(false); lblTemporizador.setManaged(false); }
        if (btnCancelarCarga != null) { btnCancelarCarga.setVisible(false); btnCancelarCarga.setManaged(false); }
        if (timelineLogin != null) timelineLogin.stop();
    }

    public void mostrarCargaLogin() {
        if (loadingOverlay != null) loadingOverlay.setVisible(true);
        if (lblTextoCarga != null) lblTextoCarga.setText("Esperando autorización en el navegador...");

        lblTemporizador.setVisible(true);
        lblTemporizador.setManaged(true);
        btnCancelarCarga.setVisible(true);
        btnCancelarCarga.setManaged(true);

        AtomicInteger segundosRestantes = new AtomicInteger(45);
        lblTemporizador.setText("Tiempo restante: " + segundosRestantes.get() + "s");

        if (timelineLogin != null) timelineLogin.stop();

        // Creamos un reloj que se ejecuta cada 1 segundo
        timelineLogin = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
            segundosRestantes.getAndDecrement();
            lblTemporizador.setText("Tiempo restante: " + segundosRestantes.get() + "s");

            if (segundosRestantes.get() <= 0) {
                cancelarCarga(); // Si llega a 0, se auto-cancela
            }
        }));
        timelineLogin.setCycleCount(45); // Se repetirá 45 veces
        timelineLogin.play();
    }

    @FXML
    public void cancelarCarga() {
        if (timelineLogin != null) timelineLogin.stop();
        authService.cancelarLogin(); // Apagamos el servidor temporal
        setCargando(false);          // Ocultamos toda la pantalla de carga
    }


    private void setBuscadorVisible(boolean visible) {
        if (searchBoxContainer != null) searchBoxContainer.setVisible(visible);
    }

    public void actualizarEstadoTopBar() {
        if (authService.isLogueado()) {
            // Usuario logueado: Ocultar botón superior, mostrar correo en el menú lateral
            if (btnTopLogin != null) {
                btnTopLogin.setVisible(false);
                btnTopLogin.setManaged(false);
            }
            if (boxUsuarioMenu != null && lblUsuarioEmail != null) {
                lblUsuarioEmail.setText(authService.getEmail());
                boxUsuarioMenu.setVisible(true);
                boxUsuarioMenu.setManaged(true);
            }
        } else {
            // Usuario NO logueado: Mostrar botón superior, ocultar correo del menú
            if (btnTopLogin != null) {
                btnTopLogin.setVisible(true);
                btnTopLogin.setManaged(true);
            }
            if (boxUsuarioMenu != null) {
                boxUsuarioMenu.setVisible(false);
                boxUsuarioMenu.setManaged(false);
            }
        }
    }

    @FXML
    private void iniciarSesionDesdeTopBar() {
        mostrarCargaLogin(); // Muestra el reloj que programamos antes

        authService.iniciarSesionGoogle(
                () -> Platform.runLater(() -> {
                    // Cargar datos de la nube o subir los locales
                    String datosNube = authService.cargarDatosDeNube();
                    if (datosNube != null) {
                        try {
                            Files.writeString(ARCHIVO_BIBLIOTECA.toPath(), datosNube);
                            cargarBiblioteca();
                        } catch (Exception e) { e.printStackTrace(); }
                    } else {
                        guardarDatosGlobales();
                        try {
                            String jsonLocal = Files.readString(ARCHIVO_BIBLIOTECA.toPath());
                            authService.guardarDatosEnNube(jsonLocal);
                        } catch (Exception e) { e.printStackTrace(); }
                    }

                    actualizarEstadoTopBar(); // Oculta el botón y muestra el correo
                    setCargando(false);
                    abrirInicio(); // Recarga la vista de inicio
                }),
                () -> Platform.runLater(() -> {
                    setCargando(false);
                })
        );
    }


    // =========================================================================================
    // MOTORES DE DATOS Y SINCRONIZACIÓN
    // =========================================================================================

    public void guardarDatosGlobales() {
        guardarBiblioteca(); // Guarda localmente primero

        if (authService.isLogueado()) {
            // Ejecutamos en un hilo separado para no congelar la interfaz (UI)
            new Thread(() -> {
                try {
                    String jsonLocal = Files.readString(ARCHIVO_BIBLIOTECA.toPath());
                    authService.guardarDatosEnNube(jsonLocal);
                } catch (Exception e) {
                    System.err.println("Error en sincronización automática: " + e.getMessage());
                }
            }).start();
        }
    }

    // Reemplaza estos métodos en MainController.java

    // --- CARGAR DATOS (De JSON a Objeto Java) ---
    private void cargarBiblioteca() {
        if (ARCHIVO_BIBLIOTECA.exists()) {
            try {
                String contenido = Files.readString(ARCHIVO_BIBLIOTECA.toPath());
                JSONObject json = new JSONObject(contenido);
                userData = new UserData();

                // Cargar cada sección verificando si existe en el JSON
                if (json.has("biblioteca")) {
                    JSONArray arr = json.getJSONArray("biblioteca");
                    for (int i = 0; i < arr.length(); i++) userData.biblioteca.add(arr.getString(i));
                }
                if (json.has("historial")) {
                    JSONObject obj = json.getJSONObject("historial");
                    for (String key : obj.keySet()) userData.historial.put(key, obj.getString(key));
                }
                if (json.has("progresoPagina")) {
                    JSONObject obj = json.getJSONObject("progresoPagina");
                    for (String key : obj.keySet()) userData.progresoPagina.put(key, obj.getInt(key));
                }
                if (json.has("timestampsCapitulos")) {
                    JSONObject obj = json.getJSONObject("timestampsCapitulos");
                    for (String key : obj.keySet()) userData.timestampsCapitulos.put(key, obj.getLong(key));
                }

                userData.lastUpdateTimestamp = json.optLong("lastUpdateTimestamp", 0L);
                userData.notificacionesActivas = json.optBoolean("notificacionesActivas", true);

                // Cargar capítulos leídos
                if (json.has("capitulosLeidos")) {
                    JSONObject obj = json.getJSONObject("capitulosLeidos");
                    for (String key : obj.keySet()) {
                        JSONArray arr = obj.getJSONArray(key);
                        Set<String> caps = new HashSet<>();
                        for (int i = 0; i < arr.length(); i++) caps.add(arr.getString(i));
                        userData.capitulosLeidos.put(key, caps);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Reemplaza guardarProgresoPagina
    public void guardarProgresoPagina(String mangaTitulo, String capitulo, int pagina, boolean isColor) {
        // Usamos el formato EXACTO de la app móvil, incluyendo si es a color
        String sufijoColor = isColor ? "___COLOR" : "";
        String clave = mangaTitulo + "___" + capitulo + sufijoColor;
        this.userData.progresoPagina.put(clave, pagina);

        // Usamos guardarDatosGlobales porque lo sube a la nube en un hilo separado (no congela la pantalla)
        guardarDatosGlobales();
        System.out.println("[Sincro] Guardado progreso: " + clave + " -> Pag: " + pagina);
    }

    // Reemplaza obtenerProgreso
    public int obtenerProgreso(String mangaTitulo, String capitulo, boolean isColor) {
        String sufijoColor = isColor ? "___COLOR" : "";
        return userData.progresoPagina.getOrDefault(mangaTitulo + "___" + capitulo + sufijoColor, 0);
    }

    // Reemplaza registrarLectura (el que modificamos en el mensaje anterior)
    public void registrarLectura(String mangaTitulo, String capituloLeido, boolean isColor) {
        String claveHistorial = isColor ? mangaTitulo + "___COLOR" : mangaTitulo;
        this.userData.historial.put(claveHistorial, capituloLeido);
        guardarBiblioteca();
    }

    /**
     * Registra que un capítulo ha sido abierto y lo marca en el historial.
     * Es el equivalente a lo que hace tu app móvil al entrar al Lector.
     */



    // --- GUARDAR DATOS (De Objeto Java a JSON para Firebase) ---
    public void guardarBiblioteca() {
        try {
            String json = jsonNubeLocal(); // Genera JSON con nuevo timestamp
            Files.writeString(ARCHIVO_BIBLIOTECA.toPath(), json);

            if (authService.isLogueado()) {
                authService.guardarDatosEnNube(json);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    // =========================================================================================
    // BORRADO COMPLETO ARREGLADO
    // =========================================================================================



    public void realizarBorradoCompleto() {
        try {
            if (ARCHIVO_BIBLIOTECA.exists()) Files.delete(ARCHIVO_BIBLIOTECA.toPath());
            File carpetaCapitulos = new File(Main.CAPITULOS_FOLDER);
            File carpetaListas = new File(Main.LISTADO_FOLDER);
            File carpetaMusica = new File(Main.MUSICA_FOLDER);
            File carpetaColor = new File(Main.CAPITULOS_COLOR_FOLDER);

            borrarDirectorioRecursivo(carpetaCapitulos);
            borrarDirectorioRecursivo(carpetaListas);
            borrarDirectorioRecursivo(carpetaMusica);
            borrarDirectorioRecursivo(carpetaColor);

            userData = new UserData();
            guardarBiblioteca();

            carpetaCapitulos.mkdirs();
            carpetaListas.mkdirs();
            carpetaMusica.mkdirs();
            carpetaColor.mkdirs();

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
            if (archivos != null) {
                for (File f : archivos) borrarDirectorioRecursivo(f);
            }
        }
        archivo.delete();
    }

    // =========================================================================================
    // LÓGICA DE NEGOCIO (LECTURA, PROGRESO)
    // =========================================================================================

    public boolean isCapituloLeido(String mangaTitulo, String capitulo) {
        Set<String> leidos = userData.capitulosLeidos.get(mangaTitulo);
        return leidos != null && leidos.contains(capitulo);
    }

    // MANTÉN ESTE: Registra el historial (eliminado el marcado automático de leído)
    public void registrarLectura(String mangaTitulo, String capituloLeido) {
        this.userData.historial.put(mangaTitulo, capituloLeido);
        guardarBiblioteca();
    }

    /**
     * Marca un capítulo como terminado y actualiza el historial para sugerir el siguiente.
     * Si no hay más capítulos, el manga desaparece de "Continuar Leyendo".
     */
    public void marcarCapituloComoTerminado(String mangaTitulo, String capituloTerminado, String proximoCapitulo) {
        // 1. Marcamos el capítulo actual como leído (check verde)
        this.userData.capitulosLeidos
                .computeIfAbsent(mangaTitulo, k -> new HashSet<>())
                .add(capituloTerminado);

        if (proximoCapitulo != null) {
            // 2. Si existe un siguiente capítulo, lo ponemos en el historial
            this.userData.historial.put(mangaTitulo, proximoCapitulo);
            // Limpiamos el progreso de página del nuevo capítulo sugerido para que empiece de cero
            this.userData.progresoPagina.remove(mangaTitulo + "___" + proximoCapitulo);
            System.out.println("[Sincro] Capítulo terminado. Siguiente sugerido: " + proximoCapitulo);
        } else {
            // 3. Si no hay más capítulos (te has puesto al día), lo quitamos del historial
            this.userData.historial.remove(mangaTitulo);
            System.out.println("[Sincro] Manga completado. Eliminado de 'Continuar Leyendo'.");
        }

        // Sincronizamos local y Firebase
        guardarBiblioteca();
    }

    /**
     * Este es el "Cerebro" de la sincronización.
     * Compara los datos locales con los de Firebase y decide quién gana.
     */
    public void sincronizarConNube() {
        if (!authService.isLogueado()) return;

        new Thread(() -> {
            System.out.println("[Sync] Iniciando sincronización (Estilo Móvil)...");
            String jsonNube = authService.cargarDatosDeNube();

            Platform.runLater(() -> {
                try {
                    if (jsonNube != null) {
                        // REGLA DEL MÓVIL: Al iniciar, la nube siempre aplasta a lo local.
                        System.out.println("[Sync] Nube encontrada. Descargando y aplicando...");
                        Files.writeString(ARCHIVO_BIBLIOTECA.toPath(), jsonNube);
                        cargarBiblioteca(); // Recarga el objeto userData en memoria
                        mostrarNotificacion("Datos actualizados desde la nube ☁");
                    } else {
                        // Si es un usuario completamente nuevo
                        System.out.println("[Sync] Nube vacía. Subiendo primera copia...");
                        authService.guardarDatosEnNube(jsonNubeLocal());
                    }

                    // Una vez sincronizado, construimos la interfaz
                    cargarDatosYMostrarInicio();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }).start();
    }

    /**
     * Genera el JSON actual de UserData para ser enviado a Firebase o guardado en disco.
     */
    private String jsonNubeLocal() {
        JSONObject json = new JSONObject();
        // Siempre actualizamos el timestamp antes de generar el JSON para subir/guardar

        json.put("biblioteca", new JSONArray(userData.biblioteca));
        json.put("historial", new JSONObject(userData.historial));
        json.put("progresoPagina", new JSONObject(userData.progresoPagina));
        json.put("timestampsCapitulos", new JSONObject(userData.timestampsCapitulos));
        json.put("lastUpdateTimestamp", userData.lastUpdateTimestamp);
        json.put("notificacionesActivas", userData.notificacionesActivas);

        JSONObject leidosObj = new JSONObject();
        for (Map.Entry<String, Set<String>> entry : userData.capitulosLeidos.entrySet()) {
            leidosObj.put(entry.getKey(), new JSONArray(entry.getValue()));
        }
        json.put("capitulosLeidos", leidosObj);

        // Incluimos las canciones (aunque el móvil las ignore, así no las pierdes en PC)
        json.put("canciones", new JSONObject(userData.canciones));

        return json.toString();
    }


    public int obtenerProgreso(String mangaTitulo, String capitulo) {
        return userData.progresoPagina.getOrDefault(mangaTitulo + "___" + capitulo, 0);
    }

    public void borrarProgresoManga(String titulo) {
        userData.historial.remove(titulo);
        userData.capitulosLeidos.remove(titulo);
        userData.progresoPagina.keySet().removeIf(k -> k.startsWith(titulo + "___"));
        guardarDatosGlobales();
    }

    public List<Musica> getMusicaManga(String titulo) {
        return userData.canciones.getOrDefault(titulo, new ArrayList<>());
    }

    // =========================================================================================
    // NAVEGACIÓN Y VISTAS
    // =========================================================================================

    @FXML private void toggleMenu() {
        if (drawerMenu == null) return;
        TranslateTransition tMenu = new TranslateTransition(Duration.millis(300), drawerMenu);
        if (!menuVisible) {
            tMenu.setToX(0);
        } else {
            tMenu.setToX(-MENU_WIDTH);
        }
        menuVisible = !menuVisible;
        tMenu.play();
    }

    @FXML private void onContentClick() { if (menuVisible) toggleMenu(); }

    @FXML public void abrirInicio() {
        enVistaExplorar = false; enVistaBiblioteca = false; setBuscadorVisible(true);
        if (menuVisible) toggleMenu();
        setCargando(true);
        PauseTransition pause = new PauseTransition(Duration.millis(50));
        pause.setOnFinished(e -> construirVistaInicio());
        pause.play();
    }

    @FXML
    public void volverVistaAnterior() {
        if (vistaPreCapitulos.equals("EXPLORAR")) {
            abrirExplorar();
        } else if (vistaPreCapitulos.equals("BIBLIOTECA")) {
            abrirBiblioteca();
        } else {
            // Si estábamos en Inicio, comprobamos si había una búsqueda escrita
            String textoBusqueda = searchBar.getText() != null ? searchBar.getText().trim() : "";
            if (!textoBusqueda.isEmpty()) {
                enVistaExplorar = false;
                enVistaBiblioteca = false;
                setBuscadorVisible(true);
                ejecutarBusqueda(textoBusqueda.toLowerCase());
            } else {
                abrirInicio();
            }
        }
    }


    @FXML public void abrirBiblioteca() {
        enVistaExplorar = false; enVistaBiblioteca = true; setBuscadorVisible(true);
        if (menuVisible) toggleMenu();
        setCargando(true);
        PauseTransition pause = new PauseTransition(Duration.millis(50));
        pause.setOnFinished(e -> construirVistaBiblioteca());
        pause.play();
    }

    @FXML public void abrirExplorar() {
        enVistaExplorar = true; enVistaBiblioteca = false; setBuscadorVisible(true);
        if (menuVisible) toggleMenu();
        setCargando(true);
        PauseTransition pause = new PauseTransition(Duration.millis(50));
        pause.setOnFinished(e -> construirVistaExplorar());
        pause.play();
    }

    @FXML public void abrirPerfil() {
        enVistaExplorar = false; enVistaBiblioteca = false; setBuscadorVisible(false);
        if (menuVisible) toggleMenu();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(Utils.RESOURCES_PATH + "perfil-view.fxml"));
            Node node = loader.load();
            PerfilController pc = loader.getController();
            pc.setMainController(this);
            viewContainer.getChildren().setAll(node);
        } catch (IOException e) { e.printStackTrace(); }
    }

    // =========================================================================================
    // CONSTRUCCIÓN DE LA UI
    // =========================================================================================

    private void cargarDatosYMostrarInicio() {
        setCargando(true);
        Task<List<Manga>> task = new Task<>() {
            @Override
            protected List<Manga> call() throws Exception {
                List<Manga> mangasServidor = mangaService.obtenerMangasDesdeServidor();
                for (Manga m : mangasServidor) {
                    Manga info = mangaService.obtenerInfoManga(m.getTitulo().replace(" ", "_"));
                    m.setGeneros(info.getGeneros()); m.setSinopsis(info.getSinopsis());
                    m.setEstado(info.getEstado()); m.setTipo(info.getTipo());
                }
                return mangasServidor;
            }
        };
        task.setOnSucceeded(e -> { this.listaMaestra = task.getValue(); abrirInicio(); });
        task.setOnFailed(e -> { setCargando(false); e.getSource().getException().printStackTrace(); });
        new Thread(task).start();
    }

    private void construirVistaInicio() {
        List<Image> imagenesPendientes = new ArrayList<>();
        VBox mainLayout = new VBox(35);
        mainLayout.setPadding(new Insets(20, 0, 40, 0));
        mainLayout.setStyle("-fx-background-color: #141414;");

        ScrollPane scrollVertical = new ScrollPane(mainLayout);
        scrollVertical.setFitToWidth(true);
        scrollVertical.setStyle("-fx-background: #141414; -fx-background-color: #141414; -fx-border-color: transparent;");
        aplicarScrollRapido(scrollVertical);

        List<Manga> continuar = listaMaestra.stream().filter(m -> userData.historial.containsKey(m.getTitulo())).collect(Collectors.toList());
        if (!continuar.isEmpty()) {
            mainLayout.getChildren().add(crearFilaHorizontal("Continuar Leyendo", continuar, imagenesPendientes));
        }

        List<String> generosCopia = new ArrayList<>(GENEROS_POOL);
        Collections.shuffle(generosCopia);
        for (String gen : generosCopia.subList(0, Math.min(6, generosCopia.size()))) {
            List<Manga> filtrados = listaMaestra.stream().filter(m -> m.getGeneros() != null && m.getGeneros().stream().anyMatch(g -> g.equalsIgnoreCase(gen))).collect(Collectors.toList());
            if (!filtrados.isEmpty()) mainLayout.getChildren().add(crearFilaHorizontal(gen, filtrados, imagenesPendientes));
        }
        viewContainer.getChildren().setAll(scrollVertical);
        esperarCargaImagenes(imagenesPendientes, () -> setCargando(false));
    }

    private void construirVistaBiblioteca() {
        String query = searchBar.getText() != null ? searchBar.getText().toLowerCase().trim() : "";
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
            boolean match = !userData.canciones.getOrDefault(m.getTitulo(), new ArrayList<>()).isEmpty();
            if(!query.isEmpty()) return match && m.getTitulo().toLowerCase().contains(query);
            return match;
        }).collect(Collectors.toList());

        if (!mangasConMusica.isEmpty()) layoutBiblioteca.getChildren().add(crearFilaHorizontal("♫ Mangas con Ambiente", mangasConMusica, imagenesPendientes));

        List<Manga> misMangas = listaMaestra.stream().filter(m -> {
            boolean match = userData.biblioteca.contains(m.getTitulo());
            if(!query.isEmpty()) return match && m.getTitulo().toLowerCase().contains(query);
            return match;
        }).collect(Collectors.toList());

        if (misMangas.isEmpty() && mangasConMusica.isEmpty()) {
            Label emptyLabel = new Label("No hay resultados en tu biblioteca.");
            emptyLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 16px;");
            grid.getChildren().add(emptyLabel);
        } else {
            for (Manga m : misMangas) grid.getChildren().add(crearTarjetaManga(m, imagenesPendientes));
        }
        layoutBiblioteca.getChildren().add(grid);
        ScrollPane finalScroll = new ScrollPane(layoutBiblioteca);
        finalScroll.setFitToWidth(true);
        finalScroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");
        aplicarScrollRapido(finalScroll);

        viewContainer.getChildren().setAll(finalScroll);
        esperarCargaImagenes(imagenesPendientes, () -> setCargando(false));
    }

    private void construirVistaExplorar() {
        VBox layoutExplorar = new VBox(20);
        layoutExplorar.setPadding(new Insets(20));
        layoutExplorar.setStyle("-fx-background-color: #141414;");
        Label lblTitulo = new Label("Explorar Catálogo");
        lblTitulo.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");

        HBox filtrosBox = new HBox(15);
        filtrosBox.setAlignment(Pos.CENTER_LEFT);
        cmbGeneroExplorar = new ComboBox<>();
        cmbGeneroExplorar.getItems().add("Todos los Géneros");
        cmbGeneroExplorar.getItems().addAll(GENEROS_POOL);
        cmbGeneroExplorar.setValue(filtroGeneroGuardado);
        estilizarComboBox(cmbGeneroExplorar);

        cmbEstadoExplorar = new ComboBox<>();
        cmbEstadoExplorar.getItems().addAll("Todos los Estados", "Finalizado", "En Curso");
        cmbEstadoExplorar.setValue(filtroEstadoGuardado);
        estilizarComboBox(cmbEstadoExplorar);

        cmbGeneroExplorar.setOnAction(e -> filtrarExploracion());
        cmbEstadoExplorar.setOnAction(e -> filtrarExploracion());

        // --- NUEVO: CHECKBOX DE COLOR ---
        chkColorExplorar = new CheckBox("A color");
        chkColorExplorar.setStyle("-fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 13px;");
        chkColorExplorar.setSelected(filtroColorGuardado);
        chkColorExplorar.setOnAction(e -> filtrarExploracion());

        Label lblFiltro = new Label("Filtrar por:");
        lblFiltro.setStyle("-fx-text-fill: #bdc3c7;");
        filtrosBox.getChildren().addAll(lblFiltro, cmbGeneroExplorar, cmbEstadoExplorar, chkColorExplorar);
        gridExplorar = new FlowPane();
        gridExplorar.setHgap(20); gridExplorar.setVgap(25);
        gridExplorar.setStyle("-fx-background-color: #141414;");
        layoutExplorar.getChildren().addAll(lblTitulo, filtrosBox, gridExplorar);

        ScrollPane scroll = new ScrollPane(layoutExplorar);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");
        aplicarScrollRapido(scroll);
        viewContainer.getChildren().setAll(scroll);
        filtrarExploracion();
    }

    private void estilizarComboBox(ComboBox<String> cmb) {
        cmb.setStyle("-fx-background-color: #333; -fx-text-fill: white; -fx-font-size: 13px;");
    }

    private void filtrarExploracion() {
        if (!enVistaExplorar || gridExplorar == null) return;

        String busquedaTexto = searchBar.getText() != null ? searchBar.getText().toLowerCase().trim() : "";
        String generoSel = cmbGeneroExplorar.getValue();
        String estadoSel = cmbEstadoExplorar.getValue();
        boolean colorSel = chkColorExplorar.isSelected();

        // Guardamos los estados para cuando vuelvas de leer un capítulo
        filtroGeneroGuardado = generoSel;
        filtroEstadoGuardado = estadoSel;
        filtroColorGuardado = colorSel;

        // Si el usuario escribe muy rápido, cancelamos la búsqueda anterior para que no se pisen
        if (taskFiltroExplorar != null && taskFiltroExplorar.isRunning()) {
            taskFiltroExplorar.cancel();
        }

        gridExplorar.getChildren().clear();
        setCargando(true); // Ponemos la pantalla de carga porque consultar colores lleva tiempo

        // Hacemos el filtrado en un hilo secundario para no congelar la pantalla
        taskFiltroExplorar = new Task<>() {
            @Override
            protected List<Manga> call() {
                return listaMaestra.stream().filter(m -> {
                    if (isCancelled()) return false; // Abortamos si se empezó a escribir otra letra

                    boolean matchTexto = busquedaTexto.isEmpty() || m.getTitulo().toLowerCase().contains(busquedaTexto);
                    boolean matchGenero = generoSel == null || generoSel.equals("Todos los Géneros") || (m.getGeneros() != null && m.getGeneros().stream().anyMatch(g -> g.equalsIgnoreCase(generoSel)));
                    boolean matchEstado = true;
                    if (estadoSel != null && !estadoSel.equals("Todos los Estados")) {
                        String estadoManga = m.getEstado() != null ? m.getEstado().toLowerCase() : "";
                        if (estadoSel.equals("Finalizado")) matchEstado = estadoManga.contains("finalizado") || estadoManga.contains("terminado");
                        else if (estadoSel.equals("En Curso")) matchEstado = !estadoManga.contains("finalizado") && !estadoManga.contains("terminado");
                    }

                    // OPTIMIZACIÓN: Solo consultamos al servidor el color si el manga cumple todo lo demás y la casilla está activada
                    boolean matchColor = true;
                    if (matchTexto && matchGenero && matchEstado && colorSel) {
                        matchColor = mangaService.verificarExistenciaColor(m.getTitulo());
                    }

                    return matchTexto && matchGenero && matchEstado && matchColor;
                }).collect(Collectors.toList());
            }
        };

        // Cuando termine de buscar y filtrar...
        taskFiltroExplorar.setOnSucceeded(e -> {
            List<Manga> filtrados = taskFiltroExplorar.getValue();
            List<Image> imagenesPendientes = new ArrayList<>();

            if (filtrados.isEmpty()) {
                Label empty = new Label("No se encontraron resultados con estos filtros.");
                empty.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 16px; -fx-padding: 20;");
                gridExplorar.getChildren().add(empty);
                setCargando(false);
            } else {
                for (Manga m : filtrados) {
                    gridExplorar.getChildren().add(crearTarjetaManga(m, imagenesPendientes));
                }
                if (!imagenesPendientes.isEmpty()) {
                    esperarCargaImagenes(imagenesPendientes, () -> setCargando(false));
                } else {
                    setCargando(false);
                }
            }
        });

        taskFiltroExplorar.setOnFailed(e -> setCargando(false));

        // Lanzamos el hilo
        new Thread(taskFiltroExplorar).start();
    }

    private VBox crearFilaHorizontal(String titulo, List<Manga> mangas, List<Image> trackerImagenes) {
        VBox row = new VBox(10);
        Label lbl = new Label(titulo.toUpperCase());
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 0 0 0 25;");
        HBox hb = new HBox(20);
        hb.setPadding(new Insets(10, 25, 10, 25));

        for (Manga m : mangas) {
            // 1. Guardamos la tarjeta gráfica
            javafx.scene.Node tarjeta = crearTarjetaManga(m, trackerImagenes);

            // ¡ELIMINADO EL BLOQUE QUE ROMPÍA LOS CLICS AQUÍ!

            // 2. Añadimos la tarjeta a la fila horizontal
            hb.getChildren().add(tarjeta);
        }

        ScrollPane sp = new ScrollPane(hb);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        row.getChildren().addAll(lbl, sp);
        return row;
    }

    private VBox crearTarjetaManga(Manga m, List<Image> trackerImagenes) {
        VBox card = new VBox(8); card.setAlignment(Pos.TOP_CENTER);
        StackPane imageContainer = new StackPane(); imageContainer.setPrefSize(160, 230);
        ImageView iv = new ImageView(); iv.setFitWidth(160); iv.setFitHeight(230);
        if (m.getUrlPortada() != null) {
            Image img = new Image(m.getUrlPortada(), 160, 230, true, true, true);
            iv.setImage(img);
            if (trackerImagenes != null) trackerImagenes.add(img);
        }
        Rectangle clip = new Rectangle(160, 230); clip.setArcWidth(15); clip.setArcHeight(15); iv.setClip(clip);
        iv.setCursor(Cursor.HAND); iv.setOnMouseClicked(e -> irACapitulos(m));

        Button btnAdd = new Button();
        boolean enBiblio = userData.biblioteca.contains(m.getTitulo());
        configurarEstiloBotonBiblio(btnAdd, enBiblio);
        StackPane.setAlignment(btnAdd, Pos.TOP_RIGHT); StackPane.setMargin(btnAdd, new Insets(5));
        btnAdd.setOnAction(e -> { toggleBiblioteca(m, btnAdd); e.consume(); });
        imageContainer.getChildren().addAll(iv, btnAdd);

        String siguienteCap = userData.historial.get(m.getTitulo());
        if (siguienteCap != null) {
            Label lblNext = new Label(extraerNumeroCapitulo(siguienteCap));
            lblNext.setStyle("-fx-background-color: rgba(50, 50, 50, 0.9); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #777; -fx-border-radius: 4; -fx-border-width: 1;");
            lblNext.setOnMouseEntered(e -> lblNext.setStyle("-fx-background-color: #e50914; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #e50914; -fx-border-radius: 4; -fx-border-width: 1;"));
            lblNext.setOnMouseExited(e -> lblNext.setStyle("-fx-background-color: rgba(50, 50, 50, 0.9); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand; -fx-border-color: #777; -fx-border-radius: 4; -fx-border-width: 1;"));

            // MAGIA: Si el capítulo ya fue leído (sincronizado desde el móvil), calculamos el siguiente
            if (isCapituloLeido(m.getTitulo(), siguienteCap)) {
                new Thread(() -> {
                    List<String> caps = mangaService.obtenerCapitulos(m.getTitulo(), m, false, false);
                    Platform.runLater(() -> {
                        String capBuscado = siguienteCap.endsWith(".cbz") ? siguienteCap : siguienteCap + ".cbz";
                        int idx = caps.indexOf(capBuscado);
                        if (idx != -1 && idx + 1 < caps.size()) {
                            String nextCap = caps.get(idx + 1).replace(".cbz", "");
                            lblNext.setText(extraerNumeroCapitulo(nextCap));
                        }
                    });
                }).start();
            }

            // EN LUGAR DE FORZAR EL CAPÍTULO VIEJO, USAMOS TU LÓGICA INTELIGENTE:
            lblNext.setOnMouseClicked(e -> { abrirLectorDeContinuar(m); e.consume(); });

            StackPane.setAlignment(lblNext, Pos.TOP_LEFT); StackPane.setMargin(lblNext, new Insets(6));
            imageContainer.getChildren().add(lblNext);
        }

        Label lbl = new Label(m.getTitulo());
        lbl.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 13px; -fx-font-weight: bold;");
        lbl.setMaxWidth(150); lbl.setAlignment(Pos.CENTER);
        lbl.setCursor(Cursor.HAND); lbl.setOnMouseClicked(e -> irACapitulos(m));
        card.getChildren().addAll(imageContainer, lbl);
        card.setOnMouseEntered(e -> card.setScaleX(1.05));
        card.setOnMouseExited(e -> card.setScaleX(1.0));
        return card;
    }

    private void toggleBiblioteca(Manga m, Button btn) {
        String titulo = m.getTitulo();
        boolean enBiblio;
        if (userData.biblioteca.contains(titulo)) {
            userData.biblioteca.remove(titulo);
            enBiblio = false;
        } else {
            userData.biblioteca.add(titulo);
            enBiblio = true;
        }
        configurarEstiloBotonBiblio(btn, enBiblio);
        mostrarNotificacion(enBiblio ? "¡Añadido a tu biblioteca!" : "Eliminado de biblioteca");

        // CAMBIO: Sincronización inmediata
        guardarDatosGlobales();
    }

    private void configurarEstiloBotonBiblio(Button btn, boolean added) {
        if (added) { btn.setText("✔"); btn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 50; -fx-min-width: 30; -fx-min-height: 30; -fx-cursor: hand;"); }
        else { btn.setText("+"); btn.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-background-radius: 50; -fx-min-width: 30; -fx-min-height: 30; -fx-cursor: hand;"); }
    }

    private void aplicarScrollRapido(ScrollPane scrollPane) {
        final double VELOCIDAD_SCROLL = 4.0;
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.getDeltaY() != 0) {
                event.consume();
                double contenidoAlto = scrollPane.getContent().getBoundsInLocal().getHeight();
                double visorAlto = scrollPane.getViewportBounds().getHeight();
                double maxScroll = contenidoAlto - visorAlto;
                if (maxScroll > 0) {
                    double desplazamiento = -event.getDeltaY() * VELOCIDAD_SCROLL;
                    double cambioVvalue = desplazamiento / maxScroll;
                    scrollPane.setVvalue(scrollPane.getVvalue() + cambioVvalue);
                }
            }
        });
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
                if (m.getTitulo().toLowerCase().contains(query)) grid.getChildren().add(crearTarjetaManga(m, imagenesPendientes));
            }
            ScrollPane scroll = new ScrollPane(grid);
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background: #141414; -fx-background-color: #141414;");
            aplicarScrollRapido(scroll);
            viewContainer.getChildren().setAll(scroll);
            esperarCargaImagenes(imagenesPendientes, () -> setCargando(false));
        });
        pause.play();
    }

    private void esperarCargaImagenes(List<Image> imagenes, Runnable alTerminar) {
        if (imagenes == null || imagenes.isEmpty()) { alTerminar.run(); return; }
        AtomicInteger pendientes = new AtomicInteger(imagenes.size());
        for (Image img : imagenes) {
            if (img.getProgress() == 1.0 || img.isError()) {
                if (pendientes.decrementAndGet() == 0) alTerminar.run();
            } else {
                img.progressProperty().addListener(new ChangeListener<Number>() {
                    @Override
                    public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                        if (newValue.doubleValue() == 1.0) {
                            if (pendientes.decrementAndGet() == 0) Platform.runLater(alTerminar);
                            img.progressProperty().removeListener(this);
                        }
                    }
                });
                img.errorProperty().addListener((obs, old, isError) -> {
                    if (isError && pendientes.decrementAndGet() == 0) Platform.runLater(alTerminar);
                });
            }
        }
    }

    public void irACapitulos(Manga m) {
        // Añadir esto al principio de irACapitulos y abrirCapituloDirecto:
        if (searchBoxContainer != null && searchBoxContainer.isVisible()) {
            if (enVistaExplorar) vistaPreCapitulos = "EXPLORAR";
            else if (enVistaBiblioteca) vistaPreCapitulos = "BIBLIOTECA";
            else vistaPreCapitulos = "INICIO";
        }

        // (A partir de aquí mantienes tu código: enVistaExplorar = false; ...)

        enVistaExplorar = false; enVistaBiblioteca = false; setBuscadorVisible(false);
        setCargando(true);
        Task<List<String>> fetchTask = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return mangaService.obtenerCapitulos(m.getTitulo(), m, false, false);
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
        // Añadir esto al principio de irACapitulos y abrirCapituloDirecto:
        if (searchBoxContainer != null && searchBoxContainer.isVisible()) {
            if (enVistaExplorar) vistaPreCapitulos = "EXPLORAR";
            else if (enVistaBiblioteca) vistaPreCapitulos = "BIBLIOTECA";
            else vistaPreCapitulos = "INICIO";
        }

        // (A partir de aquí mantienes tu código: enVistaExplorar = false; ...)

        enVistaExplorar = false; enVistaBiblioteca = false; setBuscadorVisible(false);
        setCargando(true);
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return mangaService.obtenerCapitulos(m.getTitulo(), m, false, false);
            }
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
                    controller.inicializarLector(listaNormalizada, index, m, this, false);
                    viewContainer.getChildren().setAll(lectorNode);
                } else { irACapitulos(m); }
            } catch (Exception ex) { ex.printStackTrace(); }
            finally { setCargando(false); }
        });
        task.setOnFailed(e -> setCargando(false));
        new Thread(task).start();
    }

    public void abrirLectorDeContinuar(Manga m) {
        String ultimoCap = userData.historial.get(m.getTitulo());

        if (ultimoCap != null) {
            // Si el capítulo que dice el historial ya lo marcamos como LEÍDO...
            if (isCapituloLeido(m.getTitulo(), ultimoCap)) {
                System.out.println("[Continuar] " + ultimoCap + " ya leído. Buscando el siguiente para " + m.getTitulo());

                setCargando(true);
                Task<List<String>> task = new Task<>() {
                    @Override protected List<String> call() throws Exception {
                        return mangaService.obtenerCapitulos(m.getTitulo(), m, false, false);
                    }
                };

                task.setOnSucceeded(e -> {
                    List<String> capitulos = task.getValue();
                    String capBuscado = ultimoCap.endsWith(".cbz") ? ultimoCap : ultimoCap + ".cbz";
                    int index = capitulos.indexOf(capBuscado);

                    // Si hay un capítulo después en la lista, abrimos ese.
                    if (index != -1 && index + 1 < capitulos.size()) {
                        String proximoCap = capitulos.get(index + 1).replace(".cbz", "");
                        System.out.println("[Continuar] Saltando al siguiente capítulo: " + proximoCap);
                        abrirCapituloDirecto(m, proximoCap); // <-- USAMOS TU MÉTODO AQUÍ
                    } else {
                        // Si no hay más (estás al día), simplemente abre el último
                        abrirCapituloDirecto(m, ultimoCap); // <-- USAMOS TU MÉTODO AQUÍ
                    }
                });

                task.setOnFailed(ev -> setCargando(false));
                new Thread(task).start();
            } else {
                // Si no está leído, abrimos el capítulo donde se quedó directamente
                abrirCapituloDirecto(m, ultimoCap); // <-- USAMOS TU MÉTODO AQUÍ
            }
        } else {
            // Si por algún motivo no hay historial, vamos a la pantalla de capítulos
            irACapitulos(m);
        }
    }

    private String extraerNumeroCapitulo(String nombreArchivo) {
        try { Matcher m = Pattern.compile("(\\d+)").matcher(nombreArchivo); if (m.find()) return "Cap. " + Integer.parseInt(m.group(1)); } catch (Exception e) {}
        return "Leer";
    }

    public void mostrarNotificacion(String msj) { System.out.println("[MangaVerse] " + msj); }




}