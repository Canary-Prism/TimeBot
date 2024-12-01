plugins {
    application
    id("com.gradleup.shadow") version "8.3.5"
    id("org.hidetake.ssh") version "2.11.2"
}

group = "canaryprism"
version = "1.2.1"

application {
    mainClass = "canaryprism.timebot.Main"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.javacord:javacord:3.8.0")
    implementation("io.github.canary-prism:slavacord:3.0.2")
    implementation("org.json:json:20240303")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("dev.dirs:directories:26")
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.apache.commons:commons-lang3:3.15.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier = ""
}


val mia_deploy = file("/Users/mia/Documents/mia-deploy.gradle")
if (mia_deploy.exists()) apply(mia_deploy)