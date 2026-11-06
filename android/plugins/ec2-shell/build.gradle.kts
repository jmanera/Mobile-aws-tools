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

    // android.util.Log returns defaults in unit tests instead of throwing.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(project(":plugin-api"))
    implementation(project(":bridge"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.aws.ssm)
    // Name tags; much smaller than the EC2 SDK.
    implementation(libs.aws.resourcegroupstaggingapi)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
