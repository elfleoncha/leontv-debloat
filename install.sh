#!/usr/bin/env bash
# LeonTV Debloat — instala el launcher en una Android TV por ADB.
# Uso:  TV_IP=192.168.1.37 ./install.sh [--build]
set -euo pipefail
cd "$(dirname "$0")"

: "${TV_IP:?Define TV_IP, por ejemplo: TV_IP=192.168.1.37 ./install.sh}"
ADB="${ADB:-adb}"
PKG=com.valen.home
APK=prebuilt/valenhome.apk

echo "==> Conectando a ${TV_IP}:5555"
"$ADB" connect "${TV_IP}:5555" || true
"$ADB" -s "${TV_IP}:5555" wait-for-device
echo "    Si la TV muestra un diálogo de huella RSA, acepta 'Permitir' (marca siempre)."

if [ "${1:-}" = "--build" ]; then
  : "${ANDROID_HOME:?Para --build define ANDROID_HOME}"
  echo "==> Compilando"
  ./gradlew assembleDebug -q --console=plain
  APK=app/build/outputs/apk/debug/app-debug.apk
fi

echo "==> Instalando $(basename "$APK")"
"$ADB" -s "${TV_IP}:5555" install -r "$APK"

echo "==> Estableciendo Home por defecto"
"$ADB" -s "${TV_IP}:5555" shell cmd package set-home-activity --user 0 "${PKG}/.MainActivity"

echo "==> Activando servicio de accesibilidad (tecla Home del mando)"
"$ADB" -s "${TV_IP}:5555" shell "settings put secure enabled_accessibility_services ${PKG}/.HomeKeyService; settings put secure accessibility_enabled 1"

echo "==> Arrancando"
"$ADB" -s "${TV_IP}:5555" shell am start -n "${PKG}/.MainActivity" >/dev/null 2>&1 || true

echo
echo "Listo. Pulsa HOME en el mando."
echo "Ajustes dentro del launcher: botón ⚙ (arriba a la derecha)."
