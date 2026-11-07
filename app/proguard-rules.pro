# Release build rules. The app has nothing to hide, so names are kept: crash logs stay readable without a mapping file.
-dontobfuscate

# Our own code is small and is named in the manifest, in Room's generated code and in the tests: keep it whole. Only the
# libraries (Compose, Material, Room, AndroidX) are shrunk.
-keep class com.axiomstead.foldsms.** { *; }
