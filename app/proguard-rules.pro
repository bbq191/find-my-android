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
#-renamesourcefileattribute SourceFile

# ==================== 腾讯地图/定位 ====================
-keep public class com.tencent.lbssearch.** {*;}
-keep public class com.tencent.map.** {*;}
-keep public class com.tencent.mapsdk.** {*;}
-keep public class com.tencent.tencentmap.**{*;}
-keep public class com.tencent.tmsbeacon.**{*;}
-keep public class com.tencent.tmsbeacon.**{*;}
-dontwarn com.qq.**
-dontwarn com.tencent.**

# ==================== Eclipse Paho MQTT ====================
-keep class org.eclipse.paho.** { *; }

# ==================== Gson ====================
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ==================== Room ====================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# ==================== 数据模型 ====================
-keep class me.ikate.findmy.data.model.** { *; }
-keep class me.ikate.findmy.data.local.entity.** { *; }
-keep class me.ikate.findmy.data.remote.mqtt.message.** { *; }