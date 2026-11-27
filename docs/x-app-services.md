# Parámetros de Panaccess para configurar

Este archivo describe el formato de los parámetros que la TV y la app del celular leen de la cuenta de
Panaccess (parámetros de personalización remota de cada hotel).

**Para pedir a Panaccess: crear estos dos parámetros, que todavía no existen** (los nombres son propuestos):
`X_APP_MOBILE_TILES` y `X_APP_MOBILE_BACKGROUND`. Sin ellos la app del celular funciona igual, con el orden de
tiles por defecto y el fondo liso del tema.

| Parámetro | Para qué | Dónde está |
|---|---|---|
| `X_APP_SERVICES` | Menú y servicios del hotel (bebidas, spa...), solo para mirar | [más abajo](#x_app_services--catálogo-de-servicios-del-hotel-desde-panaccess) |
| `X_APP_WIFI` | Wi-Fi del hotel con QR para conectar el celular | [más abajo](#x_app_wifi--wi-fi-del-hotel-con-qr-desde-panaccess) |
| `X_APP_MOBILE_TILES` | Orden y tamaño de los tiles de la home de la app del celular. **Nombre propuesto: hay que crearlo en Panaccess** | [más abajo](#x_app_mobile_tiles--home-de-la-app-del-celular-nombre-propuesto) |
| `X_APP_MOBILE_BACKGROUND` | Fondo de la app del celular (foto o color). **Nombre propuesto: hay que crearlo en Panaccess** | [al final](#x_app_mobile_background--fondo-de-la-app-del-celular-nombre-propuesto) |

---

# X_APP_SERVICES — catálogo de servicios del hotel desde Panaccess

Mientras no haya conexión con el sistema del hotel (CRM/PMS), el operador puede mandar por Panaccess
el menú y los servicios del hotel y la TV los **muestra, sin opción de pedir**: nombre, descripción,
precio, duración y foto. No hay "Pedir" ni "Mis pedidos".

Es un parámetro del dispositivo, igual que `X_APP_WIDGETS`: el valor puede ser el JSON directo, o
una **URL** a un archivo con ese JSON (el campo del parámetro en Panaccess no alcanza para varias
categorías con textos en cinco idiomas y fotos).

## Formato

```json
{
  "currency": "MXN",
  "categories": [
    {
      "name": { "es": "Bebidas", "en": "Drinks", "pt": "Bebidas", "fr": "Boissons", "de": "Getränke" },
      "items": [
        {
          "name": { "es": "Margarita de la casa", "en": "House margarita" },
          "description": { "es": "Tequila, limón y sal de mar", "en": "Tequila, lime and sea salt" },
          "price": 120,
          "image": "https://ejemplo.com/margarita.jpg"
        }
      ]
    },
    {
      "name": { "es": "Spa y bienestar", "en": "Spa & wellness" },
      "items": [
        {
          "name": { "es": "Masaje relajante", "en": "Relaxing massage" },
          "price": 1800,
          "durationMin": 60,
          "image": "https://ejemplo.com/masaje.jpg"
        }
      ]
    }
  ]
}
```

- `currency`: código ISO 4217 (MXN, USD, EUR...). Sin cargar, USD.
- `name` y `description` (de categorías e ítems): un texto simple, o uno por idioma (`es`, `en`,
  `pt`, `fr`, `de`). Si falta el idioma del huésped, se usa inglés, después español. `title` también
  vale como `name`.
- `price`: número. **Sin cargar = no se muestra precio.** `0` = "Sin cargo".
- `image` (o `images`, se usa la primera): URL de la foto.
- `durationMin`: solo para servicios con duración (masajes), se muestra como "60 min".
- `id` es opcional (se genera solo).
- Solo el nombre es obligatorio. Un ítem sin nombre, o una categoría sin nombre o sin ítems, se
  descarta sin afectar al resto. Si el JSON entero es inválido, la TV no muestra la sección de
  servicios.

Código: `core/services/.../OperatorServices.kt` (`parseOperatorCatalog`, `ReadOnlyServices`).

---

# X_APP_WIFI — Wi-Fi del hotel con QR, desde Panaccess

**Nombre del parámetro: `X_APP_WIFI`.**

El operador carga la red Wi-Fi de huéspedes y la TV muestra un **QR** para conectar el celular, con el
nombre de la red y la clave en texto por si la cámara no lo lee. Es un parámetro del dispositivo, como los
otros, pero **el valor es siempre el JSON directo** (no una URL): son cuatro datos y entran en el campo.

## Formato

```json
{"ssid":"HotelGuest","password":"abc12345x","security":"WPA","hidden":false}
```

Red abierta (sin clave):

```json
{"ssid":"HotelLibre","security":"nopass"}
```

| Campo | Qué es |
|---|---|
| `ssid` | Nombre de la red. **Obligatorio.** |
| `password` | Clave. Obligatoria si la red tiene clave (WPA o WEP). |
| `security` | `WPA` (por defecto), `WEP` o `nopass` (red abierta). Se acepta en minúsculas. Un valor que no conoce (`WPA2`, `WPA3`, `SAE`) se trata como WPA. |
| `hidden` | `true` si la red está oculta. Por defecto `false`. También vale `"true"` o `"1"` como texto. |

- La clave se usa **tal cual**: no se recortan espacios. Si lleva comillas `"` o barras `\`, hay que escaparlas
  en el JSON (`\"` y `\\`).
- Es la clave de la **red de huéspedes**: se le da a cualquiera. No cargar la red del personal.
- Sin el parámetro, o si no sirve (sin `ssid`, una red con clave sin `password`, o un JSON inválido), la TV no
  muestra nada de Wi-Fi y todo queda como antes. Un JSON roto se descarta entero.
- En la home Lobby el QR ocupa el lugar de «Conectar celular» (sin el parámetro, vuelve «Conectar celular»); en la
  home de siempre aparece «Wi-Fi del hotel» entre las acciones rápidas. Los textos están en es, en, pt, fr y de.

Detalle del texto del QR y del código: `docs/x-app-wifi.md`.

---

# X_APP_MOBILE_TILES — home de la app del celular (nombre propuesto)

**Nombre del parámetro: `X_APP_MOBILE_TILES`. Es un nombre propuesto: todavía no existe en Panaccess y hay que
crearlo** (igual que se hizo con `X_APP_LOGO`). Hasta entonces la app del celular usa un orden por defecto.

La home de la app del celular es una grilla de **tiles**: casillas que llevan a una pantalla, como los íconos y
los widgets de un iPhone. Este parámetro define **qué tiles hay, de qué tamaño y en qué orden**. Es un
parámetro del dispositivo, y el valor es **el JSON directo** (no una URL).

## Formato

Un arreglo de objetos con `id` y `size`, en el orden en que se dibujan:

```json
[{"id":"live","size":"M"},{"id":"vod","size":"M"},{"id":"widgets","size":"M"},{"id":"wifi","size":"S"},{"id":"weather","size":"S"}]
```

| Campo | Qué es |
|---|---|
| `id` | Qué tile es (ver la tabla de abajo). Se acepta en minúsculas o mayúsculas. |
| `size` | `S` (ícono de 1×1), `M` (widget de 2×2) o `W` (widget ancho, de 4×2). Un tamaño ausente o desconocido se toma como `S`. |

| `id` | Qué muestra |
|---|---|
| `live` | Canales en vivo: el programa al aire y, en `W`, los siguientes canales. Solo si la marca tiene `liveTv`. |
| `vod` | Películas y series, con pósters. Solo si la marca tiene `vod` y la cuenta tiene alguna película o serie. |
| `widgets` | Los widgets de la cuenta (`X_APP_WIDGETS`), cada uno como un tile, en el orden de Panaccess. |
| `wifi` | La red Wi-Fi de huéspedes con su QR (`X_APP_WIFI`). Solo si la cuenta la manda. |
| `weather` | Temperatura y descripción del clima. Solo si la cuenta manda el clima. |
| `services` | Servicios del hotel (`X_APP_SERVICES`). Solo si la cuenta los manda. |
| `hotel` | Ficha del hotel. Solo si la marca tiene `hotelInfo`. |
| `remote` | Control remoto de la TV de la habitación (flechas, OK, volumen, canal, atrás, inicio, guía). Siempre está: si el huésped está en la app, el celular está vinculado a la TV. Las órdenes pasan por el backend. |
| `settings` | Existe en el diseño pero **hoy no se dibuja**: el idioma viene de la cuenta. |

- La grilla tiene 4 columnas. Un tile chico rellena el hueco que deja uno grande.
- `widgets` se expande a un tile por cada widget de la cuenta. Con `M` (el valor habitual) forman un mosaico que
  repite ancho, mediano, mediano (`W`, `M`, `M`, `W`, `M`, `M`…) para que no queden todos iguales; lo que sobra
  al final no deja huecos (uno solo va ancho, dos van medianos). Con `W` van todos anchos y con `S` todos chicos.
- Si un tile no admite el tamaño pedido, se usa el más grande que admita sin pasarse. `settings` solo admite `S`.
- Un tile repetido se dibuja una sola vez. Un `id` desconocido se ignora. Un elemento sin `id` se descarta.
- Lo que la cuenta o la marca no tiene **no se dibuja** (por ejemplo, sin `X_APP_WIFI` no aparece el tile de
  Wi-Fi), aunque esté en la lista.
- Sin el parámetro, o con un JSON inválido, se usa el orden por defecto: `remote`, `live` (M), `vod` (M), `widgets`
  (M), `wifi`, `weather`, `services`, `hotel` y `settings` (S).

Código: `app/mobile/.../HomeLayout.kt` (`MOBILE_TILES_PARAM`, `parseTileConfig`, `resolveHomeTiles`).
Diseño de referencia: `docs/mockup-celular/index.html` (el panel «Parámetros» edita este mismo JSON).

---

# X_APP_MOBILE_BACKGROUND — fondo de la app del celular (nombre propuesto)

**Nombre del parámetro: `X_APP_MOBILE_BACKGROUND`. Es un nombre propuesto: todavía no existe en Panaccess y hay que
crearlo.** Es aparte del fondo de la TV (`X_APP_LOGO_BACKGROUND`) porque una foto pensada para una pantalla
horizontal queda mal en un celular vertical. Es un parámetro del dispositivo y el valor es un texto simple:

| Valor | Qué pasa |
|---|---|
| `https://ejemplo.com/fondo.jpg` | Esa foto a pantalla completa detrás de todas las pantallas, con una capa lisa del color del tema encima (sin degradado) para que los textos se lean. |
| `#0E2E32` | Un color liso en lugar del fondo del tema. |
| sin cargar, vacío, o un `#` con un hex que no sirve | El fondo liso del tema, como siempre. |

Código: `app/mobile/.../MobileBackground.kt` (`MOBILE_BACKGROUND_PARAM`, `parseMobileBackground`, `MobileBackdrop`).
