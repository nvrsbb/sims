# Opt-in SMS companion

Owner authorized receiving and sending for the next PR on 2026-11-05. Open Settings → Open SMS companion. Access starts disabled and requires confirmation plus Android SMS and Phone/SIM grants. Restricted SMS permissions depend on the installer; if unavailable, the app blocks operations and offers Android settings. No permission bypass, default-role request, installation or real carrier test is part of this change.

## Receive and send ownership

An external default messaging app must remain selected. It owns MMS, notifications, incoming provider insertion and read state. FoldSMS reads up to 200 recent SMS rows, retains Long provider IDs and refreshes through ContentObserver, SMS_RECEIVED notification and foreground resume. Identical bodies/timestamps remain distinct. It never writes device message rows or marks them read. RCS and MMS are outside this inbox.

The sender uses a subscription-specific SmsManager after explicit SIM selection. Review shows normalized numeric recipient, exact message, selected SIM, encoded part count and carrier-charge warning. No implicit/default SIM fallback. USSD/command recipients and more than ten parts are rejected. The external default remains responsible for the full messaging experience; Android automatically writes sends by nondefault apps to the SMS provider.

## Durable handoff and results

The SMS composer uses separate private preferences and a revision. A forced durable snapshot precedes journal preparation. The separate Room schema-1 journal stores recipient, body SHA-256, revision, subscription and per-part results, not message bodies. Preview schema 4, sample preferences, application ID and signing identity are unchanged. Preview export includes no real SMS, SMS composer or journal. Backup remains disabled.

PREPARED means no dispatch has started and may be cancelled. A transaction moves it to SUBMITTED before invoking SmsManager. A crash or service exception around this boundary is inherently uncertain: retain the draft and never automatically retry. Reopening reconciles completed results but never sends. Another attempt requires an explicit duplicate/charges warning, a new revision and another send review. Editing a draft also produces a new revision; old callbacks cannot clear it.

Each immutable explicit callback identifies an operation and part. The result receiver is non-exported. Unknown/duplicate callbacks are ignored; the first result wins. All parts must succeed before reporting network sent and clearing an unchanged draft. No delivery receipt is requested or claimed. Failed/partial/unknown results preserve text. Disabling access blocks new work but cannot cancel already submitted messages; result metadata can still be recorded without clearing the draft.

## Validation and remaining work

JVM rules cover number/body limits, permission gates and multipart statuses. Isolated Android tests cover denied/inactive-SIM paths, concurrent duplicate confirmations, out-of-order/duplicate callbacks, partial/unknown results, late-callback draft preservation, cancellation, explicit retry and completed-journal reconciliation. Compose tests exercise opt-in cancellation, incoming refresh with distinct Long IDs and cancel/confirm sending through a fake platform. No test invokes native SmsManager dispatch or modifies owner data.

A dedicated disposable-emulator CI phase grants ordinary read/receive permissions, injects one fabricated inbound SMS through the emulator console and checks the native provider reader. The normal test suite skips this guarded smoke, then CI runs it separately with an emulator-only flag. No native outgoing send occurs. Refused grants fail the smoke without changing restricted-permission allowlists. Execution is pending.

Installer permission behavior on the owner device, carrier callbacks, actual process death, lock-screen behavior, small-screen/large-font walkthrough and physical Fold6 transitions remain validation items. No real carrier messages have been sent. Journal history currently grows with attempts; add deliberate, privacy-preserving pruning that retains active/uncertain attempts. Default-role eligibility, MMS, notifications, real-message backup/restore and planned two-number/rollback testing remain prerequisites before replacing Google Messages.

## Android contracts

- [SmsManager](https://developer.android.com/reference/android/telephony/SmsManager): subscription-specific manager, multipart sent results and nondefault-app provider persistence.
- [Telephony.Sms.Intents](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents): SMS_RECEIVED notification versus default-app SMS_DELIVER ownership.
- [Manifest.permission](https://developer.android.com/reference/android/Manifest.permission): restricted SMS permissions and installer allowlisting.
