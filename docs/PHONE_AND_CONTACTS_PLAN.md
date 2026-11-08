# Calling, contacts and carrier features: plan

Owner request 2026-11-08, with the choices made in the session: the full default Phone app, contacts list with search and editing and "add to contacts" from a text, balance checks by USSD, and grouping the Smart carrier's messages. Accessibility comes first and is done.

## What is different from SMS, and why it is staged
SMS only reads and sends text. A default **Phone app** takes over every call, including **emergency calls (112/911)**. If it is wrong, calls can fail. So:
- Every stage is its own PR, tested, and used on the phone before the next one starts.
- The Samsung Phone and Contacts apps stay installed (they cannot be removed); switching back is one tap in Android Settings.
- Emergency numbers are never filtered, blocked, delayed or put behind a confirmation. They are placed through the same system call as any other number.
- The final stage (becoming the default Phone app) starts only after the owner has used stages 1 to 4 on the phone and says go.
- What automated tests cannot show (a real call, audio, Bluetooth, the proximity sensor, a real emergency path) is checked by the owner with a checklist, the same way as for SMS. The emulator can simulate incoming and outgoing calls for the call screens only.

## Stages
1. **Contacts screen (read) and "Add to contacts".** A Contacts screen: list, search, open a contact. In a conversation: **Add to contacts** for a number that is not saved (opens the system new-contact form with the number filled in; no new permission), and **Edit contact** for one that is (system editor). Editing in the system editor, not our own form, keeps changes safe and visible to every app; our own edit form is a later, optional step because it needs the write-contacts permission.
2. **Tap to call.** A call button in a conversation, on a contact, and on numbers inside texts. Placed through the system (needs the Call permission). The Samsung Phone still shows the call.
3. **Balance and USSD screen.** Send a USSD code (for example a balance check) on the chosen SIM and show the answer. Single request and reply only: menus that need several replies stay with the system dialog. Saved codes are editable; I will not guess operator codes, the owner supplies the ones that work on Smart.
4. **Smart messages in one place.** A "Carrier" group for messages from the carrier's sender names (balance, promos, load): it needs the owner to tell me the sender names that appear in the inbox (names only, no text). Shown as a filter and a label.
5. **Dialer and call history.** A dial pad, recent calls (needs the call-log permission) and handling of `tel:` links. Still not the default Phone app.
6. **Default Phone app (the in-call screen).** Incoming and ongoing call screens, answer, decline, hold, mute, speaker, keypad during calls, Bluetooth and earpiece routing, ringtone and notifications, the proximity sensor, call blocking integration, dual SIM and eSIM. The system falls back to its built-in call screen if our call service cannot start; this fallback is expected but is verified only on the phone. Large and the riskiest, hence last.

## Permissions this will ask for, and when
Call (stage 2), call log (stage 5), write contacts (only if we build our own edit form), phone numbers and answer calls (stage 6). Each is requested when its stage first needs it, never all at once.

## Not promised
Samsung-only features (call recording, Bixby, call effects, Edge panels) will not exist in our call screen. Video calling, voicemail and RCS are out of scope.
