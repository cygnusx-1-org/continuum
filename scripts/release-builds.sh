#!/bin/bash

# version name
VERSION_NAME=$(grep '^[[:space:]]*versionName[[:space:]]*"' app/build.gradle | head -n1 | awk '{ print $2 }' | tr -d '"')

if [ -z "$VERSION_NAME" ]; then
  echo "Error: Could not find versionName in app/build.gradle"
  exit 1
fi

# Release variant: "release" (default; publishes to the continuum repo) or "beta" (builds the
# beta variant and publishes to the continuum-beta repo). Set from the optional leading CLI
# argument in the dispatch section at the bottom.
VARIANT="release"

# The beta GitHub release is published in the separate continuum-beta repo, which requires the
# release's git tag to exist there. continuum-beta is a placeholder (not a fork), so pushing the
# tag also uploads the commit it points at. Tags still go to origin (continuum) as well.
BETA_GIT_REMOTE="git@github.com:cygnusx-1-org/continuum-beta.git"

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

# Section 1: everything before "./gradlew assembleRelease"
1() {
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

  # Refuse to tag from a dirty working tree — anything still uncommitted here
  # was not captured by the release commit, so the tag would point at a tree
  # that does not match what actually gets built and released.
  if [ -n "$(git status --porcelain)" ]; then
    echo "Error: working tree is dirty after the release commit. Commit or stash your changes before releasing."
    git status --short
    exit 1
  fi

  # Creating new tag for the new release
  git tag -a "${VERSION_NAME}" -m "Version ${VERSION_NAME}"

  # Push tags to the git repository (origin = the non-beta continuum repo)
  git push --tags

  # A beta release is published as a GitHub release in the separate continuum-beta repo, which
  # needs this tag to exist there too. Push it (and the commit it points at) to continuum-beta.
  if [ "${VARIANT}" = "beta" ]; then
    git push "${BETA_GIT_REMOTE}" "refs/tags/${VERSION_NAME}"
  fi
}

# Section 2: "./gradlew assembleRelease" (or assembleBeta for the beta variant)
2() {
  # Creating apk in app/build/outputs/apk/<variant>
  if [ "${VARIANT}" = "beta" ]; then
    ./gradlew assembleBeta
  else
    ./gradlew assembleRelease
  fi

  RC="${?}"

  # Check return code, and exit with the return code if it is not zero.
  if [ "${RC}" -ne 0 ]; then
    exit "${RC}"
  fi
}

# Section 3: everything after "./gradlew assembleRelease"
3() {
  # Build the APKs and upload them to GitHub as a new release (continuum for release,
  # continuum-beta for beta).
  scripts/release-github.sh "${VARIANT}" "${RELEVANT_COMMIT_MESSAGES}"

  # Push the release commit (e.g. the CHANGELOG update) to origin, the non-beta continuum repo.
  git push
}

# Optional leading variant argument (release|beta). Defaults to release; "beta" builds the beta
# variant and publishes to the continuum-beta repo.
case "${1}" in
  release|beta)
    VARIANT="${1}"
    shift
    ;;
esac

# Dispatch: run all sections in order by default, or an individual section
# when its name is passed as the (next) argument.
case "${1}" in
  1) 1 ;;
  2) 2 ;;
  3) 3 ;;
  ""|all)
    1
    2
    3
    ;;
  *)
    echo "Usage: ${0} [release|beta] [1|2|3|all]"
    exit 1
    ;;
esac
