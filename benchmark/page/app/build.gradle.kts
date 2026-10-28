import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import java.security.MessageDigest

plugins { id("com.android.application") }

val candidateVersion = providers.gradleProperty("drawable.candidateVersion").orElse("0.1.0-dev.3").get()

android {
    namespace = "io.github.jichuo1.meguviz.drawable.pagebench"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.jichuo1.meguviz.drawable.pagebench"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "io.github.jichuo1.meguviz.drawable.pagebench.PageInstrumentation"
        testApplicationId = "io.github.jichuo1.meguviz.drawable.pagebench.test"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    testBuildType = "release"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("io.github.jichuo1.meguviz:meguviz-drawable-android:$candidateVersion")
}

tasks.register("verifyCandidateResolution") {
    doLast {
        val artifacts = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        val seen = artifacts.map { artifact ->
            val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                ?: error("Source project substitution is not allowed")
            val staged = rootProject.file("../../build/staging-maven/${id.group.replace('.', '/')}/${id.module}/${id.version}/${artifact.file.name}")
            check(staged.isFile && staged.readBytes().contentEquals(artifact.file.readBytes())) { "Artifact differs from staging: $id" }
            val sha = MessageDigest.getInstance("SHA-256").digest(artifact.file.readBytes()).joinToString("") { "%02x".format(it) }
            println("candidate ${id.group}:${id.module}:${id.version} sha256=$sha")
            "${id.group}:${id.module}:${id.version}"
        }.toSet()
        check(seen == setOf("io.github.jichuo1.meguviz:meguviz-drawable-android:$candidateVersion",
            "io.github.jichuo1.meguviz:meguviz-drawable-core:$candidateVersion") && artifacts.size == 2) {
            "Unexpected runtime graph: $seen"
        }
    }
}
