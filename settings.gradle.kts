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
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CadernoDeReceitas"

// :app     -> Etapa 2 (Jetpack Compose, Navigation 3, Room, DataStore, WorkManager...)
// :parcial -> Parcial (Vistas Android com XML, Intent explicito, ViewBinding)
include(":app")
include(":parcial")
