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

    // CAMBIO 1: Usamos un directorio en lugar de un solo archivo
    private final File CACHE_DIR = new File(Main.LISTADO_FOLDER, "cache_capitulos");

    // Cache temporal en memoria para lista de mangas
    private List<Manga> cacheMangasMemoria = new ArrayList<>();

    public MangaService() {
        // Aseguramos que la carpeta de caché exista al iniciar el servicio
        if (!CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
        }
    }

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

    // ... (El método obtenerMangasPorGenero se mantiene igual) ...
    public List<Manga> obtenerMangasPorGenero(String genero) {
        if (cacheMangasMemoria.isEmpty()) {
            obtenerMangasDesdeServidor();
        }
        List<Manga> filtrados = new ArrayList<>(cacheMangasMemoria);
        Collections.shuffle(filtrados);
        return filtrados.stream().limit(10).collect(Collectors.toList());
    }

    // ... (El método obtenerMangasDesdeServidor se mantiene igual) ...
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
                this.cacheMangasMemoria = lista;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    /**
     * CAMBIO 2: Método obtenerCapitulos con lógica de expiración
     * @param mangaNombre El nombre del manga
     */
    public List<String> obtenerCapitulos(String mangaNombre, Manga manga) {
        String mangaId = mangaNombre.replace(" ", "_");
        File mangaCacheFile = new File(CACHE_DIR, mangaId + ".json");

        // Verificamos si podemos usar la caché
        if (debeUsarCache(mangaCacheFile, manga.getEstado())) {
            System.out.println("Usando caché para: " + mangaNombre);
            return leerCacheIndividual(mangaCacheFile);
        }

        System.out.println("Descargando lista fresca para: " + mangaNombre);
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
                // Guardamos en archivo individual
                guardarEnCacheIndividual(mangaCacheFile, caps);
            }
        } catch (Exception e) {
            // Fallback: Si falla la red, intentamos devolver caché aunque sea vieja
            if (mangaCacheFile.exists()) {
                return leerCacheIndividual(mangaCacheFile);
            }
            return new ArrayList<>();
        }
        return caps;
    }

    // CAMBIO 3: Lógica de decisión de caché (La "Condición")
    private boolean debeUsarCache(File archivo, String estado) {
        // 1. Si no existe el archivo, no hay caché que valga
        if (!archivo.exists()) return false;

        // 2. Si el estado es FINALIZADO, la caché es válida para siempre
        if (estado != null && estado.toUpperCase().contains("FINALIZADO")) {
            return true;
        }

        // 3. Si está en emisión (o estado desconocido), usamos TTL de 24 horas
        long ttlMillis = 24 * 60 * 60 * 1000; // 24 horas
        long diferencia = System.currentTimeMillis() - archivo.lastModified();

        // Si la diferencia es menor al TTL, la caché es válida. Si es mayor, devolvemos false (refrescar).
        return diferencia < ttlMillis;
    }


    // ... (El método descargarArchivo se mantiene igual) ...
    public File descargarArchivo(String mangaNombre, String nombreCapitulo) throws Exception {
        String mangaId = mangaNombre.replace(" ", "_");
        String urlFinal = getBaseUrl();
        File destination = new File(Main.CAPITULOS_FOLDER, nombreCapitulo);

        if (destination.exists()) return destination;

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

    // ... (El método obtenerInfoManga se mantiene igual) ...
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

    // --- NUEVOS MÉTODOS DE CACHÉ INDIVIDUAL ---

    private void guardarEnCacheIndividual(File archivo, List<String> capitulos) {
        try {
            // Creamos un JSON simple con la lista
            JSONArray jsonArray = new JSONArray(capitulos);
            // Sobreescribimos el archivo. Al escribir, se actualiza automáticamente el 'lastModified'
            Files.writeString(archivo.toPath(), jsonArray.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> leerCacheIndividual(File archivo) {
        List<String> caps = new ArrayList<>();
        try {
            String content = Files.readString(archivo.toPath());
            JSONArray array = new JSONArray(content);
            for (int i = 0; i < array.length(); i++) {
                caps.add(array.getString(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return caps;
    }
}