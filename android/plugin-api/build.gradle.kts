// Contract between the core and target plugins. Pure Kotlin, no Android or UI
// dependencies, so third parties can build plugins against it.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
