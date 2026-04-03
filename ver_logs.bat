@echo off
set "logFile=%LOCALAPPDATA%\WinSysUpdate\sys_log.txt"
if exist "%logFile%" (
    echo --- Ultimas 20 linhas do log ---
    powershell -Command "Get-Content '%logFile%' -Tail 20 -Wait"
) else (
    echo Log nao encontrado.
    pause
)