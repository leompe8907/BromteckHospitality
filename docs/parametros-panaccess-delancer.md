# Parámetros de Panaccess a configurar (Delancer / Cableatlántico)

Estos son los parámetros `X_...` del panel de Panaccess/Cableatlántico que la app de Hospitality
ya lee o va a necesitar leer, por cuenta de hotel. Todos son opcionales: si no están configurados,
la app sigue andando con los valores genéricos de siempre, no se rompe nada.

## Ya configurado y funcionando

| Parámetro | Tipo | Para qué | Estado |
|---|---|---|---|
| `X_APP_LOGO_BACKGROUND` | texto (URL de imagen) | Fondo de la pantalla principal/idioma/bienvenida | ✅ Confirmado y funcionando (ej. `https://i.imgur.com/jpFevda.jpeg`) |

## A confirmar / pedir que se configure

| Parámetro | Tipo | Para qué | Nota |
|---|---|---|---|
| `X_APP_LOGO` | texto (URL de imagen) | Logo de la marca, reemplaza el logo genérico de Hospitality | **Nombre propuesto por nosotros, todavía no confirmado con Panaccess/Cableatlántico** — puede que el nombre real sea otro. El código ya está listo para leerlo; si el nombre final es distinto, es un cambio de una línea. |
| `X_SHOW_WEATHER` | booleano (`true`/`false`) | Prender el clima en pantalla | Existe en la lista oficial de parámetros de Panaccess, pero no sabemos si ya está disponible/habilitado para esta cuenta. |
| — (sin nombre de parámetro) | — | **Ubicación del hotel**, para que el clima sepa qué ciudad consultar | No sabemos cómo se configura esto del lado de Cableatlántico — ¿es parte de la cuenta del operador, algo que se carga aparte? Hay que preguntarlo directamente. |
| `X_DESIGN_CLOCK_POSITION` | enum (`DISABLED` / `BANNER` / `TOP_BANNER`) | Dónde se muestra el reloj en pantalla | Opcional — si no se configura, podemos mostrar el reloj siempre en una posición fija nuestra, sin depender de este parámetro. |

## Pendiente de definir (sin parámetro todavía — no pedir aún)

- **Splash** (pantalla de arranque): tiene un problema de diseño aparte (los parámetros de
  Panaccess recién se conocen después del login, pero el splash se ve antes) — antes de pedir un
  parámetro nuevo para esto hay que decidir cómo resolver ese problema. Ver conversación aparte.
- **Placeholder** (cuando no hay imagen de fondo/logo): ya resuelto sin necesitar ningún
  parámetro — es un color fijo definido en el código, no depende de Panaccess.

## Lo que ya se usa hoy, sin relación a esta tanda de pedidos

Por si sirve de referencia — estos ya estaban en uso antes, para otras cosas (contenido del hotel,
número de habitación, idioma):

| Parámetro | Para qué |
|---|---|
| `X_HOTEL_ID` | Identifica qué hotel es esta instalación, para traer su contenido (fotos/actividades) |
| `X_HOTEL_APK_BASE_URL` / `X_HOTEL_APK_BASE_FOLDER` | Sistema viejo de contenido del hotel |
| `X_HOTEL_SHOW_ABOUT` | Prende/apaga la sección "Sobre el hotel" |
| `X_DESIGN_SHOW_HOTEL_ROOM_NUMBER` | Muestra u oculta el número de habitación en pantalla |
| `X_DEFAULT_LANGUAGE` | Idioma por defecto |
