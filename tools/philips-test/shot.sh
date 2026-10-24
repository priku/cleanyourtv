#!/usr/bin/env bash
# Screenshot after a key script: shot.sh home 'input keyevent 3; sleep 3'
. "$(dirname "$0")/common.sh"
NAME="${1:?usage: shot.sh <name> '<key script>'}"
KEYS="${2:-true}"
FILE="$OUT/$NAME-$STAMP.png"
pull_b64 "$KEYS; sleep 2; screencap -p /sdcard/cyt-shot.png" /sdcard/cyt-shot.png "$FILE"
sips -Z 960 "$FILE" >/dev/null 2>&1 || true
echo "$FILE"
