#!/usr/bin/env bash
# Gera pacotes-instalados.txt para você escolher o que desativar em remover.txt
# e descobrir os apps do fabricante (HDMI, foco) para o Config.kt do launcher.
set -u
cd "$(dirname "$0")"
IP="${1:-}"
[ -z "$IP" ] && read -rp "IP do projetor: " IP
DEV="$IP:5555"
adb connect "$DEV" >/dev/null
{
  echo "# Apps instalados pelo usuário"
  adb -s "$DEV" shell pm list packages -3 | tr -d '\r' | sed 's/^package://' | sort
  echo
  echo "# Apps de sistema (cuidado ao desativar)"
  adb -s "$DEV" shell pm list packages -s | tr -d '\r' | sed 's/^package://' | sort
} > pacotes-instalados.txt
echo "Salvo em pacotes-instalados.txt"
echo "Dica: abra o app de foco/HDMI no projetor e rode:"
echo "  adb -s $DEV shell dumpsys activity activities | grep -m1 topResumedActivity"
