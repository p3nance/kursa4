plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("org.json:json:20230618")
    implementation("com.google.code.gson:gson:2.10.1")
}

application {
    // Точка входа при запуске из IDE/Gradle
    mainClass.set("com.example.authapp.Main")
    // Пробрасываем кодировку в JavaExec, чтобы консоль была UTF-8
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dconsole.encoding=UTF-8"
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

/**
 * Единая UTF-8 кодировка:
 * - компиляция Java
 * - тесты
 * - любой JavaExec (вкл. run)
 */
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
tasks.withType<Test>().configureEach {
    systemProperty("file.encoding", "UTF-8")
    jvmArgs("-Dconsole.encoding=UTF-8")
}
tasks.withType<JavaExec>().configureEach {
    systemProperty("file.encoding", "UTF-8")
    jvmArgs("-Dconsole.encoding=UTF-8")
}

/**
 * Рекомендуется также в gradle.properties (в корне проекта или ~/.gradle):
 * org.gradle.jvmargs=-Dfile.encoding=UTF-8
 * Это задаст UTF-8 для Gradle-демона и скриптов сборки.
 */

/**
 * FAT (uber) JAR с Main-Class = Launcher.
 * Если точка входа другая — поменяй имена.
 */
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.example.authapp.Launcher"
    }
    // Включаем зависимости внутрь JAR
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    // Убираем дубликаты и конфликтные сигнатуры
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.txt")
    // На больших проектах иногда полезно:
    // zip64 = true
}
