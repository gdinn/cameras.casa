# Extra R8 rules for the `e2eRelease` build type only (app/build.gradle.kts). The real release
# never sees them.
#
# RealEnvironmentSmokeTest runs from its own APK against this minified app, and it calls a few app
# classes directly to store the QR credentials and the stream URL before it opens MainActivity
# (the camera step is the one thing it skips). R8 sees no caller for some of them in the app, or
# would inline them, so they are kept here as written. Everything else is minified exactly as in
# release, which is the point of the build type.
#
# Keep this list to what the smoke calls. A rule that keeps more would hide R8 breakage the smoke is
# there to catch.

# Storing the QR credentials: parse, then save through the production repository and DataStore.
-keep class com.gdisys.cameras.core.storage.domain.usecase.ParseUserPreferencesFromQrCodeUseCase { *; }
-keep class com.gdisys.cameras.core.storage.domain.usecase.SaveUserPreferencesUseCase { *; }
-keep class com.gdisys.cameras.core.storage.data.UserPreferencesRepositoryImpl { *; }
-keep class com.gdisys.cameras.core.storage.data.DataStoreManager { *; }
-keep class com.gdisys.cameras.core.storage.data.DataStoreKt { *; }

# Storing the stream URL.
-keep class com.gdisys.cameras.core.storage.domain.usecase.SaveStreamUrlsUseCase { *; }
-keep class com.gdisys.cameras.core.storage.data.StreamPreferencesRepositoryImpl { *; }
-keep class com.gdisys.cameras.core.storage.data.StreamPreferencesDataStoreManager { *; }
-keep class com.gdisys.cameras.core.storage.data.StreamPreferencesDataStoreKt { *; }

# Reading the playback state the cards expose to semantics.
-keep class com.gdisys.cameras.feature.cameras.components.StreamPlaybackState { *; }
-keep class com.gdisys.cameras.feature.cameras.components.StreamPlaybackStateKt { *; }

# Classes the test APK reaches inside the app APK. AGP leaves out of the test APK every library the
# app also depends on, so the test code calls the app's copy, which R8 has already shrunk.
# AndroidX Test traces its steps through androidx.tracing, which the app links but barely calls.
-keep class androidx.tracing.** { *; }
# The test is Kotlin and blocks on coroutines (runBlocking), and the app's copies of the standard
# library and of kotlinx.coroutines lack whatever the app itself does not call. They are kept whole:
# R8 breakage the smoke is after lives in the app's own classes, not in these two libraries.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
# Compose UI Test drives the app's Compose runtime and UI layer by name, and R8 does more than drop
# classes there: it merges some away (Composer, an interface with one implementation). The two
# layers it calls are kept whole, with the AndroidX pieces it reaches through them. Foundation and
# Material 3, which hold the app's widgets, are not on the list and stay shrunk as in release.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.concurrent.futures.** { *; }
