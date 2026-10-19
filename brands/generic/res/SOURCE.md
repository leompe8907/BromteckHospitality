# brands/generic/res — ícono y banner de Bromteck

Copia **literal** del flavor `bromteck` de `androidpanaccessapp/PanaccessApp/app/src/brandings/bromteck/res/`
(mismo set que usa el base app para su propia marca, 2026-08-11). Se usa en `generic` porque esta
marca (la de Delancer/Cableatlántico) no tenía ícono propio todavía: antes caía al "H" genérico de
`app/tv/src/main/res` — ver pedido del cliente, 2026-10-19 ("no queremos que nos roben").

No editar acá: si Bromteck cambia de logo, se vuelve a copiar desde el base app.

| Archivo copiado | Origen |
|---|---|
| `mipmap-*/ic_launcher*.webp` | `brandings/bromteck/res/mipmap-*/ic_launcher*.webp` |
| `mipmap-anydpi-v26/ic_launcher*.xml` | ídem |
| `values/ic_launcher_background.xml` | ídem |
| `mipmap-xhdpi/ic_banner.png` | ídem |
| `drawable/tv_banner.xml` | nuevo, wrapper `<bitmap>` sobre `ic_banner` (mismo patrón que `brands/royal_hotel`) |
