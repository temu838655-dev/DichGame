#!/usr/bin/env bash
# Stages native TV web projects for vendor-specific packaging.
# This does NOT sign a Samsung .wgt or promise HDMI overlay compatibility.
set -euo pipefail
cd "$(dirname "$0")/.."
out="${1:-dist-tv}"
mkdir -p "$out/tizen" "$out/webos"
for name in tizen webos; do
  cp "tv-$name/index.html" "$out/$name/index.html"
  cp "tv-web-common/receiver.js" "$out/$name/receiver.js"
  cp "tv-web-common/styles.css" "$out/$name/styles.css"
done
cp tv-tizen/config.xml "$out/tizen/config.xml"
cp tv-webos/appinfo.json "$out/webos/appinfo.json"
python3 - "$out" <<'PY'
import os, sys, json, zlib, struct
from xml.etree import ElementTree as ET
root = sys.argv[1]
ET.parse(os.path.join(root, 'tizen', 'config.xml'))
with open(os.path.join(root, 'webos', 'appinfo.json'), encoding='utf-8') as f:
    json.load(f)
# Generate a reproducible 80 x 80 PNG app icon without Pillow or external fonts.
font = {
'D': ["11110","10001","10001","10001","10001","10001","11110"],
'G': ["01111","10000","10000","10111","10001","10001","01111"],
}
size = 80
rows = []
for y in range(size):
    raw = bytearray([0])
    for x in range(size):
        bg = (18, 29, 31, 255)
        border = x in (2, 3, 76, 77) or y in (2, 3, 76, 77)
        v = (244, 195, 140, 255) if border else bg
        for glyph, x0 in [('D', 14), ('G', 44)]:
            gx, gy = (x - x0) // 5, (y - 22) // 5
            if x >= x0 and 22 <= y < 57 and 0 <= gx < 5 and 0 <= gy < 7:
                if font[glyph][gy][gx] == '1': v = (244, 195, 140, 255)
        raw.extend(v)
    rows.append(bytes(raw))
def chunk(kind, data):
    return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 0xffffffff)
png = (b'\x89PNG\r\n\x1a\n'
       + chunk(b'IHDR', struct.pack('!2I5B', size, size, 8, 6, 0, 0, 0))
       + chunk(b'IDAT', zlib.compress(b''.join(rows), level=9))
       + chunk(b'IEND', b''))
for name in ('tizen','webos'):
    with open(os.path.join(root, name, 'icon.png'), 'wb') as f:
        f.write(png)
print('Native TV sources staged and checked in', root)
PY
if command -v zip >/dev/null 2>&1; then
  (cd "$out/tizen" && zip -q -r ../DichGame-TV-Tizen-SOURCE.zip .)
  (cd "$out/webos" && zip -q -r ../DichGame-TV-webOS-SOURCE.zip .)
fi
echo "Samsung: use Tizen Studio + your TV-specific signing profile to build .wgt."
echo "LG: run ares-package $out/webos -o $out to build .ipk (Developer Mode required to install)."
