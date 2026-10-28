#!/usr/bin/env bash
set -euo pipefail

expected_api="$1"
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "$expected_api"
test -z "$(adb shell pm list packages io.github.jichuo1.meguviz.drawable.contracttest | tr -d '\r')"
adb install android/build/outputs/apk/androidTest/debug/android-debug-androidTest.apk | tee verification/api/install.log
tr -d '\r' < verification/api/install.log | grep -x 'Success' > /dev/null
adb shell am instrument -w -r io.github.jichuo1.meguviz.drawable.contracttest/io.github.jichuo1.meguviz.drawable.android.test.ContractInstrumentation | tee verification/api/result.log
tr -d '\r' < verification/api/result.log | grep -x 'CONTRACT PASS checks=52' > /dev/null
tr -d '\r' < verification/api/result.log | grep -x 'INSTRUMENTATION_CODE: -1' > /dev/null
