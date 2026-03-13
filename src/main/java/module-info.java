module com.zixion.mangaverse {
    // 1. Módulos principales de JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.media;

    // 2. Módulos estándar de Java
    requires java.net.http; // Necesario para descargar capítulos y conectar con Firebase
    requires java.desktop;  // ¡El salvavidas! Permite usar ImageIO (para el WebP) y abrir el navegador web

    requires jdk.httpserver;


    // 3. Librerías externas
    requires org.json;      // Para leer y escribir los archivos de guardado y configuración

    // 4. Permisos de acceso (Para que JavaFX pueda inyectar la vista en tus clases)
    opens com.zixion.mangaverse to javafx.fxml;
    exports com.zixion.mangaverse;

    opens com.zixion.mangaverse.controllers to javafx.fxml;
    exports com.zixion.mangaverse.controllers;

    exports com.zixion.mangaverse.models;
    exports com.zixion.mangaverse.services;
}