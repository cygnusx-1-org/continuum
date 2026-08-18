#!/bin/bash
#
# Verify the Roborazzi layout goldens in app/src/test/screenshots against the current layouts.
#
# GitHub CI is intentionally disabled for this fork, so this (and `./gradlew :app:check`, which
# depends on verifyRoborazziDebug) is how golden drift gets caught. On failure Gradle only says the
# build failed, so this script reads the results summary and names the images that moved.
#
# Usage: scripts/verify-goldens.sh
#   Re-record after an intended layout change: ./gradlew :app:recordRoborazziDebug

set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root" || exit 1

summary="app/build/test-results/roborazzi/debug/results-summary.json"
compare_dir="app/build/outputs/roborazzi"

# Results from a previous run survive in build/, so a run that dies before the tests execute (a
# compile error, an OOM, a task-configuration failure) leaves a stale summary behind. Reporting it
# would print "All goldens current." for a run that verified nothing. Mark the start time so the
# failure path below can tell a fresh summary from a leftover one.
started="$(mktemp)"
trap 'rm -f "$started"' EXIT

golden_count=$(ls app/src/test/screenshots/*.png 2>/dev/null | wc -l)
echo "Verifying $golden_count layout goldens in app/src/test/screenshots against the current layouts."
echo "Rendering every case with Roborazzi and diffing it — takes about a minute on a cold run."
echo

# -q so the task list does not bury the summary below. Gradle still prints ERROR-level output, so a
# compile failure or a test assertion is not hidden by it.
./gradlew -q :app:verifyRoborazziDebug
status=$?

if [ ! -f "$summary" ]; then
  echo
  echo "No Roborazzi results at $summary — the test run did not get far enough to compare."
  exit $status
fi

# Only meaningful when the build failed. A successful build that was UP-TO-DATE does not rewrite the
# summary, and its numbers still stand: Gradle only skips the task when the goldens and the code that
# renders them are both unchanged.
if [ $status -ne 0 ] && [ "$summary" -ot "$started" ]; then
  echo
  echo "Roborazzi results at $summary are left over from an earlier run — this run did not"
  echo "get as far as comparing goldens. Nothing here says anything about their current state."
  exit $status
fi

python3 - "$summary" "$compare_dir" <<'PY'
import json, os, sys

summary_path, compare_dir = sys.argv[1], sys.argv[2]
with open(summary_path) as fh:
    data = json.load(fh)

s = data.get("summary", data)
total = s.get("total", 0)
changed = s.get("changed", 0)
added = s.get("added", 0)
unchanged = s.get("unchanged", 0)

print()
print(f"Goldens: {total} total, {unchanged} unchanged, {changed} changed, {added} added")

if not changed and not added:
    print("All goldens current.")
    raise SystemExit(0)

results_dir = os.path.join(os.path.dirname(summary_path), "results")
moved = []
if os.path.isdir(results_dir):
    for name in os.listdir(results_dir):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(results_dir, name)) as fh:
            r = json.load(fh)
        if r.get("type") in ("changed", "added"):
            golden = r.get("golden_file_path") or r.get("compare_file_path") or name
            moved.append((r.get("type"), os.path.basename(golden)))

for kind, name in sorted(moved):
    print(f"  {kind:9} {name}")

# Derive the diff images from the results above rather than listing compare_dir. Roborazzi rewrites
# results/ every run but leaves outputs/roborazzi/ alone, so after fixing layout A and breaking
# layout B that directory holds both, and listing it would send you to A's diffs for a problem that
# no longer exists.
diffs = []
for _, name in sorted(moved):
    candidate = os.path.join(compare_dir, name[:-len(".png")] + "_compare.png")
    if os.path.isfile(candidate):
        diffs.append(os.path.basename(candidate))

if diffs:
    print()
    print(f"Side-by-side diffs in {compare_dir}/:")
    for d in diffs[:20]:
        print(f"  {d}")
    if len(diffs) > 20:
        print(f"  ... and {len(diffs) - 20} more")

print()
print("If these changes are intended: ./gradlew :app:recordRoborazziDebug")
PY

exit $status
