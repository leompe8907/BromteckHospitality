plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Servicios del hotel (pedidos): el contrato que implementa el backend propio (otro proyecto, ver
// docs/backend-idea.md, "Servicios / pedidos") y una versión de prueba para construir las pantallas
// de la TV, el celular y el mesero sin él.
android {
    namespace = "com.networkbroadcast.hospitality.services"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
