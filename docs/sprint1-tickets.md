# Sprint 1 — tickets para cargar en Jira

Borrador local, sin publicar en ningún lado. Se arma acá hasta que se autorice el conector de
Jira en esta sesión de Claude Code (ver `docs/backend-idea.md` y `docs/backend-funcionalidades.md`
para el resto del proyecto — esto es solo lo urgente / primer sprint).

## Hecho / en curso para la demo del lunes

### HOSP-S1-01 — Filtro verde en pósters de "Películas y series"
- **Tipo:** Bug
- **Prioridad:** Alta
- **Estado:** Arreglado en código (09/oct), compila OK, pendiente de confirmar visualmente en TV.
- **Causa:** `AsyncImage` del póster no tenía `placeholder`/`error`, así que mientras cargaba se
  veía el fondo de la tarjeta (`colors.surface`, verde azulado en la paleta Resort) detrás de cada
  póster.
- **Fix:** se agregó un `ColorPainter` gris neutro como `placeholder`/`error`.
- **Archivo:** `app/tv/src/main/kotlin/.../screens/EntertainmentScreens.kt` (función `VodScreen`)

### HOSP-S1-02 — Botón "sobre el hotel" en Acciones rápidas
- **Tipo:** Verificación / config
- **Prioridad:** Alta
- **Estado:** Ya implementado en código (`HomeAction.AboutHotel`, `AboutHotelScreen.kt`), gateado
  por el flag `features.hotelInfo` en `brand.json`.
- **Pendiente de decidir:** con qué marca se hace la demo del lunes — `generic` ("Hospitality TV")
  tiene el flag **apagado**, `demo` ("Aqua Caribe") lo tiene prendido. Si se demuestra con
  `generic`, hay que prender el flag a mano en `brands/generic/assets/brand.json` (una línea).

## Nuevo — agregado por el usuario el 09/oct, para meter en este mismo sprint

### HOSP-S1-03 — Mostrar/ocultar contraseña en el login (ícono de ojo)
- **Tipo:** Mejora de UX
- **Prioridad:** Media
- **Descripción:** En la pantalla de login de la TV (usuario + contraseña de la cuenta de
  Panaccess), agregar un ícono de "ojo" para alternar entre contraseña oculta/visible. Hoy no
  existe — el campo siempre oculta el texto con puntos.
- **Por qué importa:** se vio en vivo probando el login (220000025 / contraseña oculta) — con
  control remoto es muy fácil tipear mal la contraseña y no hay forma de verificarla antes de
  mandar "Entrar".
- **Dónde:** pantalla de login de `app/tv` (Panaccess, `core/panaccess`/`app/tv` según dónde viva
  el composable del formulario de usuario/contraseña).

### HOSP-S1-04 — Sanitizar el mensaje de error de autenticación
- **Tipo:** Bug / seguridad
- **Prioridad:** Alta
- **Descripción:** Al fallar el login se vio en pantalla un mensaje tipo
  `"No se pudo iniciar el servicio de TV." + "...edentials: Empty answer when..."` — parece el
  mensaje interno de una excepción (algo como `InvalidCredentialsException: Empty answer when...`)
  mostrado tal cual al usuario, en vez de un mensaje genérico.
- **Por qué importa:** no hay que exponer la forma interna de los objetos/excepciones que maneja
  la app (nombres de clases, mensajes técnicos del cliente de Panaccess) en una pantalla que ve
  cualquiera en el hotel — es tanto un problema de UX como de superficie de información interna.
- **Fix propuesto:** mapear los errores de login a mensajes genéricos para el usuario ("No se
  pudo iniciar sesión, verificá los datos e intentá de nuevo"), y mandar el detalle técnico solo a
  logs (`Hospitality.Login`), nunca a la UI.
- **Dónde:** manejo de errores de login, probablemente en `core/panaccess` (donde se arma el
  mensaje) y la pantalla de login en `app/tv`.

### HOSP-S1-05 — Preloading de contenido en la home
- **Tipo:** Mejora de UX / performance percibida
- **Prioridad:** Media-Alta
- **Descripción:** Al entrar a la pantalla principal ya logueado, "TV en vivo" y "Películas y
  series" aparecen vacíos/cargando varios minutos después del login, en vez de estar listos. El
  usuario lo notó probando en vivo: "inicié sesión hace unos minutos y todavía no me aparecen los
  canales ni las películas y series porque están cargando".
- **Fix propuesto:** arrancar la carga del catálogo (canales, guía, VOD) en paralelo con/apenas
  después del login, no recién cuando el usuario entra a cada pantalla — mostrar un estado de
  carga claro mientras tanto en vez de silencio/vacío.
- **Dónde:** flujo de login → home en `app/tv` (dónde se dispara hoy la carga de
  `core/entertainment`/`core/panaccess`).

## Backlog suelto (no es para este sprint, polish general — ver también el diagnóstico completo de Fase 1 GIGMAX ya conversado en el chat)
- Agregar "sobre el hotel" en `app/mobile` (hoy solo existe en TV).
- Revisar el título "Acciones rápidas" (mezcla acciones con un ítem informativo).
- Completar los tokens de tema restantes para personalización (hoy solo 4 de ~10 son overrideables).

## Pendiente de decisión (no bloquea el sprint 1, pero bloquea Fase 1 completa de GIGMAX)
- Backend real de emparejamiento QR celular↔TV — requiere servidor propio, no es tarea de días.
  Spec aparte cuando se encare (el usuario pidió un `.md` separado para esto, todavía no se
  escribió).
- Si "panel de PanAccess" (mencionado por el usuario) es lo mismo que el "panel web del hotel" de
  GIGMAX o algo distinto — sigue sin confirmar.
