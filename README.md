# FoldSMS — private Android SMS replacement prototype

Version 0.1 is a sample inbox for a Samsung Galaxy Z Fold6. It cannot read, send, receive, modify, or delete real messages. It has no SMS or network permissions and does not request the default SMS role.

## Current preview

- Fabricated conversations, search, and category filters.
- Conversation selection and editable drafts saved per conversation; sending disabled.
- Single pane below 600dp available width and split pane at 600dp or wider.
- No backend, account, analytics, or cloud backup.

Read [the MVP specification](docs/MVP_SPEC.md) for future milestones and the requirements before attempting a real SMS replacement.

Follow the [feature tracker](docs/TRACKER.md) for the next ten feature PRs, their completion checks, and separate phone validation status.

## Build on this Mac

Repository: `/Users/arvinbobis/Programs/sims`

Use JDK 17, Android SDK platform 35, and Build Tools 34.0.0. The Gradle wrapper pins Gradle 8.10.2 and verifies its distribution checksum. Open this folder in Android Studio, select JDK 17 for Gradle, and let Studio locate the SDK. Alternatively set `JAVA_HOME` to JDK 17 and configure `sdk.dir` in ignored `local.properties`, then run:

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

This session's temporary build tools live in the originating Codex chat's `work/android-toolchain` directory. `local.properties` points there. A durable Android Studio installation can replace that path later; do not commit local machine paths, debug signing keys, or SDK files.

## GitHub build

The configured repository is `https://github.com/nvrsbb/sims.git`. The **Android debug APK** workflow runs unit tests, Android lint, and debug assembly on pull requests and pushes to `main`, with JDK 17 and the wrapper. Its **FoldSMS-debug-apk** artifact contains the APK. Local build success does not verify a GitHub Actions run. A unit-test task reporting `NO-SOURCE` means no unit tests ran.

CI also compiles the Android UI-test APK (`:app:assembleDebugAndroidTest`). This does not execute UI tests; use `:app:connectedDebugAndroidTest` on a dedicated test emulator/device for that. UI tests use fabricated data.

See [the working agreements](AGENTS.md) for development, review, merge, and phone update practices. Features can be checked individually, then installed on the phone as a batch.

## First Fold6 check

1. Keep Google Messages as the default SMS app.
2. Install only the preview APK and open **FoldSMS Preview**.
3. Check search, filters, opening a conversation, and the in-app Back button on the cover screen.
4. Unfold and check that the inbox and conversation fit side by side. Also check text at your normal font size and in multi-window mode.
5. Confirm that sending stays disabled and that no SMS permission or default-app prompt appears.

Device launch and fold behavior require a physical-device check. Do not treat this prototype as a functioning messenger. Before any future default-app trial, complete the backup, restore, two-way SMS/MMS, notification, and rollback gates in the specification.
