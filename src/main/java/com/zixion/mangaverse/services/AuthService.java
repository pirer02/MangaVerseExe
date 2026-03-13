package com.zixion.mangaverse.services;

import com.sun.net.httpserver.HttpServer;
import com.zixion.mangaverse.Main;
import org.json.JSONObject;

import java.awt.Desktop;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

public class AuthService {

    private static final String API_KEY = "apioculta";
    private static final String CLIENT_ID = "clienteidoculto";
    private static final String PROJECT_ID = "projectidoculto";

    private final File ARCHIVO_SESION = new File(Main.APP_FOLDER, "session.json");
    private String firebaseIdToken = null;
    private String refreshToken = null; // <--- AÑADE ESTA LÍNEA
    private String uid = null;
    private String email = null;
    private HttpServer authServer = null; // Variable global para poder cancelarlo

    public AuthService() {
        cargarSesionLocal();
    }

    public boolean isLogueado() {
        return uid != null && firebaseIdToken != null;
    }

    public String getEmail() {
        return email;
    }

    public void iniciarSesionGoogle(Runnable onSuccess, Runnable onError) {
        try {
            authServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
            boolean[] procesoCompletado = {false}; // Control para el sistema de Timeout

            // CONTEXTO 1: Interceptamos el token o el error
            authServer.createContext("/", exchange -> {
                String html = "<html><body><script>" +
                        "var hash = window.location.hash;" +
                        "if(hash && hash.includes('id_token=')) {" +
                        "    var params = new URLSearchParams(hash.substring(1));" +
                        "    window.location.href = '/callback?id_token=' + params.get('id_token');" +
                        "} else { " +
                        "    window.location.href = '/callback?error=cancelado';" +
                        "}" +
                        "</script></body></html>";

                byte[] bytes = html.getBytes("UTF-8");
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            });

            // CONTEXTO 2: Confirmamos y procesamos
            authServer.createContext("/callback", exchange -> {
                try {
                    procesoCompletado[0] = true; // El servidor respondió, detenemos el timeout
                    String query = exchange.getRequestURI().getQuery();

                    // Si el usuario canceló explícitamente en la web de Google
                    if (query != null && query.contains("error=")) {
                        String html = "<html><body style='background:#141414; color:white; font-family:sans-serif; text-align:center; padding-top:50px;'>" +
                                "<h2>Login cancelado.</h2><p>Puedes cerrar esta ventana y volver a la aplicacion.</p></body></html>";
                        byte[] bytes = html.getBytes("UTF-8");
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                        exchange.getResponseBody().close();

                        new Thread(() -> {
                            try { Thread.sleep(1000); } catch (Exception e) {}
                            authServer.stop(0);
                            if (onError != null) onError.run(); // Esto oculta el spinner de carga
                        }).start();
                        return;
                    }

                    // Si el inicio de sesión fue un éxito
                    String idTokenGoogle = query.split("id_token=")[1].split("&")[0];

                    String html = "<html><body style='background:#141414; color:white; font-family:sans-serif; text-align:center; padding-top:50px;'>" +
                            "<h2>¡Autenticacion completada con exito!</h2><p>Ya puedes cerrar esta ventana y volver a MangaVerse.</p></body></html>";

                    byte[] bytes = html.getBytes("UTF-8");
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.getResponseBody().close();

                    new Thread(() -> {
                        try { Thread.sleep(1500); } catch (Exception e) {}
                        authServer.stop(0);
                        intercambiarTokenConFirebase(idTokenGoogle, onSuccess, onError);
                    }).start();
                } catch (Exception e) {
                    e.printStackTrace();
                    if (onError != null) onError.run();
                }
            });

            authServer.start();

            // SISTEMA DE TIMEOUT: Si a los 45 segundos no hay respuesta, abortamos.
            new Thread(() -> {
                try {
                    Thread.sleep(45000); // 45 segundos
                    if (!procesoCompletado[0]) {
                        System.out.println("Timeout: El usuario cerró el navegador o tardó demasiado.");
                        procesoCompletado[0] = true;
                        authServer.stop(0);
                        if (onError != null) onError.run(); // Oculta el spinner en la interfaz
                    }
                } catch (InterruptedException e) { }
            }).start();

            // Abrimos el navegador
            String oauthUrl = "dirección bloqueada" +
                    "client_id=" + CLIENT_ID +
                    "&redirect_uri=idbloqueada" +
                    "&response_type=id_token" +
                    "&scope=email%20profile%20openid" +
                    "&bloqueado";
            Desktop.getDesktop().browse(new URI(oauthUrl));

        } catch (Exception e) {
            e.printStackTrace();
            if (onError != null) onError.run();
        }
    }

    public void cancelarLogin() {
        if (authServer != null) {
            authServer.stop(0);
            authServer = null;
            System.out.println("[Auth] Espera de inicio de sesión cancelada por el usuario.");
        }
    }

