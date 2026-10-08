plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.networkbroadcast.hospitality.designsystem"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    buildFeatures { compose = true }
}

dependencies {
    // Para themeColors(): la dirección visual y los colores salen de brand.json.
    api(project(":core:brand"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.ui.tooling.preview)
    // QR (emparejamiento de la TV, códigos de ubicación de la app del mesero).
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)
}
