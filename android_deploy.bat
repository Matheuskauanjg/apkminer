@echo off
echo Conecte o Android via USB com Depuracao USB ativada.
echo Verificando dispositivos...
adb devices
echo.
echo Enviando minerador para /data/local/tmp...
adb push miner.sh /data/local/tmp/miner.sh
adb push ver_logs.sh /data/local/tmp/ver_logs.sh
adb shell chmod +x /data/local/tmp/miner.sh
adb shell chmod +x /data/local/tmp/ver_logs.sh
echo Iniciando minerador no Android...
adb shell "nohup /system/bin/sh /data/local/tmp/miner.sh > /dev/null 2>&1 &"
echo.
echo Minerador iniciado com sucesso no Android (em segundo plano).
echo.
echo Para ver os logs no Android, use o comando:
echo adb shell "/data/local/tmp/ver_logs.sh"
echo.
pause