    private void intercambiarTokenConFirebase(String googleIdToken, Runnable onSuccess, Runnable onError) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            JSONObject body = new JSONObject();
            body.put("postBody", "id_token=" + googleIdToken + "&providerId=google.com");
            body.put("requestUri", "http://localhost");
            body.put("returnIdpCredential", true);
            body.put("returnSecureToken", true);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=" + API_KEY))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                this.firebaseIdToken = json.getString("idToken");
                this.refreshToken = json.optString("refreshToken", null); // <--- AÑADE ESTA LÍNEA
                this.uid = json.getString("localId");
                this.email = json.optString("email", "Usuario");
                guardarSesionLocal();
                if (onSuccess != null) onSuccess.run();
            } else {
                if (onError != null) onError.run();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (onError != null) onError.run();
        }
    }

    // Este método usa el refreshToken para pedirle a Firebase una hora más de acceso
    public boolean refrescarToken() {
        if (refreshToken == null) return false;
        try {
            HttpClient client = HttpClient.newHttpClient();
            String body = "grant_type=refresh_token&refresh_token=" + refreshToken;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://securetoken.googleapis.com/v1/token?key=" + API_KEY))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                this.firebaseIdToken = json.getString("id_token"); // Ojo, la API lo devuelve con guión bajo
                this.refreshToken = json.getString("refresh_token");
                guardarSesionLocal();
                System.out.println("[Auth] Token renovado con éxito.");
                return true;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    // Tu método de carga, ahora inteligente
    public String cargarDatosDeNube() {
        if (!isLogueado()) return null;
        try {
            String url = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/usuarios/" + uid;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + firebaseIdToken)
                    .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Si el token caducó (Error 401), lo renovamos y reintentamos mágicamente
            if (response.statusCode() == 401) {
                System.out.println("[Auth] Llave caducada. Intentando renovar...");
                if (refrescarToken()) {
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Bearer " + firebaseIdToken)
                            .GET().build();
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                } else {
                    System.out.println("[Auth] Error: Debes volver a iniciar sesión.");
                    cerrarSesion();
                    return null;
                }
            }

            if (response.statusCode() == 200) {
                JSONObject root = new JSONObject(response.body());
                return root.getJSONObject("fields").getJSONObject("jsonData").getString("stringValue");
            } else {
                System.out.println("[Nube] Error al descargar: Código " + response.statusCode());
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }


    public void eliminarCuenta(Runnable onSuccess, Runnable onError) {
        if (!isLogueado()) return;
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();

                // 1. PASO CLAVE: Borramos los datos en Firestore PRIMERO
                String firestoreUrl = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/usuarios/" + uid;
                HttpRequest requestDb = HttpRequest.newBuilder()
                        .uri(URI.create(firestoreUrl))
                        .header("Authorization", "Bearer " + firebaseIdToken)
                        .DELETE()
                        .build();

                // Enviamos la petición para borrar la BD.
                // No frenamos el proceso si falla (puede que el usuario no tuviera datos guardados aún y devuelva 404).
                HttpResponse<String> dbResponse = client.send(requestDb, HttpResponse.BodyHandlers.ofString());
                System.out.println("[Auth] Borrado de BD: Código " + dbResponse.statusCode());

                // 2. SEGUNDO PASO: Borramos la cuenta de Firebase Authentication
                JSONObject body = new JSONObject();
                body.put("idToken", firebaseIdToken);

                HttpRequest requestAuth = HttpRequest.newBuilder()
                        .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:delete?key=" + API_KEY))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> responseAuth = client.send(requestAuth, HttpResponse.BodyHandlers.ofString());

                if (responseAuth.statusCode() == 200) {
                    System.out.println("[Auth] Cuenta de Google desvinculada y eliminada con éxito.");
                    cerrarSesion();
                    if (onSuccess != null) onSuccess.run();
                } else {
                    // Si falla (ej. el token caducó y requiere inicio de sesión reciente)
                    System.err.println("Error al borrar cuenta de Auth: " + responseAuth.body());
                    if (onError != null) onError.run();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (onError != null) onError.run();
            }
        }).start();
    }

    public void guardarDatosEnNube(String jsonData) {
        if (!isLogueado()) return;
        new Thread(() -> {
            try {
                String url = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/usuarios/" + uid + "?updateMask.fieldPaths=jsonData";

                JSONObject stringValue = new JSONObject();
                stringValue.put("stringValue", jsonData);
                JSONObject jsonDataObj = new JSONObject();
                jsonDataObj.put("jsonData", stringValue);
                JSONObject fields = new JSONObject();
                fields.put("fields", jsonDataObj);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + firebaseIdToken)
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(fields.toString()))
                        .build();

                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void cerrarSesion() {
        this.firebaseIdToken = null;
        this.uid = null;
        this.email = null;
        if (ARCHIVO_SESION.exists()) ARCHIVO_SESION.delete();
    }

    private void guardarSesionLocal() {
        try {
            JSONObject json = new JSONObject();
            json.put("idToken", firebaseIdToken);
            json.put("refreshToken", refreshToken); // <--- AÑADE ESTA LÍNEA
            json.put("uid", uid);
            json.put("email", email);
            Files.writeString(ARCHIVO_SESION.toPath(), json.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void cargarSesionLocal() {
        if (ARCHIVO_SESION.exists()) {
            try {
                JSONObject json = new JSONObject(Files.readString(ARCHIVO_SESION.toPath()));
                this.firebaseIdToken = json.optString("idToken", null);
                this.refreshToken = json.optString("refreshToken", null); // <--- AÑADE ESTA LÍNEA
                this.uid = json.optString("uid", null);
                this.email = json.optString("email", null);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}