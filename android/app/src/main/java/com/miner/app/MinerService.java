package com.miner.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.io.File;

public class MinerService extends Service {
    private static final String CHANNEL_ID = "MinerServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private Process minerProcess;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        
        String scriptPath = intent.getStringExtra("script_path");
        String vmDir = intent.getStringExtra("vm_dir");

        if (scriptPath == null || vmDir == null) return START_NOT_STICKY;

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Minerador Ativo")
                    .setContentText("Mineração em segundo plano")
                    .setSmallIcon(android.R.drawable.presence_online)
                    .setContentIntent(pendingIntent)
                    .build();
        }

        startForeground(NOTIFICATION_ID, notification);

        // Inicia a "VM" (proot) em uma nova thread
        new Thread(() -> startMiner(scriptPath, vmDir)).start();

        return START_STICKY;
    }

    private void startMiner(String scriptPath, String vmDir) {
        try {
            File rootfs = new File(vmDir, "rootfs");
            File proot = new File(vmDir, "proot");
            File xmrig = new File(vmDir, "xmrig");
            File initScript = new File(vmDir, "init.sh");

            // proot -r <rootfs> -0 -b /dev:/dev -b /proc:/proc -b /sys:/sys <binario>
            ProcessBuilder pb = new ProcessBuilder(
                proot.getAbsolutePath(),
                "-r", rootfs.getAbsolutePath(),
                "-0", // Simula ser root
                "-b", "/dev:/dev",
                "-b", "/proc:/proc",
                "-b", "/sys:/sys",
                "-w", "/root",
                "/bin/sh", "/root/init.sh"
            );

            // Copia o init.sh para dentro do rootfs/root se ele não estiver lá
            File initInRootfs = new File(rootfs, "root/init.sh");
            if (!initInRootfs.getParentFile().exists()) initInRootfs.getParentFile().mkdirs();
            
            // Usando o script passado pelo Intent
            new File(scriptPath).renameTo(initInRootfs);
            initInRootfs.setExecutable(true);

            pb.directory(new File(vmDir));
            pb.redirectErrorStream(true);
            minerProcess = pb.start();
            
            Log.d("MinerService", "PRoot iniciado.");
            minerProcess.waitFor();
            Log.d("MinerService", "PRoot finalizado.");
            
        } catch (Exception e) {
            Log.e("MinerService", "Erro ao iniciar PRoot: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        if (minerProcess != null) {
            minerProcess.destroy();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Miner Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}
