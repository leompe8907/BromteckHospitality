# core/shared — copia de PanaccessApp

Copia **literal** (mismo paquete, sin cambios) de archivos de
`androidpanaccessapp/PanaccessApp/shared/src/commonMain/kotlin/com/panaccess/android/streaming/shared/`,
tomados del commit `7310d5011` (rama local `features/migracion-compose-mobile`, que sigue a `leo/features/migracion-compose-mobile`), 2026-10-01.

Excepción: `epg/EpgUrlBuilder.kt` reemplaza el `expect fun sha256Hex` por su implementación
Android (`androidMain/epg/Sha256.android.kt`, mismo cuerpo), porque este módulo no es KMP.

No editar acá: si hace falta un cambio, va en PanaccessApp y se vuelve a copiar. Cuando el
proyecto base tenga un módulo común publicable, este módulo se reemplaza por esa dependencia
sin tocar el resto de Hospitality (por eso se conserva el paquete original).

| Archivo |
|---|
| `cas/CasEnvelope.kt` |
| `cas/LenientPrimitives.kt` |
| `domain/model/Bouquet.kt` |
| `domain/model/CatchupGroup.kt` |
| `domain/model/HtmlEntities.kt` |
| `domain/model/License.kt` |
| `domain/model/LoginError.kt` |
| `domain/model/Stream.kt` |
| `domain/model/VodCategory.kt` |
| `domain/model/VodItem.kt` |
| `epg/EpgCache.kt` |
| `epg/EpgEvent.kt` |
| `epg/EpgGrid.kt` |
| `epg/EpgSchedule.kt` |
| `epg/EpgUrlBuilder.kt` |
| `vod/VodShelves.kt` |
