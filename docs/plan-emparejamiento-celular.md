# Plan: celular emparejado con la TV del hotel

Estado: **borrador (2026-11-25). Implementado solo lo que no depende de dudas: ver sección 8.** Las dudas que
frenaron el resto están en la sección 9. Complementa [`emparejamiento.md`](emparejamiento.md) (diseño del
emparejamiento) y no lo reemplaza.

Lo que está marcado como **(verificado)** salió de leer el código o los docs de los repos. Lo marcado como
**(estimación)** o **(inferencia)** es mío y hay que confirmarlo.

---

## 1. Qué queremos

1. El huésped **escanea el QR de la TV** con el celular.
2. Al escanear, el celular recibe **el login de Panaccess (usuario y contraseña) y el tema del hotel**. Si el
   huésped ya tiene la app, **entra solo**, sin escribir nada.
3. Desde el celular puede **usar la TV como control remoto** y **ver el contenido** (video) por su cuenta.
4. Un botón **"enviar a la tele"** hace que lo elegido empiece a reproducirse en la TV.
5. Cada TV tiene **4 licencias**: 1 para la TV y 3 para celulares. Si no queda ninguna libre, la app del celular
   abre **sin video** (sigue sirviendo de control remoto).
6. Al hacer **check-out**, el celular **borra el login y vuelve a la pantalla de bienvenida genérica del hotel**.
   Las licencias quedan libres para el siguiente huésped. El hotel es responsable de hacer el check-out
   (desde la TV, el celular o el panel de staff).

Decisiones ya tomadas en esta conversación:

- La cuenta de Panaccess es **una por hotel** (el usuario cree que sí; falta confirmarlo, ver D1).
- Las credenciales **van cifradas en el backend**.
- No hay una cuenta de Panaccess por celular. Cada celular toma una licencia de la cuenta del hotel.
- La interfaz del celular la está haciendo el usuario en otra rama; este plan solo cubre la lógica y la conexión.

Decisiones del 25/11 (respuestas a las dudas, detalle en 5.0):

- La habitación se identifica por el **nombre de la licencia** de la TV, que no cambia porque la TV nunca cierra sesión.
- Se asume **4 licencias por habitación** y se prueba con una cuenta real.
- El alta de cada TV la **autoriza el staff desde el panel**.
- La clave de cifrado vive **en el servidor**.
- El celular entra con **confirmación en la TV** (alguien con el control remoto acepta).
- Check-out desde la TV, el celular o el panel. La TV **no cierra sesión** (solo vuelve a elegir idioma); el celular **sí**.
- Tras el check-out el celular muestra una **pantalla genérica configurable por el hotel** ("escaneá el QR de la tele").
- **3 celulares con video**; el resto, solo control remoto.
- Desde el celular se puede enviar a la TV **cualquier contenido que se pueda transmitir**.
- **Android primero**, iOS después.
- Panel de staff y API comparten servidor y dominio. El despliegue no está definido y no hace falta definirlo ahora.

---

## 2. Arquitectura

### 2.1 Quién habla con quién

```
     TV (en la habitación)            BACKEND HOSPITALITY                CELULAR (huésped)
    ┌────────────────────┐          ┌─────────────────────────┐        ┌────────────────────┐
    │ app/tv             │─código──►│ pairing/                │◄─canje─│ app/mobile         │
    │                    │◄─órdenes─│  códigos, celulares,    │        │                    │
    │ muestra el QR      │ (polling)│  órdenes, check-out     │─login─►│ recibe usuario +   │
    │ ejecuta las órdenes│          │                         │ +tema  │ clave + tema       │
    └─────────┬──────────┘          │ hotel/                  │        └─────────┬──────────┘
              │                     │  login de Panaccess del │                  │
              │                     │  hotel (CIFRADO)        │                  │
              │                     └─────────────────────────┘                  │
              │ login + video                                          login + video
              │                     ┌─────────────────────────┐                  │
              └────────────────────►│  PANACCESS              │◄─────────────────┘
                                    │  cuenta del hotel       │
                                    │  licencias por habitación│
                                    │  (1 TV + 3 celulares)   │
                                    └─────────────────────────┘
```

Reglas de la arquitectura:

- **El video nunca pasa por nuestro backend.** TV y celular hablan con Panaccess directamente.
- **El backend solo hace de intermediario** entre TV y celular (códigos, órdenes, check-out) y de **guardián del
  login del hotel**. Los dos están en internet, no hace falta que estén en la misma red.
- **En la primera versión el backend no habla con Panaccess.** Las licencias ya existen en la cuenta del hotel
  (se crean a mano o con un script en Panaccess) y el celular simplemente toma una libre al hacer login.

### 2.2 Flujo A: alta de la TV (una vez por TV)

```
 Staff/instalador ──(1) alta autorizada──► Backend ──(2) tvId + secreto──► TV (lo guarda cifrado)
```

Hoy el alta (`POST /api/tv/register/`) es pública: solo pide hotel y habitación **(verificado)**. Hay que cerrarla
antes de que el canje entregue credenciales (ver D6).

### 2.3 Flujo B: emparejar el celular

```
 TV                         Backend                         Celular                       Panaccess
  │─(1) pide código────────►│                                 │                               │
  │◄─(2) 123456 (10 min)────│                                 │                               │
  │  muestra QR con el      │                                 │                               │
  │  enlace ...?code=123456 │          (3) escanea el QR, la app se abre con el código        │
  │                         │◄─(4) POST /api/phone/pair/──────│                               │
  │                         │      {code}                     │                               │
  │                         │─(5) token + handoff (tema) ────►│                               │
  │                         │      + session (usuario+clave)  │                               │
  │                         │                                 │─(6) login + toma una licencia►│
  │◄─(7) "celular conectado"│  (la TV lo ve al consultar      │◄──────────────────────────────│
  │      (consulta cada 2-3s)  /api/tv/phones/)               │                               │
```

- El QR sigue llevando **solo el código** (regla de `contract/pairing.json`). El login viaja en la respuesta del
  canje (paso 5), por HTTPS, **nunca en el QR**.
- El paso 5 usa un gancho que ya existe en el backend: `SessionProvisioner.provision` **(verificado)**. Hoy
  no entrega nada. Hay que escribir la versión que entrega el login del hotel.
- Si el hotel no tiene login cargado o no hay licencias, el celular igual se vincula (control remoto), sin video.

### 2.4 Flujo C: usar el celular

