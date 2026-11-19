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

rootProject.name = "vigil"

val modules = listOf(
    "modules:shared:protocol",
    "modules:shared:crypto",
    "modules:shared:model",
    "modules:shared:platform-api",
    "modules:server:runtime",
    "modules:server:session",
    "modules:server:storage",
    "modules:server:decision",
    "test-support:session-harness",
)

modules.forEach { include(":$it") }

// Platform builds stay independent so loader toolchains can evolve separately.
