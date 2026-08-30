package com.labs.labrats;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.view.Display;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to isolate API 30+ calls and prevent ClassNotFoundException on older devices.
 */
@RequiresApi(api = Build.VERSION_CODES.R)
public class Api30Helper {

    public static void takeScreenshot(AccessibilityService service, ScreenshotCallback callback) {
        service.takeScreenshot(Display.DEFAULT_DISPLAY, ContextCompat.getMainExecutor(service), new AccessibilityService.TakeScreenshotCallback() {
            @Override
            public void onSuccess(AccessibilityService.ScreenshotResult screenshotResult) {
                android.hardware.HardwareBuffer hardwareBuffer = screenshotResult.getHardwareBuffer();
                try {
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.getColorSpace());
                    if (bitmap != null) {
                        android.graphics.Bitmap softwareBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
                        if (softwareBitmap != null) {
                            int targetWidth = 720;
                            int targetHeight = (int) (softwareBitmap.getHeight() * (targetWidth / (float) softwareBitmap.getWidth()));
                            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(softwareBitmap, targetWidth, targetHeight, true);
                            
                            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out);
                            callback.onSuccess(out.toByteArray());
                            
                            scaled.recycle();
                            softwareBitmap.recycle();
                        }
                        bitmap.recycle();
                    } else {
                        callback.onFailure("Buffer wrap failed");
                    }
                } catch (Exception e) {
                    callback.onFailure(e.getMessage());
                } finally {
                    if (hardwareBuffer != null) hardwareBuffer.close();
                }
            }

            @Override
            public void onFailure(int errorCode) {
                callback.onFailure("OS Error: " + errorCode);
            }
        });
    }

    @SuppressWarnings("MissingPermission")
    public static Location getCurrentLocation(Context context, LocationManager locationManager, String provider) {
        final CountDownLatch latch = new CountDownLatch(1);
        final Location[] freshLoc = new Location[1];
        try {
            locationManager.getCurrentLocation(
                    provider,
                    null,
                    ContextCompat.getMainExecutor(context),
                    loc -> {
                        freshLoc[0] = loc;
                        latch.countDown();
                    });
            
            if (!latch.await(8, TimeUnit.SECONDS)) {
                // Timeout
            }
        } catch (Exception ignored) {}
        return freshLoc[0];
    }
}
