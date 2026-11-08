# Phone test checklist (owner, on the Fold6)

What automated tests cannot show: real carriers, notifications, the fold, the provider on a real inbox. Do the groups in order; stop and report at the first failure. Takes about 45 minutes in total, and each group stands alone.

**Which APK.** From the build that has a `FoldSMS-release-apk` artifact on, install the **release** APK: it is the build that would ship (smaller, not debuggable, same key, so it installs over the debug one). If something behaves differently from before, say that it is the release build.

**Safety first.** Keep Google Messages installed. Switching back is Settings in FoldSMS → Choose another app, or Android Settings → Apps → Default apps → SMS app. Do not delete a real conversation while testing; use a conversation you made for the test.

**What to report.** The group and step number, what you expected, what happened, which SIM, and whether it also fails in Google Messages. Never paste message text, numbers or names. If anything failed, open FoldSMS → ⋮ → Settings → *Troubleshooting* and share that log (it holds only error names, codes and sizes). If Android shows "FoldSMS keeps stopping", the log will have a CRASH line.

## A. Install and first run (5 min)
1. Install over the current build. Everything is still there: conversations, pins, archive, categories, drafts. (Upgrade check.)
2. Open the app. The list loads; scroll to the very bottom of a long inbox: older conversations keep appearing ("Loading older messages…"), and old conversations you remember are reachable.
3. Search a word you know is in an old message. If it is not found, tap *Search all messages* and it appears.
4. Open a conversation with more than 200 messages: the oldest messages are there.

## B. Receiving (10 min, FoldSMS is the default app)
1. Another phone sends you a short SMS: it arrives, a notification shows "New message" on the lock screen and the sender and text once unlocked, the conversation moves to the top.
2. A long SMS (over 160 characters) arrives as one message, not in pieces.
3. A message with an emoji shows correctly.
4. Send to the *other* SIM too (you have two): both arrive, the SIM label shows in the conversation.
5. A one-time code arrives (any service): the notification has **Copy code**; tapping it copies, removes the notice, marks the conversation read. Paste somewhere to check. After a minute the clipboard is empty.
6. The **Latest code** card appears above the list and disappears after copying or after ten minutes.
7. Does the keyboard (Gboard or Samsung) offer the code by itself? Note yes or no.

## C. Sending (5 min; costs a few SMS)
1. In an existing conversation, type a short reply and tap Send: it goes out at once, no dialog, and the thread shows **Sent** under the newest message.
2. A long reply (more than one part) shows the review dialog; Cancel sends nothing.
3. New message to a number or a contact name: the review dialog appears.
4. Tap Send twice quickly: one message only.
5. Airplane mode, then send: the message shows **Failed** or **Sending…**, the draft stays, nothing is resent by itself.

## C7. Reply from the notification (3 min, costs 1-2 SMS)
1. Another phone texts you. Pull down the notification (unlocked) and use **Reply**: type `C7 quick reply` and send. The notice changes to "Reply sent" and goes away; the other phone gets the text; the conversation in FoldSMS shows it as sent.
2. Reply with a long text (over 160 characters): the notice says it was saved as a draft; nothing is sent; opening FoldSMS shows the text in the reply box for review.
3. Type something in a conversation's reply box and leave it unsent; then reply from a notification: the notice says you have an unsent message and the draft is untouched.
4. A notification from a name sender (a shop or bank) has no Reply button.

## D. Pictures (10 min; optional, MMS depends on the sender's network)
MMS is limited on the owner's network and some senders cannot send it at all, so a missing picture may be the carrier. Test only with a sender whose phone can send MMS; FoldSMS does not send pictures or take part in RCS, on purpose.
1. A photo arrives from another phone: notification, picture in the thread, tap enlarges.
2. Photo with a caption, then several photos, then a short video (a video may be too big for the carrier, that is normal).
3. A group message with three people.
4. If a picture notification says it could not be downloaded: check mobile data is on for the SIM that received it, then open *Troubleshooting* in Settings and report it. Try the same in Google Messages to see whether it is the carrier.

## E. The screen (10 min)
1. Fold and unfold with a conversation open: nothing is lost (draft, scroll position, search text), list and conversation sit side by side when unfolded.
2. Cover screen: the search bar, the three chips (All, Personal, Business), the selection bar (Read, Unread, Category, Delete) and the reply box all fit and can be tapped.
3. Largest font size and display size: nothing is cut off, the reply box stays reachable with the keyboard open, the newest message stays in view.
4. Rotate; swipe the app away and reopen; leave it for an hour and reopen: no crash, no lost draft.
5. Dark and light themes both readable.

## F. Messages you control (5 min, on a *test* conversation)
1. Long-press a conversation → select; **Select all**; **Category** moves several to Business and back to Automatic.
2. **Unread** makes the newest received message unread; **Read** clears it.
3. **Delete** on the test conversation asks first and removes it (and it is gone in Google Messages too, they share the same store).
4. Show unread only (menu) works; archive then receive a message in it: it returns to the inbox.
5. Contacts: names show instead of numbers; New message finds a contact by name.

## F3. Blocking (5 min, needs a second phone)
1. In a conversation with the second phone's number: ⋮ → **Block this number**: the dialog warns that texts *and calls* stop. Confirm; the header says "Blocked".
2. From the second phone send a text and call: nothing arrives in FoldSMS (the call is rejected). Check that the Phone app's blocked numbers list also shows it.
3. ⋮ → **Unblock this number** (or Settings → Blocked numbers → Unblock): texts arrive again.

## F2. Backup (5 min)
1. Settings → *Export messages to a file* → **Messages only** → pick a place (for example Downloads). It shows progress and then "Saved N SMS and M MS". Open the file in a text viewer: it starts with `<smses count=...>` and your messages are readable.
2. Export again **With pictures and attachments**: the file is much bigger, takes longer, and the result line says how many attachments were included or could not be read.
3. Start an export and press Cancel: the unfinished file is removed.

## H. Contacts (5 min)

1. Menu → Contacts: names show; search a name and 3+ digits of a number.
2. Tap a name → opens a message to it. Edit opens the system editor.
3. Open a conversation with an unsaved number → ⋮ → Add to contacts; save, come back, the name shows after refresh.

## H2. Calling (3 min)

1. Contacts → a person → Call: first time asks for the Call permission; Allow → the call starts. Hang up.
2. Conversation with a number → ⋮ → Call. A sender name (e.g. a bank) has no Call item.
3. Refuse the permission once: the dialer should open with the number filled in.

## H3. Balance (3 min, may cost a few centavos)

1. Menu → Balance and codes → Check on *123#: the Smart answer shows. Try each SIM if you have two.
2. Add a code with + (must look like *143#), then remove it.
3. Switch to airplane mode and Check: a plain failure message, no crash.

## G. Switching back (3 min)
1. Choose another app → Google Messages: your messages are all there, new ones arrive there.
2. Switch to FoldSMS again from the banner or Settings: it works at once.

## Not covered here
Sending pictures and RCS (deliberately not built; RCS chats stay in Google Messages), restoring a backup into FoldSMS (not built; the export is a safety copy).
