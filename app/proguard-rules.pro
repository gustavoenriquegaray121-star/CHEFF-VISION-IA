# Gemini AI
-keep class com.google.ai.** { *; }

# CameraX
-keep class androidx.camera.** { *; }

# Location
-keep class com.google.android.gms.location.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
