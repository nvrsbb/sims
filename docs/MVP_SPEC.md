# FoldSMS — MVP Product & Engineering Specification

## Product intent
Private, local-first SMS/MMS replacement for a single Samsung Galaxy Z Fold6. Phase 1 is a **non-invasive demonstrator**. No SMS permissions, no role change, no real message access, no account, no server and no telemetry.

## Authorized incremental milestone — 2026-11-05

Owner prioritized real SMS receive/send before preview restore. Add an opt-in nondefault SMS companion, preserving Google Messages for MMS, notification and provider ownership. Require runtime permissions, explicit active-SIM selection and reviewed send confirmation. All tests remain fabricated with fake sending; no owner-phone installation. This intermediate milestone does not complete P2/P3 or establish replacement readiness. See `SMS_TRANSPORT.md`.

The owner subsequently requested direct access to stored SMS and MMS from the main preview screen and removal of the separate companion screen. Real conversations are opt-in in the main Chats tab (menu: Real messages) and keep sample data, drafts and deletion controls separate; the earlier separate Device tab was removed. Google Messages remains default for MMS download, provider writes and notifications; MMS sending remains a later milestone.

## UI direction

Owner preference: use Telegram as a design reference for its straightforward, no-nonsense interface. Favor a clean conversation list, clear typography and hierarchy, restrained chrome, readable message bubbles, and direct, discoverable actions. Adapt these principles to native Android navigation and the Fold6 cover and expanded layouts while retaining the shared semantic palette, flat fills, accessibility, and light/dark themes. This is visual and interaction inspiration; it does not expand the SMS/MMS product scope.

## Screen inventory
1. Inbox: thread previews, unread indicator, category chips and search.
2. Conversation: chronological bubbles, timestamps, composer, delivery state.
3. New message: contact/number picker, compose, SIM selection where applicable.
4. Categories: Personal, Finance, Services, Promotions, OTP, Unknown; locally configurable rules.
5. Search: sender, number, body, date and category.
6. Message details: timestamps, copy, sender, delivery status, SIM where supported.
7. Settings: default-role explanation, notifications, privacy, export/restore, backups.
8. Safety/debug: permissions, current SMS role, incoming/outgoing test checklist.
9. Foldable layout: single pane on cover display, thread list and conversation split on inner display (responsive based on available width).

## Scope by milestone
**P0 (delivered starter):** native UI, mock message threads, category filters, basic search, cover/inner responsive layout and CI debug APK generation. No SMS permissions or data. No real sending.

**P1 (next development):** local metadata tables, durable categories, UI tests, draft composer and message navigation. Still without SMS access.

**P2 (controlled role implementation):** complete default-SMS role eligibility with SMS_DELIVER receiver, WAP_PUSH_DELIVER receiver, SENDTO activity filters and RESPOND_VIA_MESSAGE service; role request via RoleManager. Implement carrier SMS receive, content provider reads/writes, notification channels, send/delivery results, multipart handling and dual-SIM behavior. MMS send/receive must be fully implemented before treating replacement as dependable. No automatic deletions. See Android Telephony official documentation.

**P3 (limited real-world trial):** export backup and test restoration; test SMS receipt and sending with a second number or emulator, then change default app on Fold6 in a planned test window. Verify OTP arrival and notifications, switching default back to Google Messages, screen folding while composing, app process death, battery restrictions, airplane-mode recovery and messages with attachments.

## Local data model proposal (Room)
- Conversation: threadId, address, displayName, lastSnippet, lastTimestamp, unreadCount, category, pinned, archived.
- MessageMetadata: messageId, threadId, transport (SMS/MMS), direction, timestamp, status, subscriptionId, categoryOverride.
- CategorizationRule: id, priority, senderPattern, bodyPattern, category, enabled.
- Draft: threadId, text, lastUpdated.
- TestingEvent: type, timestamp, result, optional non-sensitive detail.

The Android Telephony provider is the source of truth for system messages; Room is only for app-specific metadata and indexing. Avoid duplicating full message bodies unless explicitly needed.

## Privacy defaults
Local-only, no analytics SDK, no network permission, no OTP uploads, no automatic delete, no database backup to cloud. Later opt-in export must use Android's document picker and clear user confirmation.

## Known platform limitations
RCS transport and existing Google Messages RCS history aren't exposed through ordinary public SMS APIs. Google Play policy applies to future distribution. One-device live testing cannot independently verify every delivery path. Samsung-specific battery management and fold transition need device testing.

## Definition of done — P0
APK builds in GitHub Actions, app launches on Fold6, search/filter works on fabricated messages, conversation selection works, unfolded >600dp uses split pane, app never requests SMS permissions or changes default messaging app.

## Definition of done — before replacing default messaging
Full SMS/MMS role components completed, tested backup and restoration, two-way send/receive, SMS multipart and MMS attachments, notifications, persisted conversation history, SIM selection, failure paths and quick rollback verified.
