#!/bin/bash
# Configura una o varias TVs del hotel sin tipear la cuenta con el control remoto.
#
#   tools/configurar-tv.sh [-p paquete] IP[=habitación] [IP[=habitación] ...]
#
# Ejemplo (dos TVs, cada una con la licencia de su habitación):
#   tools/configurar-tv.sh -p com.networkbroadcast.hospitality.demo 192.168.4.145=214 192.168.4.146=215
#
# Pide el usuario y la contraseña de la cuenta de TV del hotel UNA vez (la contraseña no se muestra
# ni queda guardada en ningún lado), calcula el md5 que usa la app y deja en cada TV el archivo
# Android/data/<paquete>/files/setup.json. La app lo lee al abrir, entra sola y lo borra.
# Requisitos: adb en el PATH y la depuración por red activada en cada TV.
set -euo pipefail

PKG="com.networkbroadcast.hospitality"
while getopts "p:h" opt; do
  case $opt in
    p) PKG="$OPTARG" ;;
    *) sed -n '2,14p' "$0"; exit 1 ;;
  esac
done
shift $((OPTIND - 1))
[ $# -ge 1 ] || { sed -n '2,14p' "$0"; exit 1; }

read -r -p "Usuario de la cuenta de TV del hotel: " USER_NAME
read -r -s -p "Contraseña: " PASSWORD; echo
[ -n "$USER_NAME" ] && [ -n "$PASSWORD" ] || { echo "Faltan datos."; exit 1; }

# Mismo cálculo que PanaccessSession.login: md5(contraseña + "_panaccess").
if command -v md5 >/dev/null; then
  MD5=$(printf '%s' "${PASSWORD}_panaccess" | md5)
else
  MD5=$(printf '%s' "${PASSWORD}_panaccess" | md5sum | cut -d' ' -f1)
fi
unset PASSWORD

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
DIR="/sdcard/Android/data/$PKG/files"

for target in "$@"; do
  IP="${target%%=*}"
  ROOM=""
  [[ "$target" == *=* ]] && ROOM="${target#*=}"
  SERIAL="$IP"
  [[ "$IP" == *:* ]] || SERIAL="$IP:5555"

  if [ -n "$ROOM" ]; then
    printf '{"user":"%s","passwordMd5":"%s","room":"%s"}\n' "$USER_NAME" "$MD5" "$ROOM" > "$TMP"
  else
    printf '{"user":"%s","passwordMd5":"%s"}\n' "$USER_NAME" "$MD5" > "$TMP"
  fi

  echo "== $SERIAL${ROOM:+ (habitación $ROOM)}"
  adb connect "$SERIAL" >/dev/null
  adb -s "$SERIAL" shell mkdir -p "$DIR"
  adb -s "$SERIAL" push "$TMP" "$DIR/setup.json" >/dev/null
  adb -s "$SERIAL" shell am force-stop "$PKG"
  adb -s "$SERIAL" shell monkey -p "$PKG" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 \
    || adb -s "$SERIAL" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  echo "   listo: la TV se configura sola al abrir la app"
done
