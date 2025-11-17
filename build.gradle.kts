plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
    id("edu.sc.seis.launch4j") version "2.5.0"
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
    mainClass.set("com.example.authapp.Main")
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

// FAT (uber) JAR с Launcher как точкой входа
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

// Кодировка UTF-8 для компиляции и запуска
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

// Launch4j exe build config
launch4j {
    mainClassName = "com.example.authapp.Launcher"
    outfile = "AuthApp.exe"
        // icon = "${projectDir}/app.ico" // уберите если нет иконки
    jarTask = tasks.jar.get()
    dontWrapJar = false
    jreMinVersion = "21"
}

// Правильный порядок задач (решение ваших ошибок)
tasks.named("distZip") {
    dependsOn(tasks.named("createExe"))
}
tasks.named("distTar") {
    dependsOn(tasks.named("createExe"))
}
tasks.named("startScripts") {
    dependsOn(tasks.named("createExe"))
}

// Можно также добавить:
tasks.build {
    dependsOn(tasks.launch4j)
}
