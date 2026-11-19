import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.zip.ZipFile

plugins {
    java
    id("net.neoforged.moddev") version "2.0.148"
    id("com.gradleup.shadow") version "8.3.9"
}

group = "io.github.kansamu216.vigil.platform"
version = "0.1.0-dev.0"

// 1.21.x adapters use Java 21; shared core keeps the Java 8 API surface and is
// consumed as a versioned artifact, never as a source set of this build.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

val coreVersion: String by project
val coreGroup = "io.github.kansamu216.vigil"
val sharedRoot = "io.github.kansamu216.vigil"
val clientInternalRoot = "io.github.kansamu216.vigil.client.internal"
// 每个共享包都要重定位：核心产物之间存在传递依赖（protocol → model），
// 只搬一个包会让另一个包在两个 side jar 里形成 split package。
val sharedNamespaces = listOf(
    "$sharedRoot.protocol",
    "$sharedRoot.model",
    "$sharedRoot.crypto",
    "$sharedRoot.platform.api",
)
val sharedNamespace = "$sharedRoot.protocol"
val clientInternalNamespace = "$clientInternalRoot.protocol"
val platformPackagePrefix = "io.github.kansamu216.vigil.platform."

// Only this configuration is bundled into the mod jars. NeoForge and Minecraft
// must never end up inside them, so the shaded content is declared explicitly
// instead of using runtimeClasspath.
val vigilCore: Configuration by configurations.creating

// ModDevGradle registers project-level repositories, which makes Gradle ignore
// the settings-level list. The core repository is therefore declared here too;
// the path stays relative so no machine-local absolute path enters the build.
repositories {
    maven { url = uri("${rootDir}/../../build/module-repo") }
    maven("https://maven.neoforged.net/releases")
    mavenCentral()
}

dependencies {
    // Bundled into both side jars by the shadow tasks only; declared as a normal
    // implementation dependency so dev runs resolve the same artifact version.
    implementation("$coreGroup:shared-protocol:$coreVersion")
    vigilCore("$coreGroup:shared-protocol:$coreVersion")
}

// Dev-only classpath folder for the versioned core artifact. Nothing is
// compiled here; the core jar is expanded so FML can load it in dev runs.
val coreDevSourceSet = sourceSets.create("coreDev") {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
}

val syncCoreForDev = tasks.register<Sync>("syncCoreForDev") {
    // 核心产物有传递依赖（protocol → model），开发运行需要展开全部 jar；
    // 每个 jar 都有自己的 MANIFEST，展开时必须排除元数据并允许重复覆盖。
    from(provider { configurations["vigilCore"].files.map { zipTree(it) } }) {
        exclude("META-INF/**")
    }
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    into(layout.buildDirectory.dir("core-dev"))
}

coreDevSourceSet.output.dir(mapOf("builtBy" to syncCoreForDev), layout.buildDirectory.dir("core-dev"))

neoForge {
    // NeoForge 21.1.x is the 1.21.1 line. DEVELOPMENT.md section 14 requires M0
    // to select and lock one official combination instead of floating versions.
    version = "21.1.267"

    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
        }
    }

    mods {
        register("vigil") {
            sourceSet(sourceSets.main.get())
            // FML's dev loader only reads mod folders, not the JVM classpath, so
            // the versioned core artifact is synced into a second folder that
            // belongs to the same mod. Production jars get it via shadowing.
            sourceSet(coreDevSourceSet)
        }
    }
}

fun ShadowJar.applySidePackaging(side: String) {
    archiveBaseName.set("vigil-$side-neoforge-1.21.1")
    archiveVersion.set("${project.version}")
    archiveClassifier.set("")
    setConfigurations(listOf<FileCollection>(vigilCore))
    from(sourceSets.main.get().output)
}

val serverJar = tasks.named<ShadowJar>("shadowJar") {
    applySidePackaging("server")
}

// The client jar relocates shared core classes so a client and a server jar can
// never contribute the same package to one class loader.
val clientJar = tasks.register<ShadowJar>("clientJar") {
    applySidePackaging("client")
    sharedNamespaces.forEach { namespace ->
        relocate(namespace, namespace.replace(sharedRoot, clientInternalRoot))
    }
}

