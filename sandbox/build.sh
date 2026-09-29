#!/bin/sh
set -eu
cp -R /input/. /workspace/
ln -s /opt/toolchain/node_modules /workspace/node_modules
cp /opt/toolchain/tsconfig.json /workspace/tsconfig.check.json
node /opt/toolchain/node_modules/typescript/bin/tsc --project /workspace/tsconfig.check.json --pretty false
node /opt/toolchain/node_modules/vite/bin/vite.js build /workspace --config /opt/toolchain/vite.config.mjs --configLoader native
