pluginManagement {
    repositories {
        google()
        // Google-hosted mirror of Maven Central; tried first because Central rate-limits busy networks.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google-hosted mirror of Maven Central; tried first because Central rate-limits busy networks.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}
rootProject.name = "RiichiTutor"
include(":engine", ":app")
