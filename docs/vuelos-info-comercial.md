# Estado de vuelos en la TV del hotel — qué cuesta

Nos pidieron evaluar sumar una función para que el huésped vea el estado de su vuelo desde la
TV de la habitación. Esto es nuevo, todavía sin diseñar — este documento es solo para decidir si
vale la pena seguir adelante, mirando el costo.

## Opciones evaluadas

| Proveedor | Plan gratis | Primer plan pago |
|---|---|---|
| **AeroDataBox** | 600 consultas/mes | US$ 5,35/mes |
| **Aviationstack** | 100 consultas/mes | US$ 44,99/mes (10.000 consultas) |
| **FlightAware** (el más usado por aerolíneas) | Casi nada | Desde US$ 100/mes de mínimo |

"Consulta" = una vez que alguien pide el estado de un vuelo puntual.

## Recomendación

**AeroDataBox** es, de lejos, la opción más barata para arrancar — para una cantidad chica de
consultas por mes (un huésped consultando su propio vuelo de vez en cuando, no en vivo todo el
tiempo) el costo mensual sería de unos pocos dólares.

## Qué falta antes de poder cotizar esto en serio

Esto es solo el costo del proveedor de datos — falta definir cómo se usa en la práctica (¿el
huésped carga su número de vuelo? ¿se vincula con la reserva del hotel?), lo cual puede cambiar
cuánto se termina consultando por mes, y por lo tanto el plan necesario.
