module org.dba {
    requires javafx.controls;
    requires javafx.fxml;
    requires kotlin.stdlib;
    requires com.google.gson;
    requires org.slf4j;

    opens org.dba to javafx.fxml, com.google.gson;
    exports org.dba;
}