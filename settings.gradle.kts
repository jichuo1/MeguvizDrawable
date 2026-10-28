pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins { id("com.android.library") version "9.4.1" }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}

rootProject.name = "MeguvizDrawable"
val profile = providers.gradleProperty("meguviz.profile").orElse("host").get()
require(profile in setOf("host", "android")) { "meguviz.profile must be host or android" }
include(":core")
if (profile == "android") include(":android")
