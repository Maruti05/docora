# Docora R8 / ProGuard rules.
#
# Keep rules are limited to libraries that rely on reflection or ship their own
# resources. The app's own code is fully shrinkable.

# --- kotlinx.serialization -------------------------------------------------
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}

# --- Apache PDFBox (bundled by pdfbox-android) ----------------------------
# PDFBox resolves font/encryption/colour-space implementations reflectively and reads
# embedded resource files, so the package has to survive shrinking.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn javax.**
-dontwarn java.awt.**
-dontwarn org.apache.commons.logging.**

# --- ML Kit on-device text recognition ------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text** { *; }
-dontwarn com.google.mlkit.**

# --- Hilt -----------------------------------------------------------------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# --- Room -----------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- AndroidX WorkManager -------------------------------------------------
-keep class * extends androidx.work.ListenableWorker { public <init>(...); }

# --- SQLite FTS5 (Room / framework SQLite) --------------------------------
-keep class org.sqlite.** { *; }