```
 Control remoto:  Celular ──POST /api/phone/commands/──► Backend ◄──consulta cada 2-3 s── TV ejecuta
 Enviar a la tele: es el mismo camino con PlayChannel / PlayVod (solo viaja el id)
 Ver contenido:   Celular ◄────────────── directo ──────────────► Panaccess (con su propia licencia)
```

- La orden demora **2 a 3 segundos** por el polling **(verificado: `docs/emparejamiento.md`)**.
- La orden lleva solo el id del canal o de la película. Cada dispositivo reproduce con **su** licencia.
- "Seguir viendo desde donde voy" (con la posición) **no está** en el contrato actual.

### 2.5 Flujo D: check-out

```
 Staff (panel) / TV / Celular ──► Backend: sube el "número de estadía", desvincula TODOS los celulares
        │
        ├─► TV: ve que el número subió (aunque estuviera apagada), borra la sesión del huésped,
        │       vuelve a elegir idioma. No toca la licencia de la TV.
        └─► Celular: su próxima consulta recibe 401 "Unlinked" ──► borra el login guardado,
                     cierra sesión en Panaccess (libera la licencia) y vuelve a la pantalla genérica
```

Ya está hecho en el backend **(verificado)**: check-out desde la TV, el celular o el staff
(`/api/staff/checkout/`, panel `/panel/rooms/`), desvincular todo, número de estadía y el 401 `Unlinked`.
Falta que las apps lo usen.

---

## 3. Qué ya tenemos, qué copiar de Wind y qué falta

### 3.1 Ya tenemos (sin copiar nada)

| Dónde | Qué |
|---|---|
| Backend `pairing/` (**verificado**, ~1.400 líneas + tests) | Código de 6 dígitos con vencimiento a 10 min, un solo uso y tope de intentos; canje; varios celulares por TV; `handoff` (hotel, habitación, idioma, logo, fondo, colores); órdenes con cursor; check-out/estadía; panel de staff de habitaciones; `PAIRING_ENABLED` |
| Backend `pairing/provisioning.py` | El **gancho** `SessionProvisioner` por donde entregar el login al celular (hoy stub) |
| Backend `contract/pairing.json` | Reglas compartidas entre backend y app |
| Android `core/companion` | Las interfaces `TvPairing`, `CompanionLink`, `TvCommand` y una **simulación** (`DemoCompanion`) |
| Android TV | Ya ejecuta las `TvCommand` que le lleguen |
| Android `core/panaccess` | Login con usuario + md5 (acepta md5 de 32 caracteres), DRM de celular, catálogo, guía, VOD, configuración del operador |
| Android `app/mobile` | Recibe el enlace `hospitality://pair?code=…` y lee el código |

### 3.2 Copiar de Wind (`panaccess_wind_integration`, solo lectura: se copia con `SOURCE.md`)

| Qué | Archivo en Wind | Para qué | ¿Hace falta ya? |
|---|---|---|---|
| Cifrado de valores con Fernet | `wind/utils/encryption.py` (18 líneas) | Guardar el login del hotel cifrado | **Sí** |
| Guardar un secreto cifrado y leerlo | patrón de `SubscriberLoginInfo.set_password/get_password` en `wind/models.py` | Mismo uso | **Sí** (solo el patrón) |
| Alta/baja de licencias por API | `subscriber_provisioning.py`, `panaccess_deprovision.py`, `panaccess_singleton.py`, `panaccess_client.py`, circuit breaker y session store (~2.100 líneas) | Crear las 4 licencias por habitación y quitarlas por API | **No** en la primera versión (ver D2) |
| Reservas de licencia con vencimiento | rama `feat/perfiles-reservas-licencias` | Evitar licencias "en uso" fantasma | Solo si aparece ese problema (D3) |
| Casteo con posición y estado | ramas `feature/casteo*` (backend; las apps no lo usan) | "Seguir viendo donde iba", TV → celular | **No**, es una mejora posterior |
| Canal en tiempo real (WebSocket) | `wind/consumers.py`, `device_consumers.py` | Bajar los 2-3 s de demora | **No**, hoy alcanza el polling |

**No sirve copiar** el flujo UDID de Wind: resuelve otro problema (entregarle credenciales a una TV desde una
cuenta de Wind) y depende de cuentas, JWT y smartcards de Wind.

### 3.3 Lo que falta

| # | Falta | Dónde |
|---|---|---|
| F1 | Guardar el login de Panaccess del hotel, cifrado, y cargarlo desde el panel de staff | Backend |
| F2 | La versión del `SessionProvisioner` que entrega ese login al canjear | Backend |
| F3 | Cerrar el alta de TV (código de activación o clave de instalación) | Backend |
| F4 | Actualizar `contract/pairing.json` y sus tests con el campo `session` | Backend |
| F5 | Cliente real de la TV (`TvPairing` que llama al backend) | Android `core/companion` + `app/tv` |
| F6 | Cliente real del celular (`CompanionLink` que llama al backend) | Android `core/companion` + `app/mobile` |
| F7 | Que `app/mobile` use `core:panaccess` para el login automático y el video | Android `app/mobile` |
| F8 | Manejo del check-out en el celular (401 → borrar login, cerrar sesión, pantalla genérica) | Android `app/mobile` |
| F9 | Que el celular aplique el tema (`handoff`) | Android `app/mobile` |
| F10 | Botón "enviar a la tele" conectado a `send(...)` | Android `app/mobile` (UI en la otra rama) |
| F11 | URL del backend en las apps (hoy no hay ninguna) | Android |
| F12 | Dónde correr el backend: dominio, HTTPS, hosting (hoy no está desplegado, **verificado en `docs/todo-hospitality.md`**) | Infra |
| F13 | Dominio del QR y App Links para que el enlace `https` abra la app o la tienda | Backend + Android |
| F14 | Prueba de punta a punta con una cuenta real de Panaccess | Todos |

---

## 4. Plan por fases

Los tamaños son una **(estimación)**: S = pocas horas, M = unos días, L = más de una semana.

### Fase 0: cerrar las dudas que bloquean (ver sección 5)

Los bloqueantes originales (D1, D2, D3, D6, D7, D17) quedaron resueltos o asumidos el 25/11 (ver 5.0). Antes de
escribir código falta decidir **N4** (polling o WebSocket) y acordar **N6** con quien hace la UI.
Hecho cuando: cada una tiene respuesta escrita en este documento.

### Fase 1: backend (`BromteckHospitalityBackend`) — tamaño M

1. Modelo y formulario del login del hotel: usuario y **md5** de la contraseña, cifrado con Fernet. El panel
   calcula `md5(contraseña + "_panaccess")` (el mismo cálculo de `PanaccessClient`) y descarta la contraseña.
   Agregar `cryptography` a `requirements.txt` (hoy no figura, **verificado**). Copiar el cifrado de Wind con su
   `SOURCE.md`. La clave de cifrado va en el entorno, nunca en la base. (F1)
