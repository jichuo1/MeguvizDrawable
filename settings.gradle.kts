pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("com.android.library") version "9.4.1"
        // Declared for the optional bridge modules only; the Java modules compile no Kotlin.
        id("org.jetbrains.kotlin.android") version "2.4.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Lumen is published through JitPack coordinates; nothing else may resolve from there.
        maven("https://jitpack.io") { content { includeGroup("com.github.jichuo1.LumenCoacervationEngine") } }
    }
}

rootProject.name = "MeguvizDrawable"
// host: pure Java core. android: plus the Android module. bridges: plus the optional bridge artifacts.
val profile = providers.gradleProperty("meguviz.profile").orElse("host").get()
require(profile in setOf("host", "android", "bridges")) { "meguviz.profile must be host, android or bridges" }
include(":core")
if (profile != "host") include(":android")
