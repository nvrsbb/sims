# FoldSMS working agreements

## Product boundary

- Read `docs/MVP_SPEC.md` and `docs/CONTINUE.md` before starting work.
- Native Kotlin / Jetpack Compose app for a Samsung Galaxy Z Fold6. No server, Vercel deployment, account, analytics, or network permission.
- Owner authorized opt-in SMS receiving/sending on 2026-11-05 and an opt-in MMS companion on 2026-11-06. The app reads Telephony. Sending: the owner changed the rule on 2026-11-07 so a one-part reply in an existing conversation goes out when Send is tapped; a new recipient, a message of several parts or a possible duplicate of an earlier attempt still shows the number/body/SIM/part-count review dialog first. A reply typed in a notification follows the same rule: it is sent at once only when it is one part and not a possible duplicate; otherwise it is saved as the draft for review in the app. Development fixtures stay fabricated; tests never send, call carrier services or touch owner data.
- Owner authorized on 2026-11-07: FoldSMS becoming the default SMS/MMS app (the owner accepts Android's role dialog; see `docs/DEFAULT_SMS.md`), including storing received and sent messages, notifications, mark read/unread and delete with confirmation; and optional read-only contacts access for names and choosing a recipient. MMS sending and RCS are **deliberately out of scope** (owner decision 2026-11-08: MMS is limited on the owner's network and RCS is not available to a third-party app); receiving MMS stays. Requesting the role, installing on the phone and carrier sends during tests are not done by development runs.
- The app has no sample inbox or separate Device tab: the main screen is the real conversation list, with a welcome screen until the owner allows access.
- Preserve drafts and local metadata through app updates. Never use real messages as fixtures or log private message text.

## Development

- Start with `git status --short` and preserve unrelated work. Define the expected behavior and completion checks for one focused change at a time.
- Separate UI, storage, sample data, and pure rules as complexity grows. Avoid speculative frameworks and large rewrites.
- Add meaningful tests for new rules, failure cases, and bug regressions. Test behavior rather than duplicating implementation. Pin time in date-dependent tests.
- Use fabricated data and isolated test storage. Tests must never send messages, call carrier services, or modify owner data.
- Keep temporary scripts, screenshots, and toolchains outside tracked source. Never commit SDKs, signing keys, secrets, build outputs, or local machine paths.
- Storage changes need backward-compatible reads and explicit, tested migrations. Moving drafts from preferences to Room must preserve existing text.

## Checks and review

- Use JDK 17 and the Gradle wrapper; setup is in `docs/CONTINUE.md`. Before considering code ready, run:

  ```sh
  ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
  git diff --check
  ```

- `NO-SOURCE` means no tests ran, not passing test coverage. Report existing lint warnings separately from new failures.
- CI runs the same Gradle checks on PRs and pushes to `main`. Fix red checks before merging. Local success does not establish CI success.
- Use Compose or Android instrumentation tests for behavior needing Android, including persistence and restoration. Run them on an emulator/device when available; explicitly report when unavailable. Assembly does not prove fold or relaunch behavior.
- Review changes and callers for concrete defects: give the input/state, wrong result, location, and evidence. Distinguish reproduced findings from unverified concerns.
- Building, reviewing, and walking the UI are separate responsibilities. Separate agents are optional and require authorization; no automatic team for small changes.

## Git and phone updates

- Develop new work on focused feature branches. Open a PR into `main`, describe the user-visible change and checks actually run, and wait for green CI. The owner performs every merge; never merge or enable auto-merge.
- Inspect status before committing and stage intended files by name. Use plain commit messages. Never force-push `main` or rewrite shared history.
- Accumulate independently checked changes before a phone update. Local checks and CI do not require reinstalling after every feature. Agree on the batch before requesting phone installation.
- Preserve application ID and signing identity for updates. CI and local debug keys may differ. Never suggest uninstalling around a signature mismatch without explaining draft loss and arranging preservation.
- For distributed update batches, advance versionCode monotonically and use a meaningful versionName. Record the commit/build checked on the phone.

## Android UI and handoff

- Use the shared semantic palette, flat fills, plain wording, and light/dark themes. Adapt navigation to Android instead of copying web sidebar dimensions.
- Check cover/unfolded layouts, large fonts, keyboard visibility, Android Back, long content, and empty/search states. Scrolling must keep essential controls reachable.
- For state changes, check conversation switching, folding, activity recreation, relaunch, and process restoration as applicable; distinguish these scenarios in reports.
- Use emulator checks during development where possible. Reserve physical Fold6 checks for useful batches, especially fold transitions and installation/update behavior.
- Update `docs/CONTINUE.md` at milestones with completed work, actual verification, remaining checks, and next work. Log finished work locally; do not copy private tracker infrastructure from other projects.
