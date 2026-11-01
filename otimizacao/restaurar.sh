#!/usr/bin/env bash
# Desfaz o otimizar.sh: reativa apps, volta animações e o launcher original.
# Uso: ./restaurar.sh 192.168.0.42
set -u
cd "$(dirname "$0")"

IP="${1:-}"
[ -z "$IP" ] && read -rp "IP do projetor: " IP
DEV="$IP:5555"
adb connect "$DEV" >/dev/null
S() { adb -s "$DEV" shell "$@" | tr -d '\r'; }

echo "== Reativando apps"
grep -vE '^\s*(#|$)' remover.txt | while read -r p; do
  S pm enable "$p" 2>&1 | grep -q "new state" && echo "   reativado: $p"
done

echo "== Animações e ajustes de volta ao padrão"
S settings put global window_animation_scale 1
S settings put global transition_animation_scale 1
S settings put global animator_duration_scale 1
S settings delete global cached_apps_freezer >/dev/null
S settings delete global wifi_scan_always_enabled >/dev/null
S settings delete global ble_scan_always_enabled >/dev/null

if [ -s backup/home-original.txt ]; then
  H="$(cat backup/home-original.txt)"
  echo "== Launcher original: $H"
  S cmd package set-home-activity "$H"
fi

echo "Pronto. Reinicie o projetor:  adb -s $DEV reboot"
