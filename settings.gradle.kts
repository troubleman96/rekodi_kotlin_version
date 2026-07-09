pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "rekodi"
include(":app")
include(":core:core-ui")
include(":core:core-data")
include(":core:core-common")
include(":feature:feature-recorder")
include(":feature:feature-editor")
include(":feature:feature-library")
include(":feature:feature-settings")
include(":feature:feature-onboarding")
