#!/usr/bin/env bash
# Disposable emulator only. Checks the minified release APK really runs: it is not debuggable, it starts, the welcome screen
# shows, "Allow access" leads to the inbox (Room, the provider and the list screens work after shrinking) and nothing crashes.
set -euo pipefail
serial=emulator-5554
app=com.axiomstead.foldsms
apk=app/build/outputs/apk/release/app-release.apk
mkdir -p app/build/reports
[[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]

dump() { # dump the screen to a file and print its path
  adb -s "$serial" shell uiautomator dump /sdcard/ui.xml >/dev/null
  adb -s "$serial" pull /sdcard/ui.xml "$1" >/dev/null
}
# Waits up to $3 seconds for the screen dump $2 to match the pattern $1, dumping again every 3 seconds.
wait_for() {
  local deadline=$((SECONDS + $3))
  while (( SECONDS < deadline )); do
    dump "$2" || true
    if grep -qE "$1" "$2"; then return 0; fi
    sleep 3
  done
  return 1
}
fail() {
  echo "RELEASE SMOKE FAILED: $1"
  echo "--- what was in front:"; adb -s "$serial" shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | head -3 || true
  echo "--- last screen dump:"; cat app/build/reports/release-welcome.xml app/build/reports/release-inbox.xml 2>/dev/null | head -c 6000 || true
  echo "--- log:"; adb -s "$serial" logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ClassNotFound|NoSuchMethod|NoClassDef|$app" | tail -40 || true
  exit 1
}

adb -s "$serial" uninstall "$app" >/dev/null 2>&1 || true
adb -s "$serial" install -r "$apk"
if adb -s "$serial" shell dumpsys package "$app" | grep -q "DEBUGGABLE"; then fail "the release APK is debuggable"; fi
for permission in READ_SMS RECEIVE_SMS SEND_SMS READ_PHONE_STATE RECEIVE_MMS READ_CONTACTS; do
  adb -s "$serial" shell pm grant "$app" "android.permission.$permission" || true
done
adb -s "$serial" logcat -c
adb -s "$serial" shell am start -W -n "$app/.MainActivity" >/dev/null
wait_for "Allow access" app/build/reports/release-welcome.xml 45 || fail "the welcome screen did not show"

# Tap "Allow access" at the centre of its bounds.
read -r x y < <(python3 - <<'PY'
import re
xml = open("app/build/reports/release-welcome.xml", encoding="utf-8").read()
node = re.search(r'<node[^>]*text="Allow access"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml) or \
       re.search(r'<node[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="Allow access"', xml)
x1, y1, x2, y2 = map(int, node.groups())
print((x1 + x2) // 2, (y1 + y2) // 2)
PY
)
adb -s "$serial" shell input tap "$x" "$y"
wait_for "Search messages|No messages yet|Allow SMS access" app/build/reports/release-inbox.xml 45 || fail "the inbox did not show after Allow access"
if adb -s "$serial" logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: .*$app|ClassNotFoundException|NoSuchMethodError|NoClassDefFoundError"; then fail "a crash was logged"; fi
adb -s "$serial" uninstall "$app" >/dev/null 2>&1 || true
echo "Release smoke test passed"
