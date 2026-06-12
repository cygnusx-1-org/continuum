#!/bin/bash

# Allow passing release notes via a file path argument, direct string, or the RELEASE_NOTES env var.
if [ -n "$1" ] && [ -f "$1" ]; then
  RELEASE_NOTES="$(cat "$1")"
elif [ -n "$1" ]; then
  RELEASE_NOTES="$1"
elif [ -n "$RELEASE_NOTES" ]; then
  RELEASE_NOTES="$RELEASE_NOTES"
else
  RELEASE_NOTES=""
fi

export RELEASE_NOTES

echo "Release notes set to: $RELEASE_NOTES"

./gradlew assembleRelease -x lintVitalRelease
./gradlew githubRelease -x lintVitalRelease
