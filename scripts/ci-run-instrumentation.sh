#!/usr/bin/env bash
# Runs the instrumented tests on the CI emulator, then lists the tests that needed a retry (tag FLAKY) whatever the outcome.
set -uo pipefail
serial=emulator-5554
adb -s "$serial" logcat -c || true
adb -s "$serial" logcat -G 16M || true
ANDROID_SERIAL="$serial" ./gradlew --no-daemon :app:connectedDebugAndroidTest
status=$?
mkdir -p app/build/reports
adb -s "$serial" logcat -d -s FLAKY:W > app/build/reports/flaky-retries.txt 2>/dev/null || true
# Evidence for stalls: the whole log is uploaded, and the lines that usually explain a stall are printed.
adb -s "$serial" logcat -d -b all -v threadtime > app/build/reports/emulator-logcat.txt 2>/dev/null || true
echo "Possible causes of stalls (ANR, crashes, long garbage collections, dialogs):"
grep -E "ANR in|am_anr|FATAL EXCEPTION|Process .* has died|am_crash|GrantPermissionsActivity|PermissionController|Skipped [0-9]+ frames|Davey!|Long monitor contention|paused for [0-9]{3,}" app/build/reports/emulator-logcat.txt | cut -c1-220 | head -40 || true
echo "Tests that passed only on a retry:"
grep -c "retrying" app/build/reports/flaky-retries.txt || true
grep "retrying" app/build/reports/flaky-retries.txt | cut -c1-200 || true
exit $status
