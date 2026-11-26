plugins {
    alias(libs.plugins.android.library)
}

// Emparejamiento TV ↔ celular: el contrato que implementa el backend propio (otro proyecto,
// ver docs/backend-idea.md), los clientes HTTP reales de ese backend (HttpTvPairing /
// HttpCompanionLink) y una versión de prueba para construir las pantallas sin él.
android {
    namespace = "com.networkbroadcast.hospitality.companion"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    // Solo se usa JsonElement para leer/armar los cuerpos: no hace falta el plugin de serialización.
    implementation(libs.kotlinx.serialization.json)
    // Guardar el secreto de la TV y el token del celular cifrados (mismo patrón que PanaccessSession).
    implementation(libs.security.crypto)
    testImplementation(libs.junit)
}
