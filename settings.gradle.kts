pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KeyMapper"
include(":app")

// Fixa explicitamente o arquivo de build do módulo Android.
project(":app").buildFileName = "build.gradle.kts"
