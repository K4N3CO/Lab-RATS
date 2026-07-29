package com.labs.labrats;

import android.os.Build;
import android.provider.Settings;
import android.content.Context;
import java.io.File;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Advanced environment analytics and string protection.
 * Ensures the system stability protocols are running in a secure environment.
 */
public class SystemAnalytics {

    private static final byte[] XOR_KEY = {0x53, 0x79, 0x73, 0x41, 0x64, 0x6D, 0x69, 0x6E};

    public static String decrypt(byte[] encrypted) {
        byte[] decrypted = new byte[encrypted.length];
        for (int i = 0; i < encrypted.length; i++) {
            decrypted[i] = (byte) (encrypted[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return new String(decrypted);
    }

    public static Object safeCall(String className, String methodName, Class<?>[] paramTypes, Object instance, Object... args) {
        try {
            Class<?> clazz = Class.forName(className);
            Method method = clazz.getMethod(methodName, paramTypes);
            return method.invoke(instance, args);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isEnvironmentRisky() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT)
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("vbox86")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }

    /**
     * Checks if the device has ADB debugging enabled.
     */
    public static boolean isDebuggingActive(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Heuristic check for root access (SU binary presence).
     */
    public static boolean isElevatedAccessDetected() {
        String[] paths = {
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    public static String getSessionIdentity() {
        return "STB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
