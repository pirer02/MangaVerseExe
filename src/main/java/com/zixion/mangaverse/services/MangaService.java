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

    private final File CACHE_DIR = new File(Main.LISTADO_FOLDER, "cache_capitulos");
    private List<Manga> cacheMangasMemoria = new ArrayList<>();

    public MangaService() {
        if (!CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
        }
    }

    public void limpiarCacheMemoria() {
        this.cacheMangasMemoria.clear();
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

    public List<Manga> obtenerMangasPorGenero(String genero) {
        if (cacheMangasMemoria.isEmpty()) {
            obtenerMangasDesdeServidor();
        }
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
                    // Añadimos timestamp para evitar caché de imágenes viejas si se cambian en el server
                    String urlPortada = getBaseUrl() + "mangas/" + nombreCarpeta + "/portada?v=" + System.currentTimeMillis();

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

    public boolean verificarExistenciaColor(String mangaNombre) {
        String mangaId = mangaNombre.replace(" ", "_");
        String urlFinal = getBaseUrl() + "mangas/" + mangaId + "/color/capitulos";

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(urlFinal)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONArray array = new JSONArray(response.body());
                return array.length() > 0;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    // Método actualizado con parámetro 'forzarActualizacion'
    public List<String> obtenerCapitulos(String mangaNombre, Manga manga, boolean isColor, boolean forzarActualizacion) {
        String mangaId = mangaNombre.replace(" ", "_");
        String sufijoCache = isColor ? "_color.json" : ".json";
        File mangaCacheFile = new File(CACHE_DIR, mangaId + sufijoCache);

        if (forzarActualizacion && mangaCacheFile.exists()) {
            mangaCacheFile.delete();
        }

        if (debeUsarCache(mangaCacheFile, manga.getEstado())) {
            System.out.println("Usando caché " + (isColor ? "(Color)" : "(Normal)") + " para: " + mangaNombre);
            return leerCacheIndividual(mangaCacheFile);
        }

        System.out.println("Descargando lista fresca " + (isColor ? "(Color)" : "(Normal)") + " para: " + mangaNombre);
        List<String> caps = new ArrayList<>();
        String urlFinal = getBaseUrl();

        try {
            HttpClient client = HttpClient.newHttpClient();
            String rutaEndpoint = isColor ? "/mangas/" + mangaId + "/color/capitulos"
                    : "/mangas/" + mangaId + "/capitulos";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlFinal + rutaEndpoint))
                    .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() == 200){
                JSONArray array = new JSONArray(response.body());
                for (int i = 0; i < array.length(); i++) {
                    caps.add(array.getString(i));
                }
                guardarEnCacheIndividual(mangaCacheFile, caps);
            }
        } catch (Exception e) {
            if (mangaCacheFile.exists()) {
                return leerCacheIndividual(mangaCacheFile);
            }
            return new ArrayList<>();
        }
        return caps;
    }

    private boolean debeUsarCache(File archivo, String estado) {
        if (!archivo.exists()) return false;
        if (estado != null && estado.toUpperCase().contains("FINALIZADO")) {
            return true;
        }
        // CAMBIO: Caché de 1 hora
        long ttlMillis = 1 * 60 * 60 * 1000;
        long diferencia = System.currentTimeMillis() - archivo.lastModified();
        return diferencia < ttlMillis;
    }

    public File descargarArchivo(String mangaNombre, String nombreCapitulo, boolean isColor) throws Exception {
        String mangaId = mangaNombre.replace(" ", "_");
        String urlFinal = getBaseUrl();

        File carpetaDestino = isColor ? new File(Main.CAPITULOS_COLOR_FOLDER) : new File(Main.CAPITULOS_FOLDER);
        File destination = new File(carpetaDestino, nombreCapitulo);

        if (destination.exists()) return destination;

        String nombreCapEncoded = nombreCapitulo.replace(" ", "%20");

        String urlDescarga;
        if (isColor) {
            urlDescarga = urlFinal + "download/" + mangaId + "/color/" + nombreCapEncoded;
        } else {
            urlDescarga = urlFinal + "download/" + mangaId + "/" + nombreCapEncoded;
        }

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

    private void guardarEnCacheIndividual(File archivo, List<String> capitulos) {
        try {
            JSONArray jsonArray = new JSONArray(capitulos);
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