2. `HotelLoginProvisioner` (implementa `SessionProvisioner`): al canjear, devuelve usuario + md5 en `session`.
   Sin login cargado devuelve `None` (celular sin video). `release` no hace nada. (F2)
3. Alta de TV autorizada: el staff genera un código de activación de un solo uso en el panel y la TV lo manda al
   registrarse. (F3)
4. Confirmación en la TV: el canje deja la vinculación pendiente hasta que la TV acepta; el login se entrega
   recién ahí (N1). Vencimiento del código más corto (N2).
5. Contrato: campo `session`, orden `PlayCatchup` (N3) y estado "pendiente"; `contract/pairing.json`, tests y
   `docs/emparejamiento.md` actualizados. (F4)
6. Pruebas: el canje entrega el login solo a un celular vinculado y confirmado; no aparece en logs ni en el panel.

Hecho cuando: los tests del backend pasan y un canje de prueba devuelve el login cifrado en base y en claro solo
en la respuesta.

### Fase 2: Android TV — tamaño M

1. `HttpTvPairing`: alta, pedir código, consultar celulares y órdenes, check-out. Guarda el secreto cifrado. (F5)
2. Un solo código compartido entre el tile y `PairPhoneDialog` (el pendiente de `emparejamiento.md`).
3. Aviso "celular conectado" con **pantalla de confirmación** ("¿Permitir que [celular] se conecte?", se acepta con
   el control remoto), ejecutar `TvCommand` (incluido `PlayCatchup`), leer el número de estadía y volver a elegir
   idioma. Código de activación de la TV.
4. URL del backend (F11).

Hecho cuando: una TV real muestra un código válido, ve conectarse al celular y reproduce una orden.

### Fase 3: Android celular (lógica; la UI está en la otra rama) — tamaño M

1. `HttpCompanionLink`: canje, espera de la confirmación de la TV, consulta de estado, envío de órdenes,
   desvincular. (F6)
2. `app/mobile` usa `core:panaccess`: login automático con `session` y licencia libre; sin licencia, sin video.
   Hay que actualizar la regla del `CLAUDE.md`. (F7)
3. Check-out: al abrir la app y periódicamente consulta `/api/phone/me/`; si responde 401, borra el login, cierra
   sesión de Panaccess y vuelve a la pantalla genérica. (F8)
4. Aplicar el tema del `handoff` (F9) y conectar el botón "enviar a la tele" (F10).

Hecho cuando: escanear el QR deja al celular con sesión y control remoto, y un check-out lo devuelve a la
pantalla genérica.

### Fase 4: prueba de punta a punta — tamaño S/M

TV real + celular real + backend accesible por internet + cuenta real de Panaccess. Casos: el 4.º celular sin
licencia, check-out con el celular apagado, código vencido, celular de otro hotel, TV sin red. (F14)

### Fase 5: puesta en producción — tamaño M/L

Dominio, HTTPS, hosting, copias de seguridad, `PAIRING_ENABLED`, enlace `https` con App Links. (F12, F13)

### Fase 6: mejoras posteriores (de Wind)

Crear y quitar las licencias por API, reservas de licencia, y el **WebSocket con casteo de Wind** (N4): cambia
la forma de correr el backend (Channels, Daphne, capa de Redis), por eso va como fase propia.

---

## 5. Todas las dudas

Orden de prioridad: las marcadas **BLOQUEA** impiden empezar o cambian el diseño.

### 5.0 Respuestas del 25/11 y dudas nuevas

Las tablas de más abajo quedan como estaban cuando se hicieron las preguntas. Lo vigente es esto.

| # | Respuesta | Estado |
|---|---|---|
| D1 | La habitación sale del nombre de la licencia de la TV, que no rota porque nunca se cierra sesión en la TV. El alcance (una cuenta por hotel) se asume. Si se borran los datos de la TV, se reinstala o se cambia su licencia, el backend responde 409 hasta que el staff la libere (**verificado**) | Resuelta (asumida) |
| D2 | Se asume 4 licencias y se prueba. Ojo: en Wind **no hay un 4 como regla**: aparece solo como ejemplo en un comentario de `wind/services/profiles.py`, y la cuenta de prueba tenía 3 | Asumida, a probar |
| D3 | Sí, activar una licencia usada destruye la sesión anterior (dato del usuario, no lo probé) | Resuelta |
| D4 | Sí, se pueden usar a la vez | Resuelta |
| D5 | Se asume que sí (las apps móviles de Bromteckotlin usan Panaccess) | Asumida |
| D6 | El staff genera el código de activación de la TV desde el panel | Decidida, falta construirla |
| D7 | La clave de cifrado vive en el servidor (variable de entorno) | Resuelta |
| D8 | Con una cuenta por hotel y la TV sin cerrar sesión, el check-out no puede cambiar ni cancelar ese login: dejaría sin servicio a todas las TV. El check-out solo hace que el celular borre su copia y cierre sesión. Para un huésped normal alcanza; solo quien extrajera la copia antes (con conocimientos técnicos) podría seguir usándola | Aclarada, riesgo aceptado |
| D9 | Sí, confirmación en la TV | Trabajo nuevo (N1) |
| D10 | Check-out desde la TV, el celular o el panel. La TV vuelve a elegir idioma sin cerrar sesión; el celular cierra sesión. Es lo que ya hace el backend | Resuelta |
| D11 | Pantalla genérica configurable por el hotel. El QR se renueva (N2) | Parcial (N2, N6) |
| D12 | 3 celulares con video, el resto control remoto. No encontré un tope de celulares emparejados en el backend (solo límite de pedidos de código) | Resuelta |
| D13 | Cualquier contenido transmisible. La app distingue **en vivo, catch-up y VOD** (`Playable`), pero el contrato solo tiene `PlayChannel` y `PlayVod` | Trabajo nuevo (N3) |
| D14 | Se copia lo que hace Wind: ver N4 | Abierta (N4) |
| D15 | Handoff del backend hasta el login, Panaccess después | Resuelta |
| D16 | Android primero, iOS después | Resuelta |
| D17 | Panel y API ya son el mismo proyecto Django: la raíz `/` va al panel y la API cuelga de `/api/…` (**verificado** en `hospitality_backend/urls.py`). Comparten servidor y dominio. El despliegue sigue sin definir | Resuelta (despliegue pendiente) |

Dudas nuevas:

