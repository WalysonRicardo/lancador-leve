@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"
REM Otimiza o Android do HY320 via ADB pela rede. Tudo e reversivel com restaurar.bat.

set "IP=%~1"
if "%IP%"=="" set /p "IP=IP do projetor: "
set "DEV=%IP%:5555"

adb connect %DEV% >nul
adb -s %DEV% get-state >nul 2>&1
if errorlevel 1 (
  echo Nao conectou em %DEV%. Confira o IP e se a depuracao por rede esta ligada.
  pause
  exit /b 1
)

if not exist backup mkdir backup
echo == 1/6 Backup do estado atual (pasta backup\)
adb -s %DEV% shell pm list packages -f > backup\pacotes-antes.txt
adb -s %DEV% shell pm list packages -d > backup\desativados-antes.txt
for /f "delims=" %%l in ('adb -s %DEV% shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME') do set "HOMEORIG=%%l"
> backup\home-original.txt echo !HOMEORIG!
echo    launcher original: !HOMEORIG!

echo == 2/6 Animacoes mais curtas (0.5x)
adb -s %DEV% shell settings put global window_animation_scale 0.5
adb -s %DEV% shell settings put global transition_animation_scale 0.5
adb -s %DEV% shell settings put global animator_duration_scale 0.5

echo == 3/6 Menos trabalho em segundo plano
adb -s %DEV% shell settings put global cached_apps_freezer enabled
adb -s %DEV% shell settings put global wifi_scan_always_enabled 0
adb -s %DEV% shell settings put global ble_scan_always_enabled 0
adb -s %DEV% shell pm trim-caches 16G >nul

echo == 4/6 Desativando apps da lista remover.txt
for /f "usebackq eol=# tokens=1" %%p in ("remover.txt") do (
  adb -s %DEV% shell pm disable-user --user 0 %%p 2>&1 | find "new state" >nul && echo    desativado: %%p
)

echo == 5/6 Instalando APKs da pasta apks\
if not exist apks\lancador-leve.apk (
  echo    baixando a ultima versao do Lancador Leve...
  curl -fsSL -o apks\lancador-leve.apk "https://github.com/WalysonRicardo/lancador-leve/releases/latest/download/lancador-leve.apk" || echo    sem internet? baixe o APK manualmente
)
for %%f in (apks\*.apk) do (
  echo    %%f
  adb -s %DEV% install -r "%%f"
)

echo == 6/6 Ajustes finais
adb -s %DEV% shell pm path br.leve.launcher | find "package" >nul && (
  adb -s %DEV% shell cmd package set-home-activity br.leve.launcher/.MainActivity
  echo    Lancador Leve definido como tela inicial
)
for %%p in (br.leve.launcher com.netflix.ninja com.netflix.mediaclient com.liskovsoft.smarttubetv.beta com.google.android.youtube.tv tv.pluto.android com.globo.globotv com.stremio.one) do (
  adb -s %DEV% shell pm path %%p | find "package" >nul && (
    echo    compilando %%p
    adb -s %DEV% shell cmd package compile -m speed -f %%p >nul
  )
)

echo.
echo Pronto. Reinicie o projetor para valer tudo:  adb -s %DEV% reboot
pause
