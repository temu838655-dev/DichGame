#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 2 ]]; then echo "Usage: bash install-dev.sh TV_SERIAL signed.wgt" >&2; exit 2; fi
serial="$1"
package="$2"
[[ -f "$package" && "$package" == *.wgt ]] || { echo "Provide an existing signed WGT" >&2; exit 2; }
command -v tizen >/dev/null
command -v sdb >/dev/null
sdb devices
# Confirm this is the intended connected target, not another TV.
sdb devices | awk -v serial="$serial" '$1 == serial && $2 == "device" {found=1} END {exit !found}'
python3 - "$package" <<'PY'
import sys, zipfile, xml.etree.ElementTree as ET
with zipfile.ZipFile(sys.argv[1]) as z:
    assert {"config.xml", "author-signature.xml", "signature1.xml"}.issubset(z.namelist()), "Signed WGT required"
    root=ET.fromstring(z.read("config.xml"))
    app=root.find("{http://tizen.org/ns/widgets}application")
    assert app is not None and app.attrib.get("id")=="DichGameTV.Receiver", "Wrong application"
PY
tizen install -s "$serial" -n "$(basename "$package")" -- "$(cd "$(dirname "$package")" && pwd)"
tizen run -s "$serial" -p DichGameTV.Receiver
