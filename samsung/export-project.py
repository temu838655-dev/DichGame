#!/usr/bin/env python3
"""Export the exact receiver source for local Samsung Certificate Manager signing."""
import argparse
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZipInfo
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("output", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent
manifest = ET.parse(root / "config.xml").getroot()
app = manifest.find("{http://tizen.org/ns/widgets}application")
assert app is not None and app.get("id") == "DichGameTV.Receiver"
names = ("config.xml", "index.html", "receiver.js", "pairing.js")
args.output.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(args.output, "w", compression=ZIP_DEFLATED) as archive:
    for name in names:
        data = (root / name).read_bytes()
        assert data, name
        entry = ZipInfo(name, (2020, 1, 1, 0, 0, 0))
        entry.compress_type = ZIP_DEFLATED
        archive.writestr(entry, data)
    archive.writestr("SIGNING-REQUIRED.txt", "This is an unsigned source project, not an installable WGT. Extract to a directory, remove this note, then sign and package with Tizen Studio using your own Samsung certificate profile for the target TV. Never upload private certificates or passwords to GitHub.\n")
with ZipFile(args.output) as archive:
    assert archive.testzip() is None
    assert not any("signature" in name for name in archive.namelist())
print(args.output)
