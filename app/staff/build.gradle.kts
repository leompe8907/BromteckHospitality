plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Las mismas marcas que app/tv y app/mobile (buildSrc/src/main/kotlin/Brands.kt), con su propio applicationId.
val brands: List<Brand> = loadBrands(rootProject.file("brands"))

android {
    namespace = "com.networkbroadcast.hospitality.staff"
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
                applicationId = brand.staffApplicationId
                resValue("string", "app_name", "${brand.displayName} · Staff")
            }
        }
    }

    sourceSets {
        brands.forEach { brand ->
            // Sólo los assets (catálogo, fotos): los íconos de la marca son los de las apps del huésped.
            getByName(brand.id) { assets.directories.add(File(brand.dir, "assets").path) }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Ni Panaccess ni entretenimiento: el personal sólo ve pedidos y códigos QR.
    implementation(project(":core:designsystem"))
    implementation(project(":core:brand"))
    implementation(project(":core:services"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
}
