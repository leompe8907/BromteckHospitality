# Backend propio de Hospitality — la idea

**Estado:** idea para arrancar el proyecto del backend, que es aparte de este. Acá no hay
código del backend ni contrato cerrado. El objetivo es que quien lo diseñe sepa qué necesitan
las apps y qué ya resuelve Panaccess. Las funcionalidades importantes y diferenciales, con
prioridades, están en [`backend-funcionalidades.md`](backend-funcionalidades.md).

## La división

```
                 ┌──────────────────────────┐        ┌──────────────────────────────────┐
                 │ Panaccess (tercero)      │        │ Backend propio (otro proyecto)   │
                 │ TV en vivo, guía, VOD,   │        │ Todo lo del hotel: huésped,      │
                 │ catchup, DRM, licencias, │        │ servicios, mensajes, IA, panel,  │
                 │ mensajes OSM             │        │ PMS, domótica, pagos, analíticas │
                 └────────────┬─────────────┘        └───────┬───────────────┬──────────┘
                              │                              │               │
                 ┌────────────┴──────────────────────────────┴──┐   ┌────────┴─────────┐
                 │ App TV (este repo)   ·   App móvil compañera │   │ Panel web (staff)│
                 └──────────────────────────────────────────────┘   └──────────────────┘
```

- **Panaccess sigue siendo la única fuente del entretenimiento.** El backend no repite ni hace de
  intermediario de canales, VOD ni DRM.
- **El backend es la única fuente de lo del hotel.** Las apps no hablan directo con el PMS, ni con la
  domótica, ni con los medios de pago: siempre pasan por el backend.

## Quién es quién

La pieza central. Todo lo demás cuelga de acá.

| Entidad | Qué es | De dónde sale |
|---|---|---|
| Operador | El cliente de Panaccess (p. ej. `intv`) | Panaccess |
| Hotel | Una propiedad, con su marca, idiomas y contenido | Panel |
| Habitación | Número y tipo | Panel o PMS |
| TV | Un equipo instalado en una habitación | Se vincula con su **licencia de Panaccess**: hoy `licenseName` ya es el número de habitación |
| Estadía | Huésped + habitación + fechas + idioma + preferencias | PMS (Opera, Mews…) o **carga manual en el panel** |
| Dispositivo del huésped | Su celular, emparejado a la TV durante la estadía | Emparejamiento (ver abajo) |

**Idea clave:** el huésped es una pieza intercambiable. Sin PMS, el staff lo carga a mano en 30 segundos.
Con PMS, entra solo al hacer check-in. El resto del sistema no se entera de la diferencia.

El check-out **termina la estadía**: se borran los datos del huésped en la TV, se desemparejan sus
dispositivos y se cierra la cuenta pendiente. La sesión de Panaccess de la TV no se toca.

## Qué necesita cada parte del backend

| Área | Qué hace | Lo usa |
|---|---|---|
| **Contenido del hotel** | Guía del hotel, fotos, videos, agenda (shows, Star Camp), noticias, mapa, clima. Por idioma | TV, móvil |
| **Servicios / pedidos** | Room service, housekeeping, despertador, reservas (spa, golf, buceo), transporte, bodas y eventos, concierge (chat). Cada pedido tiene estados: pedido, aceptado, en curso, listo | TV, móvil, panel |
| **Mensajería dirigida** | Avisos a una habitación, a un grupo (piso, tipo de membresía, idioma) o a todo el hotel. Con lectura confirmada | TV, móvil, panel |
| **Recomendaciones (IA)** | Ver la sección siguiente | TV, móvil |
| **Check-out express** | Muestra el folio, lo confirma y avisa a recepción | TV, móvil, PMS |
| **Integración PMS** | Check-in, check-out, nombre, idioma, fechas, folio. Opera primero (piloto Tulum) | Interno |
| **Domótica** | Luces, aire, cortinas, "no molestar". El backend traduce a cada marca de equipos | Móvil (y TV) |
| **Pagos** | Cargos a la habitación y pago con tarjeta en servicios pagos. El backend nunca guarda tarjetas: las tokeniza la pasarela | TV, móvil |
| **Analíticas** | Qué se ve, qué se pide, qué se lee, por habitación y por hotel | Panel |
| **Panel web** | La herramienta del staff: dashboard, pedidos, contenido, reservas, mensajes, estadías | Staff |

