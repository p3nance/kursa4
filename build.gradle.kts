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
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.httpserver,java.desktop,java.management",
        "--add-reads", "com.example.authapp=jdk.httpserver", "--add-reads", "com.example.authapp=java.desktop",
        "--add-reads", "com.example.authapp=java.management"))
}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Tests use fake APIs and never need local or production credentials.
    systemProperty("techstore.supabase.key", "sb_publishable_test")
    systemProperty("techstore.chat.engine", "rules")
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

tasks.test {
    useJUnitPlatform { excludeTags("ui") }
    jvmArgs("--add-reads=com.example.authapp=jdk.httpserver")
}
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

val localModelSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Opt-in check of installed Qwen3 14B through the loopback Ollama API."
    dependsOn(tasks.jar, tasks.testClasses)
    classpath = files(tasks.jar.flatMap { it.archiveFile }, sourceSets["test"].output)
    mainClass.set("com.example.authapp.services.LocalModelSmoke")
    workingDir(layout.buildDirectory.dir("local-model-smoke"))
    args(layout.buildDirectory.file("reports/local-model-smoke.json").get().asFile.absolutePath)
    doFirst { workingDir.mkdirs() }
}

val chatQuality by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Opt-in paired rules/Qwen evaluation on authored conversation scenarios."
    dependsOn(tasks.jar, tasks.testClasses)
    classpath = files(tasks.jar.flatMap { it.archiveFile }, sourceSets["test"].output)
    mainClass.set("com.example.authapp.services.ChatQualityProbe")
    workingDir(layout.buildDirectory.dir("chat-quality"))
    val suite = providers.gradleProperty("chatSuite").getOrElse("core")
    args(layout.buildDirectory.file(if (suite == "core") "reports/chat-quality.json" else "reports/chat-quality-$suite.json").get().asFile.absolutePath)
    args(providers.gradleProperty("chatCases").getOrElse(""))
    args(when (suite) { "budget" -> "/knowledge/chat-budget-cases.json"; "context" -> "/knowledge/chat-context-cases.json"; "group" -> "/knowledge/chat-group-cases.json"; "filter" -> "/knowledge/chat-filter-cases.json"; "acceptance" -> "/knowledge/chat-acceptance-cases.json"; "benchmark" -> "/knowledge/chat-benchmark-cases.json"; "order" -> "/knowledge/chat-order-cases.json"; "edit" -> "/knowledge/chat-edit-cases.json"; "language" -> "/knowledge/chat-language-cases.json"; else -> "/knowledge/chat-quality-cases.json" })
    doFirst { workingDir.mkdirs() }
}

val nativeChatReview by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Opt-in native Windows JavaFX chat review at a per-process UI scale."
    dependsOn(tasks.jar, tasks.testClasses)
    classpath = files(tasks.jar.flatMap { it.archiveFile }, sourceSets["test"].output)
    mainClass.set("com.example.authapp.services.NativeChatReview")
    val scale = providers.gradleProperty("chatScale").getOrElse("1.25")
    systemProperty("glass.win.uiScale", scale)
    systemProperty("techstore.chat.engine", "hybrid")
    jvmArgs("--add-modules=java.desktop")
    workingDir(layout.buildDirectory.dir("native-chat-review"))
    args(layout.buildDirectory.dir("reports/native-chat-$scale").get().asFile.absolutePath)
    doFirst { workingDir.mkdirs() }
}

val chatRuntimeProbe by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Opt-in real Ollama unavailable/missing/cold/queue checks."
    dependsOn(tasks.jar, tasks.testClasses)
    classpath = files(tasks.jar.flatMap { it.archiveFile }, sourceSets["test"].output)
    mainClass.set("com.example.authapp.services.ChatRuntimeProbe")
    val mode = providers.gradleProperty("chatRuntimeMode").getOrElse("missing")
    workingDir(layout.buildDirectory.dir("chat-runtime-probe"))
    args(layout.buildDirectory.file("reports/chat-runtime-$mode.json").get().asFile.absolutePath, mode)
    doFirst { workingDir.mkdirs() }
}
