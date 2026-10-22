# Contexto de esta sesión — caché liviana + perf de Guía/EPG

## ⚠️ Atención antes de leer el resto

- Branch pedido: `cache`. Branch real en el que se trabajó: **`valentina/widgets`** (la que estaba
  activa al arrancar la sesión). Nunca se creó ni se hizo checkout a una rama `cache`.
- `git status`/`git diff --stat` en `/home/sw1/Desktop/valentina/BromteckHospitality` **no muestran
  ningún cambio pendiente**, y `OperatorConfig.kt` en disco **no contiene** los cambios descriptos
  abajo (`toCacheJson`/`@Serializable`). Es decir: todo lo que sigue se hizo y se verificó compilando
  en esta sesión (con `./gradlew`, builds en verde), pero el estado actual del archivo en disco no
  lo refleja. Hay que confirmar si:
  - los cambios quedaron en otro de los worktrees detectados (`BromteckHospitality-content-preloading`
    en `fix/content-preload`, o los de `.claude/worktrees/agent-*` en `frontend`/`valentina/fixes`), o
  - algo los revirtió/descartó.
  **No asumir que el código de este resumen está aplicado sin volver a verificarlo.**

## Pedido original

Implementar el plan en `~/.claude/plans/wild-tickling-lollipop.md`: caché liviana en disco (JSON
por tipo de dato) para que Hospitality TV arranque sin "cargando" — sin Room/SQLite, solo
`OperatorConfig` + canales + VOD. Explícitamente **no** cachear `Program`/guía en vivo (demasiado
sensible al tiempo).

## Lo implementado (Etapas A y B del plan)

- **`core/panaccess/OperatorConfig.kt`**: `@Serializable` + `toCacheJson()`/`fromCacheJson()`.
- **`core/panaccess/PanaccessSession.kt`**: lee la caché de `operatorConfig` en `init{}` (síncrono,
  mismo criterio que `EncryptedSharedPreferences.create` ahí mismo); escribe a disco tras cada fetch
  exitoso; fix de null-coalescing en `onLicenseEnabled`/`refreshOperatorConfig` (`?: operatorConfig`,
  para no pisar un valor cacheado bueno con `null` ante un fallo de red).
- **`core/entertainment/EntertainmentSource.kt`**: `@Serializable` en `ChannelsResult`/`Channel`/
  `ChannelGroup`/`VodResult`/`VodShelf`/`VodItem` (no en `Program`, a propósito).
- **`core/entertainment/EntertainmentCache.kt`** (nuevo): caché JSON por archivo, recibe `File` no
  `Context` (módulo testeable en JVM puro).
- **`app/tv/HospitalityApplication.kt`** / **`HospitalityApp.kt`**: lee caché de canales/VOD antes
  del fetch real; guarda si `failure == null`.
- Tests: `OperatorConfigTest.kt` (round-trip + basura→null), `EntertainmentCacheTest.kt` (nuevo).
- `build.gradle.kts` de `core:panaccess` y `core:entertainment`: plugin `kotlin.serialization`
  agregado (siguiendo el patrón de `core:hotel`).

Verificación en esta sesión: `testDebugUnitTest` + `:app:tv:assembleDemoDebug` en verde en cada
iteración.

## Seguimiento: "sigue cargando" al navegar la Guía (programas pasados)

El usuario probó en un TV real y reportó que "programas pasados" (Guía/catchup) seguía mostrando
"cargando" todo el tiempo, incluso para canales ya vistos, incluso scrolleando dentro de la misma
pantalla sin salir. Esto está **fuera del alcance original** (Program/guía no se cachea a propósito),
pero se investigó y arregló lo relacionado:

### Decisiones tomadas por el usuario (vía AskUserQuestion)

1. Sí, implementar los 2 fixes de perf de Guía/EPG que estaban pospuestos (ya identificados en una
   memoria previa del proyecto, que se borró al implementarse):
   - Desacoplar el gate `catalogReady` de VOD (Home/Guía/TV en vivo entran solo con `channels != null`;
     `VodScreen` ya tenía su propio "cargando" si se entra antes de que VOD resuelva).
   - Dedup de pedidos en vuelo en `EpgService` (Mutex + mapa de `Deferred` por `epgStreamId:pastDays`),
     para que pedidos concurrentes al mismo canal (Home + Guía + player) se unan a un solo fetch.
2. Sí, agregar precarga de guía para los primeros 8 canales (catchup primero) mientras se está en
   Home, para que "programas pasados" no sea la primera vez que se pide esa guía.
