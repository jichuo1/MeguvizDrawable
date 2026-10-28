plugins {
    base
    // On the build classpath only, so AGP's built-in Kotlin uses this compiler for the bridge modules.
    // The Java-only modules compile no Kotlin and are unaffected.
    // AGP must be loaded in the same class loader as the Kotlin plugin it integrates with.
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}

allprojects {
    group = "io.github.jichuo1.meguviz"
}

tasks.named("check") {
    // Every module the selected profile includes: core always, plus the Android and bridge modules.
    dependsOn(provider { subprojects.map { "${it.path}:check" } })
}

// Central Portal upload bundle: ./gradlew -Pmeguviz.profile=bridges -Pmeguviz.sign=true centralBundle
// The zip is a Maven repository layout of the Central-ready modules with signatures and checksums.
// :lumen is deliberately absent: lumen-engine is published only through JitPack, and a Central
// artifact must not depend on one that Central consumers cannot resolve.
val centralModules = listOf(":core", ":android", ":compose", ":meguviz-android", ":meguviz-ui")

val cleanCentralMaven = tasks.register<Delete>("cleanCentralMaven") {
    delete(layout.buildDirectory.dir("central-maven"))
}

tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Builds the Maven Central upload bundle for the Central-ready modules."
    dependsOn(cleanCentralMaven)
    centralModules.forEach { dependsOn("$it:publishCandidatePublicationToCentralBundleRepository") }
    subprojects.forEach { module ->
        module.tasks.matching { it.name == "publishCandidatePublicationToCentralBundleRepository" }
            .configureEach { mustRunAfter(cleanCentralMaven) }
    }
    doFirst {
        check(providers.gradleProperty("meguviz.sign").orNull == "true") { "centralBundle requires -Pmeguviz.sign=true" }
    }
    from(layout.buildDirectory.dir("central-maven")) { exclude("**/maven-metadata.xml*") }
    destinationDirectory.set(layout.buildDirectory.dir("central"))
    archiveFileName.set("meguviz-drawable-${project(":core").version}-central-bundle.zip")
}
