# FoldSMS handoff — 2026-11-05

## Current state

- Owner merged #18–#23. Main baseline is `7f83db9`, including F-01–F-13, SMS companion F-15, KSP and interrupted sample-draft recovery.
- #20 and #21 were owner-merged after their refreshed checks passed. Do not duplicate them.
- The owner reported SMS receiving and sending on the Fold. F-16 MMS receiving/sending is the next priority; this increment covers provider-backed MMS viewing. Preview restore F-14 remains postponed. F-13 preview export remains fabricated-data only and excludes all SMS companion storage.
- Create focused PRs into main; the owner performs every merge. Do not use agents unless requested. Keep updates about outcomes, blockers and verification rather than file-change or agent activity details.
- Telegram is the UI reference: clean conversation lists, readable hierarchy, restrained chrome and direct actions, adapted to native Android/Fold layouts and the shared semantic palette.
- Tests use fabricated data and fake sending only. Sample sending stays disabled. Companion access is opt-in and permission-gated; no default-role prompt or phone installation. Google Messages remains default. Replacement gates: `docs/REPLACEMENT_PLAN.md`.

## Implemented behavior

Histories/details/copy, durable per-conversation drafts, draft indicators/confirmed clearing, saved navigation, full-history search, category overrides, and selected/conversation/app-wide sample-message deletion with persistent latest-operation undo. Confirmations preserve explicit scopes and counts; a new deletion replaces the previous undo. Empty conversation shells and drafts remain.

Main includes deterministic pin ordering, reversible archive, custom conversations with fabricated addresses and unsent drafts, and Light/Dark/Device settings. App-wide deletion includes archived histories and leaves pins, archive flags, custom conversation rows and all drafts intact. Compact scrolling action rows keep controls reachable.

## Storage

- Keep application ID `com.axiomstead.foldsms`, signing identity and legacy `sample_drafts` / `thread_<id>` preference keys.
- Main uses Room metadata schema 4, with explicit 1→2→3→4 migrations for nullable category/pins, archive and custom conversations. Migration and reopen tests run on isolated databases. No destructive fallback.
- Sample deletion stores hidden fixture IDs and latest undo IDs together in a separate preference file. It does not touch Telephony or custom drafts.
- Custom conversations use reserved negative IDs. E-03 is addressed in open PR #21: initial draft preferences and the Room row cannot commit atomically. Reserving negative draft IDs prevents later creation from overwriting an orphan draft.

## Actual verification

- Merged deletion batch: 12 JVM tests, 13 API 35 emulator tests, lint, debug and Android-test assembly passed. All three PR CI runs and merged-main CI passed.
- Integration: 22 JVM tests and 25 API 35 emulator tests passed, including migrations, pin/archive storage, custom conversation validation/reopen, settings/navigation, and app-wide deletion across archived histories while preserving metadata/custom drafts. The final rerun with isolated deletion storage also passed all 25 tests.
- Integration lint: no errors; eight baseline warnings plus one Room testing dependency version-update advisory. The existing backup rules, kapt/KSP and icon warnings remain.
- Separate deletion-batch UI walkthrough: expanded light/dark, narrow 968×2160 at density 320, 1.3× fonts, keyboard visibility/Back. A fabricated draft and conversation-wide deletion survived emulator force-stop/relaunch; undo restored history afterward. Size/font overrides were reset.
- Compose saved-state tests do not prove real activity recreation or system process restoration. The AVD fold command did not change display width; actual fold transitions and Samsung behavior remain unverified. No new phone check or install occurred.
- Integration UI walkthrough: pinning Bank moves it above newer unpinned Alex and exposes Unpin; the scrolling action row, header and composer render at expanded and narrow sizes with 1.3× fonts. Size/font overrides were reset.
- Earlier owner-reported Fold6 baseline: search, category filtering, opening threads, split view, fold/unfold and separate drafts surviving relaunch worked before the accumulated feature queue. Do not extend that evidence to newer features.

## Build and emulator

Use JDK 17 and the Gradle wrapper with SDK platform 35. Android Studio is installed; its API 35 Google APIs ARM64 `Samsung_Fold_6` AVD is available. See `docs/EMULATOR.md` for test setup and its folded-height warning.

