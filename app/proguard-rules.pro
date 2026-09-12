# VerifySphere ProGuard Rules
# Aggressive obfuscation to protect AES key and logic

# Keep TurnstileKit public API (needed at runtime)
-keep class com.turnstilekit.TurnstileSDK { *; }
-keep class com.turnstilekit.TurnstileCallback { *; }
-keep class com.turnstilekit.TurnstileResult { *; }
-keep class com.turnstilekit.TurnstileDialog { *; }

# Keep the Activity for intent handling
-keep class com.verifysphere.MainActivity {
    public <init>();
}

# Obfuscate everything else aggressively
-optimizationpasses 5
-dontusemixedcaseclassnames
-overloadaggressively
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Remove logging in release (makes reverse engineering harder)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Keep native crash handlers
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Material
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
