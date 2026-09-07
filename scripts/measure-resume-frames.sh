#!/bin/bash
#
# Measure whether "Resume where I left off" drops frames, by driving the same journey twice -- once
# with the setting off, once with it on -- and comparing what the compositor actually rendered.
#
# Why this exists: ResumeState.capture runs on every onActivityPaused, on the main thread, and ends
# in a synchronous file write; and the gallery, filtered-posts and search screens each run a whole
# Post, PostFilter or MultiReddit through Gson the first time they are captured. Neither cost can be
# established from a unit test -- app/src/test/.../resume/ResumeCaptureCostTest.kt bounds the shape
# of that work on a desktop JVM, which says nothing about ART, this flash, or this GPU. Only frame
# times on a device answer the question.
#
# Usage:
#   scripts/measure-resume-frames.sh [--serial <serial>] [--runs 5]
#                                    [--sub pics]
#                                    [--post https://www.reddit.com/r/pics/comments/<id>/]
#                                    [--repeat-post https://www.reddit.com/r/pics/comments/<id>/]
#
# --repeat-post opens one screen and then backgrounds it twice, so the capture that describes that
# screen (the first) can be read against one that does not (the second). See the comment on those
# steps for why it is done that way round and not by opening the screen twice.
#
# WHAT THIS CANNOT MEASURE: the Gson describe. ViewRedditGalleryActivity, FilteredPostsActivity and
# SearchResultActivity are the three screens that run a whole Post, PostFilter or MultiReddit
# through Gson on their first capture, and none of them can be opened by a deep link -- the gallery
# viewer is started from inside the app with a Parcelable Post extra and has no URL form at all.
# Reaching it needs a tap on the media inside a gallery post, which is not scripted here because
# the coordinates move with the content. The describe that --repeat-post isolates is therefore the
# cheap one: reading a screen's intent extras, not serializing an object graph.
#
# The app is force-stopped between runs, which is deliberate rather than tidiness: the setting is
# read through SharedPreferences, and this script writes it by editing the preferences XML under
# run-as. A live process holds those values in memory and would write its own copy back over them.
#
# Reads nothing and writes nothing outside the debug package and the output directory. It does not
# clear app data, so the signed-in account survives.

set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root" || exit 1

pkg="org.cygnusx1.continuum.debug"
prefs_file="${pkg}_preferences.xml"
account_prefs="ml.docilealligator.infinityforreddit.current_account.xml"
setting_base="resume_where_i_left_off"

serial="${ANDROID_SERIAL:-}"
runs=5
sub="pics"
post_url=""
repeat_post_url=""
out_dir="${TMPDIR:-/tmp}/resume-frames-$(date -u +%Y%m%d-%H%M%S)"

require_value() {
  if [ "$2" -lt 2 ]; then
    echo "$1 needs a value." >&2
    exit 2
  fi
}

while [ $# -gt 0 ]; do
  case "$1" in
    --serial)  require_value "$1" "$#"; serial="$2"; shift 2 ;;
    --runs)    require_value "$1" "$#"; runs="$2"; shift 2 ;;
    --sub)     require_value "$1" "$#"; sub="$2"; shift 2 ;;
    --post)    require_value "$1" "$#"; post_url="$2"; shift 2 ;;
    --repeat-post) require_value "$1" "$#"; repeat_post_url="$2"; shift 2 ;;
    --out)     require_value "$1" "$#"; out_dir="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

# Same serial resolution as scripts/run-device-tests.sh, for the same reason: this machine keeps
# more than one device up and picking the wrong one silently measures the wrong app.
if [ -z "$serial" ] && [ -f local.properties ]; then
  serial="$(sed -n 's/^[[:space:]]*deviceSerial[[:space:]]*=[[:space:]]*//p' local.properties | tail -1)"
fi

