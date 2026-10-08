plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Marcas: buildSrc/src/main/kotlin/Brands.kt (las comparte con app/mobile).
val brands: List<Brand> = loadBrands(rootProject.file("brands"))

android {
    namespace = "com.networkbroadcast.hospitality.tv"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    flavorDimensions += "brand"
    productFlavors {
        brands.forEach { brand ->
            create(brand.id) {
                dimension = "brand"
                applicationId = brand.applicationId
                resValue("string", "app_name", brand.displayName)
            }
        }
    }

    sourceSets {
        brands.forEach { brand ->
            getByName(brand.id) {
                res.directories.add(File(brand.dir, "res").path)
                assets.directories.add(File(brand.dir, "assets").path)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:brand"))
    implementation(project(":core:hotel"))
    implementation(project(":core:entertainment"))
    implementation(project(":core:panaccess"))
    implementation(project(":core:companion"))
    implementation(project(":core:services"))
    // QR del emparejamiento (misma librería que usa PanaccessApp).
    implementation(libs.zxing.core)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    // PanHlsExtractorFactory, el extractor HLS que usa el base por defecto (PLAYER_USE_HLS_PANEXTRACTOR).
    implementation(libs.panaccess.panexo.datasource)
    implementation(libs.media3.ui)
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
