pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins { id("com.android.application") version "9.4.1" }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { maven { url = uri("../../build/staging-maven") }; google(); mavenCentral() }
}
rootProject.name = "DrawablePageBenchmark"
include(":app")
