package com.zixion.mangaverse.models;

public class Musica {
    private String nombre;
    private String nombreArchivo; // El nombre del archivo .mp3 físico

    public Musica(String nombre, String nombreArchivo) {
        this.nombre = nombre;
        this.nombreArchivo = nombreArchivo;
    }

    public String getNombre() { return nombre; }
    public String getNombreArchivo() { return nombreArchivo; }

    @Override
    public String toString() { return nombre; } // Para que el ComboBox muestre el nombre
}