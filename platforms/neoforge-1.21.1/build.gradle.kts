import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.zip.ZipFile

plugins {
    java
    id("net.neoforged.moddev") version "2.0.148"
    id("com.gradleup.shadow") version "8.3.9"
}

group = "io.github.kansamu216.vigil.platform"
version = "0.1.0-dev.1"

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
val sharedNamespaces = listOf(
    "$sharedRoot.protocol",
    "$sharedRoot.model",
    "$sharedRoot.crypto",
    "$sharedRoot.platform.api",
)
val sharedNamespace = "$sharedRoot.protocol"
val clientInternalNamespace = "$clientInternalRoot.protocol"
val platformPackagePrefix = "io.github.kansamu216.vigil.platform."
val serverPackagePrefix = "io.github.kansamu216.vigil.platform.neoforge1211.server."

// Separate content sets: the client must never ship server runtime or server
// adapter classes.
val sharedCore: Configuration by configurations.creating
val serverCore: Configuration by configurations.creating
val clientCore: Configuration by configurations.creating
val devCore: Configuration by configurations.creating

// Server-only sources are compiled into the server jar and loaded only on a
// dedicated server through @EventBusSubscriber(dist = DEDICATED_SERVER).
val serverSourceSet = sourceSets.create("server") {
    java.srcDir("src/server/java")
    resources.srcDir("src/server/resources")
}
configurations["serverImplementation"].extendsFrom(configurations["implementation"])
configurations["serverRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])
// 服务端源集需要加载器与 Minecraft 类型，继承主源集的编译/运行类路径。
serverSourceSet.compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
serverSourceSet.runtimeClasspath += serverSourceSet.output + sourceSets.main.get().runtimeClasspath

repositories {
    maven { url = uri("${rootDir}/../../build/module-repo") }
    maven("https://maven.neoforged.net/releases")
    mavenCentral()
}

dependencies {
    implementation("$coreGroup:shared-protocol:$coreVersion")
    "serverImplementation"("$coreGroup:server-runtime:$coreVersion")

    sharedCore("$coreGroup:shared-protocol:$coreVersion")
    clientCore("$coreGroup:shared-protocol:$coreVersion")
    serverCore("$coreGroup:shared-protocol:$coreVersion")
    serverCore("$coreGroup:server-runtime:$coreVersion")
    devCore("$coreGroup:shared-protocol:$coreVersion")
    devCore("$coreGroup:server-runtime:$coreVersion")
}

val coreDevSourceSet = sourceSets.create("coreDev") {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
}

val syncCoreForDev = tasks.register<Sync>("syncCoreForDev") {
    from(provider { devCore.files.map { zipTree(it) } }) {
        exclude("META-INF/**")
    }
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    into(layout.buildDirectory.dir("core-dev"))
}

coreDevSourceSet.output.dir(mapOf("builtBy" to syncCoreForDev), layout.buildDirectory.dir("core-dev"))

neoForge {
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
            sourceSet(serverSourceSet)
            sourceSet(coreDevSourceSet)
        }
    }
}

tasks.named("runServer") {
    dependsOn(serverSourceSet.classesTaskName)
}

fun ShadowJar.applySidePackaging(side: String, core: Configuration, includeServer: Boolean) {
    archiveBaseName.set("vigil-$side-neoforge-1.21.1")
    archiveVersion.set("${project.version}")
    archiveClassifier.set("")
    setConfigurations(listOf<FileCollection>(core))
    from(sourceSets.main.get().output)
    if (includeServer) {
        from(serverSourceSet.output)
    }
}

val serverJar = tasks.named<ShadowJar>("shadowJar") {
    applySidePackaging("server", serverCore, true)
}

// The client jar relocates shared core classes so a client and a server jar can
// never contribute the same package to one class loader.
val clientJar = tasks.register<ShadowJar>("clientJar") {
    applySidePackaging("client", clientCore, false)
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
 * Side jar acceptance: metadata, relocation, no server code in the client, and
 * no package outside the platform namespace shared by both jars.
 */
val verifySideJars = tasks.register("verifySideJars") {
    group = "verification"
    description = "Checks side jars for metadata, relocation, side isolation and split packages."
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

        val serverBootstrap = "io.github.kansamu216.vigil.platform.neoforge1211.server.VigilServer"
        if (serverBootstrap !in serverClasses) {
            problems += "server jar does not carry $serverBootstrap"
        }
        if (clientClasses.any { it.startsWith(serverPackagePrefix) }) {
            problems += "client jar contains server adapter classes"
        }
        if (clientPackages.any { it.startsWith("$sharedRoot.server.") }) {
            problems += "client jar contains server runtime packages"
        }
        if ("$sharedRoot.server.runtime.GuardRuntime" !in serverClasses) {
            problems += "server jar does not carry the server runtime"
        }

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
            "[VIGIL-SIDE] verified server={} client={} relocation={}",
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

tasks.named<Jar>("jar") {
    archiveClassifier.set("thin")
}
