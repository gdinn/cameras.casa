# R8 rules for the release build (`isMinifyEnabled = true` in app/build.gradle.kts).
#
# Every block below exists because R8 cannot see the call site: the code is reached through JNI,
# from the manifest, or through a serializer looked up at runtime. A missing rule here fails on the
# device and not in the build, so the rationale stays next to the rule.
#
# Libraries that ship their own consumer rules (Hilt/Dagger, AndroidX, CameraX, ML Kit,
# kotlinx.serialization's own runtime) are deliberately not repeated here.

# ---------------------------------------------------------------------------
# Crash reports
# ---------------------------------------------------------------------------
# Keep enough debug information for a stack trace to be re-symbolized with
# app/build/outputs/mapping/release/mapping.txt, while still hiding the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# Used for the on-disk preference schema (UserPreferences, StreamPreferences, GridPreferences,
# VpnConfigDefaults, VpnConfigTokens), for the QR-code payload, and for the type-safe navigation
# routes. Serializers are resolved at runtime from the annotated class's `Companion` or `INSTANCE`,
# which is a lookup R8 cannot trace.
#
# The four `-if` rules are the ones published by the kotlinx.serialization README; the rules scoped
# to `com.gdisys.cameras.**` keep the generated `$$serializer` classes of this project's models.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep `serializer()` on the companion of every @Serializable class.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `INSTANCE.serializer()` of @Serializable objects — this is what the five
# `NavigationRoute` destinations are.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the generated serializers of this project's models, plus everything their descriptors
# reference.
-keep,includedescriptorclasses class com.gdisys.cameras.**$$serializer { *; }
-keepclassmembers class com.gdisys.cameras.** {
    *** Companion;
}
-keepclasseswithmembers class com.gdisys.cameras.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# ML Kit — component discovery
# ---------------------------------------------------------------------------
# `MlKitComponentDiscoveryService` lists its `ComponentRegistrar`s as manifest <meta-data> *values*
# and instantiates each one with `Class.forName(name).newInstance()`. ML Kit's own consumer rules
# keep the registrar *names*, but R8 in full mode drops the default constructor of a class nothing
# constructs in code, and `ComponentDiscovery` swallows the resulting failure: the barcode
# component is then never registered and `BarcodeScanning.getClient()` NPEs on the QR screen.
# Keeping the no-arg constructor is what makes reflective discovery work.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# ---------------------------------------------------------------------------
# WebRTC — io.getstream:stream-webrtc-android
# ---------------------------------------------------------------------------
# The native layer instantiates these classes, invokes their methods and reads their fields by
# name through JNI (observers, `SurfaceViewRenderer`, the factories and every callback interface),
# so nothing under org.webrtc may be renamed or stripped.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# ---------------------------------------------------------------------------
# WireGuard — com.wireguard.android:tunnel
# ---------------------------------------------------------------------------
# `GoBackend` reaches the Go tunnel through JNI: the native symbols are bound by the fully
# qualified Java name, so renaming the class or its native methods breaks the link at runtime.
# The config model (com.wireguard.config, com.wireguard.crypto) is what `toWireGuardConfig()`
# builds, and `GoBackend$VpnService` is declared in AndroidManifest.xml.
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**
