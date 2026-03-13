package com.zixion.mangaverse.models;

import java.util.*;

public class UserData {
    public Set<String> biblioteca = new HashSet<>();
    public Map<String, String> historial = new HashMap<>();
    public Map<String, Set<String>> capitulosLeidos = new HashMap<>();
    public Map<String, Integer> progresoPagina = new HashMap<>();
    public Map<String, Long> timestampsCapitulos = new HashMap<>();
    public long lastUpdateTimestamp = 0L; // <--- Clave para la sincronización
    public boolean notificacionesActivas = true;
    public Map<String, List<Musica>> canciones = new HashMap<>();

    public UserData() {}
}