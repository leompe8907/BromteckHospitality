# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Hospitality TV: a white-label Android TV hotel app, separate from `androidpanaccessapp` (the
PanAccess streaming app, a sibling project by the same company). One generic app per surface —
the hotel's identity comes from its brand folder, the operator config comes from Panaccess at
runtime. Three independent apps share the same brand flavors: `app/tv` (the TV/Firestick app,
horizontal only), `app/mobile` (a companion phone app — hotel info + "connect to TV" + ordering,
does not play any Panaccess content or hold licenses), `app/staff` (waiter/staff order queue + QR
codes for locations).

**Hard rule:** this project never modifies or deletes anything in `androidpanaccessapp`. Anything
reused from there is *copied*, with the source commit recorded in a `SOURCE.md` next to the copy
(see `core/shared/SOURCE.md`). If a change is needed in copied code, it goes into
`androidpanaccessapp` first and gets re-copied here.

The proprietary backend (hotel content panel, PMS integration, domotics, payments, AI concierge)
is a **separate, not-yet-built project**. This repo only has the client-side contracts/interfaces
ready to plug it in, plus trial/demo implementations so screens work without it. See
`docs/backend-idea.md` and `docs/backend-funcionalidades.md` for that project's design and
priorities, and the root `README.md` "Pendiente" section for what's untested or stubbed in this repo.

## Commands

No system JDK — this project only builds with the JDK bundled in Android Studio.

```bash
# set once per shell if gradlew can't find a JDK; path depends on OS/Android Studio location
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # macOS example

./gradlew testDebugUnitTest assembleDebug   # unit tests + debug APKs, all brands
./gradlew :core:hotel:test                  # unit tests for one module
./gradlew test --tests "*.HotelDataTest"    # a single test class (any module)
./gradlew :app:tv:assembleDemoDebug         # debug APK, one app + one brand flavor
```

APKs land in `app/tv/build/outputs/apk/<brand>/debug/`, `app/mobile/build/outputs/apk/<brand>/debug/`,
`app/staff/build/outputs/apk/<brand>/debug/`. Mobile/staff applicationIds are the TV brand's
applicationId + `.mobile` / `.staff` (or `mobileApplicationId`/`staffApplicationId` in `brand.json`
if set explicitly).

Useful TV logs: `adb logcat -s Hospitality.Login Hospitality.Session Hospitality.Catalog Hospitality.Vod Hospitality.Epg Hospitality.Player`
(never logs passwords or PINs).

Re-check the Panaccess login screen on a TV that already has a session, without clearing it (debug only):
```bash
adb shell am start -n <applicationId>/com.networkbroadcast.hospitality.tv.MainActivity --ez showLogin true
```

Versions pinned to match `androidpanaccessapp`: AGP 9.4.0, Kotlin 2.2.10, Gradle 9.6.0,
Compose BOM 2025.11.00, Media3 1.6.1 — keep them in sync when bumping either repo.

## Architecture

**Module boundary = "who talks to what".** The three apps never depend on each other, only on
`core/*`. `app/mobile` and `app/staff` deliberately don't depend on `core:panaccess` or
`core:entertainment` — the phone/staff apps never touch Panaccess licenses or play video; they
only talk to the TV in the room via `core:companion`.

| Module | Responsibility |
|---|---|
| `core:designsystem` | The 3 visual directions (`VisualDirection`: RESORT, CINEMATIC, MINIMAL) as color/type tokens in `HospitalityColors.kt`, plus shared Compose components (`FocusCard` — the one remote-control-focusable card every screen uses). |
| `core:brand` | Parses `brands/<brand>/assets/brand.json` into `BrandConfig`: identity, which `Features` are on/off, Panaccess connection defaults, color token overrides. This is the single white-label seam — no per-brand `if`/`else` anywhere else in the code. |
| `core:hotel` | "About the hotel" content contract (`HotelRepository`): reads from a brand asset (`hotel_data.json`) or a URL, with a fallback-to-asset implementation ready for when a real backend exists. |
| `core:entertainment` | Pure domain contract for channels/guide/VOD — knows nothing about Panaccess. |
| `core:shared` | Literal copy of domain models from `androidpanaccessapp/PanaccessApp/shared` (CAS envelopes, EPG, VOD models). Never edit here — see `core/shared/SOURCE.md` for the rule and the exact source commit. |
| `core:panaccess` | The real integration: DRM, license login, operator config (`getClientConfig`), catalog, EPG, VOD, copy-protection overlay, OSM operator messages. Ported from the iOS side of `androidpanaccessapp` (cleaner than the Android side, which is mid-migration). |
| `core:companion` | TV↔phone pairing contract (`TvPairing`, `CompanionLink`, `TvCommand`) plus a `DemoCompanion` trial implementation (random 6-digit code, any valid-format code accepted). Real pairing needs the backend — see "Pendiente" in README. |
| `core:services` | Hotel services catalog + order lifecycle, contracts for TV/phone (`GuestServices`) and staff (`StaffServices`), plus a demo implementation and UI text in 5 languages. |
| `buildSrc` (`Brands.kt`) | Turns each `brands/<brand>/` folder into a Gradle product flavor, shared by all three apps. Adding a brand = adding a folder; **never edit the apps' `build.gradle.kts` for this.** |

**White-labeling:** `brands/<brand>/assets/brand.json` (+ optional `hotel_data.json`,
`services.json`) and `brands/<brand>/res/` are picked up automatically by every app's flavor
source set. Current brands: `generic` (the base app, no hotel identity — everything about the
hotel comes from the operator), `demo` ("Aqua Caribe", Unsplash photos bundled as assets,
`"asset:folder/photo.jpg"` paths resolve via `imageModel()` in `BrandConfig.kt`), `royal_hotel`
(cinematic direction, no VOD, placeholder logo).

**Panaccess is the only source of entertainment; the (future) backend is the only source of
everything about the hotel.** Neither app talks to a PMS, domotics, or payment gateway directly —
that always goes through the backend contracts in `core:hotel`/`core:services`/`core:companion`.

**Player internals worth knowing before touching `core:panaccess`:**
- HLS uses a Panaccess-modified AAR (`libs/maven`, see `libs/README.md`) instead of stock
  `media3-exoplayer-hls` + a custom `PanHlsExtractorFactory` — stock HLS hung forever on some
  channels.
- Copy-protection registers one overlay container per process and never unregisters it
  (`CopyprotectService.unregister()` blocks the calling thread forever) — all calls to that
  service must run off the main thread.
- The Panaccess session lives in the `Application`, not the `Activity`, so an Activity recreation
  (screen unlock, rotation) doesn't force a re-login.

## Feature flags

`BrandConfig.Features` gates both the menu entry and the screen for each feature per brand —
unlike the old app's `VOD_ENABLED`, which existed with no caller. `housekeeping`, `wakeUpCall`,
`messaging`, and `roomService` default off; they turn on per-brand once the hotel backend exists
to back them (today `roomService` can run against the demo implementation for trial purposes).
