# Becoming the default SMS app (F-30)

Owner authorization: 2026-11-07, "start on step 1". Scope: build the pieces below, then offer the default-role request as the last step. Until step 4 ships the app is **not eligible** for the role (Android needs an SMS_DELIVER receiver, a WAP_PUSH_DELIVER receiver, a SENDTO activity and a RESPOND_VIA_MESSAGE service, and we declare them only in step 4), so installing an update changes nothing on the phone. Google Messages can be restored in Android settings at any time. Tests use fabricated data and fakes and send nothing.

## Steps (each its own PR)

1. **Incoming SMS and notifications** (this PR): `SmsDeliverReceiver` assembles multipart messages, writes them to the SMS provider and shows a notification; it acts only while FoldSMS is the default app. Notification permission is requested with the default-role prompt in step 4.
2. Incoming MMS: WAP_PUSH_DELIVER receiver, MMS download and storage.
3. Provider writes and message actions: sent-message storage, delete, mark read.
4. Declare the remaining role components and add the "Make FoldSMS your default" prompt, with a plain way back.

## Safety rules

- Never store or notify while another app is default (no duplicates).
- A storage failure must never hide a message: it is still notified.
- Message text is never logged.
- The prompt appears only after steps 1-3 are merged and checked on the phone.

## Step 2 status (incoming MMS)

`MmsPushReceiver` (WAP_PUSH_DELIVER) -> `downloadMultimediaMessage` into an app cache file shared only with the phone process through a FileProvider -> `MmsDownloadedReceiver` parses (`MmsPdu.kt`), writes the message and parts to the MMS provider (a failed write removes the half-written message), sends the M-NotifyResp.ind acknowledgement and notifies. A message that cannot be downloaded or read still produces a plain notice. Duplicate notifications (same transaction id) are skipped.

Verified: JVM tests of the PDU parser and acknowledgement on **fabricated** PDUs built from the encoding rules (not captured from a carrier), including truncated and random input. **Not verified:** carrier PDUs, download, provider writes, acknowledgement and the notification; these need the default role and a real MMS and are the first phone check once step 4 lands. No network permission is added; transfers go through the telephony service.

## Step 3 status (provider writes and message actions)

Added the provider layer only; the delete and mark-read buttons are wired in step 4, after the open UI PRs (#41, #42) land, to avoid rewriting the inbox twice.

- While FoldSMS is default, a reviewed send is stored as queued before dispatch, then moved to sent or failed from the send journal's final stage (partly sent counts as failed). Without the role nothing is written and sending is unchanged. A storage failure never blocks a send.
- `MessageActions` (mark thread read, delete thread) refuse unless FoldSMS is default.

Verified: JVM tests of the stage-to-type rule and part joining, lint, build. **Not verified:** the actual provider writes and deletes; they need the role and are part of the phone check.
