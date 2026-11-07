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
