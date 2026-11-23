# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.motogeek.telemetry.**$$serializer { *; }
-keepclassmembers class com.motogeek.telemetry.** {
    *** Companion;
}
-keepclasseswithmembers class com.motogeek.telemetry.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.motogeek.telemetry.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Google API Client & Drive REST
-keep class com.google.api.** { *; }
-keep class com.google.http.** { *; }
-keepattributes Signature
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.http.**
-dontwarn com.google.api.client.extensions.appengine.**

# WorkManager CoroutineWorkers
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Play Services Auth & Location
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
