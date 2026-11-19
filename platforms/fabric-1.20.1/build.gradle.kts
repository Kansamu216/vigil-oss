plugins { java }

group = "io.github.kansamu216.vigil.platform"
version = "0.1.0-dev.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

// Scaffold only: Fabric Loom, Yarn and loader versions are selected and locked
// in M0. Fabric has its own networking implementation; no Forge API shim.
