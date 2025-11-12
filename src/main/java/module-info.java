module com.example.authapp {
    requires javafx.controls;
    requires javafx.fxml;

    requires kotlin.stdlib;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;
    requires java.net.http;
    requires org.json;
    requires com.google.gson;


    opens com.example.authapp to javafx.fxml;
    exports com.example.authapp;
}