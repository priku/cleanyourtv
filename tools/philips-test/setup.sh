#!/usr/bin/env bash
# The Python packages frames.sh needs, in a venv of their own next to these scripts.
set -euo pipefail
KIT="$(cd "$(dirname "$0")" && pwd)"
python3 -m venv "$KIT/venv"
"$KIT/venv/bin/pip" -q install pillow numpy imageio-ffmpeg
echo "ready: $KIT/venv"
