plugins {
    alias(libs.plugins.android.library)
}

// Emparejamiento TV ↔ celular: el contrato que implementa el backend propio (otro proyecto,
// ver docs/backend-idea.md) y una versión de prueba para construir las pantallas sin él.
android {
    namespace = "com.networkbroadcast.hospitality.companion"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
