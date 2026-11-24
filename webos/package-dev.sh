#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v ares-package >/dev/null || { echo "Install LG webOS CLI"; exit 1; }
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
cp appinfo.json index.html "$stage/"
python3 make-icon.py "$stage/icon.png"
mkdir -p dist
ares-package "$stage" -o dist
