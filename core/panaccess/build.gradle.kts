plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.networkbroadcast.hospitality.panaccess"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}

dependencies {
    api(project(":core:entertainment"))
    api(project(":core:shared"))
    // DRM de Panaccess para celular / Firestick / Android TV genérico (libs/maven, ver libs/README.md).
    api(libs.panaccess.drm.mobile)
    // Mensajes OSM y huella antipiratería (CopyprotectService dibuja dentro de un ConstraintLayout).
    api(libs.panaccess.copyprotect)
    api(libs.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.security.crypto)
    testImplementation(libs.junit)
}
