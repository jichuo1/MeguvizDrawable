plugins {
    id("com.android.library")
    `maven-publish`
}

version = "0.1.0-dev.1"

android {
    namespace = "io.github.jichuo1.meguviz.drawable.android"
    compileSdk = 37
    defaultConfig {
        minSdk = 21
        testApplicationId = "io.github.jichuo1.meguviz.drawable.contracttest"
        testInstrumentationRunner = "io.github.jichuo1.meguviz.drawable.android.test.ContractInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    api(project(":core"))
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("candidate") {
                artifactId = "meguviz-drawable-android"
                from(components["release"])
            }
        }
        repositories {
            maven {
                name = "staging"
                url = rootProject.layout.buildDirectory.dir("staging-maven").get().asFile.toURI()
            }
        }
    }
}
