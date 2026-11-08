# FoldSMS feature tracker

Batch 1: the original ten feature PRs plus two requested message-deletion features for the sample-data milestone. Sample content remains fabricated and its sending stays disabled. Owner authorized opt-in SMS companion receiving/sending on 2026-11-05; no default-role request or phone installation. Current owner instruction (2026-11-04): focused PRs only; the owner performs all merges. No agents unless requested. Follow dependencies, keep each PR focused, and never merge during this review batch. Each item gets appropriate tests, local checks, independent review, and CI. Unexecuted Android tests and phone checks remain explicit.

Status: **Planned → In progress → PR open → Merged**. Close an item only after its PR is merged. Track phone validation separately; merging does not imply a device check. Include this file's status update in each feature PR; confirm merge state in the next PR or handoff. A postponed item needs a reason.

| ID | Feature / proposed PR | Status | PR | Phone check |
|---|---|---|---|---|
| F-01 | Chronological sample message history | Merged | [#2](https://github.com/nvrsbb/sims/pull/2) | Pending |
| F-02 | Message details and copying sample text | Merged | [#3](https://github.com/nvrsbb/sims/pull/3) | Pending |
| F-03 | Draft indicators and deliberate draft clearing | Merged | [#6](https://github.com/nvrsbb/sims/pull/6) | Pending |
| F-04 | Restore conversation navigation and Android Back | Merged | [#7](https://github.com/nvrsbb/sims/pull/7) | Pending |
| F-05 | Search across complete sample histories | Merged | [#8](https://github.com/nvrsbb/sims/pull/8) | Pending |
| F-06 | Change and remember conversation categories | Merged | [#10](https://github.com/nvrsbb/sims/pull/10) | Pending |
| F-07 | Pin important conversations | Merged | [#18](https://github.com/nvrsbb/sims/pull/18) | Pending |
| F-08 | Archive and restore conversations | Merged | [#18](https://github.com/nvrsbb/sims/pull/18) | Pending |
| F-09 | Start a new local sample conversation | Merged | [#18](https://github.com/nvrsbb/sims/pull/18) | Pending |
| F-10 | Preview settings and safety information | Merged | [#18](https://github.com/nvrsbb/sims/pull/18) | Pending |
| F-11 | Select individual messages and delete selected | Merged | [#16](https://github.com/nvrsbb/sims/pull/16) | Pending |
| F-12 | Delete all messages | Merged | [#17](https://github.com/nvrsbb/sims/pull/17) | Pending |
| F-13 | Export local preview data | Merged | [#22](https://github.com/nvrsbb/sims/pull/22) | Pending |
| F-14 | Validate and restore a preview export | Postponed: owner prioritized SMS | — | Pending |
| F-15 | Opt-in SMS receiving and sending companion | Merged | [#23](https://github.com/nvrsbb/sims/pull/23) | Owner reports SMS receive/send; other checks pending |
| F-16 | Opt-in MMS receiving/sending companion | Receive slice merged; sending pending | [#24](https://github.com/nvrsbb/sims/pull/24) | Pending |
| F-17 | Put device SMS/MMS in main preview; remove companion screen | Superseded by F-18 | [#27](https://github.com/nvrsbb/sims/pull/27) | Pending |
| F-18 | Operational Chats tab: real conversations, reply, new message, pin/archive/categories; Telegram-style UI; shared CI signing key; Device tab removed (0.4.0) | Merged | [#37](https://github.com/nvrsbb/sims/pull/37) (stack #28–#35), reply-box fix [#38](https://github.com/nvrsbb/sims/pull/38) | Hosted emulator tests green; owner reported the reply box missing before #38. Fold layouts, large fonts, upgrade over the installed build and a real send remain to check on the phone |
| F-19 | Owner phone check of 0.4.x: fold/unfold, large fonts, keyboard, Back, install over the old build, one real SMS to the owner’s own number | Next | — | Pending |
| F-20 | App icon and proper app name; resolve the backup-rules lint warning | In review | chore/icon-name-lint | Adaptive icon (speech bubble with a fold, themed-icon version), launcher name "FoldSMS". Backup rules added for Android 11 and older (`fullBackupContent`) and 12+ (`dataExtractionRules`) (nothing leaves the phone through backup or transfer, as before); lint now reports only dependency-update notices, left alone on purpose. Not verified: how the icon looks on the phone launcher |
| F-21 | Archived real conversations return to the inbox when a new message arrives | In review | feature/archive-returns | Room schema 6 adds `archivedAtDate` (tested 5→6 migration). A message received after the archive date brings the conversation back; our own replies never do; archives made before this change stay archived until restored. Not verified on a phone with real threads |
| F-22 | Search inside a conversation; delivery/failure status shown in the thread | In review (search and send status); delivery reports not done | feature/thread-search-and-status | Search icon in the conversation header: highlights matches, "k/n", arrows to older/newer, Back closes it. Under our own messages: "Failed" and "Sending…" always, "Sent" on the newest sent message. Carrier delivery reports ("Delivered") would need delivery receipts on every send and are not included; carriers do not all provide them |
| F-23 | Contact names for numbers and choosing a contact as the recipient | In review (owner authorized contacts access 2026-11-07) | feature/contacts | Optional READ_CONTACTS, asked with the other permissions; names shown in the list, conversation, search and notifications; New message finds contacts by name or number. Denying contacts changes nothing else. Not verified on a phone: the permission dialog and a real contact list |
| F-24 | One unsent SMS draft per conversation | Planned: needs a journal/revision redesign first | — | Pending |
| F-25 | Delete or mark-as-read real messages, notifications | Blocked: needs the default-SMS role, not authorized | — | Pending |
| F-26 | Reduce emulator-test flakiness in CI | Merged ([#49](https://github.com/nvrsbb/sims/pull/49)); root cause of the retries still open | [#36](https://github.com/nvrsbb/sims/pull/36) fails a hung test after three minutes; this change retries a failed test once and widens waits to 30 s | About one random test failure per hosted run before this change (different test each time, mostly 15 s waits for the inbox, passing on re-run). Retried tests are listed in the CI output and `flaky-retries.txt`; a test that needs a retry repeatedly should be fixed, not hidden. New test classes need `@RunWith(RetryRunner::class)` |
| F-27 | Small follow-ups from the lean app: launcher name "FoldSMS Preview" → "FoldSMS" (done in F-20); `AGENTS.md` and README wording (done, with the 2026-11-07 authorizations recorded); tune the automatic category rules from real-world misses | Rules tuning waits for real examples of misses from the owner | chore/icon-name-lint | Category rules: pending |
| F-28 | Review the owner’s `argon-grok-backlog.md` against the product boundary and fold what fits into this plan | Waiting: the file has not been supplied in any session | — | Pending |
| F-29 | Optional later features from `REPLACEMENT_PLAN.md` phase 5: automated rules, scheduling, WebDAV, scam detection, widgets, S Pen refinements | Later | — | Pending |
| F-30 | **Final step: ask the user to make FoldSMS their default message app** | Last on the list; blocked until the owner authorizes a default-role request and the replacement gates pass (see CONTINUE "Next work") | — | Pending |
| F-31 | Select conversations (long press, menu "Select messages", Select all) and mark read, mark unread or delete them | In review (owner request 2026-11-07) | feature/select-and-unread | Only offered while FoldSMS is the default app; delete asks first. Mark unread flips the newest received message of each conversation. Not verified on the provider: unread/read/delete on real threads, large selections |
| F-32 | "Show unread only" filter in the three-dot menu | In review (owner request 2026-11-07) | feature/select-and-unread | Works with search, categories and the archive; the open conversation stays listed after it is read |
| F-33 | Send one-part replies on tap (owner request 2026-11-07); review dialog kept for new recipients, multi-part messages and possible duplicates | In review | feature/send-without-review | Journal, one attempt per draft revision and no-auto-resend unchanged. Not verified: real carrier send, accidental taps in daily use |
| F-34 | Two categories only, Personal and Business (owner request 2026-11-07); change the category of several conversations at once | In review | feature/two-categories | Business covers promotions, services, finance and one-time codes. Saved contacts and numbers you replied to are Personal; name senders and short codes are Business; commercial-looking text from a mobile number is Business. Older stored categories read as Business, nothing migrated. Rules tuned on invented examples only; needs real misses from the owner (also F-27) |
| F-35 | One-time-code handling, first batch (owner request 2026-11-07): detect the code; Copy code in the notification, in the conversation list and in the bubble; a "Latest code" card; clipboard marked sensitive and cleared after 60 s; the conversation is marked read when you copy | In review (0.8.0) | feature/otp-copy | Copy buttons in the conversation only for codes under 24 hours old. Later in the brainstorm: auto-delete old codes, keyboard suggestion check, widget, phishing warning. Not verified on a phone: the notification button, clipboard clearing from the background, how well real messages are recognised |
| F-36 | Production readiness #1: read all messages, not only the newest 200 SMS and 100 MMS | In review | feature/page-through-all-messages | First load reads 1,000 SMS and 200 MMS; scrolling to the end reads three times more until the provider is exhausted; an opened conversation always loads its whole history; searching offers "Search all messages" which reads everything. Not verified on a phone with a very large inbox (memory, speed). Remaining readiness list: phone test matrix, release build, backup/export, direct reply and blocking, MMS sending (needs authorization), accessibility |
| F-37 | Production readiness #2: export all SMS and MMS to a file (read-only backup) | Merged; checked on the owner's phone (2026-11-07) | feature/export-messages | Settings → Export messages to a file; choose messages only or with pictures; the owner picks the file (no storage permission); XML in the SMS Backup & Restore layout (restore by other apps unverified); progress and cancel; an incomplete file is removed. Not built: restore into FoldSMS (needs provider writes, separate and riskier). Phone check: the file picker works and an inbox of 4,019 SMS exported. Not yet verified: MMS attachments in a large inbox, the time it took, opening the file elsewhere |
| F-38 | Production readiness #3: a real release build (not debuggable, library code and resources shrunk, same signing key) | In review | feature/release-build | Release APK is about 3 MB against 26 MB for debug. Names are kept (no obfuscation) so crash logs stay readable; our own code is not shrunk. CI builds it, prints its certificate, uploads `FoldSMS-release-apk` and runs a smoke test on the emulator (not debuggable, starts, welcome, Allow access leads to the inbox, no crash). The full emulator suite still runs against the debug build only: running it against shrunk libraries needs a separate build type and is not done. Not verified on a phone: everything beyond the smoke test, so use the release APK for the next phone test pass |
| F-39 | Production readiness #4: block a number from a conversation, unblock in the conversation or Settings | Merged; list checked on the owner's phone (2026-11-07) | feature/block-numbers | Uses the phone's own block list (the one the Phone app uses), which Android applies before anything reaches an app, so texts and calls from the number stop. Only plain phone numbers, only while FoldSMS is the default app. Phone check: blocking, the blocked-numbers list and unblocking work. Not yet verified: that texts and calls from a blocked number really stop (needs a second phone). Still open from the readiness list: reply from the notification, MMS sending (needs authorization), accessibility pass |
| F-40 | Tapping a notification opens that conversation (was: opened the app on its list) | In review | fix/notification-opens-conversation | Found by the owner on the phone (2026-11-07). Each sender now has its own PendingIntent, so notices no longer share one intent; the app opens the conversation with that sender, number or name, archived or not; an unknown sender leaves the list. Not verified on a phone yet |

F-07–F-10 reached main through merged integration #18. Its 22 JVM tests and 25 emulator tests passed. No new phone checks are implied. #19 (hosted CI) and #22 (export) reached main at `83f7663`. #20 KSP and #21 interrupted-draft recovery remain separate; do not duplicate them.

## F-01 — Chronological sample message history

Show several incoming/outgoing fabricated messages with timestamps and explicit sample status. Move fixtures out of the activity; keep the existing conversation IDs so saved drafts still load. Derive inbox previews from the latest message.

Completion: histories sort consistently; previews match the latest message; conversations share no message IDs; message history scrolls with the draft composer reachable. Unit tests cover ordering, ties, previews, and fixture separation. Phone check: Alex and a service conversation, switching threads, keyboard, folding, and a pre-existing draft.

Local verification: three JVM tests passed, Android lint and debug assembly succeeded. Android UI/device behavior has not been verified for this feature; its phone check remains pending.

## F-02 — Message details and copying sample text

Open a selected message's details with sender, full timestamp, direction, and clearly fabricated status; copy its text using Android clipboard handling. No fake real delivery or SIM guarantees.

Completion: details belong to the tapped message; copying uses that message's body; dismissing returns to the same conversation and draft. Android UI checks cover selection/copy/dismiss. Depends on F-01.

Implementation: tap a message for a native bottom sheet with sender, full Manila timestamp, direction, sample disclaimer, Copy text, and Close. Local verification: five JVM tests pass; lint, debug APK, and UI-test APK assembly pass. Clipboard/dismissal Compose test compiled but was not executed: no local emulator installed. Phone interaction and draft-preservation checks remain pending.

## F-03 — Draft indicators and deliberate draft clearing

Show which inbox conversations have a saved draft and offer an explicit clear action for the active draft. Empty drafts remove their indicator. Preserve the existing preference keys and text.

Completion: two conversations retain independent drafts; clearing affects only the chosen draft; empty and multiline text behave correctly; relaunch restores indicators. Test storage behavior with isolated Android preferences and UI behavior with Compose tests.

Implementation and local verification: Saved-draft badges and a confirmed, conversation-scoped clear action preserve the original draft keys and exact text. Isolated preference and Compose coverage was added; long drafts have a bounded composer. JVM tests, lint, debug APK, and Android-test APK assembly passed. Original checks were build-only; see the current integration verification above. Phone checks remain pending.

## F-04 — Restore conversation navigation and Android Back

Keep selected thread, inbox/conversation position, search, and category filter through activity recreation. Android Back from a cover-screen conversation returns to the inbox; handle keyboard Back naturally. Unfolding/folding keeps the intended conversation.

Completion: restoration tests cover a non-default conversation and filters; Back does not unexpectedly exit from a cover conversation. Report recreation, relaunch, and process-restoration checks separately. Draft contents remain in durable storage.

Implementation and local verification: Saveable navigation retains selected thread, filters, search, and per-thread/inbox/category-strip scroll state; cover Back returns to the inbox after keyboard dismissal. Restoration tests cover nondefault selection and width changes. Fresh relaunch intentionally opens the inbox; actual activity/process/fold checks remain pending. JVM tests, lint, debug APK, and Android-test APK assembly passed. Original checks were build-only; see the current integration verification above. Phone checks remain pending.

## F-05 — Search across complete sample histories

Search sender and every message body, not only the latest preview. Add fabricated addresses to support number search. Show useful result snippets, a no-results state, and a clear-search action; respect category filtering.

Completion: older-message matches open the correct conversation; whitespace/case/number formatting are handled deliberately; clearing recovers the list. Unit tests cover combined filters and matches. Depends on F-01 and F-04.

Implementation and local verification: Search covers senders, fictional reserved phone addresses, and every sample message body, with matching snippets, no-results copy, and a clear action. Tests cover older messages, case/whitespace/number formatting, category combinations, and clearing. JVM tests, lint, debug APK, and Android-test APK assembly passed. Original checks were build-only; see the current integration verification above. Phone checks remain pending.

## F-06 — Change and remember conversation categories

Let the owner assign Personal, Finance, Services, Promotions, OTP, or Unknown to a sample conversation. Store category overrides in local metadata, with a small Room foundation for later pins/archive and migration tests. Read existing draft preferences unchanged; no draft migration is required for this feature.

Completion: category changes persist and immediately affect filtering; resetting restores the sample default; unrelated drafts survive. Test database reads/writes with isolated storage and Compose filter behavior. Do not add automatic categorization rules in this PR.

Implementation and local verification: Room schema 1 stores category overrides and reset behavior without moving legacy drafts. Category changes immediately feed the combined search/filter list. Isolated storage/reopen and category/filter UI tests were added. Lint has two existing warnings plus a new kapt-versus-KSP advisory; there are no lint errors. JVM tests, lint, debug APK, and Android-test APK assembly passed. Original checks were build-only; see the current integration verification above. Phone checks remain pending.

## F-07 — Pin important conversations

Pin/unpin a conversation and list pinned conversations first, preserving deterministic order within both groups. Store the choice in the local metadata from F-06; keep search and category filters consistent.

Completion: pin state survives relaunch; filters do not resurrect excluded conversations; pinning does not change message timestamps or drafts. Unit tests cover ordering, and Android tests cover persistence. Depends on F-06.

Implementation and local verification: Pins persist in schema 2 via an explicit category-preserving migration. Ordering is deterministic and respects search/category exclusions. Conversation actions scroll horizontally; restoration tests retain a nonzero history scroll assertion. JVM tests, lint, debug APK, and Android-test APK assembly passed. Original checks were build-only; see the current integration verification above. Phone checks remain pending.

## F-08 — Archive and restore conversations

Move a sample conversation to a separate Archive view and restore it. Archive is reversible and retains message history, category, pins, and drafts. No deletion feature.

Completion: archived items leave the normal inbox and remain reachable in Archive; restoring recovers their metadata and draft; an empty Archive is understandable. Test visibility rules and persistence. Depends on F-06 and F-07.

Implementation and local verification: Archive/restore is reversible and retains history, category, pins and drafts. Schema 3 explicitly migrates archive flags. Archive exclusion precedes pin ordering and complete-history/category search. All conversation actions share one horizontally scrollable row. JVM tests, lint, debug APK, and Android-test APK assembly passed. Original checks were build-only; see the current integration verification above. Phone checks remain pending.

## F-09 — Start a new local sample conversation

Create a conversation using a locally entered sample name/address and draft text, with a clear preview explanation. Do not access Android contacts or messaging intents. Persist the custom sample conversation locally; do not fabricate sent messages from the draft.

Completion: input validation, repeated-address handling, stable IDs without fixture collisions, saved draft, relaunch, category assignment, and navigation work. Test validation and persistence. Depends on F-03, F-04, and F-06.

Implementation and local verification: Create local conversations from explicitly fabricated name/address input, with an unsent saved draft and no synthetic message history. Schema 4 adds custom rows with noncolliding reserved negative IDs and normalized unique addresses. Initial drafts save durably before row commit; existing draft keys stay unchanged. E-03 tracks interrupted-creation recovery. JVM tests, lint, debug APK, and Android-test APK assembly passed. Original checks were build-only; see the current integration verification above. Phone checks remain pending.

## F-10 — Preview settings and safety information

Add a native settings screen with remembered Light/Dark/Device theme selection, privacy/storage explanation, and a factual preview capabilities checklist. Explain the future default-SMS milestone without requesting the role. Keep navigation and drafts intact.

Completion: theme choice survives relaunch; Device follows Android; settings return to the active thread; no permissions/role prompts. Test preference mapping and navigation; check contrast, large text, and both Fold layouts. No backup/restore implementation in this PR.

## F-11 — Select individual messages and delete selected

Let the owner enter message-selection mode in a conversation, select/deselect individual messages, see the selected count, and delete only those selected messages. This concerns messages, not just conversation selection. Use local sample data initially; real Telephony-provider deletion requires the later authorized SMS milestone.

Completion: confirmation identifies the conversation and selected message count; cancelling changes nothing; deletion updates history and inbox preview and survives relaunch. Unselected messages and drafts remain intact. Provide undo that restores the deleted sample messages. Test selection, cancellation, mixed incoming/outgoing deletion, last-message deletion, undo, and persistence. Depends on F-01 and F-06.

F-11 implementation: explicit selection mode, count and conversation confirmation, per-message toggles, cancellation, empty history, and durable latest-deletion undo. A new sample-only preference file stores hidden fixture IDs and undo IDs together without migrating drafts or Room. A new deletion replaces the previous undo, stated in confirmation. Verification results are recorded in CONTINUE.md; physical Fold checks remain pending.

## F-12 — Delete all messages

Add explicit actions to delete every message in the current conversation and, separately, every message across the app. Label each scope clearly; an active search/filter must not silently redefine what “all” means. Initially this affects only local sample histories; deleting real SMS/MMS belongs to the later authorized role milestone.

Completion: confirmation states the exact scope and message count; cancellation preserves everything. Confirmed deletion survives relaunch and updates all affected histories/previews; empty conversations remain understandable and drafts remain intact. Provide undo for sample deletion. Test current-conversation versus app-wide deletion, filtered views, empty state, undo, and persistence. Depends on F-11. No automatic deletion or background cleanup.

Implementation and local verification: Native preview settings retain Light/Dark/Device appearance locally and explain fabricated histories, device-local storage and disabled transport. Settings overlays the live conversation so navigation, drafts and list state remain composed. Theme mapping has JVM coverage; isolated preference/navigation Compose tests compile only. JVM tests, lint, debug APK, and Android-test APK assembly passed. Original checks were build-only; see the current integration verification above. Phone checks remain pending.

## F-13 — Export local preview data

Save a versioned UTF-8 JSON snapshot using Android’s CreateDocument picker after explicit confirmation. Include all fabricated histories (including hidden samples), custom rows, category/pin/archive metadata, exact drafts including unattached negative IDs, deletion/undo state and theme. Explain that the file is unencrypted and cannot currently be imported. No real SMS/MMS access, new permissions, sending, migration or source writes.

Verification at PR opening: 27 JVM tests and full lint/APK checks passed; five new Android tests compile locally and await hosted CI. No Room schema change.

Completion: structured fields/text survive JSON parsing; storage iteration order is deterministic; capture leaves isolated stores unchanged; cancellation does not read/write; failed write/close never reports success; recreation while writing allows retry and warns about an incomplete file. Export is a prerequisite to, not completion of, replacement backup/restore.

## F-14 — Validate and restore a preview export

Status: **Postponed** because the owner prioritized receiving/sending SMS for the next PR; depends on merged F-13. Validate format/version, size, all types, IDs and references before any writes; reject truncated/unsupported files. Preview affected data and require explicit confirmation. Define conflict handling and a durable journal for interrupted Room/preference application, including rollback/reconciliation tests. Prove complete round-trip, cancellation, duplicate/invalid data, failure/relaunch and preservation of unrelated drafts. No real Telephony restore in this feature.

## Engineering follow-ups

F-12 implementation: separate conversation and app-wide menu actions, with immutable confirmation targets and counts from the complete dataset before search/category filtering. Both scopes preserve drafts and conversation shells, retain durable latest-operation undo, and disable deletion for an empty scope. Verification results are in CONTINUE.md.

### E-01 — Set up an Android emulator for interaction tests

Status: **Merged** via [#15](https://github.com/nvrsbb/sims/pull/15). Android Studio AVD API 35 installed and running; all 9 baseline instrumentation tests pass, including message details/copy/dismiss. Corrected nested badge selectors and keyboard-dismissal synchronization. See `docs/EMULATOR.md`. Schedule separately from the ten feature PRs.

Set up a dedicated emulator with fabricated data so Compose interaction tests can run without another Fold6 installation. Document reproducible setup and test commands. Keep its data separate from the owner's phone and drafts.

Completion: run `:app:connectedDebugAndroidTest` successfully, including the message details/copy/dismiss test; record emulator API/device configuration and results. Support narrow and expanded layouts where practical. Physical Fold6 checks remain necessary for actual folding and Samsung-specific behavior.

### E-02 — Replace Room kapt processing with compatible KSP

Status: **Merged** — [#20](https://github.com/nvrsbb/sims/pull/20). F-06 introduced the `KaptUsageInsteadOfKsp` lint advisory; the existing backup-extraction and application-icon warnings remain separate. Keep the current verified Kotlin/Room versions stable for this batch, then evaluate a compatible KSP setup and compare generated schemas before changing processors. No runtime failure was observed.

### E-03 — Recover interrupted sample-conversation drafts

Status: **Merged** — [#21](https://github.com/nvrsbb/sims/pull/21), with 28 hosted Android tests passed. F-09 writes the initial draft durably before committing its Room conversation row. Cancellation or database commit failure between those stores can leave an unattached draft; reserved negative IDs prevent later creation from overwriting it. Add a targeted cancellation regression and a deliberate recovery path. Ordinary typing retains the existing draft preference keys and behavior.

## Batch phone check results

Choose a useful subset or the completed batch before installing. Record version/commit and results here, keeping automated checks separate from owner-reported checks. Advance the app version for the distribution batch and verify signing compatibility before suggesting installation.

Existing baseline: the owner reported draft isolation, fold/unfold, and relaunch working before this queue. PR #1's visual changes have not yet been checked on the phone.

### E-04 — Run Android interaction tests in CI

Status: **Merged** — [#19](https://github.com/nvrsbb/sims/pull/19). Hosted build and all 25 baseline Android tests passed. The job uses isolated storage, report artifacts, bounded runtime and cancellation. Local cloud KVM is unavailable. No phone data or sending.

## F-15 — Opt-in SMS receiving and sending companion

Status: **Merged** through owner-merged [#23](https://github.com/nvrsbb/sims/pull/23), main `a03c7ac`. Owner authorized this priority on 2026-11-05. Settings opens a separate permission-gated device SMS inbox and durable composer. Read the latest 200 provider rows without inserts, mark-read writes or body/time deduplication; observe changes and refresh after incoming SMS. Keep an external default app for MMS, notifications and system persistence. No default-role request.

Sending requires an active explicit SIM, valid number, intact multipart encoding (maximum ten parts), and confirmation of recipient, body, SIM and carrier charges. Separate durable per-part journal prevents duplicate dispatch per draft revision and automatic retries. Uncertain/partial outcomes retain text; another attempt requires an explicit duplicate-charge warning and review. Late callbacks cannot clear newer edits. All test sends use a fake platform. Preview export excludes real SMS, its composer and journal.

Verification checkpoints and current hosted results are in PR #23. Remaining device gates: installer-dependent restricted permission grants, real SIM/carrier send callbacks, process death and Fold6 checks. Carrier and owner-phone tests are not authorized here. See `SMS_TRANSPORT.md` for limits and follow-ups.

F-15 local verification: 31 JVM tests, lint, debug APK and Android-test APK assembly passed. Only three baseline lint warnings (backup rules, icon, kapt) remain. Existing preview schemas are unchanged; new SMS journal schema 1 is separate. Android tests compile; initial hosted execution ran 47 tests (45 passed, one failed in the fluent fault-injection wrapper, one guarded smoke skipped). The wrapper is corrected; the assertions remain unchanged. See PR checks for the rerun and dedicated native receive smoke results.

Conflict refresh: existing PRs #20 and #21 incorporate main `a03c7ac` without rewriting shared history. Only tracker/handoff documentation conflicted. Preserve KSP in #20, recovery in #21, and main’s preview export/SMS behavior in both. Review refreshed-head checks on each PR; the owner performs all PR merges.

E-02 implementation: Kotlin-compatible KSP 2.0.21-1.0.28 processes Room 2.6.1; preview schema versions 1–4 and SMS journal schema 1 must remain identical to main. E-03 implementation: list unattached negative-ID drafts and recover their original ID/text after explicit fabricated name/address entry; cancellation, duplicates and missing/already-attached drafts preserve text. Its original 28 Android tests passed; current refresh results are recorded on #21. Both statuses remain PR open until owner merge.

F-15 final pre-merge verification: 31 JVM tests, build/lint, 46 ordinary Android tests and the separate synthetic inbound provider smoke passed at `3c00024`. The guarded smoke is skipped in the ordinary phase and executed separately. Real carrier sends, phone installation and replacement readiness are not implied.

Conflict-refresh local verification: both branches passed 31 JVM tests, lint, debug APK and Android-test APK assembly. Exported preview schemas 1–4 and SMS journal schema 1 match main exactly. #20 has two existing lint warnings (backup rules and icon); #21 retains those plus the separate kapt advisory. Hosted refreshed-head Android execution is tracked in the PR checks; no phone or carrier send was performed.

## F-16 — Opt-in MMS receiving/sending companion

Status: **In progress**; owner authorized on 2026-11-06 after reporting live SMS receive/send. First focused PR reads MMS stored by the external default app, with RECEIVE_MMS gating, notification-only WAP_PUSH_RECEIVED, provider-ID preservation, text/image preview and no provider writes. Google Messages retains download and notifications. Sending follows in a separate focused stack after a maintained, licensed PDU composer is selected and validated. No SMS fallback for failed MMS; all development fixtures are fabricated and carrier sends disabled. See `MMS_TRANSPORT.md`. Do not mark MMS or replacement ready until carrier and Fold checks pass.
