@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
REM Desfaz o otimizar.bat: reativa apps, volta animacoes e o launcher original.

set "IP=%~1"
if "%IP%"=="" set /p "IP=IP do projetor: "
set "DEV=%IP%:5555"
adb connect %DEV% >nul

echo == Reativando apps
for /f "usebackq eol=# tokens=1" %%p in ("remover.txt") do (
  adb -s %DEV% shell pm enable %%p 2>&1 | find "new state" >nul && echo    reativado: %%p
)

echo == Animacoes e ajustes de volta ao padrao
adb -s %DEV% shell settings put global window_animation_scale 1
adb -s %DEV% shell settings put global transition_animation_scale 1
adb -s %DEV% shell settings put global animator_duration_scale 1
adb -s %DEV% shell settings delete global cached_apps_freezer >nul
adb -s %DEV% shell settings delete global wifi_scan_always_enabled >nul
adb -s %DEV% shell settings delete global ble_scan_always_enabled >nul

if exist backup\home-original.txt (
  set /p HOMEORIG=<backup\home-original.txt
  echo == Launcher original: !HOMEORIG!
  adb -s %DEV% shell cmd package set-home-activity !HOMEORIG!
)

echo Pronto. Reinicie o projetor:  adb -s %DEV% reboot
pause
