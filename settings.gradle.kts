pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // AARs binarios de Panaccess (DRM, player), copiados de PanaccessApp. Ver libs/README.md.
        maven { url = uri("libs/maven") }
    }
}

rootProject.name = "Hospitality"

// Un módulo por tema. Las apps no se hablan entre sí; sólo dependen de core/*.
include(":core:designsystem")    // tema y componentes: las 3 direcciones visuales del canvas
include(":core:brand")           // marca blanca: lectura de brands/<marca>/brand.json
include(":core:hotel")           // contenido del hotel (formato hotel_data.json de la rama RIU)
include(":core:entertainment")   // TV en vivo / VOD: contrato del dominio
include(":core:shared")          // modelos del CAS copiados de PanaccessApp/shared (ver core/shared/SOURCE.md)
include(":core:panaccess")       // conexión al middleware (DRM, login, catálogo), copiada de PanaccessApp
include(":core:companion")       // emparejamiento TV ↔ celular (contrato del backend + versión de prueba)
include(":core:services")        // servicios del hotel: catálogo y pedidos (contrato del backend + versión de prueba)
include(":app:tv")               // app Android TV (incluye Firestick)
include(":app:mobile")           // app del celular, compañera de la TV
include(":app:staff")            // app del mesero / personal: cola de pedidos y códigos QR de ubicaciones
