# Local Android interaction checks

Use JDK 17 and the Gradle wrapper with compile SDK 35 available. Android Studio can manage the emulator independently of the SDK used by Gradle. Keep toolchains, AVDs and screenshots outside source.

Verified configuration: Android Studio AVD `Samsung_Fold_6`, API 35 / Android 15, Google APIs ARM64 image, 1856 × 2160 at density 320. This is an Android emulator, not Samsung One UI hardware. AVD startup warns that the configured folded height exceeds its LCD height; do not treat it as proof of the physical cover dimensions.

Start the dedicated AVD from Android Studio Device Manager. Confirm its serial with `adb devices -l`. The following assumes it is `emulator-5554`; substitute the actual emulator serial and never select the owner's phone:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon :app:connectedDebugAndroidTest
```

Tests use fabricated fixtures and isolated preferences/Room databases. Do not clear application data or uninstall on a physical phone. Tests using `StateRestorationTester` exercise Compose saved-state restoration; they do not prove actual activity recreation, process death, relaunch, or physical folding. Check those separately when applicable.

Local baseline on 2026-11-04: eight JVM tests, lint, debug APK and Android-test APK assembly pass. Lint has eight existing warnings: five dependency update suggestions, backup extraction rules, kapt/KSP advisory and application icon. The initial emulator run executed nine tests with one failure: draft badges were looked up in merged Card semantics. The rerun also exposed a keyboard-dismissal timing assumption in the Back test. With unmerged badge selectors and explicit keyboard-dismissal synchronization, all nine instrumentation tests pass.

F-12 session: all 13 instrumentation tests and 12 JVM tests pass. A separate UI walkthrough used a temporary `adb -s emulator-5554 shell wm size 968x2160` override with font scale 1.3, then reset size/font settings. Checked keyboard visibility/Back, expanded light/dark, and force-stop/relaunch retaining a fabricated draft, deletion and undo. `adb emu fold` did not change display width on this AVD, so physical fold continuity is still unverified.

## GitHub interaction checks

Pull requests and main pushes run the interaction-tests job after the build passes. A disposable API 35 Google APIs x86_64 emulator on a hosted Linux runner uses KVM and a 1856×2160 viewport at density 320, matching the verified baseline. It runs connectedDebugAndroidTest and uploads reports even on failure, with a 30-minute timeout. Obsolete runs are cancelled. It never targets a physical phone or owner storage. This does not prove Samsung folding or phone update behavior.