mapfile -t attached < <(adb devices | awk '$2 == "device" { print $1 }')
if [ -z "$serial" ]; then
  if [ "${#attached[@]}" -eq 1 ]; then
    serial="${attached[0]}"
  else
    echo "Set --serial, ANDROID_SERIAL, or deviceSerial= in local.properties." >&2
    printf '  attached: %s\n' "${attached[@]}" >&2
    exit 1
  fi
fi

dev() { command adb -s "$serial" "$@"; }

if ! dev shell true >/dev/null 2>&1; then
  echo "Device $serial is not reachable." >&2
  exit 1
fi
if ! dev shell pm path "$pkg" >/dev/null 2>&1; then
  echo "$pkg is not installed. Run ./gradlew :app:installDebug first." >&2
  exit 1
fi

mkdir -p "$out_dir"
echo "Device:  $serial"
echo "Output:  $out_dir"

# ---------------------------------------------------------------- the setting

# The key is account-scoped -- AccountScope.key(accountName, base) -- so writing the unscoped base
# would set nothing and the "on" arm would silently measure the "off" one.
account="$(dev shell "run-as $pkg cat shared_prefs/$account_prefs 2>/dev/null" \
  | sed -n 's/.*name="account_name">\([^<]*\)<.*/\1/p' | tail -1)"
if [ -z "$account" ]; then
  account=".anonymous"
fi
setting_key="${account}.${setting_base}"
echo "Account: $account"
echo "Key:     $setting_key"

# What the setting was before this script touched it, so it can be put back. Without this the
# device is left with the feature ON after every run -- the last arm's value -- whether or not the
# user had it on, and the force-stop at the end means they would not see it change.
original_setting="$(dev shell "run-as $pkg cat shared_prefs/${prefs_file} 2>/dev/null" \
  | tr -d '\r' \
  | sed -n "s/.*name=\"${setting_key}\" value=\"\([a-z]*\)\".*/\1/p" | tail -1)"
if [ -z "$original_setting" ]; then
  original_setting=false
fi
echo "Was:     $original_setting (restored when this finishes)"
changed_setting=0

set_resume() {
  local value="$1"
  dev shell "am force-stop $pkg"

  # run-as prints nothing both when the file is absent and when run-as itself fails -- a
  # non-debuggable build, a package mismatch, an SELinux denial. Those two must not be confused:
  # treating a failure as "no file yet" would write a fresh one-entry document over the app's real
  # preferences and destroy every setting the user has. Ask run-as whether it works at all first.
  if ! dev shell "run-as $pkg true" >/dev/null 2>&1; then
    echo "run-as $pkg failed: this needs the debug build, not the release one." >&2
    exit 1
  fi

  local xml
  # adb shell rewrites LF as CRLF on the way out on most versions, so the XML read here comes back
  # with carriage returns that are not in the file. Written back unstripped they accumulate, one
  # more per arm, in a document the app has to parse.
  xml="$(dev shell "run-as $pkg cat shared_prefs/$prefs_file 2>/dev/null" | tr -d '\r')"
  if [ -z "$xml" ]; then
    echo "  no preferences file yet; creating one"
    xml='<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<map>
</map>'
  fi
  printf '%s\n' "$xml" > "$out_dir/prefs-in.xml"
  SETTING_KEY="$setting_key" VALUE="$value" python3 - "$out_dir/prefs-in.xml" "$out_dir/prefs-out.xml" <<'PY'
import os, re, sys
key, value = os.environ["SETTING_KEY"], os.environ["VALUE"]
xml = open(sys.argv[1], encoding="utf-8").read()
entry = '<boolean name="%s" value="%s" />' % (key, value)
pattern = re.compile(r'<boolean name="%s" value="(?:true|false)" ?/>' % re.escape(key))
if pattern.search(xml):
    xml = pattern.sub(entry, xml)
else:
    xml = xml.replace("</map>", "    %s\n</map>" % entry)
open(sys.argv[2], "w", encoding="utf-8").write(xml)
PY
  # Written through run-as so it lands with the app's uid; a plain push would leave a file the app
  # cannot read and SharedPreferences would silently fall back to defaults.
  dev shell "run-as $pkg sh -c 'cat > shared_prefs/$prefs_file'" < "$out_dir/prefs-out.xml"
  local readback
  readback="$(dev shell "run-as $pkg cat shared_prefs/$prefs_file" | tr -d '\r' | grep -c "name=\"$setting_key\" value=\"$value\"")"
  if [ "$readback" -lt 1 ]; then
    echo "Failed to set $setting_key to $value." >&2
    exit 1
  fi
  # Only now is there something to put back. Set here rather than at the call site so a run that
  # died before the first successful write does not "restore" a value it never changed.
  changed_setting=1
}

