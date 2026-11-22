#!/usr/bin/env bash
# Run Gradle inside Docker, so this computer needs no JDK or Android SDK.
# Usage: tools/docker-build.sh [gradle task ...]   (default: build)
# The Gradle cache lives in the Docker volume airscan-gradle.
# To sign a release build, put keystore.properties in the repo root (see README).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IMAGE="${IMAGE:-airscan-build}"
VOLUME="${GRADLE_VOLUME:-airscan-gradle}"
# The Linux AAPT2 that AGP downloads is x86-64 only, so always use an x86-64 image.
PLATFORM=linux/amd64

if [ $# -eq 0 ]; then set -- build; fi

# Always rebuild: Docker's layer cache makes it instant when the Dockerfile is unchanged,
# and a changed SDK or build-tools version is picked up.
docker build --quiet --platform "$PLATFORM" -t "$IMAGE" "$ROOT/tools/docker" >/dev/null

# One debug key per checkout, so every debug APK is signed the same way and updates in place.
# It lives in .debug-keystore/ (ignored by Git) and is only for debug builds.
if [ ! -f "$ROOT/.debug-keystore/debug.keystore" ]; then
  mkdir -p "$ROOT/.debug-keystore"
  docker run --rm --platform "$PLATFORM" --user "$(id -u):$(id -g)" -v "$ROOT/.debug-keystore:/keys" \
    --entrypoint keytool "$IMAGE" -genkeypair -keystore /keys/debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Airscan Debug,O=Android,C=US" >/dev/null
fi

# A keystore outside the repo (absolute storeFile in keystore.properties) is mounted at the same path.
# A relative storeFile is inside the repo, which is mounted already.
KEY_ARGS=()
if [ -f "$ROOT/keystore.properties" ]; then
  store_file="$(sed -n 's/^storeFile=//p' "$ROOT/keystore.properties")"
  case "$store_file" in
    /*) [ -f "$store_file" ] && KEY_ARGS=(-v "$(dirname "$store_file"):$(dirname "$store_file"):ro") ;;
  esac
fi

docker run --rm --platform "$PLATFORM" --user "$(id -u):$(id -g)" \
  -v "$ROOT:/repo" -v "$VOLUME:/gradle" ${KEY_ARGS[@]+"${KEY_ARGS[@]}"} -w /repo \
  "$IMAGE" ./gradlew --no-daemon --console=plain "$@"