The ignored `local.properties` still points to the original temporary SDK. Its parent toolchain directory contains the downloaded JDK 17 bundle and `gradle-home`; use that JDK's `Contents/Home`. Keep these tools until a durable JDK 17 / SDK 35 setup replaces them. Do not commit local machine paths or tools.

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:connectedDebugAndroidTest
git diff --check
```

Confirm the emulator serial before tests; never target the owner's phone or clear its data. APK: `app/build/outputs/apk/debug/app-debug.apk`. A distributed phone batch needs agreed scope, monotonic versionCode, meaningful versionName, verified signing compatibility and a recorded checked commit.

## Next work

Build F-16 MMS receiving and sending in focused reviewed steps, beginning with provider-backed reading, then a validated PDU composer/send journal. Do not install on the owner phone or perform real carrier sends. Preserve existing worktrees. The owner performs every PR merge; never merge or enable auto-merge. Resume F-14 restore after SMS priority; export alone does not complete the backup/restore gate. See `SMS_TRANSPORT.md` for transport limits and follow-ups.

## F-13 verification

Local full wrapper checks passed: 27 JVM tests, lint, debug APK and Android-test APK. The Unicode regression reproduced a lossy isolated-code-unit write and now passes with JSON escapes. Three local baseline lint warnings remain: backup rules, application icon and kapt (addressed separately by #20). No Room schema change. Initial hosted execution passed 29/30 tests and exposed a mutable-reference writing checkpoint during Compose restoration. An explicit Boolean saver now freezes that checkpoint before cancellation cleanup; the unchanged regression then passed in hosted CI (30 Android tests total). Hosted checks use the #19 stack; the current cloud host has no KVM. Document streams report success only after close, chooser cancellation does not capture/open data, and interrupted UI state permits retry. Source preferences and Room data are read-only during export. Actual activity/process death and physical Fold6 behavior remain separate from Compose saved-state tests.

## F-15 implementation handoff

Feature branch `feature/sms-companion`, isolated worktree, based on owner-merged main `83f7663`. Settings exposes opt-in inbox/composer without requesting default role. Separate composer preferences and Room journal schema 1 leave preview metadata schema 4 and draft keys intact. Consent/permission/default/SIM gates are rechecked at handoff; no default-SIM fallback. No outgoing body copies in journal. Pending or partial attempts never resend automatically. Restricted SMS grants depend on installer allowlisting; fail closed without a bypass. Hosted checkpoints are recorded below and in PR checks; do not claim carrier or device readiness. No native carrier-send API is used by tests.

F-15 local checks passed: 31 JVM tests, lint (three existing warnings), debug and Android-test APK assembly. Initial hosted Android execution ran 47 tests: 45 passed, one failed at the first simulated-commit assertion, and the dedicated smoke was skipped in the ordinary suite. Fluent delegation in the test wrapper returned the real editor, bypassing simulated failure; the wrapper now retains chaining and returns failure after a memory update. Assertions remain unchanged. Current rerun and dedicated smoke results are in PR #23 checks. #21 was retargeted from the merged #19 branch to main; its implementation was not duplicated.

The owner requested conflict resolution for all open PRs. Existing branches #20 and #21 were refreshed from main `a03c7ac` without rewriting history. Only tracker/handoff documents conflicted; preserve the current SMS milestone alongside each original feature. KSP stays in #20 and recovery stays in #21. No PR is merged by the agent.

F-15 code head `262b90d`: hosted build and all 46 ordinary Android tests passed (guarded smoke skipped in that phase). Dedicated smoke stopped before permission granting because Gradle had uninstalled the packages; CI setup now reinstalls the matching build APKs on its disposable emulator. Rerun results are in PR checks.

## Open PR conflict refresh — 2026-11-05

F-15 head `3c00024` passed build, all 46 ordinary Android tests and the separate native-provider synthetic inbound SMS smoke before owner merge. Carrier sends remain untested; all outgoing tests use fakes.

#20 retains Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / Room 2.6.1. Compare all exported preview schemas and the new SMS journal schema against main; no migration or identity change is intended. #21 retains cancellation-after-draft-commit recovery, original reserved negative IDs/text, duplicate/missing-draft guards and restored-form tests. No Room migration or ordinary typing change is intended. The refreshed branches retain preview export and opt-in SMS unchanged. Local and hosted refresh verification is tracked in each PR; prior-head results alone do not verify the refresh. Physical Fold6 and actual process-death checks remain separate.

Conflict-refresh local verification: both branches passed 31 JVM tests, lint, debug APK and Android-test APK assembly. Exported preview schemas 1–4 and SMS journal schema 1 match main exactly. #20 has two existing lint warnings (backup rules and icon); #21 retains those plus the separate kapt advisory. Hosted refreshed-head Android execution is tracked in the PR checks; no phone or carrier send was performed.

## F-16 MMS handoff — 2026-11-06

The owner reported live SMS receive/send. MMS is the next authorized milestone, with Google Messages remaining default. See `MMS_TRANSPORT.md`. The current receive branch adds a permission-gated, read-only provider view and image preview; the send path is not implemented in this slice. Full MMS readiness requires text/image/video/audio send and receive, a maintained licensed PDU composer, explicit SIM, durable callbacks, native provider checks, carrier two-number trial, backup/restore and fallback testing. No phone installation or carrier send was performed during development.

Receive slice PR [#24](https://github.com/nvrsbb/sims/pull/24) is open. Local checks passed: 31 JVM tests, lint, debug APK, Android-test APK and diff whitespace. Hosted API 35 interaction checks remain pending; do not mark F-16 merged or fully received. The next focused step is PDU composer feasibility and outbound MMS design, preserving explicit SIM and nonautomatic retries.
