plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.badapple"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Pure-Java TUI: raw input, terminal size, resize, ANSI queries.
    implementation("org.jline:jline:3.25.1")
    // Pure-Java MP3 decoder for the embedded audio track.
    implementation("javazoom:jlayer:1.0.1")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "dev.badapple.Main"
}

// Target Java 17 bytecode regardless of the JDK running Gradle, so the jar
// stays portable. The build machine has JDK 21.
tasks.withType<JavaCompile> {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

// Offline-only: regenerates the embedded asset from the source mp4.
// Requires ffmpeg on PATH. Output is committed so the player never needs ffmpeg.
tasks.register<JavaExec>("generateAsset") {
    group = "build"
    description = "Regenerates the embedded Bad Apple asset from source/ (requires ffmpeg)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "dev.badapple.preprocess.Preprocessor"
    args("source/Touhou - Bad Apple.mp4", "src/main/resources/badapple")
}

tasks.shadowJar {
    archiveBaseName = "my-bad-apple"
    archiveClassifier = ""
    archiveVersion = ""
}
