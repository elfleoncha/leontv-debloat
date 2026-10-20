#!/usr/bin/env bash
# Aplica o restaura una lista de paquetes por ADB (disable-user / enable).
# Uso:  TV_IP=192.168.1.37 ./debloat.sh lista.txt
#       TV_IP=192.168.1.37 ./debloat.sh --restore keep.txt
set -euo pipefail

: "${TV_IP:?Define TV_IP, por ejemplo: TV_IP=192.168.1.37 ./debloat.sh lista.txt}"
ADB="${ADB:-adb}"
MODE="disable"
LIST=""
for a in "$@"; do
  case "$a" in
    --restore) MODE="enable" ;;
    *) LIST="$a" ;;
  esac
done
[ -n "$LIST" ] || { echo "Falta la lista de paquetes"; exit 1; }
[ -f "$LIST" ] || { echo "No existe $LIST"; exit 1; }

"$ADB" connect "${TV_IP}:5555" >/dev/null 2>&1 || true
"$ADB" -s "${TV_IP}:5555" wait-for-device

echo "==> Modo: $MODE  |  Lista: $LIST"
while read -r pkg; do
  case "$pkg" in ''|'#'*) continue ;; esac
  pkg="${pkg%%#*}"; pkg="$(echo "$pkg" | tr -d '[:space:]')"
  [ -z "$pkg" ] && continue
  if "$ADB" -s "${TV_IP}:5555" shell pm list packages | grep -qx "package:${pkg}"; then
    if [ "$MODE" = "disable" ]; then
      "$ADB" -s "${TV_IP}:5555" shell pm disable-user --user 0 "$pkg" >/dev/null && echo "  off  $pkg"
    else
      "$ADB" -s "${TV_IP}:5555" shell pm enable "$pkg" >/dev/null && echo "  on   $pkg"
    fi
  else
    echo "  --   $pkg (no instalado)"
  fi
done < "$LIST"

echo
echo "RAM libre ahora:"
"$ADB" -s "${TV_IP}:5555" shell cat /proc/meminfo | awk '/MemAvailable/{printf "  %.0f MB\n", $2/1024}'
