# FoldSMS handoff — 2026-11-02

## Current session — 2026-11-04

- Owner performs all merges; create focused PRs only. Keep updates about outcomes, blockers and verification rather than file-change or agent details. Use no agents unless requested.
- Telegram is the UI reference. Replacement backlog corrections and milestone gates are in `docs/REPLACEMENT_PLAN.md`.
- Actual main at session start is `c2e1732` and contains F-01–F-06. F-07–F-10 were merged into stacked branches, not main; integration remains pending. Historical “PR open” notes below are stale; verify target branches and main ancestry before calling work delivered.
- Android Studio and dedicated Fold emulator are installed. See `docs/EMULATOR.md`. Baseline local unit/lint/debug/Android-test assembly passed (8 JVM tests; 8 existing lint warnings). Emulator suite now passes all 9 tests after correcting merged Card badge selectors and waiting for keyboard dismissal before asserting conversation Back. These are Compose restoration checks, not actual process-death tests.
- Tonight's intended PRs: emulator/roadmap readiness, F-11 selected sample-message deletion, F-12 conversation/app-wide sample-message deletion. No SMS permissions, role requests, real sending or phone installation are authorized by this batch.

## F-11 — selected sample-message deletion

Implementation uses explicit selection and confirmation with conversation/count, persistent hidden fixture IDs, latest-deletion undo across store reopening, empty histories and unchanged draft/category storage. Undo is available until a new deletion replaces it. No real message provider operations. Verification: 11 JVM tests, lint, debug and Android-test assembly, and all 12 instrumentation tests on the API 35 Fold emulator pass. Tests cover restored selection, conversation switching, cancellation, mixed directions, persistence/reopen, undo and a simulated failed storage commit. Eight existing lint warnings; no new lint errors. Actual process-death, activity recreation and phone fold checks remain pending. PR #15 readiness CI is green; owner merges.

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
- The owner requested message selection/deletion and delete all messages. F-11 and F-12 now track these explicitly, including conversation-wide and app-wide scopes, confirmation counts, sample-data undo, and persistence. Real-message deletion remains in the future SMS milestone. These are planned, not implemented.
- Local validation of the visual changes and updated check command: debug assembly and lint succeeded; the unit-test task reported `NO-SOURCE`. No new physical-device verification was performed.
- Continue one focused feature at a time, accumulating changes before another phone installation. Sending stays disabled.

## Review batch — 2026-11-02

Owner instructions for this batch: continue from F-03 using independent agents and stacked PRs; never merge. All data stays fabricated and sending disabled. PR #5 already proposes F-11/F-12; leave it for review rather than duplicating it.

Cloud verification uses JDK 17 and Android SDK 35 with writable Android user/cache directories and in-process Kotlin compilation. Keep signing keys outside source; do not install this environment’s debug build over the phone without checking signing compatibility. No emulator/device is attached; Android UI/storage tests compile only. Actual recreation, process death, relaunch, fold transitions, large fonts, keyboard/Back, and phone update checks remain pending. E-01 remains planned.

- F-03: Saved-draft badges and a confirmed, conversation-scoped clear action preserve the original draft keys and exact text. Isolated preference and Compose coverage was added; long drafts have a bounded composer. Local JVM/lint/debug/Android-test assembly passed; instrumentation unexecuted. Status: PR open, awaiting owner review; never merged.

- F-04: Saveable navigation retains selected thread, filters, search, and per-thread/inbox/category-strip scroll state; cover Back returns to the inbox after keyboard dismissal. Restoration tests cover nondefault selection and width changes. Fresh relaunch intentionally opens the inbox; actual activity/process/fold checks remain pending. Local JVM/lint/debug/Android-test assembly passed; instrumentation unexecuted. Status: PR open, awaiting owner review; never merged.

- F-05: Search covers senders, fictional reserved phone addresses, and every sample message body, with matching snippets, no-results copy, and a clear action. Tests cover older messages, case/whitespace/number formatting, category combinations, and clearing. Local JVM/lint/debug/Android-test assembly passed; instrumentation unexecuted. Status: PR open, awaiting owner review; never merged.

- F-06: Room schema 1 stores category overrides and reset behavior without moving legacy drafts. Category changes immediately feed the combined search/filter list. Isolated storage/reopen and category/filter UI tests were added. Lint has two existing warnings plus a new kapt-versus-KSP advisory; there are no lint errors. Local JVM/lint/debug/Android-test assembly passed; instrumentation unexecuted. Status: PR open, awaiting owner review; never merged.

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
