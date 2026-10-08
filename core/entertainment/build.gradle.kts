plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.networkbroadcast.hospitality.entertainment"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
