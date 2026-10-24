#!/usr/bin/env bash
# gfxinfo over 20 dock moves (5 right, 5 left, twice) per run: jank.sh <theme folder> <name> [runs]
. "$(dirname "$0")/common.sh"
THEME="${1:?usage: jank.sh <theme folder> <name> [runs]}"
NAME="${2:?usage: jank.sh <theme folder> <name> [runs]}"
RUNS="${3:-3}"
push_theme "$THEME"
for run in $(seq 1 "$RUNS"); do
  tv '( input keyevent 3; sleep 5; dumpsys gfxinfo com.gathin.tvhome reset; for r in 1 2; do for i in 1 2 3 4 5; do input keyevent 22; sleep 0.5; done; for i in 1 2 3 4 5; do input keyevent 21; sleep 0.5; done; done; sleep 1 ) >/dev/null 2>&1; echo "'"$NAME"' run '"$run"': $(dumpsys gfxinfo com.gathin.tvhome | grep -E "Total frames rendered|Janky frames|50th|90th|99th" | tr -s " " | tr "\n" ";")"' \
    | tee -a "$OUT/$NAME-$STAMP.txt"
done
