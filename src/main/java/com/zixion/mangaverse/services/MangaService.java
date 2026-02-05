package com.zixion.mangaverse.services;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.zixion.mangaverse.Main;
import com.zixion.mangaverse.models.Manga;
import org.json.JSONArray;
import org.json.JSONObject;

public class MangaService {
    private String IP_LOCAL = "ipoculto/";
    private String IP_PUBLICA = "http://95.61.154.61:5000/";

    private final String CACHE_FILE = Main.LISTADO_FOLDER + File.separator + "cache_capitulos.json";

    // Cache temporal en memoria para no saturar el servidor con cada fila de Netflix
    private List<Manga> cacheMangasMemoria = new ArrayList<>();

    private boolean isServerAlive(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofMillis(300))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "mangas"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String getBaseUrl() {
        if (isServerAlive(IP_LOCAL)) {
            return IP_LOCAL;
        }
        return IP_PUBLICA;
    }

    /**
     * MÉTODO NUEVO: Filtra los mangas por género.
     * Si no tienes un endpoint en la API para esto, filtramos la lista general en Java.
     */
    public List<Manga> obtenerMangasPorGenero(String genero) {
        // Si la caché está vacía, cargamos primero
        if (cacheMangasMemoria.isEmpty()) {
            obtenerMangasDesdeServidor();
        }

        // Filtramos por género (esto asume que el objeto Manga tiene la lista de géneros cargada)
        // Como la carga inicial no trae géneros, aquí podrías llamar a un endpoint
        // específico si tu API lo soporta, ej: url/mangas/filter?genre=shonen

        // Simulación: Si no hay filtro en API, devolvemos una sublista aleatoria para el diseño
        List<Manga> filtrados = new ArrayList<>(cacheMangasMemoria);
        Collections.shuffle(filtrados);
        return filtrados.stream().limit(10).collect(Collectors.toList());
    }

    public List<Manga> obtenerMangasDesdeServidor() {
        List<Manga> lista = new ArrayList<>();
        String urlFinal = getBaseUrl();

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlFinal + "mangas"))
                    .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONArray jsonArray = new JSONArray(response.body());
                for (int i = 0; i < jsonArray.length(); i++) {
                    String nombreCarpeta = jsonArray.getString(i);
                    String nombreVisual = nombreCarpeta.replace("_", " ").replace("-", " ");
                    String urlPortada = getBaseUrl() + "mangas/" + nombreCarpeta + "/portada";

                    Manga manga = new Manga(nombreVisual, null, null, null, null, null, null);
                    manga.setUrlPortada(urlPortada);
                    lista.add(manga);
                }
                this.cacheMangasMemoria = lista; // Guardamos en memoria
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    public List<String> obtenerCapitulos(String mangaNombre) {
        // Normalizar nombre para URL (espacios a guiones bajos)
        String mangaId = mangaNombre.replace(" ", "_");

        List<String> capsCache = leerCacheCapitulos(mangaId);
        if (!capsCache.isEmpty()) return capsCache;

        List<String> caps = new ArrayList<>();
        String urlFinal = getBaseUrl();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlFinal + "mangas/" + mangaId + "/capitulos"))
                    .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() == 200){
                JSONArray array = new JSONArray(response.body());
                for (int i = 0; i < array.length(); i++) {
                    caps.add(array.getString(i));
                }
                guardarEnCache(mangaId, caps);
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return caps;
    }

    public File descargarArchivo(String mangaNombre, String nombreCapitulo) throws Exception {
        String mangaId = mangaNombre.replace(" ", "_");
        String urlFinal = getBaseUrl();
        File destination = new File(Main.CAPITULOS_FOLDER, nombreCapitulo);

        if (destination.exists()) return destination;

        // URL encode para caracteres especiales en el nombre del capítulo
        String nombreCapEncoded = nombreCapitulo.replace(" ", "%20");
        String urlDescarga = urlFinal + "download/" + mangaId + "/" + nombreCapEncoded;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlDescarga))
                .GET().build();

        HttpResponse<java.nio.file.Path> response = client.send(request,
                HttpResponse.BodyHandlers.ofFile(destination.toPath()));

        if (response.statusCode() == 200) return destination;
        else throw new IOException("Error en servidor: " + response.statusCode());
    }

    public Manga obtenerInfoManga(String mangaNombre) {
        String mangaId = mangaNombre.replace(" ", "_");
        String urlFinal = getBaseUrl() + "mangas/" + mangaId + "/info";
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlFinal.replace(" ", "%20")))
                    .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                String titulo = json.optString("titulo", mangaNombre);
                String sinopsis = json.optString("descripcion", "No hay descripción.");
                String estado = json.optString("estado", "Desconocido");
                String tipo = json.optString("tipo", "Manga");

                List<String> generos = new ArrayList<>();
                JSONArray arr = json.optJSONArray("generos");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) generos.add(arr.getString(i));
                }
                Manga m = new Manga(titulo, null, null, sinopsis, generos, estado, tipo);
                m.setUrlPortada(getBaseUrl() + "mangas/" + mangaId + "/portada");
                return m;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return new Manga(mangaNombre, null, null, "Sin descripción", new ArrayList<>(), "", "");
    }

    // --- MÉTODOS DE CACHÉ JSON ---
    private void guardarEnCache(String mangaNombre, List<String> capitulos) {
        try {
            File file = new File(CACHE_FILE);
            JSONObject root = file.exists() ? new JSONObject(Files.readString(file.toPath())) : new JSONObject();
            root.put(mangaNombre, new JSONArray(capitulos));
            Files.writeString(file.toPath(), root.toString());
        } catch (Exception e) { }
    }

    private List<String> leerCacheCapitulos(String mangaNombre) {
        List<String> caps = new ArrayList<>();
        try {
            File file = new File(CACHE_FILE);
            if (!file.exists()) return caps;
            JSONObject root = new JSONObject(Files.readString(file.toPath()));
            if (root.has(mangaNombre)) {
                JSONArray array = root.getJSONArray(mangaNombre);
                for (int i = 0; i < array.length(); i++) caps.add(array.getString(i));
            }
        } catch (Exception e) { }
        return caps;
    }
}