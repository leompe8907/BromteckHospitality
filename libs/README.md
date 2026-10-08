# libs/maven

Repositorio Maven local con las librerías binarias de Panaccess, copiadas de
`androidpanaccessapp/PanaccessApp/libs`. Está en formato Maven porque un módulo de librería
no puede depender de un `.aar` suelto.

| Artefacto | Archivo original |
|---|---|
| `com.panaccess:exoplayer-hls:1.6.1-panaccess` | `libs/player/lib-exoplayer-hls-release.aar`: **reemplaza** a `androidx.media3:media3-exoplayer-hls` (versión modificada que necesita PanHlsExtractorFactory) |
| `com.panaccess:panexo-datasource:1.0.19r204` | `libs/player/panexo_datasource-1.0.19r_204.aar` (PanHlsExtractorFactory: extractor HLS de Panaccess) |
| `com.panaccess:copyprotect:1.0.19r190` | `libs/player/pan_copyprotect-1.0.19r_190.aar` (mensajes OSM del operador y huella antipiratería sobre el video) |
| `com.panaccess:drm-mobile-nb:2.0.52r291` | `libs/panaccess/drm-mobile_nb-2.0.52r_291.aar` (celular, Firestick, Android TV genérico) |

Los STB con DRM propio (Mecool, Datamax, Intelbras…) usan otro AAR; se agregan acá cuando haga falta.
