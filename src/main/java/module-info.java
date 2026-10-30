module com.example.authapp {

    requires javafx.controls;
    requires javafx.fxml;


    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;
    requires java.net.http;
    requires org.json;
    requires com.google.gson;
    requires java.sql;



    opens com.example.authapp to javafx.fxml;
    opens controllers to javafx.fxml;
    opens com.example.authapp.dto to com.google.gson, com.fasterxml.jackson.databind;
    exports com.example.authapp;
}
