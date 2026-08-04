package com.labs.labrats;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class StabilityWorker extends Worker {

    public StabilityWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        // Check if server is running
        if (!CoreSyncService.isRunning) {
            android.util.Log.d("StabilityWorker", "Persistence Trigger: Server found offline. Reviving...");
            Intent serviceIntent = new Intent(context, CoreSyncService.class);
            serviceIntent.setAction("START");

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception ignored) {}
        }

        return Result.success();
    }
}
