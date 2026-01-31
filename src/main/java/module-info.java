module com.zixion.mangaverse {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.zixion.mangaverse to javafx.fxml;
    exports com.zixion.mangaverse;
}