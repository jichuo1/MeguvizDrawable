#!/usr/bin/env bash
set -euo pipefail

expected_api="$1"
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "$expected_api"
core_package=io.github.jichuo1.meguviz.drawable.contracttest
provider_package=io.github.jichuo1.meguviz.drawable.providerfixture
provider_test_package=io.github.jichuo1.meguviz.drawable.providerfixture.test
aapt="$ANDROID_HOME/build-tools/37.0.0/aapt"
installed_packages="$(adb shell pm list packages | tr -d '\r')"
for package in "$core_package" "$provider_package" "$provider_test_package"; do
    if grep -Fx "package:$package" <<< "$installed_packages" > /dev/null; then
        echo "Refusing to replace existing package: $package" >&2
        exit 1
    fi
done
"$aapt" dump badging verification/api/core-test.apk > verification/api/core-badging.log
"$aapt" dump badging verification/api/provider-app.apk > verification/api/provider-app-badging.log
"$aapt" dump badging verification/api/provider-test.apk > verification/api/provider-test-badging.log
grep -F "package: name='$core_package' " verification/api/core-badging.log > /dev/null
grep -F "package: name='$provider_package' " verification/api/provider-app-badging.log > /dev/null
grep -F "package: name='$provider_test_package' " verification/api/provider-test-badging.log > /dev/null
"$aapt" dump xmltree verification/api/core-test.apk AndroidManifest.xml > verification/api/core-manifest.log
"$aapt" dump xmltree verification/api/provider-test.apk AndroidManifest.xml > verification/api/provider-test-manifest.log
grep -F 'android:name(0x01010003)="io.github.jichuo1.meguviz.drawable.android.test.ContractInstrumentation"' verification/api/core-manifest.log > /dev/null
grep -F "android:targetPackage(0x01010021)=\"$core_package\"" verification/api/core-manifest.log > /dev/null
grep -F 'android:name(0x01010003)="example.provider.app.ProviderInstrumentation"' verification/api/provider-test-manifest.log > /dev/null
grep -F "android:targetPackage(0x01010021)=\"$provider_package\"" verification/api/provider-test-manifest.log > /dev/null
install_and_verify() {
    local package="$1" apk="$2" label="$3"
    adb install "$apk" | tee "verification/api/$label-install.log"
    tr -d '\r' < "verification/api/$label-install.log" | grep -x 'Success' > /dev/null
    mapfile -t paths < <(adb shell pm path "$package" | tr -d '\r' | sed -n 's/^package://p')
    test "${#paths[@]}" -eq 1
    adb pull "${paths[0]}" "verification/api/$label-installed.apk" > "verification/api/$label-pull.log" 2>&1
    cmp "$apk" "verification/api/$label-installed.apk"
    sha256sum "$apk" "verification/api/$label-installed.apk" > "verification/api/$label-installed.sha256"
}
install_and_verify "$core_package" verification/api/core-test.apk core
adb shell am instrument -w -r io.github.jichuo1.meguviz.drawable.contracttest/io.github.jichuo1.meguviz.drawable.android.test.ContractInstrumentation | tee verification/api/result.log
tr -d '\r' < verification/api/result.log | grep -x 'CONTRACT PASS checks=52' > /dev/null
tr -d '\r' < verification/api/result.log | grep -x 'INSTRUMENTATION_CODE: -1' > /dev/null
adb shell am instrument -w -r -e mode protocol io.github.jichuo1.meguviz.drawable.contracttest/io.github.jichuo1.meguviz.drawable.android.test.ContractInstrumentation | tee verification/api/protocol-result.log
tr -d '\r' < verification/api/protocol-result.log | grep -x 'PROTOCOL PASS checks=22' > /dev/null
tr -d '\r' < verification/api/protocol-result.log | grep -x 'INSTRUMENTATION_CODE: -1' > /dev/null
adb shell am instrument -w -r -e mode wrapper io.github.jichuo1.meguviz.drawable.contracttest/io.github.jichuo1.meguviz.drawable.android.test.ContractInstrumentation | tee verification/api/wrapper-result.log
tr -d '\r' < verification/api/wrapper-result.log | grep -x 'WRAPPER PASS checks=36' > /dev/null
tr -d '\r' < verification/api/wrapper-result.log | grep -x 'INSTRUMENTATION_CODE: -1' > /dev/null
install_and_verify "$provider_package" verification/api/provider-app.apk provider-app
install_and_verify "$provider_test_package" verification/api/provider-test.apk provider-test
adb shell am instrument -w -r "$provider_test_package/example.provider.app.ProviderInstrumentation" | tee verification/api/provider-result.log
tr -d '\r' < verification/api/provider-result.log | grep -x 'PROVIDER PASS: cut geometry, software Canvas, owner isolation, tint rejection' > /dev/null
tr -d '\r' < verification/api/provider-result.log | grep -x 'INSTRUMENTATION_CODE: -1' > /dev/null
