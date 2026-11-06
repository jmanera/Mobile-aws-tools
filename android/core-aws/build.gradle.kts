// AWS access for the app: SSO sign-in, token storage and role credentials.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.jmanera.devopsmobiletools.core.aws"
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
    implementation(libs.aws.ssooidc)
    implementation(libs.aws.sso)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
