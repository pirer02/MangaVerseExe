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
    public String getSinopsis() {
        return sinopsis;
    }
    public void setSinopsis(String sinopsis) {
        this.sinopsis = sinopsis;
    }
    public List<String> getGeneros() {
        return generos;
    }
    public void setGeneros(List<String> generos) {
        this.generos = generos;
    }
    public String getEstado() {
        return estado;
    }
    public void setEstado(String estado) {
        this.estado = estado;
    }
    public String getTipo() {
        return tipo;
    }
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