## Recomendaciones con IA (reemplaza a Binit)

En la rama `mario/features/ia-hoteleria` la TV llamaba a Binit con
`GET /api/VacationRecommendation/planbyroom/{habitación}` y recibía una lista de
`{uid, date, title, shortDescription, description, imageUrl}`: un plan de actividades por día para la
habitación. **Esa integración no se porta.** Ahora lo genera el backend propio.

La idea:
- **Entrada:** la estadía (fechas, idioma, cantidad de personas, si hay niños), la agenda y los
  servicios del hotel, el clima, el historial de pedidos y reservas durante la estadía, y las
  preferencias que el huésped marque.
- **Salida:** la misma forma que ya usaba la app, para no rediseñar pantallas. Es un plan por día
  con título, descripción corta y larga e imagen. Se agrega una **acción opcional** que lleve directo a
  reservar el servicio sugerido, por ejemplo "Reservar spa a las 16 h".
- **Dónde corre el modelo:** en el backend, nunca en la TV. La TV solo muestra lo que recibe.
- **Fallback sin IA:** si el modelo falla o no hay datos, se muestra la agenda del día. Nunca queda
  una pantalla vacía.
- **Privacidad:** el modelo solo ve los datos de esa estadía. Al hacer check-out se descarta lo
  personal y queda solo el agregado anónimo para las analíticas.

## Tiempo real

Un único canal que el backend mantiene con cada TV y cada celular (WebSocket o similar). Por ahí
viajan mensajes, cambios de estado de pedidos, check-in y check-out, y órdenes como "mandá esto a la
TV". Se construye **una vez** y lo reutilizan todas las áreas.

## Emparejamiento celular ↔ TV

**El Wi-Fi de hotel casi siempre aísla los dispositivos entre sí:** el celular no ve a la TV en la red
local, y por eso no sirve el casteo por WebSocket local que usa PanaccessApp.

1. La TV muestra un **código corto o un QR**, que le pide al backend.
2. El huésped lo escanea o lo escribe en la app móvil.
3. El backend vincula ese celular con la estadía de esa habitación hasta el check-out.
4. Desde ahí, "mandar a la TV", "compartir sesión" y la domótica se hacen como **órdenes por el canal de
   tiempo real**, no por la red local.

## Cómo se engancha lo que ya hay en la app de TV

Las piezas para conectar el backend ya están preparadas en la app:

| En la app hoy | Se reemplaza o completa con |
|---|---|
| `HotelRepository` (lee `hotel_data.json` o la API vieja `images_hotel`) | Una implementación que lee del backend. Mismo formato de datos |
| Flags `roomService`, `housekeeping`, `wakeUpCall`, `messaging` en `brand.json` (apagados) | Los prende el backend por hotel, cuando la función esté lista |
| `GuestSession` (solo el idioma) | La estadía que manda el backend: nombre, idioma, fechas |
| Habitación = `licenseName` de la licencia de Panaccess | Se mantiene como forma de identificar la TV, o se reemplaza por un registro propio |
| Mensajes OSM de Panaccess | Siguen funcionando. La mensajería dirigida del backend se suma; no los reemplaza |
| Check-out local (borra la sesión del huésped) | Además avisa al backend, que cierra la estadía y el folio |

## Decisiones abiertas

- **¿Nube, on-premise en cada hotel, o ambas?** El deck menciona despliegue on-premise.
- **¿Cómo se identifica una TV ante el backend?** Por la licencia de Panaccess o con un registro
  propio (código del instalador).
- **¿Qué PMS después de Opera?** Mews, Cloudbeds…
- **¿Qué pasarela de pagos?** Y si se cobra a la habitación, con la cuenta del PMS.
- **¿Qué proveedor o modelo de IA para las recomendaciones?** También hace falta decidir si entra en
  la etapa 1 o después.
- **¿Qué protocolos de domótica?** Depende del equipamiento del hotel piloto.

## Fuentes

- Plan "Panaccess White-Label y Hospitality — Arranque del Proyecto" (Claude Doc, 30-09).
- Decks "Hospitality", "Áreas del Proyecto" y "Entretenimiento en Vivo".
- Rama `mario/features/ia-hoteleria` de PanaccessApp (integración Binit).
