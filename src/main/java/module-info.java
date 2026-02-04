module com.zixion.mangaverse {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.net.http;
    requires org.json;


    opens com.zixion.mangaverse to javafx.fxml;
    exports com.zixion.mangaverse;
    exports com.zixion.mangaverse.controllers;
    opens com.zixion.mangaverse.controllers to javafx.fxml;
    exports com.zixion.mangaverse.models;
    opens com.zixion.mangaverse.models to javafx.fxml;
    exports com.zixion.mangaverse.services;
    opens com.zixion.mangaverse.services to javafx.fxml;
}