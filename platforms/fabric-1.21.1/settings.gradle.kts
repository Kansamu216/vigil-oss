pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven("https://maven.fabricmc.net")
        mavenCentral()
    }
}
rootProject.name = "vigil-fabric-1.21.1"
