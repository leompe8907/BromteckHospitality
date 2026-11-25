# Hospitality

App de TV para hoteles, separada de PanaccessApp. Una sola app genérica: la identidad del
hotel sale de su carpeta de marca y de la configuración que manda el operador en Panaccess.
Diseño del canvas "Hospitality Android TV", con módulos por tema.

**Regla:** este proyecto no modifica ni borra nada de `androidpanaccessapp`. Lo que se reutiliza
de ahí se **copia**, y en cada copia queda escrito de dónde salió.

## Qué funciona (probado en dispositivo con una cuenta real de Panaccess)

| Área | Estado |
|---|---|
| Login de la TV (lo hace el instalador una vez) | Credenciales → verificación → licencias → licencia habilitada. Se guarda cifrado (usuario + md5, nunca la contraseña) y la TV entra sola al reiniciar. Si ninguna licencia se habilita sola (todas en uso), pantalla para elegir una. Sin internet al arrancar, reintenta cada 15 s (no muestra el login). No hay forma de cerrar sesión desde la TV: el login vuelve sólo si el servidor rechaza las credenciales guardadas o se borran los datos de la app. Diseño "Recepción" (foto de la marca + formulario); el texto no nombra al proveedor |
| Recuperación de sesión | Si una llamada falla, el catálogo llega vacío o el player falla dos veces: `loggedIn` → re-verificar → re-habilitar la licencia → reintentar una vez (como `CasFunctionHelper` del base). Probado contra el servidor |
| Mensajes del operador (OSM) | Listener del DRM (`OsmListener` de pan_copyprotect); diálogo encima de todo. Sin probar con un mensaje real |
| Protección de copia | Licencia móvil + `configureAppBehaviorService` al habilitar la licencia; huella del operador dibujada sobre el video en vivo |
| Configuración del operador | `getClientConfig`: parámetros `X_…`, operador, CDN de la guía |
| TV en vivo | Filas por bouquet con logos; player HLS a pantalla completa con zapping (↑/↓, CH+/CH−) y "ahora / después". HLS y extractor de Panaccess, como el base (sin ellos, Record News cargaba para siempre) |
| Guía (EPG) | Canales + programación del canal elegido, posicionada en lo que se emite ahora. Elegir un canal (OK o toque) muestra su guía, no lo reproduce; se reproduce desde el programa "Ahora". Descarga ZIP del CDN, caché 3 h, 5 descargas a la vez |
| Catchup | Desde la guía: programa ya emitido con `catchupId` → "Ver de nuevo". **Sin probar con datos reales**: la cuenta de prueba no tiene catchup |
| VOD | Filas por categoría con pósters, ficha (fondo, duración, sinopsis) y player con pausa y ±10 s |
| Hotel | Idioma del huésped, "acerca del hotel", check-out (borra la sesión del huésped, no la de la TV) |
| App del celular | Info del hotel de la marca + "Conectar con la TV": código de 6 dígitos o QR (`hospitality://pair?code=…`). En la TV, "Conectar celular" muestra código + QR (se renueva al vencer). **Con versión de prueba**: falta el backend |
| Servicios del hotel | Catálogo de la marca (`assets/services.json`: bebidas, spa, room service, habitación; zonas piscina y playa con camastros numerados). TV: fila "Servicios del hotel" en la home, catálogo, pedido a la habitación con cargo a su cuenta, "Mis pedidos" con el estado y aviso cuando cambia. Celular: lo mismo, más pedir desde un camastro (QR `hospitality://spot?zone=pool&spot=12`), confirmación explícita del cargo y cancelar mientras nadie lo aceptó. **Con versión de prueba**: el pedido avanza solo; con el backend lo mueve el personal. Pago con tarjeta: espera la pasarela |
| App del personal (`app/staff`) | Cola de pedidos por estado (nuevos, en curso, entregados) con la ubicación primero, botón por paso (aceptar → salir a entregar → entregado, que registra el cargo) y los QR de cada camastro para imprimir. **Con versión de prueba**: pedidos de ejemplo |
| Configuración por archivo | `tools/configurar-tv.sh IP[=habitación] …` deja `setup.json` (usuario + md5, nunca la contraseña) en la carpeta de la app; la TV entra sola, toma la licencia de esa habitación y borra el archivo. Gana sobre la sesión guardada (mover de habitación); si las credenciales fallan y había sesión, sigue con ella. Probado el camino del archivo (lectura y borrado); falta probar con una cuenta real |
| Marca blanca | Carpeta por marca + datos del operador: habitación = nombre de la licencia, segundo logo = `tvModel`, "acerca del hotel" desde `X_HOTEL_APK_BASE_URL`/`X_HOTEL_APK_BASE_FOLDER` |

## Estructura

