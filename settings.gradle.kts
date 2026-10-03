// Docora - settings
//
// Local-first Android document workspace.
// Single :app module with strict package-level layering (core -> data -> domain -> ui).
// Keeping one Gradle module for v1 keeps build times low while the interfaces in
// `core`/`domain` keep the layers swappable for future modularisation.

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

rootProject.name = "Docora"

include(":app")
