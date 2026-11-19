plugins { java }

group = "io.github.kansamu216.vigil.platform"
version = "0.1.0-dev.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Scaffold only: ModDevGradle integration follows the existing Voidkit
// NeoForge 21.1.x baseline and is introduced during M0.
