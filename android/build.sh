#!/bin/bash
set -eu
cd "$(dirname "$0")"
node prepare-cli.mjs
cp package-lock.json build/package-lock.json
(cd build && npm ci --ignore-scripts --no-fund --no-audit)
NODE_PATH="$PWD/build/node_modules" node ../ZCode/apps/zcode-cli/packages/dynamic-workflow/scripts/generate-libs.mjs
node prepare-cli.mjs --bundle
python3 prepare-runtime.py
python3 studio/prepare.py
cp studio/package-lock.json build/studio/package-lock.json
(cd build/studio && npm ci --legacy-peer-deps --ignore-scripts --no-audit --no-fund)
node studio/bundle.mjs
python3 studio/pack.py
./build-apk.sh "${1:-debug}"
