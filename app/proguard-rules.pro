# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-dontwarn javax.annotation.Nullable
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# With R8 full mode, it sees no subtypes of Retrofit interfaces since they are created with a Proxy
# and replaces all potential values with null. Explicitly keeping the interfaces prevents this.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Preserve UVC related classes
-keep class com.serenegiant.usb.** { *; }
-keep class com.jiangdg.uvc.** { *; }
-keep class com.herohan.uvcapp.** { *; }

# Preserve custom views and helpers
-keep class com.innocomm.uvcdemo.OverlayView { *; }
-keep class com.innocomm.perfmon.** { *; }
-keepattributes RuntimeVisibleAnnotations
-keep class * extends androidx.navigation.Navigator

# TensorFlow Lite (CPU + GPU + NNAPI)
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# TensorFlow GPU Delegate
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.gpu.**

# ML Kit (Face, Text, Pose, etc.)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# Keep everything in our main package if you encounter issues with reflection/resources
-keep class com.innocomm.uvcdemo.** { *; }

# Keep specific callbacks for USB/UVC libraries if not covered above
-keepclassmembers class * implements com.serenegiant.usb.IButtonCallback {*;}
-keepclassmembers class * implements com.serenegiant.usb.IFrameCallback {*;}
-keepclassmembers class * implements com.serenegiant.usb.IStatusCallback {*;}