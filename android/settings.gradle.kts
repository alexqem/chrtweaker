pluginManagement {
    repositories {
        google()
        // Workaround for runner-side CDN failures on repo1.maven.org (2026-11-27); remove once Central is reliable
        maven("https://cache-redirector.jetbrains.com/maven-central")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Workaround for runner-side CDN failures on repo1.maven.org (2026-11-27); remove once Central is reliable
        maven("https://cache-redirector.jetbrains.com/maven-central")
        mavenCentral()
    }
}

rootProject.name = "chr-logger"
include(":app")
