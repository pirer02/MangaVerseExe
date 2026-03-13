package com.zixion.mangaverse.controllers;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.models.UserData;
import com.zixion.mangaverse.services.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

public class PerfilController {

    @FXML private Label lblEmail;
    @FXML private Label lblEstadoCuenta;
    @FXML private Button btnLogin;
    @FXML private VBox boxGestionCuenta;
    @FXML private CheckBox chkNotificaciones;

    private MainController mainController;
    private AuthService authService; // Lo recibimos del MainController para que compartan la misma sesión

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        this.authService = mainController.getAuthService(); // <-- Comparten el mismo motor de Firebase
        cargarDatosUsuario();
    }

    private void cargarDatosUsuario() {
        if (mainController == null || authService == null) return;
        UserData userData = mainController.getUserData();

        chkNotificaciones.setSelected(userData.notificacionesActivas);

        if (authService.isLogueado()) {
            lblEmail.setText(authService.getEmail());
            lblEstadoCuenta.setText("Cuenta vinculada con Firebase");
            lblEstadoCuenta.setStyle("-fx-text-fill: #4DA8DA; -fx-font-size: 14;");
            btnLogin.setVisible(false);
            btnLogin.setManaged(false);
            boxGestionCuenta.setVisible(true);
            boxGestionCuenta.setManaged(true);
        } else {
            lblEmail.setText("No has iniciado sesión");
            lblEstadoCuenta.setText("Tu biblioteca solo se guarda en este PC");
            lblEstadoCuenta.setStyle("-fx-text-fill: gray; -fx-font-size: 14;");
            btnLogin.setVisible(true);
            btnLogin.setManaged(true);
            boxGestionCuenta.setVisible(false);
            boxGestionCuenta.setManaged(false);
        }
    }

    @FXML
    private void eliminarCuenta() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("¿Eliminar cuenta permanentemente?");
        alert.setHeaderText("Esta acción no se puede deshacer.");
        alert.setContentText("Se borrarán todos tus datos guardados en la nube, incluyendo tu biblioteca y progreso de forma definitiva.");
        darEstiloOscuro(alert);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {

            if (mainController != null) mainController.setCargando(true);

            authService.eliminarCuenta(
                    () -> Platform.runLater(() -> {
                        // Si todo sale bien
                        cargarDatosUsuario();
                        if (mainController != null) {
                            mainController.actualizarEstadoTopBar();
                            mainController.realizarBorradoCompleto(); // Borra local y resetea UI
                        }
                    }),
                    () -> Platform.runLater(() -> {
                        // Si falla (Firebase exige que los inicios de sesión sean recientes para borrar)
                        if (mainController != null) mainController.setCargando(false);

                        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                        errorAlert.setTitle("Error de Autenticación");
                        errorAlert.setHeaderText(null);
                        errorAlert.setContentText("Por motivos de seguridad, Firebase requiere que inicies sesión nuevamente antes de eliminar tu cuenta. Cierra sesión, vuelve a entrar e inténtalo de nuevo.");
                        darEstiloOscuro(errorAlert);
                        errorAlert.show();
                    })
            );
        }
    }

    @FXML
    private void toggleNotificaciones() {
        if (mainController != null) {
            mainController.getUserData().notificacionesActivas = chkNotificaciones.isSelected();
            mainController.guardarDatosGlobales();
        }
    }

    @FXML
    private void iniciarSesion() {
        if (mainController != null) mainController.mostrarCargaLogin();

        authService.iniciarSesionGoogle(
                () -> Platform.runLater(() -> {
                    String datosNube = authService.cargarDatosDeNube();
                    if (datosNube != null) {
                        try {
                            Files.writeString(new File(Main.APP_FOLDER, "biblioteca.json").toPath(), datosNube);
                            // Reiniciamos el MainController para que cargue la biblioteca bajada y aplique los listeners
                            mainController.initialize();
                        } catch (Exception e) { e.printStackTrace(); }
                    } else {
                        mainController.guardarDatosGlobales();
                        try {
                            String jsonLocal = Files.readString(new File(Main.APP_FOLDER, "biblioteca.json").toPath());
                            authService.guardarDatosEnNube(jsonLocal);
                        } catch (Exception e) { e.printStackTrace(); }
                    }

                    cargarDatosUsuario();
                    if (mainController != null) {
                        mainController.actualizarEstadoTopBar(); // Ocultamos el botón de la barra superior
                        mainController.setCargando(false);
                        mainController.abrirInicio();
                    }
                }),
                () -> Platform.runLater(() -> {
                    if (mainController != null) mainController.setCargando(false);
                })
        );
    }

    @FXML
    private void cambiarCuenta() {
        authService.cerrarSesion();
        iniciarSesion();
    }

    @FXML
    private void cerrarSesion() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("¿Cerrar Sesión?");
        alert.setHeaderText(null);
        alert.setContentText("Tu sesión en este PC se cerrará, pero tu biblioteca seguirá a salvo en la nube para cuando vuelvas.");
        darEstiloOscuro(alert);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            authService.cerrarSesion();
            cargarDatosUsuario();
            if (mainController != null) {
                mainController.actualizarEstadoTopBar(); // Volvemos a mostrar el botón en la barra superior
                mainController.realizarBorradoCompleto();
            }
        }
    }

    @FXML
    private void forzarActualizacion() {
        if (mainController != null) {
            mainController.setCargando(true);
            mainController.getUserData().lastUpdateTimestamp = 0L;
            mainController.getUserData().timestampsCapitulos.clear();
            mainController.guardarDatosGlobales();
            Platform.runLater(() -> mainController.abrirInicio());
        }
    }

    @FXML
    private void borrarDatosLocales() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("¿Borrar Datos Locales?");
        alert.setHeaderText("Esto vaciará tu biblioteca local y eliminará mangas descargados.");
        alert.setContentText("Si tu cuenta está vinculada a Google, tu biblioteca de la nube no se borrará. ¿Continuar?");
        darEstiloOscuro(alert);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (mainController != null) {
                mainController.realizarBorradoCompleto();
            }
        }
    }

    private void darEstiloOscuro(Alert alert) {
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #1E1E1E; -fx-border-color: #e50914; -fx-border-width: 1;");
        dialogPane.lookupAll(".label").forEach(node -> ((Label) node).setStyle("-fx-text-fill: white;"));
    }
}