| # | Duda | Propuesta |
|---|---|---|
| **N1** | **Confirmación en la TV (D9).** Wind la hace por WebSocket (`cast.accept`), así que no se copia tal cual. Con polling: el canje deja la vinculación **pendiente**, la TV lo ve al consultar `/api/tv/phones/`, muestra "¿Permitir que [nombre del celular] se conecte?", alguien acepta con el control remoto y recién ahí el backend entrega el login. El celular consulta hasta que lo aceptan. ¿Cuánto espera? ¿Se confirma siempre o solo desde el 2.º celular? | Siempre, con vencimiento de 1 a 2 minutos. Es trabajo nuevo de backend, TV y celular |
| **N2** | **Fotos del QR (D11).** Hoy el código dura 10 minutos y es de un solo uso (**verificado**): una foto de un código ya usado no sirve. La TV pide uno nuevo al vencer, al volver a mirar la pantalla y al terminar la estadía (**verificado**: `SharedPairingCode`). Pedir uno nuevo invalida el anterior | Bajar el vencimiento a 1-2 minutos (`PAIRING_CODE_TTL_SECONDS`) y combinarlo con la confirmación de N1: una foto sola no conecta a nadie |
| **N3** | **Qué se puede enviar (D13).** Falta `PlayCatchup` (catch-up) en el contrato. El backend rechaza cualquier tipo o campo que no conozca, así que hay que cambiar `contract/pairing.json`, el backend y las apps a la vez. ¿Un episodio de serie se envía con su id de VOD? No lo verifiqué | Agregar `PlayCatchup`; revisar series |
| **N4** | **Polling o WebSocket (D14).** Wind usa **WebSocket**: `/ws/device/` con el protocolo `cast.*` (enviar, comandos de reproducir/pausar/mover/volumen/audio/subtítulos, estado cada ~5 s, estado en Redis). En la rama `feature/casteo` ocupa unas 2.700 líneas (`device_consumers.py` 813, `websocket_utils.py` 648, `consumers.py` 443, `cast_state.py` 389, `cast_claim.py` 253), más Channels, Daphne y capa de Redis. El backend de Hospitality usa gunicorn sin eso, y la Android TV de Wind necesita un servicio en primer plano para mantener la conexión. No se copia "un poco": es cambiar la forma de correr el backend | Primera versión con polling (ya hecho y probado, 2-3 s). Copiar el WebSocket de Wind como fase propia **si** se quiere respuesta instantánea o control con posición, volumen y estado. El contrato con cursor se mantiene para poder cambiar sin romper las apps |
| **N5** | ¿Los 3 celulares con video se reparten por orden de llegada? Con la regla "el resto, control remoto", el 4.º intenta tomar licencia y falla | Orden de llegada |
| **N6** | **Pantalla genérica tras el check-out.** ¿Cómo sabe el celular de qué hotel es antes de escanear? Si la app es genérica no lo sabe; si es una app por marca (`brands/`), sí. ¿Dónde configura el hotel el texto y la imagen? | Definir con quien hace la UI (otra rama) |

### Panaccess y licencias

| # | Duda | Por qué importa | Quién responde |
|---|---|---|---|
| **D1** BLOQUEA | ¿La cuenta es **por hotel** o por habitación? Pista (inferencia): `tools/configurar-tv.sh IP[=habitación]` usa el mismo usuario y elige la licencia por nombre de habitación | Define cuántos logins se cargan y qué ve el celular | Delancer / Panaccess |
| **D2** BLOQUEA | ¿Cómo se llaman las licencias de cada habitación y **cómo se llega a 4** (1 TV + 3)? La llamada `addLicenseBlockToSubscriber` solo lleva el código del abonado, sin cantidad, así que el tamaño del bloque parece configurarse en Panaccess. ¿Quién las crea: a mano, un script o el backend por API? | Decide si el backend necesita hablar con Panaccess (Fase 6) y cómo elige licencia el celular | Panaccess |
| **D3** BLOQUEA | Si un celular activa una licencia usada por otro, ¿Panaccess destruye la sesión anterior? ¿Se libera al cerrar sesión? ¿Y si el celular estaba apagado en el check-out? (Wind documenta que se destruye la anterior salvo `failIfInUse`; no lo probé) | "Las licencias se toman de nuevo en el check-out" depende de esto | Prueba con cuenta real + Panaccess |
| D4 | ¿Se pueden tener a la vez la TV y 3 celulares con el mismo usuario? (la cuenta de prueba tenía 3 licencias) | Valida el modelo 1 + 3 | Prueba con cuenta real |
| D5 | ¿Los productos/canales permiten reproducir en celular (DRM de celular, licencia móvil)? | Sin esto el celular no tendría video aunque tenga licencia | Panaccess |

### Seguridad

| # | Duda | Por qué importa | Propuesta |
|---|---|---|---|
| **D6** BLOQUEA | **Alta de TV**: hoy cualquiera que conozca el hotel (`X_HOTEL_ID`, que reciben todas las TV) y la habitación puede registrar una TV falsa, canjear su propio código y recibir el login del hotel | Es la puerta para obtener credenciales | Código de activación de un solo uso que el staff genera en el panel, o clave de instalación por hotel |
| **D7** BLOQUEA | ¿Guardamos solo el **md5** y no la contraseña en claro? ¿Dónde vive la clave de cifrado y cómo se rota? | El md5 sirve igual para entrar a Panaccess, así que hay que protegerlo igual | Guardar solo md5 (la TV ya trabaja así); clave Fernet en variable de entorno |
| D8 | Con cuenta por hotel, el check-out **no invalida** el login: la TV lo sigue usando. Si alguien lo extrae de su propio celular (root, copia de seguridad), puede usarlo después, y como las licencias son por habitación podría tomar una de otra habitación | Riesgo residual del diseño elegido | Aceptarlo como riesgo bajo, guardar el login en almacenamiento cifrado de Android, y preguntar a Panaccess si se puede restringir la licencia por habitación. Cambiar la clave en cada check-out no sirve: afectaría a todas las TV del hotel |
| D9 | ¿Se quiere **confirmación en la TV** antes de entregar el login al celular (como el `cast.accept` de Wind)? El código dura 10 min y es de un solo uso, pero cualquiera que vea o fotografíe el QR obtiene el login | Más seguridad a cambio de un paso más | Decidir con el cliente |

### Producto

