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

rootProject.name = "FMHuB24"
include(":app")

// Provider ABI and the providers that ship with the app. Each provider module can also be built
// into a .fmkp plugin with its buildPlugin task (see gradle/provider-plugin.gradle.kts).
include(":provider-api")
include(":provider-archive")
include(":provider-iptv")
