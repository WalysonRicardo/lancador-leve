#!/usr/bin/env bash
# Otimiza o Android do HY320 via ADB pela rede. Tudo é reversível com restaurar.sh.
# Uso: ./otimizar.sh 192.168.0.42
set -u
cd "$(dirname "$0")"

IP="${1:-}"
[ -z "$IP" ] && read -rp "IP do projetor: " IP
DEV="$IP:5555"

adb connect "$DEV" >/dev/null
if ! adb -s "$DEV" get-state >/dev/null 2>&1; then
  echo "Não conectou em $DEV. Confira o IP e se a depuração por rede está ligada."
  exit 1
fi
A() { adb -s "$DEV" "$@"; }
S() { A shell "$@" | tr -d '\r'; }

mkdir -p backup
echo "== 1/6 Backup do estado atual (pasta backup/)"
S pm list packages -f            > backup/pacotes-antes.txt
S pm list packages -d            > backup/desativados-antes.txt
S cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 > backup/home-original.txt
for k in window_animation_scale transition_animation_scale animator_duration_scale cached_apps_freezer wifi_scan_always_enabled ble_scan_always_enabled; do
  echo "$k=$(S settings get global $k)"
done > backup/ajustes-antes.txt
echo "   launcher original: $(cat backup/home-original.txt)"

echo "== 2/6 Animações mais curtas (0.5x)"
S settings put global window_animation_scale 0.5
S settings put global transition_animation_scale 0.5
S settings put global animator_duration_scale 0.5

echo "== 3/6 Menos trabalho em segundo plano"
S settings put global cached_apps_freezer enabled      # congela apps parados (Android 11), vale após reiniciar
S settings put global wifi_scan_always_enabled 0
S settings put global ble_scan_always_enabled 0
S pm trim-caches 16G >/dev/null                         # limpa cache de todos os apps

echo "== 4/6 Desativando apps da lista remover.txt"
grep -vE '^\s*(#|$)' remover.txt | while read -r p; do
  if S pm disable-user --user 0 "$p" 2>&1 | grep -q "new state"; then echo "   desativado: $p"; fi
done

echo "== 5/6 Instalando APKs da pasta apks/"
if [ ! -f apks/lancador-leve.apk ]; then
  echo "   baixando a última versão do Lançador Leve…"
  curl -fsSL -o apks/lancador-leve.apk "https://github.com/WalysonRicardo/lancador-leve/releases/latest/download/lancador-leve.apk" || echo "   (sem internet? baixe o APK manualmente)"
fi
shopt -s nullglob
for f in apks/*.apk; do echo "   $f"; A install -r "$f" | tail -n 1; done

echo "== 6/6 Ajustes finais"
if S pm path br.leve.launcher | grep -q package; then
  S cmd package set-home-activity br.leve.launcher/.MainActivity
  echo "   Lançador Leve definido como tela inicial"
fi
# Compila os apps principais em modo 'speed': abrem mais rápido (usa um pouco mais de espaço)
for p in br.leve.launcher com.netflix.ninja com.netflix.mediaclient com.liskovsoft.smarttubetv.beta \
         com.google.android.youtube.tv tv.pluto.android com.globo.globotv com.stremio.one; do
  S pm path "$p" | grep -q package && { echo "   compilando $p"; S cmd package compile -m speed -f "$p" >/dev/null; }
done

echo
echo "Pronto. Reinicie o projetor para valer tudo:  adb -s $DEV reboot"
