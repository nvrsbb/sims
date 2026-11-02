# FoldSMS handoff — 2026-11-02

## Current project

Continue in `/Users/arvinbobis/Programs/sims` (remote: `https://github.com/nvrsbb/sims.git`). The repository is public. Source and persistent drafts were pushed to `main`; inspect status/history for subsequent local changes.

Original starter archive: ignored `sims.zip`. Native Kotlin / Jetpack Compose app, package `com.axiomstead.foldsms`, min SDK 29, compile/target SDK 35. Specification: `docs/MVP_SPEC.md`.

## Working and verified

- Preview uses fabricated conversations only, no Android SMS or internet permissions, no default SMS role request, disabled sending.
- Local debug assembly and wrapper assembly passed. APK signature verified. Lint: 0 errors, 3 warnings (newer activity-compose available, missing Android 12 backup extraction rules, missing application icon).
- User installed the released preview on their only phone, a Samsung Galaxy Z Fold6, and reported search for Alex, Finance category filtering, conversation opening, unfolded split view, and fold/unfold transition all work. These are user-reported device checks, not automated tests.
- Google Messages remains the intended default. Do not enable SMS access or default-role behavior as part of the next milestone.

## Latest milestone and next work

- Persistent sample drafts are implemented and pushed. The owner installed the updated APK and reported drafts stay separate per conversation and survive folding and relaunch. These are device-reported checks, not automated process-death tests.
- GitHub Actions successfully built commit `072cee5` after updating Android SDK setup to v4.
- PR #1 merged the Budge/IBKR palette and Android working agreements. Local main was synced to `eec9632`; PR CI passed. Visual changes still need a phone check.
- CI includes unit tests, lint, and assembly on PRs and main. Agreements are in `AGENTS.md`.
- The owner requested ten feature PRs tracked and completed sequentially. Queue and acceptance checks: `docs/TRACKER.md`. Close each card only after merge; phone checks are tracked separately.
- F-01 adds chronological fabricated histories and the first JVM tests. Persistence/restoration tests remain queued work. Existing draft preference keys stay unchanged.
- F-01 local checks: three JVM tests passed; lint and debug assembly succeeded, with the same three lint warnings. No emulator or physical-device UI check was performed for this feature.
- F-01 merged as PR #2 (`03549e4`). F-02 implements tap-to-open message details and Android clipboard copying on `feature/message-details`.
- F-02 local checks passed: five JVM tests, lint, debug assembly, and UI-test APK assembly. The new clipboard/dismissal Compose test is compiled, not executed; no emulator is installed. CI now compiles UI tests as well. Phone checks remain deferred to a batch.
- F-02 merged as PR #3 (`7a29453`). F-03 is next. E-01 in `docs/TRACKER.md` records emulator setup as a planned engineering follow-up; it has not been implemented.
- Local validation of the visual changes and updated check command: debug assembly and lint succeeded; the unit-test task reported `NO-SOURCE`. No new physical-device verification was performed.
- Continue one focused feature at a time, accumulating changes before another phone installation. Sending stays disabled.

## Build from Warp on this Mac

Android Studio was not detected. The system JDK is 27; use the downloaded JDK 17 for this Gradle version. Temporary build tools are in the original Codex chat workspace. `local.properties` already points to its SDK. Do not delete that toolchain until a durable Android environment replaces it.

```sh
cd /Users/arvinbobis/Programs/sims
export JAVA_HOME='/Users/arvinbobis/Documents/Codex/2026-11-02/referenced-chatgpt-conversation-this-is-an/work/android-toolchain/jdk-17.0.21+9/Contents/Home'
export ANDROID_HOME='/Users/arvinbobis/Documents/Codex/2026-11-02/referenced-chatgpt-conversation-this-is-an/work/android-toolchain/sdk'
export GRADLE_USER_HOME='/Users/arvinbobis/Documents/Codex/2026-11-02/referenced-chatgpt-conversation-this-is-an/work/android-toolchain/gradle-home'
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Release: https://github.com/nvrsbb/sims/releases/tag/v0.1.0-preview

SMS/MMS transport, default app eligibility, real sending/receiving, backups/restoration, and notifications remain future work. Read `AGENTS.md` for development and verification requirements.
