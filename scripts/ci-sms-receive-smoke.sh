#!/usr/bin/env bash
set -euo pipefail
# Disposable emulator only. Console injection is fabricated inbound data, never a carrier send.
serial=emulator-5554
[[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
# connectedDebugAndroidTest removes its packages when finished. Reinstall this build only.
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
app=com.axiomstead.foldsms
# Ordinary grants only. If installer restrictions block either grant, fail without a bypass.
adb -s "$serial" shell pm grant "$app" android.permission.READ_SMS
adb -s "$serial" shell pm grant "$app" android.permission.RECEIVE_SMS
body="FoldSMS fabricated inbound CI ${GITHUB_RUN_ID:?} ${GITHUB_RUN_ATTEMPT:?}"
adb -s "$serial" emu sms send 2025550199 "$body"
mkdir -p app/build/reports
adb -s "$serial" shell am instrument -w -r \
  -e class com.axiomstead.foldsms.SmsProviderSmokeTest \
  -e smsSmoke true -e expectedBody "'$body'" \
  "$app.test/androidx.test.runner.AndroidJUnitRunner" | tee app/build/reports/sms-receive-smoke.txt
python3 - <<'PY'
from pathlib import Path
log = Path('app/build/reports/sms-receive-smoke.txt').read_text()
assert 'OK (1 test)' in log and 'FAILURES' not in log and 'INSTRUMENTATION_FAILED' not in log, log
PY
