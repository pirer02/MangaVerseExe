package com.zixion.mangaverse.services;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.controllers.MainController;
import com.zixion.mangaverse.models.Manga;
import com.zixion.mangaverse.models.UserData;
import javafx.application.Platform;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackgroundService {

    private MainController mainController;
    private TrayIcon trayIcon;
    private ScheduledExecutorService scheduler;

    public BackgroundService(MainController mainController, Stage primaryStage) {
        this.mainController = mainController;
        configurarSystemTray(primaryStage);
        iniciarTemporizador();
    }

    private void configurarSystemTray(Stage primaryStage) {
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray no está soportado en este SO.");
            return;
        }

        try {
            // Cargar el icono de la bandeja (Asegúrate de tener esta imagen en tus recursos)
            InputStream is = getClass().getResourceAsStream("/com/zixion/mangaverse/icons/MV.png");
            Image image = ImageIO.read(is);

            // Redimensionar para la barra de tareas
            Image trayImage = image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            trayIcon = new TrayIcon(trayImage, "MangaVerse");
            trayIcon.setImageAutoSize(true);

            // Crear el menú que aparece al hacer clic derecho en el icono de la barra de tareas
            PopupMenu popup = new PopupMenu();

            MenuItem openItem = new MenuItem("Abrir MangaVerse");
            openItem.addActionListener(e -> Platform.runLater(() -> {
                primaryStage.show();
                primaryStage.toFront();
            }));

            MenuItem exitItem = new MenuItem("Cerrar por completo");
            exitItem.addActionListener(e -> {
                if (scheduler != null) scheduler.shutdownNow();
                System.exit(0);
            });

            popup.add(openItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon.setPopupMenu(popup);

            // Doble clic en el icono para abrir
            trayIcon.addActionListener(e -> Platform.runLater(() -> {
                primaryStage.show();
                primaryStage.toFront();
            }));

            SystemTray.getSystemTray().add(trayIcon);

            // TRAMPA MÁGICA: Evitamos que JavaFX se cierre cuando cerramos la ventana
            Platform.setImplicitExit(false);

            // Reconfiguramos el botón "X" de la ventana para que solo se oculte, no se cierre
            primaryStage.setOnCloseRequest(event -> {
                event.consume(); // Cancelamos el cierre real
                primaryStage.hide(); // Ocultamos la ventana
                mostrarNotificacionOS("MangaVerse minimizado", "Seguimos buscando nuevos capítulos en segundo plano.");
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void iniciarTemporizador() {
        scheduler = Executors.newScheduledThreadPool(1);
        // Se ejecuta cada 1 hora exactactamente
        scheduler.scheduleAtFixedRate(this::buscarNuevosCapitulos, 1, 1, TimeUnit.HOURS);
    }

    private void buscarNuevosCapitulos() {
        UserData userData = mainController.getUserData();

        // Si el usuario tiene desactivado el switch en su perfil, abortamos.
        if (!userData.notificacionesActivas) return;

        MangaService ms = mainController.getMangaService();
        boolean huboCambios = false;

        System.out.println("[Background] Buscando nuevos capítulos de la biblioteca...");

        for (String titulo : userData.biblioteca) {
            try {
                // Creamos un manga "dummy" solo para poder usar el método de MangaService
                Manga dummyManga = new Manga(titulo, null, null, null, null, "En Emisión", null);

                // Pedimos los capítulos FORZANDO la actualización (saltándonos la caché)
                List<String> caps = ms.obtenerCapitulos(titulo, dummyManga, false, true);

                int totalNube = caps.size();
                int totalLocal = userData.capitulosConocidos.getOrDefault(titulo, totalNube);

                if (totalNube > totalLocal) {
                    // ¡HAY UN CAPÍTULO NUEVO! Lanzamos notificación nativa de Windows/Mac
                    mostrarNotificacionOS("¡Nuevo capítulo disponible!", "Ya puedes leer lo nuevo de " + titulo + " en MangaVerse.");

                    // Actualizamos nuestra memoria local
                    userData.capitulosConocidos.put(titulo, totalNube);
                    huboCambios = true;
                } else if (totalLocal == 0 && totalNube > 0) {
                    // Si es la primera vez que lo revisa, solo guarda el número sin notificar
                    userData.capitulosConocidos.put(titulo, totalNube);
                    huboCambios = true;
                }
            } catch (Exception e) {
                System.out.println("Error al chequear novedades de: " + titulo);
            }
        }

        // Si actualizamos los contadores de algún manga, guardamos el archivo en disco
        if (huboCambios) {
            Platform.runLater(() -> mainController.guardarDatosGlobales());
        }
    }

    private void mostrarNotificacionOS(String titulo, String mensaje) {
        if (trayIcon != null) {
            // El MessageType.INFO hace que suene el clásico "clin" de notificación del sistema
            trayIcon.displayMessage(titulo, mensaje, TrayIcon.MessageType.INFO);
        }
    }
}