# Philips test kit (fork only)

Small scripts used to test upstream PRs on the Philips 65PUS8102/12 (TPM171E, Android TV 8.0 / API 26,
2 GB). They were rewritten by hand for every report; keeping them here makes runs repeatable and the
numbers comparable between PRs. Prefer upstream's own tools where they fit (`tools/bench.py`,
`tools/trace.py`, `tools/screendiff.py`): the maintainer knows them and their output reads the same in
every report.

All scripts take `TV_HOST` (default `192.168.50.9`) and write into `tools/philips-test/out/`, one file per
run with a timestamp in its name. Never reuse a picture's file name: an image viewer can show the old
picture for the same path.

| Script | What it measures |
|---|---|
| `shot.sh <name> '<keys>'` | Screenshot after a key script, saved as `out/<name>-<time>.png` (960 px wide) |
| `frames.sh <theme> <name>` | Pushes a theme, records one DPAD_RIGHT in the grid with `screenrecord`, tracks the glass highlight's left edge frame by frame (overshoot and settle time) |
| `jank.sh <theme> <name> [runs]` | `dumpsys gfxinfo` over 20 dock moves per run: total frames, janky share, 50/90/99th percentiles |
| `memtrim.sh <name>` | Native heap allocated before and after `am send-trim-memory RUNNING_LOW`, each after a forced GC, with Home really stopped behind Spotify |

`frames.sh` needs Pillow, NumPy and imageio-ffmpeg; `setup.sh` puts them in `tools/philips-test/venv`.

## Rules for a report

- Name the PR head SHA, the main SHA it was merged onto, and exactly what was compared (which keys,
  which theme). A number without that context gets reused for something that was never measured.
- Check every number against the run's output before posting; write the comment to a file first.
- Say what was not tested.
- Tell the owner before a run that reboots the TV, turns the screen off, switches inputs or plays sound.

## Android 8 notes that cost time once

- `am send-trim-memory` with a background level is refused while Home counts as foreground; use
  `RUNNING_LOW`, and make sure Home is STOPPED (`dumpsys activity activities`): behind Play Store it stays
  PAUSED because Play Store never goes idle. Spotify works.
- Freed native memory shows in `Heap Alloc`, not in PSS: the allocator keeps the pages mapped.
- `screenrecord` writes only changed frames (about every 25-40 ms here); compare times, not frame counts.
- `screencap` output is mixed into stdout: wrap the commands before it in `( ... ) >/dev/null 2>&1`.
- `content write` does not exist on Android 8/9 (prints an error, exits 0); theme pushes fall back to cp.
- Long press: `sendevent /dev/input/event1 1 352 1 … 1 352 0` (KEY_OK is 352); `input keyevent --longpress`
  arrives as a short press.
- An accessibility service's screen-off receiver is armed when the service connects: a setting changed
  after that needs the service to reconnect (toggle the enabled list) before it applies.