| # | Duda | Propuesta |
|---|---|---|
| D10 | ¿Quién hace el check-out? ¿Solo el hotel a mano (panel, TV) o también automático (fecha de salida, PMS)? Hoy una vinculación vence sola a los 30 días (`PAIRING_MAX_AGE_DAYS`) | Manual primero; PMS después |
| D11 | ¿Qué muestra la pantalla genérica del hotel tras el check-out? | La define la UI (otra rama) |
| D12 | ¿Cuántos celulares se pueden **emparejar** (control remoto) y cuántos pueden tener **video**? | Control: sin tope o uno razonable; video: el de las licencias libres |
| D13 | "Enviar a la tele": ¿solo canal o película, o también "seguir desde donde voy"? ¿Y de la TV al celular? | Primero solo el id, como el contrato actual |
| D14 | ¿Alcanza la demora de 2-3 s o hace falta WebSocket? | Empezar con polling |
| D15 | ¿Qué tema manda: el del backend (`handoff`, disponible antes del login) o el de Panaccess (`OperatorConfig`, disponible después)? | `handoff` hasta el login, Panaccess después |
| D16 | ¿El celular es solo Android? (las apps de Wind también tienen iOS) | Confirmar |

### Infraestructura

| # | Duda | Propuesta |
|---|---|---|
| **D17** BLOQUEA | ¿Dónde se aloja el backend? Sin un servidor con HTTPS accesible, la TV y el celular (en redes distintas) no se pueden probar. Hoy no está desplegado: hay `Dockerfile`, `docker-compose` "no es producción" y CI | Para la demo, el modo local con túnel de Cloudflare que ya existe (`docs/demo-local.md`) |
| D18 | Dominio del QR y App Links (`assetlinks.json`) para que el enlace `https` abra la app instalada o lleve a la tienda. Hoy el QR usa un placeholder y el celular solo reconoce `hospitality://` | Definir dominio junto con D17 |
| D19 | ¿Cómo conoce la app la URL del backend? El to-do del backend menciona compilar el APK con `apiBaseUrl` | Compilada por marca |
| D20 | ¿De dónde sale el número de habitación en la TV? Hoy es el `licenseName` de la licencia; el to-do lo lista como investigar | Seguir con `licenseName` |

### Apps

| # | Duda | Propuesta |
|---|---|---|
| D21 | `app/mobile` pasa a depender de `core:panaccess` (hoy no, por una regla del `CLAUDE.md`). Aumenta el tamaño de la app por el DRM | Aprobarlo y actualizar la regla |
| D22 | La UI del celular está en otra rama: acordar las interfaces (`CompanionLink`, `LinkState`) antes de tocar. `LinkState.Reason` no tiene `TooManyAttempts` | Congelar la interfaz y avisar al que hace la UI |
| D23 | Celular sin red en el check-out: se entera al volver a abrir la app. ¿Se acepta que mientras tanto conserve el login guardado? | Consultar siempre `/api/phone/me/` antes de usar el login guardado |

---

## 6. Riesgos principales

1. **Alta de TV pública** (D6): con login de por medio, es el punto más débil.
2. **Una cuenta por hotel** (D1, D8): un login extraído sirve para todo el hotel y el check-out no lo invalida.
3. **Licencias** (D2, D3): si Panaccess no libera la licencia como esperamos, los celulares viejos podrían ocupar cupos.
4. **No hay backend desplegado** (D17): nada se puede probar con dispositivos reales hasta resolverlo.
5. **Nada de esto se probó con dispositivos ni con una cuenta real de Panaccess.**

## 7. Fuera de alcance de este plan

- La interfaz del celular (otra rama).
- Pedidos, servicios, mensajes, PMS.
- Que el backend cree y borre licencias por API (Fase 6).

---

## 8. Estado de la implementación (2026-11-25)

Se trabajó **solo en este repo (Android), sin UI y sin git**. El backend (`BromteckHospitalityBackend`) es otro
repo y no se tocó. De Wind no hizo falta copiar nada todavía (el cifrado es del backend).

### Hecho (todo en `core/companion`)

| Qué | Archivo |
|---|---|
| Cliente HTTP de la TV contra el backend actual: alta (`/api/tv/register/`), pedir código, consultar celulares y órdenes con cursor, desemparejar uno o todos, check-out. Guarda el secreto, el cursor y el número de estadía. Si el backend responde 401 (el staff liberó la habitación) descarta todo y se registra de nuevo | `HttpTvPairing.kt` |
| Detección de check-out por otro camino con la regla del `stay.number` de `contract/pairing.json` (también con la TV apagada) y orden `Checkout`, sin dispararse dos veces ni por el check-out propio | `HttpTvPairing.kt` (`checkouts`) |
| Cliente HTTP del celular: canjear código, guardar el token, `refresh()` contra `/api/phone/me/` (401 → borra todo y vuelve a desvinculado), enviar órdenes, check-out desde el celular, `unlink()` solo local. Arranca ya vinculado si hay un token guardado | `HttpCompanionLink.kt` |
| `Handoff` (tema y datos del hotel) leído del canje y de `/api/phone/me/`, y guardado cifrado con la vinculación | `Handoff.kt`, `LinkedTv.handoff` |
| Transporte HTTP sin dependencias nuevas (`HttpURLConnection`) detrás de una interfaz para probar sin red | `HttpTransport.kt` |
| Almacenamiento cifrado (mismo esquema que `PanaccessSession`) y uno en memoria para tests | `KeyValueStore.kt` |
| Armado y lectura de los mensajes | `Wire.kt` |
| **38 tests pasan** (`./gradlew :core:companion:testDebugUnitTest`), incluida una copia del contrato del backend (`src/test/resources/contract/`, con `SOURCE.md`) corrida del lado de la app | `*Test.kt` |
| Las dos apps (`app/tv`, `app/mobile`) siguen compilando | — |

Cambios en lo que ya existía (todos **aditivos**, la versión de prueba no cambia de comportamiento):

- `TvPairing`: `checkOut()` (por defecto hace `unpairAll()`) y `checkouts` (por defecto no emite).
- `CompanionLink`: `refresh()` y `checkOut()` (por defecto no hacen nada / `unlink()`).
- `LinkedTv`: campo nuevo `handoff` con valor por defecto `null`.
- `app/tv/.../HospitalityApp.kt`: el botón de check-out llama `tvPairing.checkOut()` en vez de `unpairAll()` (una línea). Con el
  backend real `unpairAll()` **no** termina la estadía ni sube el número, así que el celular y la TV no se enteraban igual.

### No verificado

- Nada se probó contra el backend real ni con dispositivos: los tests usan un backend simulado que contesta lo que
  documenta `docs/emparejamiento.md` del backend.
- `EncryptedKeyValueStore` y `UrlConnectionTransport` no tienen test (necesitan Android / red).

