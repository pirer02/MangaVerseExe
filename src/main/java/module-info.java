module com.zixion.mangaverse {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;


    opens com.zixion.mangaverse to javafx.fxml;
    exports com.zixion.mangaverse;
}