plugins {
    id("com.android.library")
}

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
}

dependencies {
    api(project(":core"))
}
