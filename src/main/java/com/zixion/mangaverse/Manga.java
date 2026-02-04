package com.zixion.mangaverse;

import javafx.scene.image.Image;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

public class Manga {
    private String titulo;
    private File archivo;
    private Image portada;
    private String urlPortada;

    public Manga(String titulo, File archivo, Image portada) {
        this.titulo = titulo;
        this.archivo = archivo;
        this.portada = portada;
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
