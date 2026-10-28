plugins {
    base
}

allprojects {
    group = "io.github.jichuo1.meguviz"
}

tasks.named("check") {
    dependsOn(":core:check")
    if (providers.gradleProperty("meguviz.profile").orElse("host").get() == "android") {
        dependsOn(":android:check")
    }
}

// Central Portal upload bundle: ./gradlew -Pmeguviz.profile=android -Pmeguviz.sign=true centralBundle
// The zip is a Maven repository layout of every published module with signatures and checksums.
val cleanCentralMaven = tasks.register<Delete>("cleanCentralMaven") {
    delete(layout.buildDirectory.dir("central-maven"))
}

tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Builds the Maven Central upload bundle for all published modules."
    dependsOn(cleanCentralMaven)
    dependsOn(java.util.concurrent.Callable {
        subprojects.map { it.tasks.named("publishCandidatePublicationToCentralBundleRepository") }
    })
    subprojects.forEach { module ->
        module.tasks.matching { it.name == "publishCandidatePublicationToCentralBundleRepository" }
            .configureEach { mustRunAfter(cleanCentralMaven) }
    }
    doFirst {
        check(providers.gradleProperty("meguviz.sign").orNull == "true") { "centralBundle requires -Pmeguviz.sign=true" }
        check(subprojects.size == 2) { "centralBundle requires -Pmeguviz.profile=android so both modules are included" }
    }
    from(layout.buildDirectory.dir("central-maven")) { exclude("**/maven-metadata.xml*") }
    destinationDirectory.set(layout.buildDirectory.dir("central"))
    archiveFileName.set("meguviz-drawable-${project(":core").version}-central-bundle.zip")
}
