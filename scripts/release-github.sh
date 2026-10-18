#!/bin/bash

# Uploads already-built APKs as a GitHub release; it does not build them. Build first with
# `scripts/release-builds.sh [release|beta] 2` (or the matching ./gradlew assemble task).
#
# Optional leading variant argument (release|beta); defaults to release. "beta" uploads the beta
# variant's APKs and publishes the GitHub release to the continuum-beta repo (selected in
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
else
  APK_DIR="app/build/outputs/apk/release"
fi

# Guard the APK output before uploading anything to GitHub. These are exactly the files
# githubRelease uploads: one APK per ABI split (app/build.gradle splits.abi), none of them
# built from a dirty tree. A missing APK means no build was run; a duplicate or "-dirty" APK
# would ship the wrong artifact to a release.
shopt -s nullglob
ARM64_APKS=("${APK_DIR}"/*arm64-v8a*.apk)
ARMEABI_APKS=("${APK_DIR}"/*armeabi-v7a*.apk)
DIRTY_APKS=("${APK_DIR}"/*-dirty*.apk)
shopt -u nullglob

if [ "${#ARM64_APKS[@]}" -ne 1 ]; then
  echo "Error: expected exactly one arm64-v8a APK in ${APK_DIR}, found ${#ARM64_APKS[@]}:"
  [ "${#ARM64_APKS[@]}" -gt 0 ] && printf '  %s\n' "${ARM64_APKS[@]}"
  echo "Build it first with: scripts/release-builds.sh ${VARIANT} 2"
  exit 1
fi

if [ "${#ARMEABI_APKS[@]}" -ne 1 ]; then
  echo "Error: expected exactly one armeabi-v7a APK in ${APK_DIR}, found ${#ARMEABI_APKS[@]}:"
  [ "${#ARMEABI_APKS[@]}" -gt 0 ] && printf '  %s\n' "${ARMEABI_APKS[@]}"
  echo "Build it first with: scripts/release-builds.sh ${VARIANT} 2"
  exit 1
fi

if [ "${#DIRTY_APKS[@]}" -gt 0 ]; then
  echo "Error: found APK(s) built from a dirty tree (filename contains '-dirty'):"
  printf '  %s\n' "${DIRTY_APKS[@]}"
  exit 1
fi

# Nothing here builds the APKs, so a set left over from the previous release passes every check
# above and would be uploaded under this release's tag. Each build replaces the folder's APKs
# with "continuum-<abi>-<versionName>.apk" (RenameApksTask), so the name says which version it is.
VERSION_NAME=$(grep '^[[:space:]]*versionName[[:space:]]*"' app/build.gradle | head -n1 | awk '{ print $2 }' | tr -d '"')
if [ -z "${VERSION_NAME}" ]; then
  echo "Error: Could not find versionName in app/build.gradle"
  exit 1
fi
for APK in "${ARM64_APKS[0]}" "${ARMEABI_APKS[0]}"; do
  case "$(basename "${APK}")" in
    continuum-arm64-v8a-"${VERSION_NAME}".apk|continuum-armeabi-v7a-"${VERSION_NAME}".apk) ;;
    *)
      echo "Error: ${APK} is not a ${VERSION_NAME} build."
      echo "Build it first with: scripts/release-builds.sh ${VARIANT} 2"
      exit 1
      ;;
  esac
done

CHANGELOG_OVERRIDE_FILENAME="changelog_override.txt"

RELEASE_NOTES="${1}"
# Fall back to changelog_override.txt when no release notes were passed on the command line, so a
# direct `scripts/release-github.sh` invocation still picks up this release's notes.
if [ -z "${RELEASE_NOTES}" ] && [ -f "${CHANGELOG_OVERRIDE_FILENAME}" ]; then
  RELEASE_NOTES="$(cat "${CHANGELOG_OVERRIDE_FILENAME}")"
fi

if [ -z "${RELEASE_NOTES}" ]; then
  echo "Error: release notes are empty and ${CHANGELOG_OVERRIDE_FILENAME} was not found or is empty." >&2
  exit 1
fi

export RELEASE_NOTES
export RELEASE_VARIANT="${VARIANT}"

echo "Release variant: ${VARIANT}"
echo "Release notes set to: $RELEASE_NOTES"

# Mirakle does not reliably forward environment variables to the remote Gradle invocation, so pass
# the release notes as a Gradle project property (part of the command line, which survives the
# handoff) rather than relying on the exported RELEASE_NOTES env var.
./gradlew githubRelease "-PreleaseNotes=${RELEASE_NOTES}"
