# Parámetros de Panaccess para configurar

Este archivo describe el formato de los parámetros que la TV lee de la cuenta de Panaccess (parámetros de
personalización remota de cada hotel):

| Parámetro | Para qué | Dónde está |
|---|---|---|
| `X_APP_SERVICES` | Menú y servicios del hotel (bebidas, spa...), solo para mirar | [más abajo](#x_app_services--catálogo-de-servicios-del-hotel-desde-panaccess) |
| `X_APP_WIFI` | Wi-Fi del hotel con QR para conectar el celular | [al final](#x_app_wifi--wi-fi-del-hotel-con-qr-desde-panaccess) |

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
