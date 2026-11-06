// Built-in plugin: interactive shell on an EC2 instance through Session Manager.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.jmanera.devopsmobiletools.plugins.ec2shell"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":plugin-api"))
    implementation(project(":bridge"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
