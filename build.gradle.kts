val productGroup = "io.github.kansamu216.vigil"
val productVersion = "0.1.0-dev.4"
val sharedLibs = extensions
    .getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
    .named("libs")

val moduleRepository = rootProject.layout.buildDirectory.dir("module-repo")

subprojects {
    group = productGroup
    version = productVersion

    val hasSources = buildFile.exists() || projectDir.resolve("src").exists()
    val isSourceModule = hasSources && (
        path.startsWith(":modules:") || path.startsWith(":test-support:"))
    if (!isSourceModule) return@subprojects

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    configure<JavaPluginExtension> {

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
        add("testImplementation", sharedLibs.findLibrary("junit-jupiter").get())
    }

    afterEvaluate {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("module") {
                    from(components["java"])
                    artifactId = project.path.removePrefix(":").replace(":", "-")
                        .removePrefix("modules-")
                }
            }
            repositories {
                maven {
                    name = "moduleRepository"
                    url = moduleRepository.get().asFile.toURI()
                }
            }
        }
    }
}

tasks.register("publishModules") {
    group = "publishing"
    description = "Publishes versioned modules to the local file repository."
    dependsOn(provider {
        subprojects.mapNotNull { sub ->
            val taskName = "publishAllPublicationsToModuleRepositoryRepository"
            if (sub.tasks.findByName(taskName) != null) "${sub.path}:$taskName" else null
        }
    })
}
