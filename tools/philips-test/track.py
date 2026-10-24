"""Tracks the glass highlight's left edge in a 960x540 screenrecord of one D-pad move.

The glass has a bright vertical rim; in the band where the first grid row sits, the left-most column with a
long run of bright pixels is its left edge. Prints "time:x" for every recorded frame.
"""
import glob
import os
import re
import subprocess
import sys
import tempfile

import imageio_ffmpeg
import numpy as np
from PIL import Image

# Rows of the first grid row at 960x540, calibrated for upstream's default layout (4 columns); set
# TRACK_BAND="top,bottom" and TRACK_RUN for another layout (verified only on the default layout with an empty theme `{}`: a white tile in the band is picked up
# instead of the rim, so look at the last frame before trusting the numbers).
BAND = tuple(int(v) for v in os.environ.get("TRACK_BAND", "340,470").split(","))
BRIGHT = 170                                   # a pixel this bright...
RUN = int(os.environ.get("TRACK_RUN", "70"))   # ...in this many rows of the band is the glass rim


def track(video):
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    info = subprocess.run([ff, "-i", video, "-vf", "showinfo", "-f", "null", "-"],
                          capture_output=True, text=True).stderr
    times = [float(t) for t in re.findall(r"pts_time:([0-9.]+)", info)]
    with tempfile.TemporaryDirectory() as d:
        subprocess.run([ff, "-loglevel", "error", "-i", video, "-fps_mode", "passthrough",
                        os.path.join(d, "%04d.png")], check=True,
                       stderr=subprocess.DEVNULL)
        out = []
        for i, f in enumerate(sorted(glob.glob(os.path.join(d, "*.png")))):
            a = np.asarray(Image.open(f).convert("L")).astype(int)[BAND[0]:BAND[1]]
            count = (a > BRIGHT).sum(axis=0)
            xs = [x for x in range(60, 700) if count[x] > RUN]
            t = times[i] if i < len(times) else -1
            out.append(f"{t:.3f}:{xs[0] if xs else '-'}")
    return out


if __name__ == "__main__":
    for v in sys.argv[1:]:
        print(os.path.basename(v), " ".join(track(v)))
