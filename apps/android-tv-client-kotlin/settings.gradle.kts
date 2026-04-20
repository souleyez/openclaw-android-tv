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

rootProject.name = "android-tv-client-kotlin"

include(":app")
include(":core:capability")
include(":core:network")
include(":core:storage")
include(":feature:bootstrap")
include(":feature:home")
include(":feature:runtime")
