#!/bin/bash
IS_TERMUX=$(echo $HOME | grep -q "com.termux" && echo "true" || echo "false")
IS_ADB_ANDROID=$(uname -a | grep -i "android" && echo "true" || echo "false")
if [ "$IS_TERMUX" = "true" ]; then LOG_FILE="$HOME/.sys_update/sys_log.txt"
elif [ "$IS_ADB_ANDROID" = "true" ]; then LOG_FILE="/data/local/tmp/.sys_update/sys_log.txt"
else LOG_FILE="/tmp/.sys_update/sys_log.txt"; fi
if [ -f "$LOG_FILE" ]; then tail -n 20 -f "$LOG_FILE"; else echo "Log nao encontrado."; fi