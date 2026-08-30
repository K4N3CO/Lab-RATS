package com.labs.labrats;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import androidx.annotation.RequiresApi;

/**
 * Helper class to isolate API 24+ calls and prevent ClassNotFoundException on older devices.
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class Api24Helper {

    public static boolean dispatchClick(AccessibilityService service, int x, int y) {
        Log.d("Api24Helper", "dispatchClick: x=" + x + ", y=" + y);
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 150);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            boolean result = service.dispatchGesture(builder.build(), null, null);
            Log.d("Api24Helper", "dispatchGesture result: " + result);
            return result;
        } catch (Exception e) {
            Log.e("Api24Helper", "Error in dispatchClick: " + e.getMessage());
            return false;
        }
    }

    public static boolean dispatchSwipe(AccessibilityService service, int x1, int y1, int x2, int y2, int duration) {
        try {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, Math.max(duration, 100));
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(stroke);
            return service.dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            return false;
        }
    }
}
