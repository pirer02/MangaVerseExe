package com.zixion.mangaverse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

public class MangaService {

    // IP del servidor dentro de la casa de Pablo
    private String IP_LOCAL = "ipoculto/";
    // Tu IP pública que ya tienes puesta
    private String IP_PUBLICA = "http://95.61.154.61:5000/";

    private String getBaseUrl() {
        try {
            // Intentamos ver si la IP local responde en 200ms
            if (java.net.InetAddress.getByName("192.168.0.XX").isReachable(200)) {
                return IP_LOCAL;
            }
        } catch (Exception e) {
            // Si falla, asumimos que estamos fuera
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
