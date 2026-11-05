# FoldSMS replacement gates

The owner-provided `argon-grok-backlog.md` is a planning input, not an override of the existing product boundary. Continue with focused PRs; the owner performs all merges. No agents unless explicitly requested. Preserve the application ID, signing identity, drafts and local metadata. Telegram is the UI reference described in MVP_SPEC.md.

## Delivery order

1. Run existing Android tests on the configured emulator and record actual results. Finish sample-only message selection/deletion with persistent undo, then conversation-wide and app-wide deletion with explicit counts and scopes.
2. Reconcile stacked F-07–F-10 branches with main before relying on their code. A merged PR targeting another feature branch does not prove delivery to main.
3. Prepare a separately authorized transport milestone: role eligibility, provider import, inbound/outbound multipart SMS, notifications, dual SIM, role loss and Direct Boot recovery. Implement incrementally with fabricated fixtures and emulator checks.
4. Before an agreed phone default-app trial, verify inbound AND outbound MMS, carrier behavior, backup AND restoration, interrupted operations, duplicate prevention, and rollback to Google Messages. Emulator success does not establish carrier or Samsung behavior.
5. Schedule optional features separately: automated rules, scheduling, WebDAV, scam detection, widgets and S Pen refinements.

## Corrections required before adopting detailed transport tickets

- Move outbound MMS and tested backup/restore before the replacement gate, rather than leaving them in Phase 5.
- Defer notification Delete until the trash engine exists, or explicitly move its dependency earlier.
- Treat Telephony as the system-message source of truth. Provider and Room writes are separate transactions: use a durable operation journal and idempotent reconciliation, including crash boundaries. ContentObserver does not carry an application generation token; verify current provider state rather than assuming callback provenance.
- Prove a maintained, license-compatible MMS PDU parser/composer and the subscription-specific SmsManager API path with a small feasibility task. Do not assume internal framework PDU classes are public SDK APIs.
- Add permissions when the corresponding authorized feature needs them, rather than requesting contacts, exact alarms and network capabilities in advance.
- Define measured performance targets and datasets. A 120 Hz frame is about 8.3 ms; a blanket 16 ms target is not equivalent. Zero loss is a design goal, not a guarantee established by a finite test suite.
- Identity and restore deduplication must not collapse distinct legitimate messages or sender IDs. Test same-body/same-time messages, multipart callbacks, group participants and SMS/MMS ID collisions.

## Efficient verification

Run relevant local checks before pushing; push once per reviewable revision. GitHub Actions builds PRs and main, with superseded runs cancelled per PR/ref. Keep emulator interaction tests local for now. Never mark compiled instrumentation tests as executed or equate assembly with restoration/fold verification.

## First data-preservation features

- F-13: versioned preview export through Android CreateDocument, explicit unencrypted-file confirmation, read-only snapshot and write/close failure handling. No SMS/provider access or new permissions.
- F-14: validated preview import/restore, with preview/confirmation, no-write cancellation, collision checks, cross-store interruption journal and complete round-trip tests. Export alone does not satisfy backup AND restoration.

These features advance local data preservation within the fabricated-data milestone. They do not authorize role requests, real messages, transport, phone installation or a default-app trial. The transport queue and physical/carrier gates above remain separate.

## Authorized SMS companion increment — 2026-11-05

Owner prioritized opt-in SMS receipt/send before F-14 preview restore. F-15 keeps an external app default, reads SMS provider rows without writes, and confirms each subscription-specific send with per-part results. This does not complete the default-role/MMS/notification or backup/restore gates. All development sends are fake; no owner-phone installation or carrier send has been performed. Restricted permission grants remain installer-dependent. See `SMS_TRANSPORT.md`.
