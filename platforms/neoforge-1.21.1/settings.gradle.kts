pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        // Versioned core artifacts published by the root build. Relative on
        // purpose: no machine-local absolute path and no implicit mavenLocal().
        maven("../../build/module-repo")
        maven("https://maven.neoforged.net/releases")
        mavenCentral()
    }
}
rootProject.name = "vigil-neoforge-1.21.1"
