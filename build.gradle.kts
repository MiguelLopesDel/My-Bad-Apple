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

// Headless ASCII preview of a single frame, for verifying the decode/downscale pipeline.
tasks.register<JavaExec>("preview") {
    group = "verification"
    description = "Prints one frame of the asset as ASCII (args: 'frame cols rows')"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.badapple.debug.Preview"
    args = ((project.findProperty("args") as String?) ?: "3000 100 45").split(" ")
}

// Headless 2x2 PNG of all four color modes for a frame, for visual inspection.
tasks.register<JavaExec>("colorPreview") {
    group = "verification"
    description = "Renders one frame in all color modes to a PNG (args: 'outPng frame')"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.badapple.debug.ColorPreview"
    args = ((project.findProperty("args") as String?) ?: "color-preview.png 3000").split(" ")
}

tasks.shadowJar {
    archiveBaseName = "my-bad-apple"
    archiveClassifier = ""
    archiveVersion = ""
}