# ---------------------------------------------------------------- the journey

# Every step below except 01-scroll ends in an activity transition, which is where capture() runs;
# the scroll is there to give the capture after it something new to write, and is measured only
# because it is the one sustained-animation step in the journey. framestats holds only the last
# ~120 frames -- about two seconds at 60Hz -- so the window is reset immediately before each step
# and dumped immediately after, keeping each file to one transition rather than letting the
# interesting frames scroll out of the buffer.
settle() { sleep "${1:-2}"; }

step() {
  local label="$1"; shift
  dev shell "dumpsys gfxinfo $pkg reset" >/dev/null 2>&1
  "$@"
  settle 2
  dev shell "dumpsys gfxinfo $pkg framestats" > "$out_dir/$label.txt" 2>&1
}

open_url() { dev shell "am start -a android.intent.action.VIEW -d '$1' $pkg" >/dev/null 2>&1; }
go_back() { dev shell input keyevent KEYCODE_BACK >/dev/null 2>&1; }
go_home() { dev shell input keyevent KEYCODE_HOME >/dev/null 2>&1; }
go_foreground() { dev shell "monkey -p $pkg -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1; }

# Not a transition, but it is what arms PostFragment.storeResumeCache on idle, so the capture that
# follows has a moved anchor to write rather than an unchanged one to skip.
scroll_feed() {
  local i
  for i in 1 2 3; do
    dev shell input swipe 540 1600 540 500 300 >/dev/null 2>&1
    sleep 1
  done
}

journey() {
  local arm="$1" run="$2"
  local prefix="$arm-run$run"

  dev shell "am force-stop $pkg"
  settle 1
  dev shell "monkey -p $pkg -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1
  settle 6   # cold start, plus the first feed page

  step "$prefix-01-scroll" scroll_feed
  step "$prefix-02-open-subreddit" open_url "https://www.reddit.com/r/$sub/"
  step "$prefix-03-back-to-feed" go_back

  if [ -n "$post_url" ]; then
    step "$prefix-04-open-post" open_url "$post_url"
    step "$prefix-05-back-from-post" go_back
  fi

  if [ -n "$repeat_post_url" ]; then
    # Isolating a describe takes more care than opening the same screen twice, for two reasons.
    #
    # A describe does NOT happen on the transition INTO a screen. Android runs A.onPause before
    # B.onCreate, so the capture that the opening transition triggers still sees a stack without B
    # in it. B is recorded a moment later and described on the FIRST CAPTURE AFTER THAT -- which is
    # the next time something pauses, i.e. step 07 here.
    #
    # And BACK cannot be the second sample: it finishes the activity, recordDestroyed drops the
    # entry, and reopening the screen is a new screen that describes all over again (the same rule
    # ResumeStateCostTest pins down in "a dismissed screen is described again when reopened"). So
    # the second sample has to keep the screen alive -- HOME pauses without finishing.
    #
    # Read 07 against 08: same stack, same screen, described in the first and not the second.
    step "$prefix-06-open-post-again" open_url "$repeat_post_url"
    step "$prefix-07-background-describes" go_home
    go_foreground; settle 3
    step "$prefix-08-background-described" go_home
    go_foreground; settle 3
    step "$prefix-09-back-from-post" go_back
  fi

  step "$prefix-10-home" go_home
}

# Runs on any exit, including the early ones inside set_resume, so an interrupted run does not
# leave the setting somewhere the user did not put it.
restore_setting() {
  [ "$changed_setting" -eq 1 ] || return 0
  # Disarmed first: set_resume exits non-zero on a device that has gone away, and without this that
  # failure would re-enter the handler that is already running.
  trap - EXIT
  changed_setting=0
  echo "Restoring $setting_key to $original_setting"
  set_resume "$original_setting"
}
trap restore_setting EXIT

for arm in off on; do
  echo
  echo "=== arm: setting $arm"
  if [ "$arm" = "off" ]; then set_resume false; else set_resume true; fi
  for run in $(seq 1 "$runs"); do
    echo "  run $run/$runs"
    journey "$arm" "$run"
  done
done

dev shell "am force-stop $pkg"

# ---------------------------------------------------------------- the numbers

python3 - "$out_dir" <<'PY'
import collections, glob, os, sys

out_dir = sys.argv[1]

# framestats is a CSV, and its columns are NOT the short list the vitals documentation shows -- this
# Android version emits 23 of them, with FrameTimelineVsyncId, FrameDeadline, FrameInterval and
# FrameStartTime interleaved among the ones that matter. Hardcoding positions from the docs put
# IntendedVsync and FrameCompleted two and three columns off, and the difference between two
# unrelated nanosecond timestamps came out as a "frame time" of a hundred million milliseconds.
# So the header is read and the columns are found by name. A dump contains one block per window,
# each with its own header, which is why the map is rebuilt whenever a header line appears.
#
# Flags is the first column. A non-zero value means the frame is one Android itself does not count
# -- the window was resized, or the frame was skipped -- and its timestamps are not comparable with
# the rest, so those are dropped and reported separately instead of inflating the jank figure.
BUDGET_MS = 1000.0 / 60.0

# Above this a row is not a slow frame, it is a corrupt one -- see the guard in frames(). Ten
# seconds is nearly an order of magnitude past the worst real frame this has recorded (1.45s, on a
# translated-arm64 image) and eight orders below the garbage values, so the gap it sits in is wide
# enough that it makes no judgement about what counts as slow.
IMPLAUSIBLE_MS = 10_000.0

dropped = collections.Counter()
malformed = collections.Counter()

def frames(path, arm):
    out = []
    cols = None
    inside = False
    for line in open(path, encoding="utf-8", errors="replace"):
        line = line.strip().rstrip(",")
        if not line:
            continue
        # The rows live strictly between a pair of these markers. Without that bound, cols stayed
        # set after the block ended and gfxinfo's own summary lines -- "50th percentile: 21ms" --
        # started with a digit, looked like frame rows, and were counted as unreadable ones.
        if line == "---PROFILEDATA---":
            inside = not inside
            if not inside:
                cols = None
            continue
        if not inside:
            continue
        if line.startswith("Flags,"):
            cols = {name: i for i, name in enumerate(line.split(","))}
            continue
        if cols is None or not line[0].isdigit():
            continue
        parts = line.split(",")
        try:
            flags = int(parts[cols["Flags"]])
            start = int(parts[cols["IntendedVsync"]])
            end = int(parts[cols["FrameCompleted"]])
        except (ValueError, IndexError, KeyError):
            malformed[arm] += 1
            continue
        if flags != 0:
            dropped[arm] += 1
            continue
        # A frame that never completed reports 0 here; counted as a duration it would be an
        # enormous negative or positive number depending on the column order.
        if end <= start:
            malformed[arm] += 1
            continue
        ms = (end - start) / 1_000_000.0
        # Some rows carry garbage in FrameCompleted rather than a timestamp -- IntendedVsync is
        # always sane in these, so the bad column is the far end. Two distinct values turned up on
        # the run this guard was written for, 4611708558415757320 (just over 2**62) and
        # 723402832204270604, and subtracting a real vsync from either gives a "frame" measured in
        # decades. Left in, they made `max` meaningless and dragged `p95` up by a position. Rare:
        # 5 rows in 1234.
        #
        # Bounded on the duration rather than on the specific values, so a third garbage constant
        # needs no new case here. Anything discarded is counted and printed below, so a real frame
        # over the bound is visible in that count even though it is out of the statistics.
        if ms > IMPLAUSIBLE_MS:
            malformed[arm] += 1
            continue
        out.append(ms)
    return out

def pct(values, p):
    if not values:
        return float("nan")
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(round(p / 100.0 * (len(ordered) - 1))))]

