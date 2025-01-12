import com.github.jengelman.gradle.plugins.shadow.internal.JavaJarExec
import kotlin.String

plugins {
    application
    kotlin("jvm") version "2.0.0"

    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.gradlets.typescript").version("1.4.1") apply false
}

val erika_version: String by project

group = "net.essentuan"
version = erika_version

val reactor_core_version: String by project
val coroutines_version: String by project
val guava_version: String by project
val gson_version: String by project
val reflections_version: String by project
val dateparser_version: String by project

val log4j_version: String by project
val slf4j_version: String by project
val esl_version: String by project
val ktor_version: String by project
val mongo_version: String by project
val brigadier_version: String by project
val acf_version: String by project
val buster_version: String by project
val semver_version: String by project
val kord_version: String by project
val emoji_version: String by project

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("net.essentuan.erika.AppMainKt")
}

repositories {
    mavenCentral()
    mavenLocal()

    maven("https://jitpack.io")
    maven("https://libraries.minecraft.net")
}

val generatedOutput: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(kotlin("reflect"))
    implementation("io.projectreactor:reactor-core:$reactor_core_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
    implementation("com.google.guava:guava:$guava_version-jre")
    implementation("com.google.code.gson:gson:$gson_version")
    implementation("org.reflections:reflections:$reflections_version")
    implementation("com.github.sisyphsu:dateparser:$dateparser_version")
    implementation("com.github.essentuan:esl:v$esl_version")

    implementation("org.apache.logging.log4j:log4j-api:$log4j_version")
    implementation("org.apache.logging.log4j:log4j-core:$log4j_version")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4j_version")
    implementation("org.slf4j:slf4j-simple:$slf4j_version")

    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")

    implementation("org.mongodb:mongodb-driver-reactivestreams:$mongo_version")

    implementation("com.mojang:brigadier:$brigadier_version")
    implementation("com.essentuan:acf:$acf_version")

    implementation("com.github.essentuan:buster:v$buster_version")
    implementation("org.semver4j:semver4j:$semver_version")

    implementation("dev.kord:kord-core:$kord_version")
    implementation("dev.kord:kord-common:$kord_version")
    implementation("dev.kord:kord-gateway:$kord_version")

    implementation("com.vdurmont:emoji-java:$emoji_version")
}

val typescript by tasks.registering(Copy::class) {
    dependsOn(":typescript:build")

    from(project(":typescript").buildDir.resolve("webpack")) {
        exclude("**/*.LICENSE.txt")
    }

    into(buildDir.resolve("typescript"))
}

sourceSets {
    main {
        resources.srcDir(typescript)
    }
}

tasks {
    shadowJar {
        archiveBaseName.set("erika")
        archiveVersion.set("v${project.version}")
        archiveClassifier.set("")
    }

    named("run", JavaExec::class) {
        standardInput = System.`in`

        args = listOf(
            "-services .",
            "-disabled Kord",
            "-mongo mongodb://127.0.0.1:27017/?authSource=theSimpleOnes",
            "-db Erika",
        )
    }
}