| Módulo | Qué tiene |
|---|---|
| `core/designsystem` | 3 direcciones visuales (resort, cinematográfico, minimalista), fuentes Cormorant Garamond + Rubik (OFL), `FocusCard` para el control remoto |
| `core/brand` | `brand.json`: identidad, flags, conexión a Panaccess |
| `core/hotel` | Contenido del hotel: JSON de la marca (formato de la rama RIU) o la API del sistema viejo |
| `core/entertainment` | Contrato del entretenimiento (canales, guía, VOD, qué reproducir) sin saber de Panaccess |
| `core/shared` | **Copia literal** de modelos y lógica de `PanaccessApp/shared` (ver `core/shared/SOURCE.md`) |
| `core/panaccess` | Conexión real: DRM, login con licencias, configuración, catálogo, guía, VOD. Port de los servicios iOS del base (`shared/iosMain`) |
| `core/companion` | Emparejamiento TV ↔ celular: contrato del backend (`TvPairing`, `CompanionLink`, `TvCommand`) + versión de prueba |
| `core/services` | Servicios del hotel: catálogo, pedidos y sus estados, contrato del backend (`GuestServices` para TV y celular, `StaffServices` para el personal) + versión de prueba y textos en 5 idiomas |
| `app/tv` | App Android TV (también Firestick y Android TV genérico) |
| `app/mobile` | App del celular, compañera de la TV: info del hotel, "Conectar con la TV" y servicios. No reproduce ni usa licencias de Panaccess |
| `app/staff` | App del personal (meseros, spa): pedidos y códigos QR de ubicaciones. applicationId: el de la marca + `.staff` |
| `tools/` | `configurar-tv.sh`: configuración de TVs por archivo del instalador |
| `buildSrc` | Carga de marcas compartida por las tres apps (`Brands.kt`) |
| `brands/<marca>/` | Una carpeta por marca |
| `libs/maven` | AAR del DRM de Panaccess copiado del base (ver `libs/README.md`) |

## Marcas

Cada carpeta de `brands/` genera un flavor; no se edita Gradle.

```
brands/<marca>/
  assets/brand.json       id (= carpeta), applicationId, displayName, dirección visual, idiomas, flags, panaccess{…}
  assets/hotel_data.json  contenido del hotel (opcional)
  assets/services.json    catálogo de servicios (opcional; se ofrece con features.roomService)
  res/                    íconos (mipmap-*), tv_banner, brand_logo, fuentes (res/font/hospitality_*.ttf)
```

- `generic` — **la app base**: "Hospitality TV", sin nombre de hotel. Todo lo del hotel llega del operador.
- `demo` — borrador "Aqua Caribe", con los textos de ejemplo de la rama RIU. Las fotos son de Unsplash y van dentro de la app: fondos en `assets/fondos/`, "acerca del hotel" en `assets/hotel/` (licencia y autores en `brands/demo/CREDITOS-fotos.md`). En `brand.json` y `hotel_data.json`, `"asset:carpeta/foto.jpg"` apunta a los assets de la marca.
- `royal_hotel` — borrador, cinematográfico, sin VOD. El logo no se copió: en PanaccessApp
  `brandings/royal_hotel/res/drawable/logo.png` es idéntico al de `yabnet`.

Los valores de `panaccess` por defecto (`NetworkBroadcast` / `Bromteck` / `cvsys` / `intv`) son los
del flavor `royal_hotel` del base. El descubrimiento sólo se usa si el usuario es un email (igual que el base).

## Compilar

