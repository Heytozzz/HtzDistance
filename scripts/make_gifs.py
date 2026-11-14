"""Builds one GIF per take from the rec-<take>-<frame>-<fov> screenshots of RecordingClientTest.

Usage: python scripts/make_gifs.py versions/26.3/build/run/clientGameTest/screenshots docs/gifs [width]
"""
import math
import re
import sys
from collections import defaultdict
from pathlib import Path

from PIL import Image

shots = Path(sys.argv[1])
out = Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
width = int(sys.argv[3]) if len(sys.argv) > 3 else 640

takes = defaultdict(list)
for f in shots.glob('*.png'):
    m = re.search(r'rec-([a-z]+)-(\d+)-(\d+)-(\d+)\.png$', f.name)
    if m:
        takes[m.group(1)].append((int(m.group(2)), f, int(m.group(3)) / 1000.0, int(m.group(4))))

for take, frames in takes.items():
    frames.sort()
    images = []
    for _, f, exact, rendered in frames:
        im = Image.open(f).convert('RGB')
        # Rendered slightly wider than wanted: crop the center to the exact field of view.
        scale = math.tan(math.radians(exact) / 2) / math.tan(math.radians(rendered) / 2)
        cw, ch = im.width * scale, im.height * scale
        left, top = (im.width - cw) / 2, (im.height - ch) / 2
        im = im.resize((im.width, im.height), Image.LANCZOS, box=(left, top, left + cw, top + ch))
        h = round(im.height * width / im.width)
        images.append(im.resize((width, h), Image.LANCZOS))
    # One shared palette keeps colors stable between frames (no dithering shimmer).
    palette = images[len(images) // 2].quantize(colors=255, method=Image.MEDIANCUT)
    gif = [im.quantize(palette=palette, dither=Image.Dither.NONE) for im in images]
    durations = [70] * len(gif)
    path = out / f'htzdistance-{take}.gif'
    gif[0].save(path, save_all=True, append_images=gif[1:], duration=durations, loop=0, optimize=True, disposal=1)
    print(take, len(gif), 'frames', round(path.stat().st_size / 1e6, 1), 'MB')
