#!/bin/bash

# version name
VERSION_NAME=$(grep '^[[:space:]]*versionName[[:space:]]*"' app/build.gradle | head -n1 | awk '{ print $2 }' | tr -d '"')

if [ -z "$VERSION_NAME" ]; then
  echo "Error: Could not find versionName in app/build.gradle"
  exit 1
fi

# build.gradle
BUILD_GRADLE_FILENAME="app/build.gradle"

# changelog
CHANGELOG_FILENAME='CHANGELOG.md'
DATE="$(date +%Y-%-m-%-d)"

COMMIT_MESSAGE_PREFIX='    '
COMMIT_MESSAGE_SPECIAL_PREFIX='\* '

CHANGELOG_OVERRIDE_FILENAME="changelog_override.txt"

if [ ! -f ${CHANGELOG_OVERRIDE_FILENAME} ]; then
  # Grab commit messages since that tag, matching specific format
  RELEVANT_COMMIT_MESSAGES=`git log $(git describe --tags --abbrev=0)..HEAD | grep "^${COMMIT_MESSAGE_PREFIX}${COMMIT_MESSAGE_SPECIAL_PREFIX}" | sed "s/^${COMMIT_MESSAGE_PREFIX}//g"`

  if [ -z "$RELEVANT_COMMIT_MESSAGES" ]; then
    echo "Warning: No relevant commit messages found after latest tag"
  fi
else
  RELEVANT_COMMIT_MESSAGES=`cat ${CHANGELOG_OVERRIDE_FILENAME}`
fi

SEPARATOR='---'

VERISON_LINE="${VERSION} / ${DATE}"
VERSION_LENGTH=${#VERISON_LINE}
VERSION_SEPARATOR=$(printf '=%.0s' $(seq 1 ${VERSION_LENGTH}))

CHANGELOG_ENTRY="
${VERSION_NAME} / ${DATE}
${VERSION_SEPARATOR}
${RELEVANT_COMMIT_MESSAGES}"

# Update CHANGELOG automatically
awk -v sep="${SEPARATOR}" -v new_entry="${CHANGELOG_ENTRY}" '
  # Print each line of the file
  {
    print $0
    # After finding the separator line (even with spaces around it), insert the new entry
    if ($0 ~ /^[[:space:]]*---[[:space:]]*$/) {
      print new_entry
    }
  }
' "${CHANGELOG_FILENAME}" > "${CHANGELOG_FILENAME}.tmp" && mv "${CHANGELOG_FILENAME}.tmp" "${CHANGELOG_FILENAME}"


# commit
git add "${BUILD_GRADLE_FILENAME}" "${CHANGELOG_FILENAME}"

COMMIT_MESSAGE="Updated ${CHANGELOG_FILENAME}"

# Make a new commit for a new release
git commit -m "${COMMIT_MESSAGE}"

# Creating new tag for the new release
git tag -a "${VERSION_NAME}" -m "Version ${VERSION_NAME}"

# Push tags to the git repository
git push --tags

# Creating apk in app/build/outputs/apk/Release
./gradlew assembleRelease

RC="${?}"

# Check return code, and exit with the return code if it is not zero.
if [ "${RC}" -ne 0 ]; then
  exit "${RC}"
fi

# Creating .apk in app/build/outputs/apk/Release, and uploading it to git repository in GitHub as a new release.
scripts/release-github.sh "${RELEVANT_COMMIT_MESSAGES}"
