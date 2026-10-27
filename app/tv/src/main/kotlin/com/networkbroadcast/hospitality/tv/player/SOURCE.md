# tv/player — copia de PanaccessApp

Copia de `androidpanaccessapp/PanaccessApp/app/src/main/java/com/panaccess/android/streaming/activity/video/`,
tomada del commit `2f6ade452` (2026-10-26); los dos archivos no cambian desde `e0528f515` (2025-03-27).

| Archivo | Original | Cambios |
|---|---|---|
| `LimitTrackSelection.kt` | `video/LimitTrackSelection.kt` | solo el `package` |
| `BitrateLimitter.kt` | `video/BitrateLimitter.kt` | el `package` y el log (`LogHelper` → `android.util.Log`, que no existe acá) |

Qué resuelven: arrancar cada reproducción en el perfil HLS más bajo (tope de 2,5 Mbps) y soltar
el tope apenas el player llega a `STATE_READY`. El primer frame sale bastante antes — lo que más
se nota al zapear. En el base es `PLAYER_HLS_ALWAYS_START_WITH_LOWEST_PROFILE` (`BuildConfig`,
prendido para varios clientes); acá es un parámetro de cuenta, ver
`OperatorConfig.playerStartWithLowestProfile`.

No editar acá: si hace falta un cambio, va en PanaccessApp y se vuelve a copiar.