by_arm = collections.defaultdict(list)
by_step = collections.defaultdict(list)
for path in sorted(glob.glob(os.path.join(out_dir, "*.txt"))):
    name = os.path.basename(path)[:-4]
    if "-run" not in name:
        continue
    arm = name.split("-", 1)[0]
    step = name.split("-", 2)[2]
    f = frames(path, arm)
    by_arm[arm] += f
    by_step[(arm, step)] += f

lines = []
def say(s=""):
    print(s)
    lines.append(s)

say("Frame times, ms, from dumpsys gfxinfo framestats (FRAME_COMPLETED - INTENDED_VSYNC).")
say("Budget at 60Hz is %.2f ms; 'janky' below means a frame over that." % BUDGET_MS)
say()
say("%-30s %7s %7s %7s %7s %7s %7s" % ("", "frames", "janky", "jank%", "p50", "p95", "max"))

def row(label, f):
    if not f:
        say("%-30s %7s" % (label, "none"))
        return
    janky = sum(1 for x in f if x > BUDGET_MS)
    say("%-30s %7d %7d %6.1f%% %7.2f %7.2f %7.2f" % (
        label, len(f), janky, 100.0 * janky / len(f), pct(f, 50), pct(f, 95), max(f)))

for arm in ("off", "on"):
    row("ALL, setting %s" % arm, by_arm[arm])
