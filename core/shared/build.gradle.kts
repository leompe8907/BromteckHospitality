plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Modelos y parser del CAS copiados de PanaccessApp/shared (ver SOURCE.md). Kotlin puro.
android {
    namespace = "com.panaccess.android.streaming.shared"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    testImplementation(libs.junit)
}
