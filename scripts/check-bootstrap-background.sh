#!/usr/bin/env bash
set -Eeuo pipefail

PACKAGE="com.monnixcode.workspace"
ACTIVITY="${PACKAGE}/.MainActivity"
APK_PATH="${APK_PATH:-android-app/app/build/outputs/apk/debug/app-debug.apk}"
WAIT_SECONDS="${WAIT_SECONDS:-20}"
SERIAL="${ADB_SERIAL:-}"
RESET=0
LOG_FILE="${LOG_FILE:-monixcode-bootstrap-logcat.txt}"

usage() {
    cat <<'USAGE'
Usage: scripts/check-bootstrap-background.sh [options]

Options:
  --apk PATH       APK to install (default: android-app/app/build/outputs/apk/debug/app-debug.apk)
  --serial ID      ADB device serial (or set ADB_SERIAL)
  --wait SECONDS   Seconds to collect logs after launch (default: 20)
  --reset          Clear app data before launch; removes the app runtime
  --log PATH       Save filtered logcat output (default: monixcode-bootstrap-logcat.txt)
  -h, --help       Show this help

The script does not reset app data unless --reset is explicitly provided.
The default APK must be a debuggable build for the run-as runtime marker check.
USAGE
}

while (($#)); do
    case "$1" in
        --apk) APK_PATH="$2"; shift 2 ;;
        --serial) SERIAL="$2"; shift 2 ;;
        --wait) WAIT_SECONDS="$2"; shift 2 ;;
        --log) LOG_FILE="$2"; shift 2 ;;
        --reset) RESET=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

ADB=(adb)
if [[ -n "$SERIAL" ]]; then ADB+=( -s "$SERIAL" ); fi

if ! command -v adb >/dev/null 2>&1; then
    echo "ERROR: adb is not installed or not on PATH" >&2
    exit 1
fi
if [[ ! -f "$APK_PATH" ]]; then
    echo "ERROR: APK not found: $APK_PATH" >&2
    echo "Build it first with: ./android-app/gradlew -p android-app assembleDebug" >&2
    exit 1
fi

"${ADB[@]}" wait-for-device
DEVICE="$(${ADB[@]} shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
ABI="$(${ADB[@]} shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r')"
ANDROID="$(${ADB[@]} shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
echo "Device: ${DEVICE:-unknown}"
echo "Android: ${ANDROID:-unknown}"
echo "ABI: ${ABI:-unknown}"
echo "APK: $APK_PATH"

if (( RESET )); then
    echo "Resetting app data because --reset was supplied"
    "${ADB[@]}" shell pm clear "$PACKAGE" >/dev/null
fi

echo "Installing APK"
"${ADB[@]}" install -r "$APK_PATH" >/dev/null

echo "Clearing logcat"
"${ADB[@]}" logcat -c

echo "Launching $ACTIVITY"
"${ADB[@]}" shell am force-stop "$PACKAGE"
"${ADB[@]}" shell am start -W -n "$ACTIVITY" >/tmp/monixcode-am-start.txt
cat /tmp/monixcode-am-start.txt

echo "Collecting logs for ${WAIT_SECONDS}s"
sleep "$WAIT_SECONDS"

"${ADB[@]}" logcat -d -v threadtime \
    -s MONIXCODE:I MonnixcodePTY:I AndroidRuntime:E ActivityManager:E \
    > "$LOG_FILE" || true

echo
echo "===== Filtered Bootstrap / PTY logcat ====="
cat "$LOG_FILE"

echo "===== End logcat ====="

if grep -qE 'FATAL EXCEPTION|Process: com\.monnixcode\.workspace.*died|AndroidRuntime.*FATAL' "$LOG_FILE"; then
    echo "RESULT: FAIL - fatal exception detected" >&2
    exit 10
fi

PID="$(${ADB[@]} shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')"
if [[ -z "$PID" ]]; then
    echo "RESULT: FAIL - app process is not running after launch" >&2
    exit 11
fi
echo "Process: $PID"

STATUS="not-checked"
if "${ADB[@]}" shell run-as "$PACKAGE" test -f files/monnixcode-runtime/bootstrap-install.json >/dev/null 2>&1; then
    STATUS="verified-marker-present"
elif "${ADB[@]}" shell run-as "$PACKAGE" test -f files/monnixcode-runtime/usr/etc/monnixcode-bootstrap-installed >/dev/null 2>&1; then
    STATUS="layout-marker-present"
else
    STATUS="marker-not-present-or-run-as-unavailable"
fi
echo "Runtime marker: $STATUS"

if grep -q 'Embedded Bootstrap install finished: verified' "$LOG_FILE"; then
    echo "RESULT: PASS - background Bootstrap install completed as verified"
elif grep -q 'Embedded Bootstrap install failed' "$LOG_FILE"; then
    echo "RESULT: WARN - app stayed alive but Bootstrap installation failed"
    exit 12
else
    echo "RESULT: WARN - app stayed alive; Bootstrap completion log was not observed"
fi