### Decisiones tomadas sin preguntar (revisar)

- **Polling** de órdenes cada 2,5 s y de celulares cada 3 s (`HttpTvPairing.start`). Es lo que el backend ya soporta (N4).
- `commands` es un `StateFlow`: cada orden queda 500 ms y vuelve a `null`, para que dos órdenes iguales seguidas no se pierdan.
- Un `TooManyAttempts` (429) del canje se muestra como `NotAvailable` (el enum no lo tiene; ver D22).
- La orden `Checkout` no es un `TvCommand` (rompería los `when` exhaustivos de la TV): sale por `checkouts`.

---

## 9. Dudas recolectadas durante la implementación

Lo que **no** se hizo por depender de estas respuestas. Ordenadas por lo que más desbloquea.

| # | Duda | Qué frena |
|---|---|---|
| **Q1** | **¿Puedo tocar `BromteckHospitalityBackend`?** Dijiste "solo en este worktree". Todo F1–F4 (login del hotel cifrado, `HotelLoginProvisioner`, código de activación de TV, confirmación en la TV, TTL más corto, `PlayCatchup`, contrato con `session`) es backend | Todo el flujo de login automático y el envío de contenido en diferido |
| **Q2** | **¿Qué forma tiene `session`?** Nombres de las claves (¿`user` y `md5`?), si va el md5 o la contraseña, y si incluye algo más (nombre de licencia a usar) | Que el celular haga login en Panaccess; no leí `session` en el cliente |
| **Q3** | **Confirmación en la TV (N1):** necesita un estado "pendiente" en el contrato. `LinkState` es `sealed`: agregar `Pending` rompe los `when` exhaustivos de la UI del celular que estás haciendo en otra rama. ¿Lo agregamos y avisás a esa rama? Lo mismo con `TooManyAttempts` en `Reason` | Pantalla de confirmación y espera en el celular |
| **Q4** | **¿De dónde salen `hotelId` y `room` en la TV?** `X_HOTEL_ID` y el `licenseName` solo se conocen **después** del login de Panaccess en la TV. Entonces `HttpTvPairing` se crea recién ahí, no al arrancar la app. ¿Está bien? | Conectar `HttpTvPairing` a `HospitalityApplication` |
| **Q5** | **URL del backend (D19):** ¿en `brand.json` por marca? ¿Se permite `http` o solo `https`? (el cliente no impone nada hoy) | Conectar los clientes en las dos apps; también el modo demo vs real |
| **Q6** | **Check-out de la TV sin red:** `checkOut()` devuelve `false` y los celulares siguen vinculados. Reintentarlo más tarde podría sacar a un huésped nuevo que ya se emparejó. ¿La TV avisa en pantalla, reintenta, o se deja al staff (panel)? | Qué hace la UI al fallar |
| **Q7** | **401 en la TV → se vuelve a registrar sola** (el staff liberó la habitación). Con el alta "el primero que llega" cualquiera puede ocupar la habitación antes. Cuando exista el código de activación (Q1) esto cambia: ¿ahí se pide un código nuevo al staff? | Comportamiento tras "Liberar TV" |
| **Q8** | **Carga:** cada TV hace ~0,8 pedidos/s (órdenes + celulares). El backend dimensionó 40/s para 100 TV **solo con órdenes**. ¿Consultar celulares solo mientras se muestra el código? Eso requiere que la app lo indique | Ajustar `start` |
| **Q9** | **Celular: cuándo llamar `refresh()`** (D23): al abrir la app y al volver a primer plano; ¿también cada X minutos mientras está abierta? ¿Antes de usar el login guardado siempre? | Wiring de `app/mobile` |
| **Q10** | **`app/mobile` depende de `core:panaccess` (D21)** y la regla del `CLAUDE.md` dice que no. Hay que aprobarlo y cambiar la regla para el login automático y el video | Login/video en el celular |
| **Q11** | **Nombre del celular en la lista de la TV** (`deviceName`): ¿`Build.MODEL`? ¿Lo escribe el huésped? | Texto en la pantalla de confirmación |
| **Q12** | **Interfaces compartidas con la UI (D22):** los cambios aditivos de la sección 8 tocan `Companion.kt`, que la otra rama también puede estar editando. ¿Se avisa para evitar conflictos al juntar? | Merge |
| **Q13** | **Series y catch-up (N3):** sin `PlayCatchup` el celular no puede mandar un programa grabado. ¿Un episodio de serie se manda como `PlayVod`? | Contrato y `TvCommand` |
| **Q14** | **Confirmar contra una cuenta real de Panaccess** D1, D2 (4 licencias), D5 (video en celular) | Todo lo de licencias |

Siguiente paso sugerido apenas haya respuesta: Q1 (decide si sigo con el backend), Q4/Q5 (conectar la TV), Q2/Q3/Q10 (celular).

---

## 10. Backend (2026-11-26): worktree y primera tanda

Con el permiso de Q1 se trabajó en el repo del backend, en su propio worktree:
`BromteckHospitalityBackend/.claude/worktrees/emparejamiento-celular`, rama `worktree-emparejamiento-celular`, creada desde
`hotel` (526cffc). Sin commits. El detalle técnico está en `docs/emparejamiento.md` de ese repo (secciones nuevas «Login del
hotel y código de activación de TV» y «Servidor sin panel»).

### Hecho en el backend (todo detrás de configuraciones apagadas por defecto)

| Qué | Dónde (backend) |
|---|---|
| Login de Panaccess del hotel guardado cifrado (md5, Fernet, clave en `SECRETS_ENCRYPTION_KEYS`, rotable). Cifrado tomado de Wind (`pairing/SOURCE.md`) | `pairing/crypto.py`, `pairing/hotel_login.py`, modelo `HotelPanaccessLogin` |
| `HotelLoginProvisioner`: el canje entrega `session: {username, passwordMd5}` (nombres **provisorios**) o `null` | `pairing/provisioning.py` |
| Cargar/borrar el login **sin panel**: `manage.py set_hotel_panaccess_login` (pide la contraseña por teclado) | `pairing/management/commands/` |
| Código de activación de TV (un solo uso, atado a hotel + habitación, 8 dígitos, 24 h, con hash): `manage.py create_tv_activation_code`; el alta lo exige con `PAIRING_REQUIRE_ACTIVATION` (403 `InvalidActivationCode`) | `pairing/services.py`, `pairing/views.py`, modelo `TvActivationCode` |
| `PANEL_ENABLED=false`: el servidor atiende solo el emparejamiento (sin `/panel/`, `/api/staff/` ni `/`) | `hospitality_backend/urls.py` |
| `contract/pairing.json` con `registerErrors`, `activation` y `session` | `contract/` |
| `cryptography` agregado a `requirements.txt` | — |

