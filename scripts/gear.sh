#!/usr/bin/env bash
# Pleos Connect 에뮬레이터의 가상 콘솔(에뮬레이터 내부 8088 포트) HTTP API로 기어를 바꾼다.
# 사용법: scripts/gear.sh P|R|N|D   (adb 대상이 여러 개면 ANDROID_SERIAL 지정)
set -euo pipefail

case "${1:-}" in
  P|p) GEAR=Park ;;
  R|r) GEAR=Reverse ;;
  N|n) GEAR=Neutral ;;
  D|d) GEAR=Drive ;;
  *) echo "usage: $0 P|R|N|D" >&2; exit 1 ;;
esac

ADB="${ADB:-adb}"
PORT="${CONSOLE_PORT:-18088}"
BASE="http://127.0.0.1:${PORT}"

"$ADB" forward "tcp:${PORT}" tcp:8088 >/dev/null
# 콘솔은 브라우저 세션 하트비트가 있을 때만 입력을 받는다(emulator_inactive 방지).
curl -fsS -X POST "${BASE}/browser/session?client=side-cli" >/dev/null
# 기어 변경 시 브레이크를 밟은 상태로 둔다.
curl -fsS -X PUT "${BASE}/vehicle/controls?speed=0&accelerator=0&brake=100&steering=0" >/dev/null
curl -fsS -X PUT "${BASE}/vehicle/gear?gear=${GEAR}"
echo
