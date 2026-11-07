#!/usr/bin/env bash
# Runs the instrumented tests on the CI emulator, then lists the tests that needed a retry (tag FLAKY) whatever the outcome.
set -uo pipefail
serial=emulator-5554
adb -s "$serial" logcat -c || true
ANDROID_SERIAL="$serial" ./gradlew --no-daemon :app:connectedDebugAndroidTest
status=$?
mkdir -p app/build/reports
adb -s "$serial" logcat -d -s FLAKY:W > app/build/reports/flaky-retries.txt 2>/dev/null || true
echo "Tests that passed only on a retry:"
grep -c "retrying" app/build/reports/flaky-retries.txt || true
grep "retrying" app/build/reports/flaky-retries.txt | cut -c1-200 || true
exit $status
