plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("edu.sc.seis.launch4j") version "2.5.0"  // плагин для создания exe
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

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.example.authapp.Launcher"
    }
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.txt")
}

/**
 * Конфигурация launch4j для создания exe
 */
launch4j {
    mainClassName = "com.example.authapp.Launcher"
    outfile = "AuthApp.exe"
    icon = "${projectDir}/app.ico"  // если есть иконка
    jarTask = tasks.jar.get()
    dontWrapJar = false // оборачиваем jar в exe
    jreMinVersion = "17"
    // Дополнительные настройки, если нужно:
    // classpath, headerType, jvmOptions, etc.
}

tasks.build {
    dependsOn(tasks.launch4j)
}
