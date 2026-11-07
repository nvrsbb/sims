#!/usr/bin/env bash
# Runs the instrumented tests on the CI emulator, then lists the tests that needed a retry (tag FLAKY) whatever the outcome.
set -uo pipefail
serial=emulator-5554
# Right after boot the Google apps update and index in the background and starve the tests; let that settle first.
adb -s "$serial" shell am wait-for-broadcast-idle >/dev/null 2>&1 || true
sleep 30
adb -s "$serial" logcat -c || true
adb -s "$serial" logcat -G 16M || true
ANDROID_SERIAL="$serial" ./gradlew --no-daemon :app:connectedDebugAndroidTest
status=$?
mkdir -p app/build/reports
adb -s "$serial" logcat -d -s FLAKY:W > app/build/reports/flaky-retries.txt 2>/dev/null || true
# Evidence for stalls: the whole log is uploaded. For each retried test the log lines of the 45 seconds before the retry that
# usually explain a stall (ANR, crashes, permission dialog, frame stalls, lock contention) are printed, not the boot noise.
adb -s "$serial" logcat -d -b all -v threadtime > app/build/reports/emulator-logcat.txt 2>/dev/null || true
python3 - <<'PY' || true
import re, datetime
log = open("app/build/reports/emulator-logcat.txt", errors="replace").read().splitlines()
stamp = re.compile(r"^(\d\d-\d\d) (\d\d):(\d\d):(\d\d)")
def secs(line):
    m = stamp.match(line)
    return int(m.group(2)) * 3600 + int(m.group(3)) * 60 + int(m.group(4)) if m else None
marker = re.compile(r"ANR in|am_anr|FATAL EXCEPTION|has died|am_crash|GrantPermissionsActivity|Davey! duration=[0-9]{4,}|Skipped [0-9]{2,} frames|Long monitor contention|paused for [0-9]{3,}|foldsms.*(ANR|died)")
for line in log:
    if " FLAKY " in line and "retrying" in line:
        end = secs(line)
        if end is None: continue
        print("Before: " + line[line.index("retrying"):][:150])
        shown = 0
        for other in log:
            t = secs(other)
            if t is not None and end - 45 <= t <= end and marker.search(other) and " FLAKY " not in other:
                print("   " + other[:200]); shown += 1
                if shown >= 15: break
        if shown == 0: print("   (nothing matching in that window)")
PY
echo "Tests that passed only on a retry:"
grep -c "retrying" app/build/reports/flaky-retries.txt || true
grep "retrying" app/build/reports/flaky-retries.txt | cut -c1-200 || true
exit $status
