pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins { id("com.android.application") version "9.4.1" }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("../../build/staging-maven")
            metadataSources {
                if (providers.gradleProperty("consumer.metadata").orNull == "pom") {
                    mavenPom()
                    artifact()
                    ignoreGradleMetadataRedirection()
                } else {
                    gradleMetadata()
                    mavenPom()
                }
            }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "DrawableArtifactConsumer"
include(":app")