Del lado Android (este repo): `HttpTvPairing` acepta `activationCode` y expone `TvRegistration.ActivationRequired` (403).

### No hecho en el backend, y por qué

- **Confirmación en la TV (N1):** cambia el flujo del canje (vinculación pendiente, `session` recién al aceptar) y el contrato
  que ve el celular; depende de Q3 (estado `Pending` en la UI del celular). Queda como siguiente paso.
- **`PlayCatchup` (N3):** no se sabe qué identifica un catch-up (¿canal + programa?). Q13.
- **Formulario en el panel** para cargar el login y generar códigos: lo mismo con comandos ya funciona; es un agregado.
- **Conectar el cliente de la TV al código de activación:** la TV necesita pantalla para escribirlo (UI).

### Dudas nuevas del backend

| # | Duda |
|---|---|
| **B1** | Los nombres de `session` (`username`, `passwordMd5`) son provisorios. ¿Sirven o los cambio para la app del celular? |
| **B2** | Código de activación: 8 dígitos, 24 h (cambiado el 26/11 a pedido). Resuelta; falta decidir si también lo genera el panel. |
| **B3** | `PAIRING_REQUIRE_ACTIVATION` se exige por defecto fuera del entorno local. Cuando la TV real se registra necesita pantalla para el código: ¿quién lo escribe en la instalación? |
| **B4** | Sin panel, los hoteles se crean con el admin de Django (que sigue activo, es de la empresa). ¿Hace falta un comando `ensure_hotel` o alcanza con el admin? |
| **B5** | `PANEL_ENABLED=false` también corta `/api/hotels/<id>/weather/` (lo usa la TV con panel). ¿Se quiere un servidor sin panel con clima, o el clima es parte del panel? |
| **B6** | La clave `SECRETS_ENCRYPTION_KEYS` hay que generarla y guardarla en un lugar seguro del servidor. Si se pierde, hay que volver a cargar el login de cada hotel. ¿Dónde vive en producción? |

### Actualización 26/11 (roles y admin)

Decisiones del usuario: habilitar un cliente es **solo de Bromteck**; el staff del hotel personaliza y hace check-out.

- Hecho en el backend: el admin de Django (superusuario) sirve para crear el hotel (formulario simple de nombre y
  descripción), cargar el login de Panaccess (campo de contraseña, nunca se muestra), generar códigos de activación (se ven una
  sola vez) y **liberar una TV** (borrarla en el admin). "Liberar TV" se **sacó** del panel del hotel y de la API de staff
  (`POST /api/staff/tvs/<id>/reset/` ya no existe). Hay tests de que el staff del hotel no entra.
- El código de activación es de 8 dígitos y dura 24 h.
- To-do anotado en `docs/todo-hospitality.md` del backend: distinguir roles **dentro** del hotel (administrador vs recepción).
- Queda sin borrar `pairing/templates/pairing/panel/room_confirm_reset.html` (ya sin uso): borrarlo a mano.
- Resueltas: B2 (código), B4 (hoteles se crean en el admin), B5 (se asume que el clima es parte del panel).

### Actualización 26/11 (código de varios usos y confirmación en la TV)

Decisiones del usuario: **el código sirve para varios celulares (tope 5, siempre dentro de sus 10 minutos)** y **cada celular se
acepta en la TV** ("¿Permitir que [nombre] se conecte?", el mensaje está aprobado).

- **Backend (hecho, con tests):** `PAIRING_CODE_MAX_USES` (5), `PAIRING_REQUIRE_CONFIRMATION` (se exige fuera del entorno local y de
  los tests), `PAIRING_CONFIRM_TTL_SECONDS` (120). El canje deja al celular pendiente; la TV lo ve en `pending`, lo acepta con
  `POST /api/tv/phones/<id>/accept/` o lo rechaza con `DELETE`; el login (`session`) llega una sola vez en `GET /api/phone/me/` ya
  aceptado. Un pendiente no manda órdenes (403 `Pending`). Detalle en `docs/emparejamiento.md` del backend.
- **Android (hecho, sin interfaz):** `TvPairing.pendingPhones` / `acceptPhone` / `rejectPhone` (con valores por defecto, la demo no cambia).
  `HttpCompanionLink.link` espera en `Linking` hasta que lo aceptan (si lo rechazan o se vence: `Failed(Expired)`) y **conserva el
  login** en `Linked.tv.login` (`HotelLogin`, cifrado en el store, sin salir en `toString`). **No hizo falta agregar `Pending` a `LinkState`**:
  Q3 queda resuelta sin tocar los `when` de la UI.
- **Lo que falta, y es interfaz:** la pantalla de la TV que muestra el mensaje y llama a `acceptPhone`/`rejectPhone`, y en el celular un texto
  mientras está en `Linking` ("esperando que acepten en la TV"). Hasta que la TV tenga esa pantalla **no se prende** `PAIRING_REQUIRE_CONFIRMATION`
  en una TV real (en local queda apagado a propósito).

Nuevas dudas: **C1** `Expired` también cubre "rechazado": ¿se quiere un motivo propio para el texto del celular? **C2** Un celular que canjea y
nadie acepta gasta uno de los 5 usos: la TV pide un código nuevo (¿al rechazar un celular debería pedirlo sola?).

---

## 11. Supuestos de licencias de Panaccess (2026-11-26)

Panaccess es una caja negra para el equipo y Wind **no** resuelve esto (no nombra licencias ni separa "TV" de "celular": Panaccess las
asigna con `addLicenseBlockToSubscriber`). Se anota lo que se **supone** y se sigue; todo esto hay que confirmarlo con quien administra
la cuenta de Panaccess, y con una prueba real.

### Cómo lo hace Wind (leído, no probado)

- Una licencia asignada por perfil; la app prueba las candidatas **en orden con `failIfInUse: true`** (sin robar) y se queda con la primera que sirve.
- Puede prestar licencias que no estén asignadas a ningún perfil.
- Si ninguna sirve, pregunta "¿continuar aquí?" y recién ahí usa la propia con `failIfInUse: false` (cierra la otra sesión).
- Rama sin mergear `feat/perfiles-reservas-licencias`: reservas con vencimiento para evitar licencias "en uso" fantasma.

### Cómo está hoy esta app

