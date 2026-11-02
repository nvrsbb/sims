# FoldSMS feature tracker

Batch 1: ten proposed feature PRs for the sample-data milestone. All content remains fabricated; no SMS/contact/network permission, default-role request, or sending. Work in order, one open feature PR at a time. Each item gets a focused branch, tests appropriate to its behavior, local checks, review, and green CI before merge.

Status: **Planned → In progress → PR open → Merged**. Close an item only after its PR is merged. Track phone validation separately; merging does not imply a device check. Include this file's status update in each feature PR; confirm merge state in the next PR or handoff. A postponed item needs a reason.

| ID | Feature / proposed PR | Status | PR | Phone check |
|---|---|---|---|---|
| F-01 | Chronological sample message history | In progress | Pending | Pending |
| F-02 | Message details and copying sample text | Planned | — | Pending |
| F-03 | Draft indicators and deliberate draft clearing | Planned | — | Pending |
| F-04 | Restore conversation navigation and Android Back | Planned | — | Pending |
| F-05 | Search across complete sample histories | Planned | — | Pending |
| F-06 | Change and remember conversation categories | Planned | — | Pending |
| F-07 | Pin important conversations | Planned | — | Pending |
| F-08 | Archive and restore conversations | Planned | — | Pending |
| F-09 | Start a new local sample conversation | Planned | — | Pending |
| F-10 | Preview settings and safety information | Planned | — | Pending |

## F-01 — Chronological sample message history

Show several incoming/outgoing fabricated messages with timestamps and explicit sample status. Move fixtures out of the activity; keep the existing conversation IDs so saved drafts still load. Derive inbox previews from the latest message.

Completion: histories sort consistently; previews match the latest message; conversations share no message IDs; message history scrolls with the draft composer reachable. Unit tests cover ordering, ties, previews, and fixture separation. Phone check: Alex and a service conversation, switching threads, keyboard, folding, and a pre-existing draft.

Local verification: three JVM tests passed, Android lint and debug assembly succeeded. Android UI/device behavior has not been verified for this feature; its phone check remains pending.

## F-02 — Message details and copying sample text

Open a selected message's details with sender, full timestamp, direction, and clearly fabricated status; copy its text using Android clipboard handling. No fake real delivery or SIM guarantees.

Completion: details belong to the tapped message; copying uses that message's body; dismissing returns to the same conversation and draft. Android UI checks cover selection/copy/dismiss. Depends on F-01.

## F-03 — Draft indicators and deliberate draft clearing

Show which inbox conversations have a saved draft and offer an explicit clear action for the active draft. Empty drafts remove their indicator. Preserve the existing preference keys and text.

Completion: two conversations retain independent drafts; clearing affects only the chosen draft; empty and multiline text behave correctly; relaunch restores indicators. Test storage behavior with isolated Android preferences and UI behavior with Compose tests.

## F-04 — Restore conversation navigation and Android Back

Keep selected thread, inbox/conversation position, search, and category filter through activity recreation. Android Back from a cover-screen conversation returns to the inbox; handle keyboard Back naturally. Unfolding/folding keeps the intended conversation.

Completion: restoration tests cover a non-default conversation and filters; Back does not unexpectedly exit from a cover conversation. Report recreation, relaunch, and process-restoration checks separately. Draft contents remain in durable storage.

## F-05 — Search across complete sample histories

Search sender and every message body, not only the latest preview. Add fabricated addresses to support number search. Show useful result snippets, a no-results state, and a clear-search action; respect category filtering.

Completion: older-message matches open the correct conversation; whitespace/case/number formatting are handled deliberately; clearing recovers the list. Unit tests cover combined filters and matches. Depends on F-01 and F-04.

## F-06 — Change and remember conversation categories

Let the owner assign Personal, Finance, Services, Promotions, OTP, or Unknown to a sample conversation. Store category overrides in local metadata, with a small Room foundation for later pins/archive and migration tests. Read existing draft preferences unchanged; no draft migration is required for this feature.

Completion: category changes persist and immediately affect filtering; resetting restores the sample default; unrelated drafts survive. Test database reads/writes with isolated storage and Compose filter behavior. Do not add automatic categorization rules in this PR.

## F-07 — Pin important conversations

Pin/unpin a conversation and list pinned conversations first, preserving deterministic order within both groups. Store the choice in the local metadata from F-06; keep search and category filters consistent.

Completion: pin state survives relaunch; filters do not resurrect excluded conversations; pinning does not change message timestamps or drafts. Unit tests cover ordering, and Android tests cover persistence. Depends on F-06.

## F-08 — Archive and restore conversations

Move a sample conversation to a separate Archive view and restore it. Archive is reversible and retains message history, category, pins, and drafts. No deletion feature.

Completion: archived items leave the normal inbox and remain reachable in Archive; restoring recovers their metadata and draft; an empty Archive is understandable. Test visibility rules and persistence. Depends on F-06 and F-07.

## F-09 — Start a new local sample conversation

Create a conversation using a locally entered sample name/address and draft text, with a clear preview explanation. Do not access Android contacts or messaging intents. Persist the custom sample conversation locally; do not fabricate sent messages from the draft.

Completion: input validation, repeated-address handling, stable IDs without fixture collisions, saved draft, relaunch, category assignment, and navigation work. Test validation and persistence. Depends on F-03, F-04, and F-06.

## F-10 — Preview settings and safety information

Add a native settings screen with remembered Light/Dark/Device theme selection, privacy/storage explanation, and a factual preview capabilities checklist. Explain the future default-SMS milestone without requesting the role. Keep navigation and drafts intact.

Completion: theme choice survives relaunch; Device follows Android; settings return to the active thread; no permissions/role prompts. Test preference mapping and navigation; check contrast, large text, and both Fold layouts. No backup/restore implementation in this PR.

## Batch phone check

Choose a useful subset or the completed batch before installing. Record version/commit and results here, keeping automated checks separate from owner-reported checks. Advance the app version for the distribution batch and verify signing compatibility before suggesting installation.

Existing baseline: the owner reported draft isolation, fold/unfold, and relaunch working before this queue. PR #1's visual changes have not yet been checked on the phone.