fun zipClasses(file: File): Set<String> =
    ZipFile(file).use { zip ->
        zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".class") }
            .map { it.name.removeSuffix(".class").replace('/', '.') }
            .toSet()
    }

fun zipPackages(file: File): Set<String> =
    zipClasses(file).map { it.substringBeforeLast('.') }.toSet()

fun zipEntries(file: File): Set<String> =
    ZipFile(file).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }

fun classBytes(file: File, className: String): ByteArray? =
    ZipFile(file).use { zip ->
        val entry = zip.getEntry(className.replace('.', '/') + ".class") ?: return null
        zip.getInputStream(entry).use { it.readBytes() }
    }

fun ByteArray.containsAscii(text: String): Boolean =
    String(this, Charsets.ISO_8859_1).contains(text)

/**
 * M0 acceptance for this platform: both sides package the mod metadata and the
 * core constants, the client relocates them, and no package outside the
 * platform's own namespace appears in both jars.
 */
val verifySideJars = tasks.register("verifySideJars") {
    group = "verification"
    description = "Checks side jars for metadata, relocation and split packages."
    dependsOn(serverJar, clientJar)

    doLast {
        val serverFile = serverJar.get().archiveFile.get().asFile
        val clientFile = clientJar.get().archiveFile.get().asFile
        val problems = mutableListOf<String>()

        listOf(serverFile to "server", clientFile to "client").forEach { (file, side) ->
            if (!file.isFile) {
                problems += "$side jar missing: ${file.name}"
                return@forEach
            }
            val entries = zipEntries(file)
            if ("META-INF/neoforge.mods.toml" !in entries) {
                problems += "$side jar has no META-INF/neoforge.mods.toml"
            }
            if (entries.any { it.startsWith("net/minecraft/") || it.startsWith("net/neoforged/") }) {
                problems += "$side jar bundles loader or Minecraft classes"
            }
        }

        val serverClasses = zipClasses(serverFile)
        val clientClasses = zipClasses(clientFile)
        val serverPackages = zipPackages(serverFile)
        val clientPackages = zipPackages(clientFile)

        if ("$sharedNamespace.VigilChannels" !in serverClasses) {
            problems += "server jar does not carry $sharedNamespace.VigilChannels"
        }
        if ("$clientInternalNamespace.VigilChannels" !in clientClasses) {
            problems += "client jar does not carry relocated $clientInternalNamespace.VigilChannels"
        }
        val unrelocated = sharedNamespaces.filter { namespace ->
            clientPackages.any { it == namespace || it.startsWith("$namespace.") }
        }
        if (unrelocated.isNotEmpty()) {
            problems += "client jar still carries unrelocated packages: ${unrelocated.joinToString(", ")}"
        }

        // Relocation must rewrite the references inside the mod's own classes,
        // not only move the shared class file.
        val channelClass = "io.github.kansamu216.vigil.platform.neoforge1211.channel.VigilChannel"
        val clientChannel = classBytes(clientFile, channelClass)
        val sharedPath = sharedNamespace.replace('.', '/')
        val relocatedPath = clientInternalNamespace.replace('.', '/')
        if (clientChannel == null) {
            problems += "client jar has no $channelClass"
        } else {
            if (!clientChannel.containsAscii(relocatedPath)) {
                problems += "client $channelClass does not reference $relocatedPath"
            }
            if (clientChannel.containsAscii(sharedPath)) {
                problems += "client $channelClass still references $sharedPath"
            }
        }

        val split = serverPackages.intersect(clientPackages)
            .filterNot { it.startsWith(platformPackagePrefix) }
        if (split.isNotEmpty()) {
            problems += "split packages between side jars: ${split.sorted().joinToString(", ")}"
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Side jar verification failed:\n" + problems.joinToString("\n") { " - $it" }
            )
        }
        logger.lifecycle(
            "[VIGIL-M0] verified side jars: server={} client={} relocation={}",
            serverFile.name, clientFile.name, clientInternalNamespace
        )
    }
}

tasks.named("assemble") {
    dependsOn(serverJar, clientJar, verifySideJars)
}

tasks.named("check") {
    dependsOn(verifySideJars)
}

// The plain jar has no bundled core and is not a deliverable; keep it clearly
// separated from the two side jars produced by the shadow tasks.
tasks.named<Jar>("jar") {
    archiveClassifier.set("thin")
}
