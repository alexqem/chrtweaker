# Proguard rules for chr-logger
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}

# Preserve Gson models and serialized fields
-keepclassmembers class com.alexqem.chr.core.model.** {
    @com.google.gson.annotations.SerializedName <fields>;
    <fields>;
}
-keep class com.alexqem.chr.core.model.** { *; }
-keepclassmembers class com.alexqem.chr.core.tweaks.** {
    @com.google.gson.annotations.SerializedName <fields>;
    <fields>;
}
-keep class com.alexqem.chr.core.tweaks.** { *; }
