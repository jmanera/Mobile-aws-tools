// Contract between the core and target plugins. Pure Kotlin, no Android or UI
// dependencies, so third parties can build plugins against it.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
