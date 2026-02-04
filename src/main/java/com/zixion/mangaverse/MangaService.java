package com.zixion.mangaverse;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class MangaService {
    private String IP_LOCAL = "ipoculto/";
    private String IP_PUBLICA = "http://95.61.154.61:5000/";

    private final String CACHE_FILE = Main.LISTADO_FOLDER + File.separator + "cache_capitulos.json";

    private boolean isServerAlive(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofMillis(300)) // Timeout rápido
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "mangas")) // Probamos el endpoint principal
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String getBaseUrl() {
        // Intentamos local, si no, pública
        if (isServerAlive(IP_LOCAL)) {
            return IP_LOCAL;
        }
        return IP_PUBLICA;
    }

    public List<Manga> obtenerMangasDesdeServidor() {
        List<Manga> lista = new ArrayList<>();
        String urlFinal = getBaseUrl();
        System.out.println("Usando conexión: " + urlFinal);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlFinal + "mangas")) // Usando el endpoint de la API Python
                    .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Código de respuesta del servidor: " + response.statusCode());

            if (response.statusCode() == 200) {
                JSONArray jsonArray = new JSONArray(response.body());
                for (int i = 0; i < jsonArray.length(); i++) {
                    String nombreCarpeta = jsonArray.getString(i); // Ej: "Air_gear"

                    // 1. Limpiamos el nombre para la interfaz
                    String nombreVisual = nombreCarpeta.replace("_", " ").replace("-", " ");

                    // 2. Creamos la URL hacia el nuevo endpoint de Flask
                    String urlPortada = getBaseUrl() + "mangas/" + nombreCarpeta + "/portada";

                    // 3. Creamos el objeto
                    Manga manga = new Manga(nombreVisual, null, null);
                    manga.setUrlPortada(urlPortada);
                    lista.add(manga);
                }
            } else {
                System.err.println("Error: El servidor respondió con algo distinto a 200 OK");
            }
        } catch (Exception e) {
            System.err.println("¡FALLO DE CONEXIÓN! Asegúrate de que el contenedor esté corriendo y los puertos abiertos.");
            e.printStackTrace();
        }
        return lista;
    }

    public List<String> obtenerCapitulos(String mangaNombre) {
        List<String> capsCache = leerCacheCapitulos(mangaNombre);
        if (!capsCache.isEmpty()) {
            System.out.println("Cargando capítulos desde caché para: " + mangaNombre);
            return capsCache;
        }
        List<String> caps = new ArrayList<>();
        String urlFinal = getBaseUrl();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlFinal + "mangas/" + mangaNombre + "/capitulos"))
                    .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() == 200){
                JSONArray array = new JSONArray(response.body());
                for (int i = 0; i < array.length(); i++) {
                    caps.add(array.getString(i));
                }
                guardarEnCache(mangaNombre, caps);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
        return caps;
    }

    public File descargarArchivo(String mangaNombre, String nombreCapitulo) throws Exception {
        String urlFinal = getBaseUrl();
        // 1. Definimos dónde debería estar el archivo
        File destination = new File(Main.CAPITULOS_FOLDER, nombreCapitulo);

        if (destination.exists()) {
            System.out.println("Archivo encontrado en capitulos-cache: " + nombreCapitulo);
            return destination;
        }

        // 3. Si no existe, procedemos con la descarga normal
        String urlDescarga = urlFinal + "download/" + mangaNombre + "/" + nombreCapitulo;
        System.out.println("Descargando desde: " + urlDescarga);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlDescarga.replace(" ", "%20")))
                .GET()
                .build();

        HttpResponse<java.nio.file.Path> response = client.send(request,
                HttpResponse.BodyHandlers.ofFile(destination.toPath()));

        if (response.statusCode() == 200) {
            return destination;
        } else {
            throw new IOException("Error en servidor: " + response.statusCode());
        }
    }

    private void guardarEnCache(String mangaNombre, List<String> capitulos) {
        try {
            File file = new File(CACHE_FILE);
            JSONObject root = file.exists() ? new JSONObject(Files.readString(file.toPath())) : new JSONObject();

            root.put(mangaNombre, new JSONArray(capitulos));
            Files.writeString(file.toPath(), root.toString());
        } catch (Exception e) {
            System.err.println("No se pudo guardar la caché de capítulos.");
        }
    }

    private List<String> leerCacheCapitulos(String mangaNombre) {
        List<String> caps = new ArrayList<>();
        try {
            File file = new File(CACHE_FILE);
            if (!file.exists()) return caps;

            JSONObject root = new JSONObject(Files.readString(file.toPath()));
            if (root.has(mangaNombre)) {
                JSONArray array = root.getJSONArray(mangaNombre);
                for (int i = 0; i < array.length(); i++) {
                    caps.add(array.getString(i));
                }
            }
        } catch (Exception e) {
            // Error leyendo caché, devolvemos lista vacía para forzar descarga
        }
        return caps;
    }
}
