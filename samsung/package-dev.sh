#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${TIZEN_PROFILE:?Set TIZEN_PROFILE to your Samsung certificate profile}"
command -v tizen >/dev/null || { echo "Install Tizen Studio CLI first"; exit 1; }
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
cp config.xml index.html receiver.js "$stage/"
tizen package -t wgt -s "$TIZEN_PROFILE" -- "$stage"
mkdir -p dist
find "$stage" -maxdepth 1 -name '*.wgt' -exec cp {} dist/ \;
ls dist/*.wgt
