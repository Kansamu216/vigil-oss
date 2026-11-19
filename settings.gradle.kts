pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://neoforged.forgecdn.net/releases")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://neoforged.forgecdn.net/releases")
        mavenCentral()
    }
}

rootProject.name = "vigil-oss"

val publicModules = listOf(
    "modules:shared:protocol",
    "modules:shared:crypto",
    "modules:shared:model",
    "modules:shared:platform-api",
    "modules:shared:detection-api",
    "modules:server:runtime",
    "modules:server:session",
    "modules:server:storage",
    "modules:server:decision",
    "modules:server:feature-integrity",
    "modules:server:feature-ban",
    "modules:server:feature-audit",
    "modules:server:feature-screenshot",
    "modules:server:feature-behavior",
    "modules:client:agent",
    "modules:client:mod-sensor",
    "modules:client:self-check",
    "modules:client:window-capture",
    "test-support:codec",
    "test-support:session",
    "test-support:platform-contract",
)

publicModules.forEach { include(":$it") }

// Platform builds under platforms/ are intentionally independent Gradle builds.
// Do not include them here: Forge, NeoForge and Fabric evolve their toolchains
// at different rates and must be allowed to fail or upgrade independently.
