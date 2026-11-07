# Making the Samples tab operational

Owner request (2026-11-06): the main tab should show and work with real conversations, then the separate Device tab is removed.

## Boundary

Allowed and already authorized: read device SMS/MMS through the opt-in companion, send SMS only after number, text, SIM and part-count confirmation. Google Messages (or another external app) stays the default SMS app.

Not part of this plan, because they need the default-SMS role or new authorization: deleting real messages, marking real messages read, writing to the SMS provider, MMS sending, notifications, contacts, backup/restore of real messages. Where the sample UI offered these, the real-conversation UI hides them. All tests keep fabricated data and fake platforms; nothing sends in tests.

## Product decisions

- Opt-in off: the main tab keeps the fabricated sample inbox exactly as today.
- Opt-in on and permissions granted: the main tab shows real conversations. A "Sample data" entry in the overflow menu opens the sample inbox so fixtures stay reachable.
- Drafts, categories, pins and archive flags for real conversations are local metadata, keyed separately from sample ids. Existing sample drafts and metadata must survive every migration.

## PR queue (each a focused PR into `main`; stacked in this order)

1. **Telegram-style UI and fixed signing key** (branch `ccr-ee25a6b3-5sufuc`). Done and in review: restyle, shared signing key, UI test updates, version 0.3.0.
2. **Conversation grouping rules.** Pure Kotlin: group `DeviceSms` and `DeviceMms` by provider thread id into conversations (title from address, last message, time, unread count, SMS/MMS merged and ordered, stable sort). JVM tests for ties, missing thread ids, same-time messages, multipart and MMS-only threads.
3. **Real conversation list in the main tab.** Replaces the sample list when device access is on. Permission and opt-in states move into this screen (empty, permission missing, no SIM, loading, error). Search and category filter wired to real threads. Compose tests with the fake platform.
4. **Real conversation view.** Open a real thread from the list: tailed bubbles for SMS and MMS, MMS images, SIM label, date separators, Back/rotation/fold restoration. Read-only first.
5. **Reply and send in a conversation.** Per-conversation reply drafts (new draft store with a backward-compatible read of the existing composer draft), the existing confirm → send → per-part status flow, SIM picker, failed/unconfirmed states in the thread, no automatic resend. Fake-platform tests for failure and restore.
6. **Local metadata for real threads.** Room schema 5 with an explicit tested 4→5 migration: source column so real and sample ids cannot collide; pin, archive, category for real threads. Exported schema and migration test on an isolated database.
7. **New message.** The pencil button starts a message to a typed number (no contacts permission), using the same confirmation flow.
8. **Remove the Device tab.** Navigation becomes Chats and Settings, copy and docs updated, instrumented tests retargeted, version 0.4.0, handoff notes.

## Verification per PR

Local: JVM tests, lint, debug and Android-test assembly, `git diff --check`. Hosted: the Android workflow with its emulator tests. Fold behavior, large fonts, cover screen and real carrier sending are checked by the owner on the phone and reported separately from automated results.

## Status (2026-11-07)

Landed on `main` through #37 (the whole stack #28–#35 in one merge, version 0.4.0) and #38 (the reply box had been pushed below the bottom of the screen). Deliberate deviations from the plan: replies use the existing single shared SMS draft (one unsent draft app-wide, blocked with a visible reason when it belongs to someone else) instead of per-conversation drafts, because per-conversation drafts would change the revision and journal safeguards; metadata uses a new table instead of an extra column, which makes the 4→5 migration purely additive.

Lessons: stacked PRs that are merged into their base branches never reach `main`, so retarget each to `main` before merging or land the stack in one PR. Semantic test clicks work on off-screen controls, so UI tests must use real taps and `assertIsDisplayed` for anything a person has to reach.

Hosted emulator results are in each PR's checks; Fold behavior, large fonts, activity/process death, upgrade over the installed build and any real carrier send remain unverified on a device. Open work is listed in `docs/TRACKER.md` (F-19 onward).
