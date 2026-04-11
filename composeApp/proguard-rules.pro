# ============================================================================
# Agora — ProGuard / R8 rules for release builds
# ============================================================================

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `@Serializable` model classes
-keep,includedescriptorclasses class com.app.community.core.model.**$$serializer { *; }
-keepclassmembers class com.app.community.core.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.app.community.core.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable data classes in repositories (e.g. CommunityMemberWithCommunity)
-keep,includedescriptorclasses class com.app.community.core.data.repository.**$$serializer { *; }
-keepclassmembers class com.app.community.core.data.repository.** {
    *** Companion;
}
-keepclasseswithmembers class com.app.community.core.data.repository.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor ---
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# --- Supabase ---
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# --- OkHttp (used by Ktor on Android) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# --- Koin ---
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# --- Compose ---
-dontwarn androidx.compose.**

# --- Firebase ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- Voyager ---
-keep class cafe.adriel.voyager.** { *; }
-dontwarn cafe.adriel.voyager.**

# --- General ---
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
