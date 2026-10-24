#!/usr/bin/env bash
# One DPAD_RIGHT in the first grid row, recorded and tracked: frames.sh <theme folder> <name> [runs]
# Prints "time:left-edge-x" per recorded frame (960x540 coordinates). The peak above the final value is the
# overshoot; the time from the first move to the last change is the settle time.
. "$(dirname "$0")/common.sh"
THEME="${1:?usage: frames.sh <theme folder> <name> [runs]}"
NAME="${2:?usage: frames.sh <theme folder> <name> [runs]}"
RUNS="${3:-2}"
PY="$KIT/venv/bin/python"
[ -x "$PY" ] || { echo "run $KIT/setup.sh first" >&2; exit 1; }
push_theme "$THEME"
for run in $(seq 1 "$RUNS"); do
  FILE="$OUT/$NAME-$STAMP-$run.mp4"
  pull_b64 "input keyevent 3; sleep 3; input keyevent 20; sleep 1; input keyevent 21; input keyevent 21; input keyevent 21; sleep 1; rm -f /sdcard/cyt-rec.mp4; (screenrecord --time-limit 3 --size 960x540 /sdcard/cyt-rec.mp4 &); sleep 1; input keyevent 22; sleep 3" /sdcard/cyt-rec.mp4 "$FILE"
  "$PY" -I "$KIT/track.py" "$FILE"
done
