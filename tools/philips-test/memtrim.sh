#!/usr/bin/env bash
# Native heap before and after a RUNNING_LOW trim, each after a forced GC, with Home stopped behind Spotify:
# memtrim.sh <name> [runs]. Read the "Heap Alloc" column (6th number on the Native Heap line); PSS hardly
# moves on Android 8 because the allocator keeps freed pages mapped.
. "$(dirname "$0")/common.sh"
NAME="${1:?usage: memtrim.sh <name> [runs]}"
RUNS="${2:-2}"
GC='am dumpheap com.gathin.tvhome /data/local/tmp/cyt.hprof; sleep 6; rm -f /data/local/tmp/cyt.hprof'
for run in $(seq 1 "$RUNS"); do
  tv "( input keyevent 3; sleep 5; monkey -p com.spotify.tv.android -c android.intent.category.LEANBACK_LAUNCHER 1; sleep 15; $GC ) >/dev/null 2>&1; echo '--- $NAME run $run, Home: '\$(dumpsys activity activities | grep -A3 'gathin.tvhome/.MainActivity t' | grep -o 'state=[A-Z]*' | head -1); echo before:; dumpsys meminfo com.gathin.tvhome | grep -E '^ *Native Heap |^ *TOTAL |Views:'; am send-trim-memory com.gathin.tvhome RUNNING_LOW; sleep 3; ( $GC ) >/dev/null 2>&1; echo after:; dumpsys meminfo com.gathin.tvhome | grep -E '^ *Native Heap |^ *TOTAL |Views:'; ( input keyevent 3; sleep 4 ) >/dev/null 2>&1; echo crashes: \$(logcat -d -b crash | grep -c tvhome)" \
    | tee -a "$OUT/$NAME-$STAMP.txt"
done
