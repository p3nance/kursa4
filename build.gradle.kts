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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("org.json:json:20230618")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.testfx:openjfx-monocle:21.0.2")
}

application {
    mainClass.set("com.example.authapp.Launcher")
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
tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.httpserver,java.desktop",
        "--add-reads", "com.example.authapp=jdk.httpserver", "--add-reads", "com.example.authapp=java.desktop"))
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Tests use fake APIs and never need local or production credentials.
    systemProperty("techstore.supabase.key", "sb_publishable_test")
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("headless.geometry", "1920x1200")
    systemProperty("prism.order", "sw")
    systemProperty("file.encoding", "UTF-8")
    jvmArgs("-Dconsole.encoding=UTF-8")
    jvmArgs("--add-modules=jdk.httpserver,java.desktop")
    systemProperty("javafx.cachedir", layout.buildDirectory.dir("javafx-cache").get().asFile.absolutePath)
}
tasks.withType<JavaExec>().configureEach {
    systemProperty("file.encoding", "UTF-8")
    jvmArgs("-Dconsole.encoding=UTF-8")
}

tasks.test { useJUnitPlatform { excludeTags("ui") } }
val uiTest by tasks.registering(Test::class) {
    description = "Loads every JavaFX screen against a local fake API and renders snapshots."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("ui") }
    shouldRunAfter(tasks.test)
}
tasks.check { dependsOn(uiTest) }

// Launch4j exe build config
launch4j {
    mainClassName = "com.example.authapp.Launcher"
    outfile = "AuthApp.exe"
        // icon = "${projectDir}/app.ico" // уберите если нет иконки
    jarTask = tasks.jar.get()
    dontWrapJar = false
    jreMinVersion = "21"
}

// Windows packaging is explicit: ./gradlew createExe. Normal builds also run on Linux.

// Verify embedded client knowledge from a directory without local sidecars or credentials.
val packagedKnowledgeTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the built client JAR's component facts and benchmarks without external knowledge."
    dependsOn(tasks.jar, tasks.testClasses)
    classpath = files(tasks.jar.flatMap { it.archiveFile }, sourceSets["test"].output)
    mainClass.set("com.example.authapp.domain.PackagedKnowledgeProbe")
    workingDir(layout.buildDirectory.dir("packaged-knowledge-test"))
    doFirst { workingDir.mkdirs() }
}
tasks.check { dependsOn(packagedKnowledgeTest) }

val profileRecommendations by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Writes optional offline recommendation timings for the public catalog snapshot."
    dependsOn(tasks.jar, tasks.testClasses)
    classpath = files(tasks.jar.flatMap { it.archiveFile }, sourceSets["test"].output)
    mainClass.set("com.example.authapp.domain.RecommendationProfile")
    workingDir(layout.buildDirectory.dir("recommendation-profile"))
    args(layout.buildDirectory.file("reports/recommendation-profile.json").get().asFile.absolutePath)
    doFirst { workingDir.mkdirs() }
}
