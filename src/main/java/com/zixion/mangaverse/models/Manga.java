package com.zixion.mangaverse.models;

import javafx.scene.image.Image;

import java.io.File;
import java.util.List;

public class Manga {
    private String titulo;
    private File archivo;
    private Image portada;
    private String urlPortada;

    public String sinopsis;
    public List<String> generos;
    public String estado;
    public String tipo;

    public Manga(String titulo, File archivo, Image portada, String sinopsis, List<String> generos, String estado, String tipo) {
        this.titulo = titulo;
        this.archivo = archivo;
        this.portada = portada;
        this.sinopsis = sinopsis;
        this.generos = generos;
        this.estado = estado;
        this.tipo = tipo;
    }

    public String getTitulo() {
        return titulo;
    }
    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }
    public File getArchivo() {
        return archivo;
    }
    public void setArchivo(File archivo) {
        this.archivo = archivo;
    }
    public Image getPortada() {
        return portada;
    }
    public void setPortada(Image portada) {
        this.portada = portada;
    }
    public String getUrlPortada() {
        return urlPortada;
    }
    public void setUrlPortada(String urlPortada) {
        this.urlPortada = urlPortada;
    }
}
