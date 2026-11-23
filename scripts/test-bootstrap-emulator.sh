#!/usr/bin/env bash
set -Eeuo pipefail

PACKAGE="com.monnixcode.workspace"
ACTIVITY="${PACKAGE}/.MainActivity"
APK_PATH="${APK_PATH:-android-app/app/build/outputs/apk/debug/app-debug.apk}"
WAIT_SECONDS="${WAIT_SECONDS:-90}"
LOG_FILE="${LOG_FILE:-bootstrap-emulator-logcat.txt}"
SERIAL="${ADB_SERIAL:-}"

usage() {
    cat <<'USAGE'
Usage: scripts/test-bootstrap-emulator.sh [options]

Options:
  --apk PATH       Debug APK path
  --serial ID      ADB serial; defaults to the only connected device
  --wait SECONDS   Maximum wait for verified marker (default: 90)
  --log PATH       Logcat output path
  -h, --help       Show help

The script clears only the target app data so the test always exercises first-launch
embedded Bootstrap installation. It requires a debuggable APK because it uses run-as.
USAGE
}

while (($#)); do
    case "$1" in
        --apk) APK_PATH="$2"; shift 2 ;;
        --serial) SERIAL="$2"; shift 2 ;;
        --wait) WAIT_SECONDS="$2"; shift 2 ;;
        --log) LOG_FILE="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

ADB=(adb)
if [[ -n "$SERIAL" ]]; then ADB+=( -s "$SERIAL" ); fi

command -v adb >/dev/null 2>&1 || { echo "ERROR: adb is required" >&2; exit 1; }
[[ -f "$APK_PATH" ]] || { echo "ERROR: APK not found: $APK_PATH" >&2; exit 1; }

"${ADB[@]}" wait-for-device
"${ADB[@]}" install -r "$APK_PATH" >/dev/null
"${ADB[@]}" shell pm clear "$PACKAGE" >/dev/null
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am force-stop "$PACKAGE"
"${ADB[@]}" shell am start -W -n "$ACTIVITY"

start_epoch=$(date +%s)
verified=0
shell_ready() {
    "${ADB[@]}" shell run-as "$PACKAGE" sh -c '
        shell=files/monnixcode-runtime/usr/bin/sh
        if test -x "$shell"; then
            exit 0
        fi
        test -L "$shell" &&
            test -x files/monnixcode-runtime/usr/bin/dash &&
            test "$(readlink "$shell")" = dash
    ' >/dev/null 2>&1
}
while true; do
    if "${ADB[@]}" shell run-as "$PACKAGE" test -f files/monnixcode-runtime/bootstrap-install.json >/dev/null 2>&1 && \
       shell_ready; then
        verified=1
        break
    fi
    now=$(date +%s)
    if (( now - start_epoch >= WAIT_SECONDS )); then break; fi
    sleep 2
done

"${ADB[@]}" logcat -d -v threadtime \
    -s MONIXCODE:I MonnixcodePTY:I AndroidRuntime:E ActivityManager:I ActivityTaskManager:I \
    > "$LOG_FILE" || true

cat "$LOG_FILE"

if grep -qE 'FATAL EXCEPTION|AndroidRuntime.*FATAL|Process: com\.monnixcode\.workspace.*died' "$LOG_FILE"; then
    echo "FAIL: fatal exception or process death detected" >&2
    exit 10
fi

if [[ "$verified" != 1 ]]; then
    echo "FAIL: Bootstrap marker or executable shell was not ready within ${WAIT_SECONDS}s" >&2
    echo "Runtime listing:" >&2
    "${ADB[@]}" shell run-as "$PACKAGE" find files/monnixcode-runtime -maxdepth 3 -type f -print 2>&1 || true
    exit 11
fi

if ! grep -q 'Embedded Bootstrap install finished: verified' "$LOG_FILE"; then
    echo "FAIL: verified marker exists but completion log was not observed" >&2
    exit 12
fi

PID="$("${ADB[@]}" shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')"
if [[ -z "$PID" ]]; then
    echo "FAIL: app process is not alive after Bootstrap installation" >&2
    exit 13
fi
echo "Process: $PID"

echo "PASS: embedded Bootstrap installed and usr/bin/sh is executable on the emulator"
