# FoldSMS handoff — 2026-11-02

## Current project

Continue in `/Users/arvinbobis/Programs/sims` (remote: `https://github.com/nvrsbb/sims.git`). The repository is public. The remote initially contained an empty commit and the `v0.1.0-preview` APK release. Source is now committed locally; this handoff does not imply it has been pushed.

Original starter archive: ignored `sims.zip`. Native Kotlin / Jetpack Compose app, package `com.axiomstead.foldsms`, min SDK 29, compile/target SDK 35. Specification: `docs/MVP_SPEC.md`.

## Working and verified

- Preview uses fabricated conversations only, no Android SMS or internet permissions, no default SMS role request, disabled sending.
- Local debug assembly and wrapper assembly passed. APK signature verified. Lint: 0 errors, 3 warnings (newer activity-compose available, missing Android 12 backup extraction rules, missing application icon).
- User installed the released preview on their only phone, a Samsung Galaxy Z Fold6, and reported search for Alex, Finance category filtering, conversation opening, unfolded split view, and fold/unfold transition all work. These are user-reported device checks, not automated tests.
- Google Messages remains the intended default. Do not enable SMS access or default-role behavior as part of the next milestone.

## Next step agreed in chat

Implement a sample conversation draft composer. Store drafts separately per conversation and preserve text through folding and app relaunch. Sending stays disabled. This composer has been proposed but has NOT been implemented. Work one step at a time.

## Build from Warp on this Mac

Android Studio was not detected. The system JDK is 27; use the downloaded JDK 17 for this Gradle version. Temporary build tools are in the original Codex chat workspace. `local.properties` already points to its SDK. Do not delete that toolchain until a durable Android environment replaces it.

```sh
cd /Users/arvinbobis/Programs/sims
export JAVA_HOME='/Users/arvinbobis/Documents/Codex/2026-11-02/referenced-chatgpt-conversation-this-is-an/work/android-toolchain/jdk-17.0.21+9/Contents/Home'
export ANDROID_HOME='/Users/arvinbobis/Documents/Codex/2026-11-02/referenced-chatgpt-conversation-this-is-an/work/android-toolchain/sdk'
export GRADLE_USER_HOME='/Users/arvinbobis/Documents/Codex/2026-11-02/referenced-chatgpt-conversation-this-is-an/work/android-toolchain/gradle-home'
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon :app:assembleDebug :app:lintDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Release: https://github.com/nvrsbb/sims/releases/tag/v0.1.0-preview

GitHub Actions has not been verified. Original source behavior was preserved during import; wrapper, workflow command, README, and local build documentation were added or updated. SMS/MMS transport, default app eligibility, real sending/receiving, backups/restoration, and notifications remain future work.
