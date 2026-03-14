pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // Spotify Auth SDK
        maven { url = uri("https://maven.spotify.com/releases") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Spotify Auth SDK
        maven { url = uri("https://maven.spotify.com/releases") }
        // jaudiotagger
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MDfy"
include(":app")
