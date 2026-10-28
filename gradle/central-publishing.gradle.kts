// Maven Central metadata, signing and bundle repository, shared by the published modules.
// Apply after the module declares its "candidate" publication; signing stays off unless
// -Pmeguviz.sign=true is given, so ordinary builds and the staging evidence are unaffected.
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(provider { artifactId })
            description.set(provider { project.description ?: artifactId })
            url.set("https://github.com/jichuo1/MeguvizDrawable")
            inceptionYear.set("2026")
            developers {
                developer {
                    id.set("jichuo1")
                    name.set("jichuo1")
                    url.set("https://github.com/jichuo1")
                }
            }
            scm {
                url.set("https://github.com/jichuo1/MeguvizDrawable")
                connection.set("scm:git:https://github.com/jichuo1/MeguvizDrawable.git")
                developerConnection.set("scm:git:ssh://git@github.com/jichuo1/MeguvizDrawable.git")
            }
            issueManagement {
                system.set("GitHub")
                url.set("https://github.com/jichuo1/MeguvizDrawable/issues")
            }
        }
    }
    repositories {
        maven {
            name = "centralBundle"
            url = rootProject.layout.buildDirectory.dir("central-maven").get().asFile.toURI()
        }
    }
}

if (providers.gradleProperty("meguviz.sign").orNull == "true") {
    extensions.configure<SigningExtension> {
        // The system gpg and its agent hold the key and passphrase; nothing secret enters the build.
        useGpgCmd()
        sign(extensions.getByType<PublishingExtension>().publications)
    }
}
