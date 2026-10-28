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
