# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# youtubedl-android
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

# Coil
-keep class coil.** { *; }
-keep class okio.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# JSON
-dontwarn java.beans.**
-dontwarn org.w3c.dom.**
-dontwarn com.fasterxml.jackson.**
-keep class org.json.** { *; }
-keep class com.fasterxml.jackson.** { *; }

# App classes
-keep class com.example.ytdownloader.** { *; }
