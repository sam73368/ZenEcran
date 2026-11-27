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

rootProject.name = "ZenEcran"
include(":app")
// Fausses applis pour les tests sur émulateur uniquement
include(":fake-instagram", ":fake-facebook", ":fake-snapchat")
