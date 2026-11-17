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

# ---------------------------------------------------------------------------
# Samba — com.hierynomus:smbj (+ net.engio:mbassador)
# ---------------------------------------------------------------------------
# smbj delivers its internal events (session logoff, connection close) through mbassador, which
# finds the listener methods by their @Handler annotation and builds its dispatchers by reflection.
# Nothing in code calls those methods or constructors, so R8 would strip or rename them and the
# events would be lost without any error. The library is small, so it is kept whole, along with
# the @Handler methods of every listener.
-keep class net.engio.mbassy.** { *; }
-keepclassmembers class * {
    @net.engio.mbassy.listener.Handler <methods>;
}
# Optional paths that Android does not provide and the app never takes: mbassador's EL filters
# (javax.el) and smbj's Kerberos/SPNEGO login (org.ietf.jgss). The app logs in with NTLM.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**

# ---------------------------------------------------------------------------
# Room databases — androidx.work (WorkManager keeps its jobs in a Room database)
# ---------------------------------------------------------------------------
# Room creates the generated `<Database>_Impl` by reflection, through its no-argument constructor.
# Nothing constructs it in code, so R8 in full mode removes that constructor, and WorkManager then
# fails on first use with `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []`.
# Found on e2eRelease: Home builds the download repository, so every user's Home crashed. Same class
# of bug as the ML Kit registrars above: keeping the name is not enough, `<init>()` must stay.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
