# X_APP_WIFI — Wi-Fi del hotel con QR, desde Panaccess

El operador carga la red Wi-Fi de huéspedes en un parámetro de la cuenta y la TV muestra un **QR** para
conectar el celular, con el nombre de la red y la clave en texto por si la cámara no lo lee.

Es un parámetro del dispositivo, como `X_APP_WIDGETS` y `X_APP_SERVICES`, pero **el valor es siempre el
JSON directo** (no una URL): son cuatro datos y entran en el campo del parámetro.

## Formato

El formato del parámetro (para pasar a soporte) está en [`x-app-services.md`](x-app-services.md), sección
`X_APP_WIFI`. Se acepta un objeto JSON con `ssid`, `password`, `security` y `hidden`.

## Qué hace la TV

- **Sin el parámetro, o sin una red que sirva** (sin `ssid`, o una red con clave sin `password`, o un JSON
  inválido): no se muestra nada de Wi-Fi y todo queda como antes.
- **Home Lobby** (`"direction": "LOBBY"`): el QR ocupa el lugar de «Conectar celular». Sin el parámetro, vuelve
  «Conectar celular». Al apretar OK abre el QR en grande.
- **Home de siempre**: aparece «Wi-Fi del hotel» entre las acciones rápidas (al lado de «Conectar celular»),
  y abre el mismo QR en grande.
- Los textos están en es, en, pt, fr y de.

## El texto del QR

`WIFI:T:<WPA|WEP|nopass>;S:<ssid>;P:<clave>;H:true;;`, el formato que leen las cámaras de Android e iOS. Se
escapan `\ ; , : "` con barra invertida, y un nombre o una clave formados solo por dígitos hexadecimales
(`0-9`, `a-f`) van entre comillas, como pide el estándar. La regla es la misma que usa el backend propio de los
hoteles; los casos compartidos están en `core/hotel/src/test/resources/wifi_qr.json` (copia de
`contract/wifi_qr.json` del backend) y `WifiQrContractTest` los prueba.

Código: `core/hotel/.../HotelWifi.kt` (`parseOperatorWifi`, `wifiQrPayload`),
`OperatorConfig.wifiRaw`, `HospitalityApplication.wifi`, `LobbyHome.kt` (`WifiTile`) y `WifiDialog.kt`.
