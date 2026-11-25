# Emparejamiento celular ↔ TV y app del celular

Notas de diseño. Lo implementado está en `core/companion` (contrato y versión de prueba), `app/mobile`
y `PairTile`/`PairPhoneDialog` de la TV. El motivo de pasar por el backend está en
[`backend-idea.md`](backend-idea.md#emparejamiento-celular--tv): el Wi-Fi de hotel aísla los dispositivos.

## Estado actual

- La TV pide un código de 6 dígitos (vence a los 10 minutos) y lo muestra con un QR:
  `hospitality://pair?code=123456` (`DemoTvPairing`).
- El celular ya registra ese enlace en su manifest (`app/mobile`), así que el QR abre la app con el código cargado.
- Todo es **versión de prueba**: el celular acepta cualquier código de 6 dígitos. El backend
  (`BromteckHospitalityBackend`) tiene el emparejamiento como etapa 4 pendiente; acá es la etapa 5.
- Pendiente de la home Lobby: el tile y `PairPhoneDialog` piden cada uno su propio código (con el backend
  real el segundo podría invalidar el del tile) y ninguno se entera de cuándo el celular se conectó.
- La TV ya ejecuta las órdenes (`TvCommand`) que lleguen.

## Decisiones tomadas

- **El celular también ve el contenido.** Al escanear el QR queda con sesión de Panaccess, sin pedir login
  (el emparejamiento hace de login, así que cómo hace login una app mobile de Delancer no importa) y obtiene
  el catálogo y la config del operador (logo, fondo, textos). Si la sesión es la misma de la TV o una
  licencia propia del celular depende de la decisión abierta de licencias. Esto reemplaza la idea de que "el
  celular no usa Panaccess".
- **No se copia la app de Delancer, se reutilizan los módulos de este repo.** `app/mobile` pasa a depender de
  `core:panaccess` (DRM, login, catálogo, EPG, VOD, catch-up, HLS modificado) y `core:entertainment`, con su
  propio player y pantallas Compose. Ver "App del celular: aspecto".
- **No hay usuario ni licencia del hotel.** Las licencias de Panaccess son por habitación (dispositivo), no
  por hotel. Sin sesión, el celular no puede leer la config antes de emparejar.
- **Primera pantalla del celular, por ahora neutra.** Antes de emparejar muestra algo como "Escanea el QR en
  la televisión de tu hotel para ver el contenido". Después del QR todo sale con la marca del operador.
- **Un APK por hotel (flavor) queda para el final.** Sirve para que la primera pantalla ya tenga la marca del
  hotel y no sea genérica. Se deja al final de todo para tener tiempo de consultarlo (publicar una app por
  hotel en la tienda tiene costo). Los flavors de marca ya existen en `buildSrc` (`Brands.kt`).
- **El QR lleva solo el código de 6 dígitos, nunca la sesión.** El backend le entrega la sesión al celular
  cuando canjea el código. Así quien vea el QR no se lleva la sesión.
- **Dominio `https` para el QR.** Un enlace `https://…/pair?code=…` abre la app si está instalada y, si no,
  lleva a la tienda (`hospitality://` solo funciona con la app ya instalada). Se cambia en un solo lugar
  (`qrPayload`).
- **La TV se identifica ante el backend por la licencia de Panaccess.** Sin registro propio por ahora.
- **La app del celular no tiene "cerrar sesión".** Los huéspedes no tienen credenciales, así que no hay a dónde
  volver a entrar. El celular se desvincula solo por el check-out (lo cierra el backend) o cuando la TV
  desempareja todo (`unpairAll`). `CompanionLink.unlink()` no se expone en la UI.
- **La TV le manda información al celular al emparejar.** Cuando el celular canjea el código, recibe de vuelta
  datos que la TV (o el backend) dejó asociados a ese código: nombre del hotel, número de habitación, idioma,
  nombre del huésped si se conoce, y los colores, logo y fondo para el celular. Es el "handoff" del
  emparejamiento. Va por el backend, no por la red local (el Wi-Fi aísla los dispositivos).
- **El celular toma de Panaccess lo mismo que la TV, menos el fondo.** Colores (confirmar, cancelar, textos,
  tarjetas/widgets, control del player), logo y tipografía son los del hotel. El fondo con imagen de la TV
  no se usa: es horizontal y en el celular queda mal. El fondo del celular es un color liso. Sin degradés.
- **La TV le manda al celular toda la información que pueda** al emparejar: hotel, habitación, idioma,
  huésped, logo, colores, Wi-Fi, clima, bienvenida, servicios y ficha del hotel.
- **La app genérica queda como está** (primera pantalla neutra). Todo lo anterior llega al escanear el QR.
- **Control remoto y "mandar a la TV" dependen de que el celular pueda hablar con la TV.** El Wi-Fi de hotel
  suele aislar los dispositivos (el celular no ve a la TV por la red local), así que hace falta un canal entre
  los dos. Sin ese canal estas dos funciones no existen y la app no las muestra (flag por marca). Ver
  contenido en el celular no depende de eso.
- **La app del celular es personalizable por parámetros.** Qué tiles hay, de qué tamaño y en qué orden, y la
  marca (logo, fondo, colores), vienen de parámetros y del handoff, no del código.

## Decisiones abiertas

- **¿Qué lleva el QR para que el celular entre a Panaccess, sin servidor propio?** Hoy la TV entra con un
  usuario y contraseña de la cuenta del hotel (la misma para todas las habitaciones), lista todas las
  licencias de la cuenta con sus PINs y toma la de su habitación, aunque otra TV la esté usando
  (`PanaccessSession.login`, `LoginService`). Si el QR llevara esos datos, el celular de cada huésped se
  quedaría con la cuenta completa del hotel. **No se hace así.** Pregunta para Panaccess/Delancer: ¿se puede
  crear un usuario limitado a una sola licencia (uno por habitación) y que el QR lleve ese? Mientras tanto
  `app/mobile` solo entra con `debugLogin` (builds de depuración) y la pantalla neutra espera el QR.
- **¿Cómo se relacionan la TV y el celular ante Panaccess?** Una licencia es por habitación y **no se puede
  usar la misma en dos dispositivos** (se vio probando una app en dos TVs; falta confirmarlo con
  Delancer/Panaccess). Entonces la sesión de la TV no se puede pasar tal cual. Idea: la licencia de la TV
  sirve para saber **cuál TV es** (y por lo tanto qué habitación) y para el celular se pide **otra licencia**
  vinculada a esa habitación. Falta definir:
  - quién pide la licencia del celular (la TV, el backend o el celular) y a quién se la cobra Panaccess;
  - si hace falta una segunda licencia por celular o una "licencia móvil" por habitación;
  - cómo se guarda la relación TV ↔ celular(es) ↔ habitación y qué pasa en el check-out.
- **Primera pantalla con la marca del hotel**: depende de los flavors por hotel (al final). Hasta entonces es
  neutra.

## Plan

0. **Decisiones** (arriba).
1. **Backend:** la TV pide un código de 6 dígitos con vencimiento; el celular lo canjea y queda vinculado a
   la habitación hasta el check-out; el backend le entrega la sesión/licencia; el canal en tiempo real
   reutiliza el de Back Wind; el check-out desempareja todo.
2. **TV** (se puede hacer ya, con la versión de prueba): un solo código compartido entre el tile y el
   diálogo; aviso de "celular conectado" a partir de `pairedPhones`; ejecutar los `TvCommand`.
3. **Celular:** dependencias de `core:panaccess` y `core:entertainment`, sesión recibida al emparejar,
   `CompanionLink` real, player y pantallas de contenido, control remoto, reconexión.
4. **Funciones sobre el canal:** control remoto, "mandar a la TV", servicios.
5. **Al final:** flavors por hotel para tener una primera pantalla con marca.

Al implementar el punto 3 hay que actualizar la regla del `CLAUDE.md` ("`app/mobile` no depende de
`core:panaccess` ni `core:entertainment`"). `core:panaccess` trae el DRM, que el celular antes no necesitaba.

## App del celular: aspecto

- **No se copia la app de Delancer** (`Bromteckotlin/PanaccessApp`). Es la app de streaming completa (656
  archivos: licencias, player, guía, catch-up, VOD, cast) y sus pantallas Compose de mobile cuelgan del
  sistema viejo de Fragments (`ActionListFragment`). Si algo se copia, va con su `SOURCE.md` y sin editar el
  original. Lo que el celular necesita del lado de Panaccess ya está en `core:panaccess` y `core:entertainment`.
- Se toma de ahí solo el aspecto (colores, barra con logo, distribución), sobre los tokens de
  `core:designsystem` que ya usa la TV, para que se vea parecido a la tele.
- **Home estilo iOS:** iconos chicos y algunos grandes, como los widgets de Apple (misma forma que el
  icono, más grandes). Qué va grande y en qué orden lo define un parámetro, no el código. Ejemplo: control
  remoto o "Conectar con la TV" grande; Wi-Fi, clima y servicios chicos. La home Lobby de la TV ya trabaja
  con tamaños por tile. Los flags de `brand.json` siguen decidiendo qué funciones existen.

## Logo y fondo del celular (parametrizable)

El operador tiene que poder cambiarlos, así que no alcanza con la carpeta de la marca. **Decidido:** el logo y
los colores son los mismos que usa la TV. El fondo con imagen de la TV no se usa en el celular (un fondo de TV
es horizontal y queda recortado); el fondo del celular es un color liso. Un fondo propio del celular, con un
parámetro aparte (por ejemplo `X_APP_BACKGROUND_MOBILE`), queda como posibilidad a futuro.

Una vez emparejado y con sesión, el celular lee estos parámetros del operador igual que la TV (`getClientConfig`,
que solo responde después de un login). Antes de emparejar no hay sesión, por eso la primera pantalla es
neutra hasta que existan los flavors por hotel.

Si se necesitara mandar imágenes al celular sin sesión propia, queda como alternativa que la TV (o el
backend) mande las URLs al emparejar y el celular las baje y las guarde (mensaje nuevo en `core/companion`).

**Origen de la regla anterior.** "El celular no usa Panaccess" ya estaba en el código inicial (2026-10-08) y
el `CLAUDE.md` solo lo documentó el 2026-10-09. El único motivo escrito era que cada celular necesitaría una
licencia, sin definir (README, punto 5). Era una decisión conservadora, no un límite técnico.
