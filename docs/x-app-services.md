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