3. Sí, agregar logs temporales de diagnóstico (marcados `DIAG` + `// TODO sacar log temporal`) en vez
   de asumir, para confirmar con datos reales en vez de adivinar.

### Bug real encontrado y arreglado (no estaba en el plan original)

Al precargar `channels` desde `EntertainmentCache` sin pasar por `PanaccessEntertainmentSource.
channels()`, el campo interno `streams` (que trae `epgStreamId`, no presente en el `Channel` liviano
de la UI) quedaba vacío hasta que terminara el fetch real en background — si se entraba a la Guía
antes de eso, `schedule()` podía devolver vacío por una carrera. Fix: `schedule()` ahora espera a
`channels()` (idempotente, mismo candado) si `streams` está vacío, antes de buscar el canal.

### Diagnóstico en vivo con adb logcat (dispositivo real, 192.168.255.17:5555)

Con los logs `DIAG` instalados, el logcat mostró: **100% cache hit en disco en `EpgService`, cero
pedidos de red reales** — pero cada `schedule(...)` igual tardaba 150–450 ms, siempre, incluso para
el mismo canal repetido muchas veces seguidas. Eso descartó la hipótesis de "vuelve a pedir red".

**Causa real encontrada:** `cache.read()` + el parseo del JSON (días de programación por canal) en
`EpgService.events()` corrían en el hilo que llama a `events()` — el hilo principal de Compose,
porque `GuideScreen`/`HospitalityApp` corren sus `LaunchedEffect` ahí. El camino de red (cache *miss*)
sí estaba bien despachado a `Dispatchers.IO` (vía `scope.async`), pero el camino rápido (cache *hit*)
bloqueaba la UI leyendo el archivo y deserializando el JSON cada vez.

**Fix aplicado:**
- `EpgService.kt` (`events()`): la lectura de caché + `parse()` ahora corren dentro de
  `withContext(Dispatchers.IO)`, igual que el camino de red.
- `PanaccessEntertainmentSource.kt` (`schedule()`): el `mapNotNull`/`sortedBy` que parsea fechas de
  cientos de eventos (CPU, no I/O) ahora corre en `withContext(Dispatchers.Default)` en vez del hilo
  que llama a `schedule()`.

**Pendiente de confirmar:** el usuario iba a recompilar, reinstalar y volver a probar con los logs
`DIAG` todavía puestos, para confirmar que el tiempo de "cargando" bajó. Si se confirma, hay que
sacar todos los logs marcados `// TODO sacar log temporal` / `DIAG` (en `EpgService.kt` y
`PanaccessEntertainmentSource.kt`, y los que se agregaron en `HospitalityApp.kt` para el caché de
canales/VOD) antes de dar esto por terminado.

## Archivos tocados en esta sesión (si están aplicados — ver advertencia arriba)

- `core/panaccess/src/main/kotlin/.../OperatorConfig.kt`
- `core/panaccess/src/main/kotlin/.../PanaccessSession.kt`
- `core/panaccess/src/main/kotlin/.../EpgService.kt`
- `core/panaccess/src/main/kotlin/.../PanaccessEntertainmentSource.kt`
- `core/panaccess/build.gradle.kts`
- `core/panaccess/src/test/kotlin/.../OperatorConfigTest.kt`
- `core/entertainment/src/main/kotlin/.../EntertainmentSource.kt`
- `core/entertainment/src/main/kotlin/.../EntertainmentCache.kt` (nuevo)
- `core/entertainment/src/test/kotlin/.../EntertainmentCacheTest.kt` (nuevo)
- `core/entertainment/build.gradle.kts`
- `app/tv/src/main/kotlin/.../HospitalityApplication.kt`
- `app/tv/src/main/kotlin/.../HospitalityApp.kt`

## Herramientas usadas / contexto adicional

- Se usó `graphify` (grafo ya existente en `graphify-out/`) al principio para ubicar los archivos
  del plan antes de tocar código.
- Dispositivo de prueba: TV Android en `192.168.255.17:5555` (vía adb sobre red). Había un segundo
  dispositivo `192.168.4.239:5555` que no se usó.
- Worktrees detectados en el repo (no investigados a fondo, pueden ser relevantes para entender dónde
  terminó el código real):
  - `/home/sw1/Desktop/valentina/BromteckHospitality-content-preloading` → branch `fix/content-preload`
  - `.claude/worktrees/agent-a3970d60784375a75` → branch `frontend`
  - `.claude/worktrees/agent-a8af2a2d573ceacae` → branch `valentina/fixes`