say()
say("Flagged frames dropped as not comparable: off %d, on %d" % (dropped["off"], dropped["on"]))
if malformed["off"] or malformed["on"]:
    say("Frames never completed or carrying a sentinel, dropped: off %d, on %d"
        % (malformed["off"], malformed["on"]))
say()

for step in sorted({s for (_, s) in by_step}):
    for arm in ("off", "on"):
        row("%s [%s]" % (step, arm), by_step[(arm, step)])
    say()

off, on = by_arm.get("off", []), by_arm.get("on", [])
if off and on:
    say("Verdict inputs:")
    say("  p95 off %.2f ms, p95 on %.2f ms, delta %+.2f ms"
        % (pct(off, 95), pct(on, 95), pct(on, 95) - pct(off, 95)))
    say("  jank off %.1f%%, jank on %.1f%%"
        % (100.0 * sum(1 for x in off if x > BUDGET_MS) / len(off),
           100.0 * sum(1 for x in on if x > BUDGET_MS) / len(on)))
    say()
    say("A delta of a millisecond or so is noise. Read the per-step rows before the totals: the")
    say("only pair worth subtracting is 07-background-describes against 08-background-described:")
    say("same screen, same stack, described in the first and not the second. Other steps pay a")
    say("describe too -- 03 and 05 each pause a screen that has just been opened -- so they are not")
    say("a baseline for it. The Gson describe -- gallery, filtered posts, search -- is NOT covered by")
    say("any step here: those screens have no deep link. See the file header.")
    say()
    say("On a translated-arm64 image every absolute number is inflated by the translator, equally in")
    say("both arms. The delta survives that; 'is it over 16.67 ms' does not, and needs real hardware.")

open(os.path.join(out_dir, "summary.txt"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
print()
print("Summary written to %s" % os.path.join(out_dir, "summary.txt"))
PY
