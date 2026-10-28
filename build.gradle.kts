plugins {
    base
}

tasks.named("check") {
    dependsOn(":core:check")
}
