package com.zixion.mangaverse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

public class MangaService {

    private String BASE_URL = "http://95.61.154.61:5000/";

    public List<Manga> obtenerMangasDesdeServidor() {
        List<Manga> lista = new ArrayList<>();
        System.out.println("Intentando conectar con la API en: " + BASE_URL); // Log de inicio

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "mangas")) // Usando el endpoint de la API Python
                    .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Código de respuesta del servidor: " + response.statusCode());

            if (response.statusCode() == 200) {
                System.out.println("Cuerpo recibido: " + response.body());
                JSONArray jsonArray = new JSONArray(response.body());
                for (int i = 0; i < jsonArray.length(); i++) {
                    String nombre = jsonArray.getString(i);
                    lista.add(new Manga(nombre, null, null));
                }
                System.out.println("Mangas procesados: " + lista.size());
            } else {
                System.err.println("Error: El servidor respondió con algo distinto a 200 OK");
            }
        } catch (Exception e) {
            System.err.println("¡FALLO DE CONEXIÓN! Asegúrate de que el contenedor esté corriendo y los puertos abiertos.");
            e.printStackTrace();
        }
        return lista;
    }
}
