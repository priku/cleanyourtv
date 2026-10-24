# Sourced by the scripts here.
set -euo pipefail
KIT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$KIT/../.." && pwd)"
export TV_HOST="${TV_HOST:-192.168.50.9}"
OUT="$KIT/out"
mkdir -p "$OUT"
STAMP="$(date +%Y%m%d-%H%M%S)"
tv() { "$ROOT/tools/venv/bin/python" -I "$ROOT/tools/tv.py" "$@"; }
# Runs $1 quietly on the TV, then base64s the file $2 there and decodes it into $3 here.
pull_b64() { tv "( $1 ) >/dev/null 2>&1; toybox base64 $2" | sed -n '/^[A-Za-z0-9+\/=]\{60,\}$/,$p' | base64 -d > "$3"; }
push_theme() { "$ROOT/tools/theme.sh" "$1" >/dev/null 2>&1; }
