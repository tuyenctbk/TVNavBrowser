# ProGuard / R8 Rules for NaviTV Browser

# 1. Preserve WebView JavaScript Interfaces (CRITICAL for dashboard.html <-> MainActivity communication)
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.tdpham.navitvbrowser.MainActivity$WebAppInterface {
    public *;
}

# 2. Room Database & SQLite Entities
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.tdpham.navitvbrowser.data.entity.** { *; }
-keep class com.tdpham.navitvbrowser.data.dao.** { *; }
-keep class com.tdpham.navitvbrowser.data.db.** { *; }

# 3. Google Mobile Ads (AdMob)
-keep public class com.google.android.gms.ads.** {
   public *;
}
-keep public class com.google.ads.** {
   public *;
}
-dontwarn com.google.android.gms.ads.**

# 4. Firebase (Auth, Firestore, Analytics, Crashlytics, Messaging, RemoteConfig, Vertex AI)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }

# 5. Kotlin Coroutines & Serialization
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 6. ZXing (QR Code generation)
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }

# 7. Android TV Leanback
-keep class androidx.leanback.** { *; }

# 8. OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# 9. Line numbers & Source File for Crashlytics De-obfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile