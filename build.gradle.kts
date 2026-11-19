val productGroup = "io.github.kansamu216.vigil"
val productVersion = "0.1.0-dev.0"

subprojects {
    group = productGroup
    version = productVersion

    val isSourceModule = path.startsWith(":modules:") ||
        path.startsWith(":private:") ||
        path.startsWith(":test-support:")
    if (!isSourceModule) return@subprojects

    apply(plugin = "java-library")

    configure<JavaPluginExtension> {
        // Shared code stays on the Java 8 API surface so future legacy adapters
        // do not require a second core implementation. Current platform builds
        // override this with Java 17 or Java 21 in their isolated builds.
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(8)
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        add("testImplementation", libs.junit.jupiter)
    }
}
