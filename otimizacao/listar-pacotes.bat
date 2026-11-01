@echo off
chcp 65001 >nul
cd /d "%~dp0"
REM Gera pacotes-instalados.txt para escolher o que desativar em remover.txt
REM e descobrir os apps do fabricante (HDMI, foco) para o Config.kt do launcher.

set "IP=%~1"
if "%IP%"=="" set /p "IP=IP do projetor: "
set "DEV=%IP%:5555"
adb connect %DEV% >nul

> pacotes-instalados.txt echo # Apps instalados pelo usuario
adb -s %DEV% shell pm list packages -3 >> pacotes-instalados.txt
>> pacotes-instalados.txt echo.
>> pacotes-instalados.txt echo # Apps de sistema (cuidado ao desativar)
adb -s %DEV% shell pm list packages -s >> pacotes-instalados.txt

echo Salvo em pacotes-instalados.txt
echo Dica: abra o app de foco/HDMI no projetor e rode:
echo   adb -s %DEV% shell dumpsys activity activities ^| find "topResumedActivity"
pause
