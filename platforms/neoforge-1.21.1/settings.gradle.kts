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
        mavenLocal()
        maven("https://maven.neoforged.net/releases")
        mavenCentral()
    }
}
rootProject.name = "vigil-neoforge-1.21.1"
