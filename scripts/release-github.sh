#!/bin/bash

# Optional leading variant argument (release|beta); defaults to release. "beta" builds the beta
# variant and publishes the GitHub release to the continuum-beta repo (selected in
# app/build.gradle via the RELEASE_VARIANT env var). Backward compatible with the previous
# single-argument (release notes) invocation.
VARIANT="release"
case "${1}" in
  release|beta)
    VARIANT="${1}"
    shift
    ;;
esac

if [ "${VARIANT}" = "beta" ]; then
  APK_DIR="app/build/outputs/apk/beta"
  ASSEMBLE_TASK="assembleBeta"
else
  APK_DIR="app/build/outputs/apk/release"
  ASSEMBLE_TASK="assembleRelease"
fi

# Guard the APK output before uploading anything to GitHub. Stale APKs from previous builds
# accumulate here, and a dirty tree taints the version suffix — either would ship the wrong
# artifact to a release.
shopt -s nullglob
ARM64_APKS=("${APK_DIR}"/*arm64-v8a*.apk)
ARMEABI_APKS=("${APK_DIR}"/*armeabi-v7a*.apk)
DIRTY_APKS=("${APK_DIR}"/*-dirty*.apk)
shopt -u nullglob

if [ "${#ARM64_APKS[@]}" -gt 1 ]; then
  echo "Error: expected at most one arm64-v8a APK, found ${#ARM64_APKS[@]}:"
  printf '  %s\n' "${ARM64_APKS[@]}"
  exit 1
fi

if [ "${#ARMEABI_APKS[@]}" -gt 1 ]; then
  echo "Error: expected at most one armeabi-v7a APK, found ${#ARMEABI_APKS[@]}:"
  printf '  %s\n' "${ARMEABI_APKS[@]}"
  exit 1
fi

if [ "${#DIRTY_APKS[@]}" -gt 0 ]; then
  echo "Error: found APK(s) built from a dirty tree (filename contains '-dirty'):"
  printf '  %s\n' "${DIRTY_APKS[@]}"
  exit 1
fi

RELEASE_NOTES="${1}"
export RELEASE_NOTES
export RELEASE_VARIANT="${VARIANT}"

echo "Release variant: ${VARIANT}"
echo "Release notes set to: $RELEASE_NOTES"

./gradlew "${ASSEMBLE_TASK}"
./gradlew githubRelease
