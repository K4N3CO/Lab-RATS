package com.labs.labrats;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Hardened Runtime String De-Obfuscator.
 * Prevents static string analysis and signature matching in DEX binaries.
 */
public class Obf {

    private static final byte[] K = { 0x53, 0x79, 0x73, 0x74, 0x65, 0x6d, 0x53, 0x65, 0x63, 0x75, 0x72, 0x69, 0x74, 0x79, 0x31, 0x33, 0x33, 0x37 }; // "SystemSecurity1337"

    public static String d(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            byte[] data = Base64.decode(s, Base64.NO_WRAP);
            byte[] out = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = (byte) (data[i] ^ K[i % K.length] ^ (i & 0x0F));
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    public static String e(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            byte[] data = s.getBytes(StandardCharsets.UTF_8);
            byte[] out = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = (byte) (data[i] ^ K[i % K.length] ^ (i & 0x0F));
            }
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return s;
        }
    }
}
