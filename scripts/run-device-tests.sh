#!/bin/bash
#
# Run the instrumented (androidTest) tests that must never be skipped, on one connected device.
#
# GitHub CI is intentionally disabled for this fork, so `./gradlew :app:check` is the enforcement
# point: it depends on the deviceTests task, which runs this. Issue #369 shipped a crash on every
# tablet and every phone in landscape, and the reason nothing caught it was that no test ever opened
# a user profile and no test ever ran in landscape. UserProfileTest closes that, and is only worth
# anything if it actually runs.
#
# Usage: scripts/run-device-tests.sh [--serial <serial>] [--class <fqcn>]
#   ./gradlew :app:check -PskipDeviceTests=true   # explicit opt-out, e.g. on a headless box
#
# Why not `./gradlew connectedAndroidTest`:
#   - it runs on EVERY attached device, and this machine keeps more than one emulator up (see
#     TESTS.md); and
#   - per TESTS.md it uninstalls afterwards, wiping app data — which logs the Reddit test account
#     out of the emulator on every run, for no benefit here.
# `adb install -r` keeps the install and its data, and targets exactly one serial.

set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root" || exit 1

# Only UserProfileTest by default. The rest of the ru.otus.pandina suite is deliberately left out of
# `check`: LoginTest and MainTest need the network, and several tests call requireAnonymousInstall()
# and so demand a freshly-cleared install. Both make them unfit for a gate that has to be
# deterministic. Run those by hand — see TESTS.md.
test_class="ru.otus.pandina.tests.UserProfileTest"
serial="${ANDROID_SERIAL:-}"

# `shift 2` is a no-op when only one positional is left, so a flag given without its value would
# spin this loop forever. Require the value explicitly.
require_value() {
  if [ "$2" -lt 2 ]; then
    echo "$1 needs a value." >&2
    echo "Usage: scripts/run-device-tests.sh [--serial <serial>] [--class <fqcn>]" >&2
    exit 2
  fi
}

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) require_value "$1" "$#"; serial="$2"; shift 2 ;;
    --class)  require_value "$1" "$#"; test_class="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

# A machine that keeps several emulators up needs to say which one is Continuum's. local.properties
# is already the per-machine, never-committed config file, so the serial lives there:
#     deviceSerial=emulator-5556
if [ -z "$serial" ] && [ -f local.properties ]; then
  serial="$(sed -n 's/^[[:space:]]*deviceSerial[[:space:]]*=[[:space:]]*//p' local.properties | tail -1)"
fi

mapfile -t attached < <(adb devices | awk '$2 == "device" { print $1 }')

if [ -z "$serial" ]; then
  case "${#attached[@]}" in
    0) serial="" ;;
    1) serial="${attached[0]}" ;;
    *)
      echo "More than one device is attached and none was chosen:"
      printf '  %s\n' "${attached[@]}"
      echo
      echo "Pick one, in any of these ways:"
      echo "  scripts/run-device-tests.sh --serial ${attached[0]}"
      echo "  ANDROID_SERIAL=${attached[0]} ./gradlew :app:check"
      echo "  echo 'deviceSerial=${attached[0]}' >> local.properties   # persists for this machine"
      exit 1
      ;;
  esac
fi

if [ -z "$serial" ]; then
  echo "No device attached, so $test_class did not run."
  echo
  echo "These tests are part of check and are not skipped silently — they are the only layer that"
  echo "exercises a real inflate of activity_view_user_detail in landscape (issue #369)."
  echo
  echo "Start an emulator, or opt out for this run:"
  echo "  ./gradlew :app:check -PskipDeviceTests=true"
  exit 1
fi

if ! printf '%s\n' "${attached[@]}" | grep -qxF "$serial"; then
  echo "Device \"$serial\" is not attached. Attached: ${attached[*]:-none}"
  exit 1
fi

echo "Device:     $serial"
echo "Test class: $test_class"

# assembleDebug splits per ABI and stamps the versionName (and "-dirty") into the filename, so glob
# rather than guess. arm64 runs fine on the x86_64 emulators here; there is no x86_64 split.
app_apk="$(ls -t app/build/outputs/apk/debug/continuum-arm64-v8a-*.apk 2>/dev/null | head -1)"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

if [ -z "$app_apk" ] || [ ! -f "$test_apk" ]; then
  echo "APKs are missing. The deviceTests Gradle task builds them; standalone, run:"
  echo "  ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest"
  exit 1
fi

echo "App APK:    $app_apk"

# -r keeps the existing install and its data; -t allows a test-only build (AGP marks debug APKs
# testOnly). Deliberately NOT -d: that flag permits an APK with a LOWER versionCode to replace a
# newer install, and this project has a documented trap there — the older app then meets the newer
# app's Room schema and dies with "A migration from 37 to 36 was required" until `pm clear`, which
# also logs the Reddit test account out. Since this runs from `check`, on any branch, an accidental
# downgrade has to be refused rather than forced.
for apk in "$app_apk" "$test_apk"; do
  if ! install_log="$(adb -s "$serial" install -r -t "$apk" 2>&1)"; then
    echo "Install failed: $apk"
    echo "$install_log"
    if printf '%s' "$install_log" | grep -q "INSTALL_FAILED_VERSION_DOWNGRADE"; then
      echo
      echo "That is a downgrade: the device has a newer versionCode than this build. Installing"
      echo "over it would leave the newer Room schema behind and the app would not start."
      echo "Uninstall first if that is really what you want:"
      echo "  adb -s $serial uninstall org.cygnusx1.continuum.debug"
    fi
    exit 1
  fi
done

output="$(mktemp)"
trap 'rm -f "$output"' EXIT

adb -s "$serial" shell am instrument -w -r \
  -e class "$test_class" \
  org.cygnusx1.continuum.debug.test/com.kaspersky.kaspresso.runner.KaspressoRunner \
  2>&1 | tee "$output" | grep -E "^INSTRUMENTATION_STATUS: test=" | sed 's/^.*test=/  /' | awk '!seen[$0]++'

echo

# `am instrument` exits 0 even when tests fail, so the result has to be read out of the stream. A
# crashed process ("Process crashed while executing") never prints an OK line, which is how the #369
# regression showed up, so treat anything that is not an explicit OK as a failure.
if grep -qE "^OK \([0-9]+ test" "$output"; then
  echo "$(grep -oE '^OK \([0-9]+ tests?\)' "$output" | tail -1) — $test_class"
  exit 0
fi

echo "Instrumented tests FAILED."
echo
sed -n '/INSTRUMENTATION_STATUS: stack=/,/^INSTRUMENTATION_STATUS_CODE/p' "$output" | head -40
grep -E "^Tests run:|^FAILURES|^Process crashed" "$output" | head -5
exit 1
