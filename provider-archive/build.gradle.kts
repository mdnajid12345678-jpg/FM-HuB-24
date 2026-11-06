import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":provider-api"))
    // org.json is provided by Android at runtime; it is only needed to compile the parsing code.
    compileOnly("org.json:json:20240303")
}

apply(from = rootProject.file("gradle/provider-plugin.gradle.kts"))
