plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.example.authapp.Main")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("org.json:json:20230618")
}

// Переопределяем запуск чтобы использовать classpath
tasks.named<JavaExec>("run") {
    modularity.inferModulePath.set(false)

    // Явно указываем использовать classpath вместо modules
    doFirst {
        println("=== USING CLASSPATH MODE ===")
        println("JavaFX version: 21")
        println("Modules: javafx.controls, javafx.fxml")
    }
}