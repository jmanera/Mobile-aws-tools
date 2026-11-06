// Terminal emulator and view, vendored from Termux (Apache-2.0) and fed by a
// remote session instead of a local process. See docs/adr/0002.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.jmanera.devopsmobiletools.terminal"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // android.os.Handler and friends return defaults in the vendored emulator tests.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
