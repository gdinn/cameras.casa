# R8 rules for the test APK of `e2eRelease` (app/build.gradle.kts), which AGP minifies with the
# app's mapping so the smoke's calls reach the obfuscated app.

# Error Prone's annotations (pulled in by the test libraries) name javax.lang.model types, which
# exist only at compile time. Nothing references them at runtime.
-dontwarn javax.lang.model.element.Modifier