El sistema no tiene Java; se usa el de Android Studio.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest assembleDebug
```

APKs: `app/tv/build/outputs/apk/<marca>/debug/`, `app/mobile/build/outputs/apk/<marca>/debug/` y `app/staff/build/outputs/apk/<marca>/debug/`
(applicationId del celular: el de la marca + `.mobile`, o `mobileApplicationId` en `brand.json`). Logs útiles: `adb logcat -s Hospitality.Login Hospitality.Session Hospitality.Catalog Hospitality.Vod Hospitality.Epg Hospitality.Player`
(nunca registran contraseñas ni PINs).

Para revisar el login en una TV que ya tiene sesión, sin borrarla (sólo en debug):

```bash
adb shell am start -n <applicationId>/com.networkbroadcast.hospitality.tv.MainActivity --ez showLogin true
```

Versiones iguales a PanaccessApp (AGP 9.4.0, Kotlin 2.2.10, Gradle 9.6.0, Compose BOM 2025.11.00, Media3 1.6.1).

## Decisiones

- **Copiar en vez de extraer del base.** La integración Panaccess de Android vive dentro de `:app`
  del base, mezclada con `MainActivity`, y esa rama está en plena migración: moverla ahora
  generaba conflictos. Se copió lo mínimo, siguiendo la versión iOS del base (que ya es una
  reimplementación limpia), y los modelos de `:shared` con su paquete original. Cuando el base
  tenga un módulo común, `core/shared` y `core/panaccess` se reemplazan por él.
- **HLS y extractor de Panaccess, como el base.** `media3-exoplayer-hls` se reemplaza por
  `lib-exoplayer-hls-release.aar` (versión modificada) + `PanHlsExtractorFactory`
  (`panexo_datasource`). Con el HLS oficial algunos canales cargaban para siempre sin error.
- **Protección de copia: un solo contenedor por proceso.** `CopyprotectService.unregister()` no
  vuelve nunca (traba el hilo que lo llama), así que el contenedor se registra una vez y cada player
  lo pone encima del video. Las llamadas al servicio van en un hilo propio, nunca en el principal.
- **La sesión vive en la `Application`**, no en la Activity: si Android recrea la pantalla
  (desbloqueo, giro) la TV no vuelve a hacer el login con licencia.
- **La app de TV es sólo horizontal.** El celular va a ser una app compañera aparte (`app/mobile`).

## Pendiente

1. **Catchup con datos reales** (la cuenta de prueba no tiene) y la pantalla de grupos de catchup
   (`CatalogService.catchupGroups/catchupEvents` ya está).
2. **OSM con un mensaje real** del operador (el listener está registrado; falta que llegue uno).
3. **Logo real de Royal Hotel.**
4. **App móvil: emparejamiento real.** Las pantallas y el contrato están (`core/companion`); hoy usan
   la versión de prueba (la TV inventa el código y el celular acepta cualquiera de 6 dígitos). Se
   reemplaza en `HospitalityApplication.tvPairing` y `MobileApplication.companion` cuando exista el
   backend. La TV ya ejecuta las órdenes (`TvCommand`) que lleguen.
5. **Ver TV en el celular** (TV Everywhere): pendiente de definir licencias (cada celular usaría una).
6. **"Conectar celular" en la home Lobby** (`LobbyHome.kt`, `PairTile`): el tile muestra el QR y el
   código, pero todavía no está terminado. Pide su propio código de emparejamiento mientras la home
   está abierta y `PairPhoneDialog` pide otro: con el backend real, el segundo podría invalidar el
   del tile. Ni el tile ni el diálogo se enteran de cuándo el celular se conectó. Hoy, con la
   versión de prueba (`DemoTvPairing`), el código es inventado. Si el operador carga `X_APP_WIFI`, el
   QR del Wi-Fi ocupa su lugar en la home Lobby (ver punto 8).
7. **Servicios del hotel con pedidos.** La TV solo muestra lo que manda la cuenta de Panaccess
   (`X_APP_SERVICES`, formato en `docs/x-app-services.md`), sin opción de pedir (`ReadOnlyServices`);
   sin ese parámetro no muestra la sección. Pedir necesita la conexión con el sistema del hotel
   (CRM/PMS), que todavía no existe: ver `docs/backend-idea.md`. El catálogo y los pedidos de prueba
   (`services.json`, `DemoGuestServices`) ya no se usan en la TV; siguen en el celular y en la app
   de staff.
8. **Parámetros nuevos de Panaccess, a configurar en cada cuenta.** Son tres (el tercero es un nombre
   propuesto que hay que crear en Panaccess):
   - `X_APP_SERVICES` — menú y servicios del hotel (bebidas, spa, etc.). Lleva el JSON directo o una
     URL a un archivo con ese JSON, igual que `X_APP_WIDGETS`: como son varias categorías, textos en
     cinco idiomas y fotos, conviene la URL. Formato completo en `docs/x-app-services.md`. Sin él, la
     sección de servicios no aparece en la TV.
   - `X_APP_WIFI` — la red Wi-Fi de huéspedes, para el QR de la home: un JSON directo, nunca una URL
     (`{"ssid":"HotelGuest","password":"abc12345x","security":"WPA","hidden":false}`). Formato completo
     en `docs/x-app-wifi.md`. Sin él, no se muestra nada de Wi-Fi.
   - `X_APP_MOBILE_TILES` — **nombre propuesto, todavía no existe en Panaccess: hay que crearlo.** Orden y
     tamaño (`S`, `M`, `W`) de los tiles de la home de la app del celular: un JSON directo
     (`[{"id":"live","size":"M"},{"id":"widgets","size":"M"},{"id":"wifi","size":"S"}]`). Formato completo en
     `docs/x-app-services.md`. Sin él, el celular usa un orden por defecto.
   - `X_APP_MOBILE_BACKGROUND` — **nombre propuesto, hay que crearlo.** Fondo de la app del celular, aparte del
     de la TV: la URL de una foto o un color en hex (`#0E2E32`). Sin él, el fondo liso del tema. Detalle en
     `docs/x-app-services.md`.
   - Falta darlos de alta en el documento de parámetros de personalización remota (Cableatlántico) y
     cargarlos en la cuenta de cada hotel.
   - Probar con una cuenta real que el valor llegue y se muestre (hoy solo está probado el parseo,
     `OperatorServicesTest` y `OperatorWifiTest`), y ver el QR de Wi-Fi en una TV real: nunca se vio ahí,
     y con un celular de verdad queda por confirmar que lo lea desde el sillón.

## Backend propio (otro proyecto)

Servicios del hotel, mensajería dirigida, recomendaciones con IA (reemplazan a Binit, que no se
porta), panel, Opera PMS, domótica, pagos y analíticas van en un proyecto aparte. La idea, y cómo se
engancha con esta app, está en [`docs/backend-idea.md`](docs/backend-idea.md); las funcionalidades
importantes y diferenciales, con prioridades, en [`docs/backend-funcionalidades.md`](docs/backend-funcionalidades.md). Los flags
(`roomService`, `housekeeping`, `wakeUpCall`, `messaging`) ya existen en `brand.json`, apagados.
