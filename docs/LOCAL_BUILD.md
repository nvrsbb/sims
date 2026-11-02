# FoldSMS starter build — 2026-11-02

Imported the original FoldSMS starter and MVP specification from sims.zip into /Users/arvinbobis/Programs/sims. Preserved the original archive and excluded it from Git.

Added a Gradle 8.10.2 wrapper with distribution checksum verification, switched the GitHub workflow to the wrapper, and updated the README for this repository and Fold6 preview checks. Application source remains the supplied sample-data prototype.

## Verified locally

- JDK 17, Gradle 8.10.2, Android SDK platform 35, Build Tools 34.0.0.
- Debug APK assembly: successful; wrapper assembly also successful.
- Android lint: 0 errors, 3 warnings (newer activity-compose available, Android 12 backup extraction rules absent, application icon absent).
- APK signature: verified.
- No Android system permissions requested. AndroidX declares only the app's own signature-protected dynamic-receiver permission.
- SHA-256: `8ddf1ae7ca010a63453839ab66d055b2ccb29b7025d909c22835f7198f952e9b`

## Pending

After this build, the user installed the preview on their Fold6 and reported that search, category filtering, conversation opening, unfolded split view, and fold transitions work. See docs/CONTINUE.md for the handoff. The app is a preview with disabled sending and hardcoded fabricated conversations. Keep Google Messages as default.

The first build used isolated tools in this Codex chat's work/android-toolchain directory. The ignored local.properties points to that SDK. Configure Android Studio/JDK 17 for a durable setup later.

GitHub Actions has not been verified. The preview APK is published in the v0.1.0-preview release. Source is committed locally for continuation in Warp; publication of that source is separate.
