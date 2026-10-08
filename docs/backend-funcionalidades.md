# Backend propio — funcionalidades importantes y diferenciales

Complementa [`backend-idea.md`](backend-idea.md). Allá está la división del sistema y lo básico de
cada área; acá, **qué más tiene que tener el backend para que el producto funcione en un hotel real
y se pueda vender mejor que la competencia**. Todo es idea: el backend es otro proyecto.

Varias de estas ideas salen de problemas que ya aparecieron armando la app de TV; se marcan con
**(visto)**. Buena parte de la base técnica ya existe en el backend de Wind (`~/Desktop/Back Wind`):
ver [Qué se puede reutilizar de Back Wind](#qué-se-puede-reutilizar-de-back-wind).

## Prioridades de un vistazo

| | Funcionalidad | Por qué |
|---|---|---|
| **Imprescindible** | Alta de TVs sin técnico (aprovisionamiento) | Instalar 300 TVs a mano no escala |
| | Gestión remota de la flota de TVs | En un hotel no se puede ir habitación por habitación |
| | Administración de licencias de Panaccess | Se agotan: 3 por cuenta en la de prueba **(visto)** |
| | Configuración remota por hotel, piso y habitación | Los parámetros `X_…` del operador no alcanzan **(visto)** |
| | La TV sigue andando si el backend se cae | El servidor viejo del hotel se cayó y se perdió todo lo del hotel **(visto)** |
| | Estadía automática desde el PMS | Saludo con nombre, idioma y check-out sin intervención |
| | Privacidad y borrado al hacer check-out | La TV y el celular de la habitación pasan a otro huésped |
| | Mensajes de emergencia | Obligación del hotel; tienen que llegar siempre |
| **Diferencial** | Conserje con IA | Responde 24 h en el idioma del huésped y pasa a una persona si hace falta |
| | Personalización por estadía | La TV "conoce" al huésped desde que entra |
| | Ventas adicionales medibles | El hotel ve cuánto vendió por la TV: es lo que justifica el producto |
| | Encuestas y reputación | Detectar al huésped insatisfecho antes de que escriba la reseña |
| | Canal propio del hotel y señalización digital | El mismo contenido en la TV, el lobby y el celular |
| | Ahorro de energía con domótica | Retorno de inversión fácil de mostrar a la gerencia |
| | Cadenas de hoteles | Una cadena administra 20 propiedades desde un panel |
| **Futuro** | TV Everywhere en el celular | Depende del modelo de licencias |
| | Llave digital, minibar, integración con el POS del restaurante | Cada una necesita un proveedor externo |

## Imprescindibles

### 1. Alta de TVs sin técnico (aprovisionamiento)
Hoy cada TV necesita que alguien escriba la cuenta de Panaccess **(visto)**. Con el backend:
- El instalador abre la app y la TV muestra un código. Desde el panel lo asigna a la habitación 214 y
  la TV queda lista: cuenta, licencia, marca y configuración bajan solas.
- Alternativa masiva: una planilla con los números de serie de los equipos y su habitación.
- Si cambian una TV de habitación, se reasigna desde el panel, sin tocar el equipo.

### 2. Gestión remota de la flota
- Estado de cada TV en el panel: encendida, versión de la app, última conexión, canal en reproducción
  y errores del player.
- Acciones remotas: reiniciar la app, borrar caché, volver a pedir el login y subir los logs. El base
  ya sube logs por SFTP en Wind; acá conviene que sea a pedido desde el panel.
- **Actualizaciones de la app escalonadas:** primero 5 TVs, después el piso, después el hotel. Que se
  pueda volver atrás si algo falla. La app tiene que poder actualizarse sola, sin Play Store, porque
  los STB de hotel muchas veces no la tienen.
- Alertas: "12 TVs del piso 3 sin conexión desde hace 10 minutos".

### 3. Administración de licencias de Panaccess
Cada TV consume una licencia de streaming. Con 3 por cuenta, la cuarta TV ya no tiene **(visto)**.
- El backend sabe qué licencia usa cada TV y asigna una al dar de alta el equipo, en vez de "la
  primera libre".
- Libera la licencia de una TV dada de baja o rota.
- Alerta antes de agotarlas ("quedan 2 libres").
- Es también la base para decidir **TV Everywhere** en el celular, que hoy está frenado por esto.

### 4. Configuración remota con herencia
Lo que hoy está en `brand.json` y en los parámetros `X_…` del operador, administrable desde el panel:
- **Niveles:** cadena → hotel → piso o tipo de habitación → habitación. Lo de abajo pisa lo de arriba.
- **Qué se configura:**
  - qué funciones se ven (VOD, guía, servicios);
  - idiomas;
  - canal de inicio;
  - colores y logo;
  - el orden de la home;
  - los textos de bienvenida.
- **Funciones prendidas por partes:** por ejemplo, room service solo en el piso de suites, para probar
  antes de abrirlo a todo el hotel.
- Cada cambio queda registrado: quién, cuándo y qué.

### 5. La TV sigue andando aunque el backend se caiga
El servidor del hotel que alimentaba clima, imágenes e información se cayó y la app perdió todo
eso **(visto)**.
- La TV guarda lo último que recibió (contenido, agenda, configuración) y lo muestra si no hay red.
- TV en vivo y VOD no dependen del backend: van directo a Panaccess. Si el backend se cae, **la tele
  se sigue viendo**.
- Un nodo local (on-premise) en el hotel, opcional, que sigue funcionando sin internet y se sincroniza
  después.
- La API está versionada, así una TV con la app vieja no se rompe cuando cambia el backend.

### 6. Estadía automática desde el PMS
- Al check-in, la TV de la habitación ya tiene el nombre, el idioma y las fechas: "Buenas tardes,
  Marta" en su idioma, sin pantalla de idioma.
- Al check-out del PMS: se borra la estadía, se desemparejan los celulares, se cierra la sesión de
  apps de terceros (Netflix, YouTube) si las hay, y la TV vuelve a la bienvenida genérica.
- Con cambio de habitación, la estadía se mueve sola.
- **Sin PMS:** el staff hace lo mismo con un botón en el panel.

### 7. Privacidad
- Nada personal queda en la TV ni en el backend después del check-out. Para analíticas solo queda lo
  agregado y anónimo.
- Cumplimiento de las leyes de protección de datos de cada país (México: LFPDPPP; Brasil: LGPD;
  Europa, por huéspedes europeos: GDPR).
- Consentimiento explícito para la personalización y para la IA.
- Ver TV no requiere ningún dato del huésped.

### 8. Mensajes de emergencia
- Alarma de incendio, evacuación o huracán (en Cancún y Tulum es real) en **todas las TVs y
  celulares** del hotel: con prioridad máxima, por encima de cualquier contenido y en el idioma de
  cada estadía.
- La TV muestra el mensaje aunque esté reproduciendo, y sube el volumen.
- El panel muestra cuántas TVs y celulares lo recibieron.
- Es distinto de la mensajería comercial, y no puede depender de que el huésped tenga la app.

## Diferenciales

### 9. Conserje con IA
Evoluciona la idea de recomendaciones de [`backend-idea.md`](backend-idea.md):
- **Chat en el celular y en la TV:** "¿a qué hora cierra el spa?", "quiero una toalla más", "reservá
  una mesa para cuatro a las 21".
- Contesta con los datos del hotel (agenda, servicios, horarios, menú), **no inventa**. Si no sabe,
  deriva a una persona del staff en el panel, con la conversación ya traducida.
- **Traducción automática** en las dos direcciones: el huésped escribe en alemán, el staff lo lee en
  español.
- Convierte la conversación en un pedido real ("toalla extra" → pedido de housekeeping).
- Mensajes proactivos y moderados: "llueve a las 16 h, hay clase de cocina en el lobby". Que no sea
  spam: el huésped puede apagarlos.

### 10. Personalización por estadía
- La home se adapta a quién está:
  - familia con niños: la agenda del Star Camp;
  - pareja: el spa;
  - huésped de negocios: las salas de reuniones.
- Mantiene el idioma, los canales favoritos y "seguir viendo" durante toda la estadía.
- **Huésped recurrente:** si vuelve y lo autoriza, el hotel recuerda sus preferencias (idioma,
  almohada, canal favorito).

### 11. Ventas adicionales medibles
Es lo que le muestra al hotel que el sistema se paga solo:
- Room service, spa, excursiones, late check-out y mejora de habitación, ofrecidos en el momento
  justo. Por ejemplo, el late check-out se ofrece la noche anterior a la salida.
- Promociones dirigidas por segmento: membresía, idioma, tipo de habitación o días de estadía.
- **Reporte de ingresos:** cuánto se vendió por la TV o el celular, por servicio y por mes.

### 12. Encuestas y reputación
- Una pregunta corta en la TV durante la estadía ("¿cómo va todo?"), no solo al final.
- Si la respuesta es mala, alerta inmediata a recepción para resolverlo antes del check-out.
- Si es buena, invitación a dejar la reseña pública.
- NPS por hotel y por área en el panel.

### 13. Canal propio y señalización digital
- **Canal del hotel:** el video institucional en loop, que la app vieja usaba como canal de inicio. Se
  administra desde el panel y aparece en la grilla como un canal más.
- El mismo contenido en las **pantallas del lobby, el restaurante y los ascensores** (agenda del día,
  menú, promociones), con programación horaria.
- Un solo lugar para cargar contenido: la TV de la habitación, el lobby y el celular.

### 14. Ahorro de energía
- Con el PMS y la domótica: habitación vacía → aire en modo eco y TV apagada. Llega el huésped → TV
  con la bienvenida y la habitación a la temperatura que eligió.
- Reporte de consumo ahorrado para la gerencia.

### 15. Cadenas y operadores
- Muchos hoteles en una sola instalación, con datos separados por hotel.
- Panel de cadena: comparar hoteles, cargar contenido común (marca, promociones de la cadena) y
  dejar que cada hotel cargue lo suyo.
- **Roles:** administrador de cadena, gerente de hotel, recepción, housekeeping, cocina. Cada uno ve
  solo lo suyo. Por ejemplo, cocina ve solo los pedidos de room service.
- El panel también es de marca blanca, como las apps.

## Lo que el panel tiene que tener sí o sí

| Rol | Qué ve |
|---|---|
| Recepción | Estadías del día, check-in y check-out manual, mensajes a habitaciones, emergencias, encuestas negativas |
| Housekeeping | Pedidos de limpieza, "no molestar" y habitaciones a limpiar después del check-out. En una tablet, no en una PC |
| Cocina / room service | Pedidos entrantes con alerta sonora, tiempos y estados |
| Gerencia | Ingresos por la TV, uso, NPS, ahorro de energía |
| Técnico | Flota de TVs, licencias, versiones, alertas |

Los pedidos tienen **tiempo máximo de respuesta**: si room service no acepta en 5 minutos, se escala
al supervisor. Sin esto, el huésped pide por la TV y nadie le contesta, que es peor que no tener la
función.

## Para la operación del propio producto (nosotros)

- **Monitoreo:** errores de las apps, caídas del player por canal y tiempos de respuesta del backend.
  Así se sabe de un problema antes de que llame el hotel.
- **Facturación por hotel:** por habitación activa por mes. Las métricas para cobrar salen del mismo
  backend.
- **Ambiente de demostración:** un hotel de prueba con contenido, como la marca `demo`, para mostrar
  a clientes sin tocar uno real.
- **Contrato de API documentado y versionado**, compartido con las apps de TV, celular y Roku.

## Qué se puede reutilizar de Back Wind

`~/Desktop/Back Wind` es el backend de Wind (Django 5.2 + DRF, Channels/Daphne, Celery + Redis,
PostgreSQL), relevado en solo lectura. Es un intermediario de cuentas frente a Panaccess, con
unos 215 tests. **Varias piezas del backend de hotel ya están resueltas ahí**:

| Necesidad del hotel | Qué tiene Back Wind | Cómo se adapta |
|---|---|---|
| Hablar con Panaccess sin romperlo | Cliente con reintentos, una sola sesión compartida en Redis (cifrada) con *lock* bloqueante, *circuit breaker* y reintento con el nombre alternativo de la función (`cv…`) (`wind/services/panaccess_*.py`) | **Copiar casi tal cual.** Nació de un incidente real: varios procesos logueándose a la vez superaron el límite de Panaccess (20 logins cada 5 minutos) |
| Licencias por habitación (punto 3) | Alta de licencias y productos con vencimiento (`addLicenseBlockToSubscriber`, `addProductToSmartcards`), pasos pendientes guardados y reintentados (`subscriber_provisioning.py`) | Licencia por TV o habitación; productos con vencimiento igual a las fechas de la estadía |
| Check-out limpio | Baja en orden con registro de cada paso: licencia → órdenes → productos → smartcards → abonado (`panaccess_deprovision.py`, `subscriber_closure.py`) | Es la secuencia de check-out del lado Panaccess |
| Canal de tiempo real | WebSocket `/ws/device/` con JWT, grupos por abonado, ping/pong, códigos de cierre y límites con Redis (`device_consumers.py`) | Base del canal único: agregar grupos `hotel_`, `piso_`, `habitación_` y mensajes de orden + confirmación + presencia |
| Emparejamiento por QR | Pedido con código temporal y clave efímera del dispositivo, entrega cifrada (AES-GCM + RSA-OAEP), vinculación desde una cuenta ya logueada (`UDIDAuthRequest`, `crypto_tv.py`) | Patrón para el emparejamiento celular ↔ TV y para dar de alta una TV sin escribir la cuenta (punto 1) |
| Equipos vinculados y revocación | Registro de dispositivos con revocación en vivo por el socket, baja automática por inactividad (`DeviceSession`, `device_session_service.py`) | Registro de la flota de TVs (punto 2) y de los celulares de cada estadía |
| Logs de las apps | `POST /api/v1/logs/` con agrupación por error (tipo Sentry), alertas por mail y retención; 28 tests (`applogs/`) | **Copiar tal cual** como diagnóstico de la flota |
| Analíticas | Ingesta incremental de la telemetría de Panaccess, agregados diarios y cache precalculada (`telemetry/`). Sin tests | Patrón para analíticas por hotel y habitación |
| Seguridad de cuentas | JWT que se invalida al cambiar la contraseña, tokens de un solo uso, OTP por mail, bloqueo por intentos, IP solo de proxies conocidos, validador de origen para apps nativas | Para el panel del staff y las cuentas de operador |
| Operación | Configuración en un solo `appConfig.py` con flags, `/health` y `/ready`, réplica de lectura con fallback, Daphne por systemd detrás de nginx, Sentry, rotación de secretos, tareas Celery con cola propia y lock | La misma plataforma |

**Lo que Back Wind no tiene y el hotel sí necesita:**
- Varios hoteles con datos separados, y roles para el staff (solo distingue `is_staff`).
- Panel para el staff.
- Integración con el PMS.
- Inventario de TVs, presencia en línea y **órdenes remotas con confirmación** (reiniciar, pedir logs, cambiar la configuración).
- Configuración por hotel, piso o habitación.
- **Mensajes OSM y de emergencia** (no llama a ninguna función de OSM).
- Notificaciones push.
- Pedidos y pagos.
- Domótica.
- Integración continua (CI): los tests se corren a mano.

**Errores que Wind ya cometió y el backend de hotel tiene que evitar desde el día uno:**
1. Muchos logins simultáneos a Panaccess. Usar una sesión compartida con *lock* bloqueante.
2. Contraseñas de Panaccess en claro en respuestas y mails, y una clave privada fija dentro de las apps. Usar claves efímeras por equipo, o que el backend maneje la sesión y las apps nunca tengan la contraseña real.
3. Tareas sin cola asignada que nunca se ejecutaban (pasó 5 veces). Asignar todas las colas en forma explícita y alertar si algo cae en la cola por defecto.
4. Un `X-Forwarded-For` falsificado salteaba la lista de IPs permitidas. Confiar en ese encabezado solo si viene de los proxies propios.
5. Lecturas viejas de la réplica dejaban entrar a cuentas cerradas. Usar la base primaria en las verificaciones de seguridad.
6. Altas a medias cuando se acaban las licencias. Guardar los pasos pendientes y que sean reintentables.
7. Bajas que no revocaban los dispositivos. Un único lugar que revoque todo (en el hotel: el check-out).
8. Nunca recorrer listas paginadas de Panaccess dentro de una petición de usuario: puede llevar hasta 40 llamadas por pedido.
9. Detalles: el código de cierre 1011 rompe Daphne; hay que cerrar las conexiones a la base al desconectar el socket; y la cadena SSL tiene que estar completa, porque si no fallan los enlaces a las apps.

Detalle completo, con archivos y líneas: README de Back Wind (§2, §7–9) y sus documentos
`GUIA_INTEGRACION_UNIFICADA.md` y `AUDITORIA_DECISIONES_Y_PENDIENTES.md`.

## Qué cambiaría en las apps cuando exista cada cosa

| Funcionalidad del backend | En las apps |
|---|---|
| Aprovisionamiento | La pantalla de login de Panaccess pasa a ser un código de alta |
| Licencias | Desaparece la pantalla de elegir licencia |
| Configuración remota | `brand.json` queda solo para lo que no puede venir del servidor (applicationId, ícono) |
| Estadía y PMS | La selección de idioma y el saludo se completan solos |
| Mensajería y emergencias | Se suma un canal propio a los mensajes OSM de Panaccess |
| Conserje y pedidos | Pantallas nuevas en la TV y el celular; se prenden los flags de servicios |
| Emparejamiento | Se reemplaza la versión de prueba de `core/companion` |
