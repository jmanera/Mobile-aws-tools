#!/usr/bin/env bash
# Builds the Go bridge as an Android library and copies it into the app.
# Requires Go, gomobile (gomobile init done), ANDROID_HOME and an NDK.
set -euo pipefail

cd "$(dirname "$0")"
TARGETS="${TARGETS:-android/arm64,android/amd64}"
OUT=build/ssmbridge.aar

if [[ -z "${ANDROID_NDK_HOME:-}" && -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME/ndk" ]]; then
  ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$(ls "$ANDROID_HOME/ndk" | sort -V | tail -1)"
  export ANDROID_NDK_HOME
fi

mkdir -p build
gomobile bind \
  -target="$TARGETS" \
  -androidapi 26 \
  -javapkg dev.mobileawstools \
  -ldflags="-s -w" \
  -trimpath \
  -o "$OUT" \
  ./ssmbridge

mkdir -p ../android/app/libs
cp "$OUT" ../android/app/libs/ssmbridge.aar
ls -lh "$OUT"
