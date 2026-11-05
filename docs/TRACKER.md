# FoldSMS feature tracker

Batch 1: the original ten feature PRs plus two requested message-deletion features for the sample-data milestone. All content remains fabricated; no SMS/contact/network permission, default-role request, or sending. Current owner instruction (2026-11-04): focused PRs only; the owner performs all merges. No agents unless requested. Follow dependencies, keep each PR focused, and never merge during this review batch. Each item gets appropriate tests, local checks, independent review, and CI. Unexecuted Android tests and phone checks remain explicit.

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
| F-13 | Export local preview data | PR open | Review branch | Pending |
| F-14 | Validate and restore a preview export | Planned | — | Pending |

F-07–F-10 reached main through merged integration #18. Its 22 JVM tests and 25 emulator tests passed. No new phone checks are implied. Open follow-ups #19–#21 add hosted interaction CI, KSP and interrupted-draft recovery; their source remains unmerged.

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

Status: **Planned**, depends on F-13. Validate format/version, size, all types, IDs and references before any writes; reject truncated/unsupported files. Preview affected data and require explicit confirmation. Define conflict handling and a durable journal for interrupted Room/preference application, including rollback/reconciliation tests. Prove complete round-trip, cancellation, duplicate/invalid data, failure/relaunch and preservation of unrelated drafts. No real Telephony restore in this feature.

## Engineering follow-ups

F-12 implementation: separate conversation and app-wide menu actions, with immutable confirmation targets and counts from the complete dataset before search/category filtering. Both scopes preserve drafts and conversation shells, retain durable latest-operation undo, and disable deletion for an empty scope. Verification results are in CONTINUE.md.

### E-01 — Set up an Android emulator for interaction tests

Status: **Merged** via [#15](https://github.com/nvrsbb/sims/pull/15). Android Studio AVD API 35 installed and running; all 9 baseline instrumentation tests pass, including message details/copy/dismiss. Corrected nested badge selectors and keyboard-dismissal synchronization. See `docs/EMULATOR.md`. Schedule separately from the ten feature PRs.

Set up a dedicated emulator with fabricated data so Compose interaction tests can run without another Fold6 installation. Document reproducible setup and test commands. Keep its data separate from the owner's phone and drafts.

Completion: run `:app:connectedDebugAndroidTest` successfully, including the message details/copy/dismiss test; record emulator API/device configuration and results. Support narrow and expanded layouts where practical. Physical Fold6 checks remain necessary for actual folding and Samsung-specific behavior.

### E-02 — Replace Room kapt processing with compatible KSP

Status: **PR open** — [#20](https://github.com/nvrsbb/sims/pull/20). F-06 introduced the `KaptUsageInsteadOfKsp` lint advisory; the existing backup-extraction and application-icon warnings remain separate. Keep the current verified Kotlin/Room versions stable for this batch, then evaluate a compatible KSP setup and compare generated schemas before changing processors. No runtime failure was observed.

### E-03 — Recover interrupted sample-conversation drafts

Status: **PR open** — [#21](https://github.com/nvrsbb/sims/pull/21), with 28 hosted Android tests passed. F-09 writes the initial draft durably before committing its Room conversation row. Cancellation or database commit failure between those stores can leave an unattached draft; reserved negative IDs prevent later creation from overwriting it. Add a targeted cancellation regression and a deliberate recovery path. Ordinary typing retains the existing draft preference keys and behavior.

## Batch phone check results

Choose a useful subset or the completed batch before installing. Record version/commit and results here, keeping automated checks separate from owner-reported checks. Advance the app version for the distribution batch and verify signing compatibility before suggesting installation.

Existing baseline: the owner reported draft isolation, fold/unfold, and relaunch working before this queue. PR #1's visual changes have not yet been checked on the phone.

### E-04 — Run Android interaction tests in CI

Status: **In progress**. Add a disposable API 35 emulator job after build checks, with isolated storage, report artifacts, bounded runtime and cancellation. CI execution is the acceptance gate; local cloud KVM is unavailable. No phone data or sending.
