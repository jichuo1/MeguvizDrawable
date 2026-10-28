plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}

val contractTest = tasks.register<JavaExec>("contractTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.meguviz.drawable.core.CoreContractTest"
}

// The current suite is an executable Java program, not a test-framework suite.
tasks.test {
    enabled = false
}

tasks.check {
    dependsOn(contractTest)
}
