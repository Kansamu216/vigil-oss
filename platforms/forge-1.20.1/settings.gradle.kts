pluginManagement {
    repositories {
        maven("https://maven.minecraftforge.net")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven("https://maven.minecraftforge.net")
        mavenCentral()
    }
}
rootProject.name = "vigil-forge-1.20.1"
