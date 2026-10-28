plugins {
    `java-library`
    `maven-publish`
}

version = "0.1.0-dev.4"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.encoding = "UTF-8"
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE")) { into("META-INF") }
}

tasks.named<Jar>("sourcesJar") {
    from(rootProject.file("LICENSE")) { into("META-INF") }
}

val contractTest = tasks.register<JavaExec>("contractTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "io.github.jichuo1.meguviz.drawable.core.CoreContractTest"
}

// The current suite is an executable Java program, not a test-framework suite.
tasks.test {
    enabled = false
}

tasks.check {
    dependsOn(contractTest)
}

publishing {
    publications {
        create<MavenPublication>("candidate") {
            artifactId = "meguviz-drawable-core"
            from(components["java"])
            pom {
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = rootProject.layout.buildDirectory.dir("staging-maven").get().asFile.toURI()
        }
    }
}