`PanaccessSession` habilita con `failIfInUse = false` (al elegir licencia y al recuperar la sesión): **si la licencia está en uso, se la toma al otro**.
Si el celular copiara eso, el 4.º celular le robaría la licencia a otro, y podría robarle la de la TV (la TV, al recuperarse, se la vuelve a robar).
**Corrección:** antes se dijo que "el 4.º celular falla en Panaccess": es probable que no, que robe.

### Supuestos (S) que se anotan y se siguen

| # | Supuesto | Estado |
|---|---|---|
| S1 | Las 4 licencias de una habitación se llaman `101` (TV, como hoy), `101-m1`, `101-m2`, `101-m3` (celulares). La regla vive en un solo lugar: `PhoneLicenses.MOBILE_SUFFIX` | Implementado y probado (`PhoneLicenses.kt`) |
| S2 | El celular solo prueba las `-mN` de su habitación, en orden, con `failIfInUse = true`; sin ninguna libre, abre sin video (control remoto). Nunca toca la de la TV ni las de otra habitación | La lógica de elegir está hecha; **falta** el login del celular que las habilita (depende de Q10) |
| S3 | Se pueden poner los nombres de licencia a mano en Panaccess | Sin confirmar |
| S4 | El bloque de licencias de una habitación llega a 4 (1 TV + 3 celulares) | Sin confirmar (D2) |
| S5 | `failIfInUse = true` falla de verdad cuando la licencia está en uso (en iOS la convención es `failIfInUse && !success ⇒ en uso`) | Sin probar con cuenta real |
| S6 | Una cuenta por hotel, con las licencias de todas sus habitaciones | Asumido (D1) |

### Para llevarle a quien administra Panaccess

1. ¿Se pueden nombrar las licencias a mano? ¿Cómo se crea el bloque de 4 por habitación (a mano, script, API)?
2. ¿Qué pasa si un celular activa una licencia en uso con `failIfInUse = true`? ¿Y con `false`? (confirmar que `true` no roba)
3. ¿El celular puede reproducir con una licencia del mismo producto que la TV (DRM de celular, D5)?
4. ¿Una cuenta por hotel o una por habitación?
5. ¿Hay forma de saber qué licencia está en uso ahora (para no probar a ciegas)?

---

## 12. Login automático del celular (2026-11-26)

**Corrección:** Q10 / D21 ("`app/mobile` tendría que depender de `core:panaccess`") era una duda **falsa**: `app/mobile` ya depende de
`core:panaccess` y `core:entertainment` (`app/mobile/build.gradle.kts`) y el `CLAUDE.md` ya lo dice. Quedan resueltas.

### Hecho (compila; sin probar con Panaccess ni dispositivo)

| Qué | Dónde |
|---|---|
| `LoginService.loginForPhone`: verifica las credenciales, toma SOLO las licencias `-mN` de la habitación (`PhoneLicenses`), en orden, con `failIfInUse = true`; la que el celular ya usaba va primero con `false`. Sin ninguna libre devuelve `Failure` **sin** lista de licencias (nunca "elegir licencia": incluiría la de la TV) | `core/panaccess/.../LoginService.kt` |
| `PanaccessSession.loginForPhone`, `isPhoneSession`, `close()`. `restore()` sigue la regla de celular si la sesión es de celular. `recover()` queda como estaba (re-habilita la licencia propia con `false`) | `core/panaccess/.../PanaccessSession.kt` |
| `MobileApplication.bindCompanionSession()`: al quedar `Linked` con login del hotel entra solo a Panaccess con la habitación de la TV; al pasar a `Unlinked` (check-out o la TV lo desvinculó) cierra la sesión, borra el login y vuelve a la pantalla neutra. Solo toca sesiones de celular emparejado (no la de depuración). `restoreSession()` además llama `companion.refresh()` antes de usar el login guardado | `app/mobile/.../MobileApplication.kt` |

Hoy no se dispara con la demo (`DemoCompanionLink` no trae login), así que nada cambia hasta enchufar `HttpCompanionLink` (Q4/Q5).

### Dudas nuevas

| # | Duda |
|---|---|
| **L1** | **Soltar la licencia al cerrar sesión.** No hay en este repo ni en Wind una llamada que le diga a Panaccess que libere la licencia de un equipo (el `cvLogout` de Wind es de su sesión de API, no de un dispositivo). `close()` borra todo localmente, pero si la licencia sigue "en uso" hasta que venza la sesión en Panaccess, el siguiente huésped podría no encontrarla libre. Hay que probarlo y preguntar a Panaccess |
| **L2** | Con `failIfInUse = true` el celular puede encontrar su propia licencia "en uso" por su sesión anterior (reinicio de la app). Se resuelve probando primero la guardada con `false`, igual que la TV; falta confirmar que alcanza |
| **L3** | Si el login llega y no hay licencia libre, el celular queda sin video pero con el login guardado (reintenta al volver a abrir la app). ¿Se quiere reintentar solo cada tanto, o un botón "reintentar"? |
| **L4** | Hay que llamar a `bindCompanionSession()`/`restoreSession()` y a `companion.refresh()` también al volver a primer plano; hoy solo al abrir la app (Q9) |

---

## 13. Cliente copiado a la app con panel (2026-11-26)

El servidor es uno solo (repo del backend). Cada app Android se compila desde su repo, así que el **cliente** (`core/companion`) tiene que estar
en las dos. Este repo (solo Panaccess) ya lo tenía; ahora también está en `BromteckHospitalityBackend/android/` (app con panel), en su worktree.

- **Copiado:** `HttpTvPairing`, `HttpCompanionLink`, `HttpTransport`, `KeyValueStore`, `Handoff`, `Wire`, los cambios de `Companion.kt` y
  `build.gradle.kts`, los tests y la copia del contrato (idéntica a `contract/pairing.json` del backend, con un test que lo exige). Más la línea del
  botón de check-out de la TV (`checkOut()` en vez de `unpairAll()`). Compila (TV y celular) y pasan los tests (49).
- **No copiado, a propósito:** (1) `SharedPairingCode`: no existe en esa app (su TV pide el código por su cuenta); (2) el login automático y el video
  del celular: esa app **no** depende de `core:panaccess` y su `PanaccessSession` es otra versión; (3) `PhoneLicenses` y la regla de licencias `-mN`.
- **Pendiente en esa app:** enchufar el cliente (URL del servidor, hotel y habitación en la TV), un solo código compartido, la pantalla de confirmación.

Duda nueva: **P1** ¿La app con panel también debe reproducir video en el celular (como esta)? Si sí, hay que traer a esa app el `core:panaccess` con la sesión
propia del celular, que es más nuevo que su copia (sección 12); no es solo copiar archivos.
