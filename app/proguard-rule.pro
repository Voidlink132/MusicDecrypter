# 安卓项目标准基础混淆规则
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

# 保留你自定义的类，避免混淆后找不到
-keep class com.ncm2flac.** { *; }

# 系统组件不混淆
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
