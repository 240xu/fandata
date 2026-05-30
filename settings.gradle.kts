pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google(); gradlePluginPortal(); mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        google(); mavenCentral()
        maven { url = uri("https://maven.nariko.org/release") }
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "FanData"
include(":legado-engine", ":plugin")