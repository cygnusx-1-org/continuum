# TODO

Things deliberately left alone, and why. State as of 2026-11-08: `8.3.3.9.1.26` is the last stable release. Betas `8.3.3.9.1.27-beta.1` to `beta.9` were published after it and are the last betas ever: from now on releases are stable only, with a plain version (see Release housekeeping). Five pull requests are open and not merged: #63 (remove the Gallery layout), #65 (backup and restore), #66 (the feed is always one column), #67 (Card Clean is now Media Focused, with two gallery options), and #64 (this file).

## Waiting on the owner

- [ ] **Merge the open pull requests and ship the next stable release.** Order: #63, #65, #66, #67, then #64. #66 is stacked on #63 and #67 on #66, so after each merge retarget the next to master and merge master into it. The next release is `8.3.3.9.1.27` (plain, no `-beta`): it rolls up everything since `.26`.
- [ ] **Phone-check what changed since `8.3.3.9.1.26`.** Nothing since `8.3.3.9.1.25-beta.6` has ever been confirmed on a phone. Riskiest first:
  - Backup and restore between the phone and the tablet (PR #65): back up on one, restore on the other. Check that a setting left at its default on the source is at its default on the target, that the app lock and a custom font come across, and that downloads still go to the target's own folder (download folders and the last-run times are deliberately not carried).
  - Media Focused gallery options (PR #67), in Settings > Post: with "Swipe Up and Down Through a Gallery" on, the gallery pages up and down and the up/down touch swap works (also with a zoomed image and a video); with "Scrolling Gallery" on, the list scrolls and a tap opens an image; with either on, a swipe left closes the gallery and a swipe right opens the post, but not while a picture is zoomed in.
  - The feed is always one column (PR #66), including the tablet, which used to default to two.
  - Voting in every layout (handlers rewritten in `26-beta.12`), and Hide Read Posts Automatically, whose filter pipeline was reworked when Media Posts Only came out (PR #63): turn it on, read a post, come back, and also hide a post by hand.
  - Swiping to the next post from a post's detail screen, which lost the media-only parameter (PR #63).
  - Card video autoplay, whose player was refactored for the Gallery tiles in beta.9 and then reverted in PR #63; it should be exactly as in `.26`.
  - Switching layouts with swipe actions turned on (the freeze fix, `27-beta.1`): no frozen screen, swipe actions still work afterwards.
  - Autoplay mute state in feeds with "Remember muting option" on, including NSFW posts (`27-beta.2`). The owner was unsure whether the failure was the first video or later ones, so the fix may not cover the real cause; ask for specifics if it still misbehaves.
  - A feed or default layout saved on Slide or Gallery now shows Card Top (see "Removed experiments"). Nothing migrates the saved value, on purpose.
  - The settings home and settings search.
- [ ] **Re-record the layout snapshot goldens.** They drift now that the feed is one column (PR #66): the landscape and tablet cases used to be laid out at half the display width, and `RoborazziLayoutTest` now lays every case out at the full width. Run `./gradlew recordRoborazziDebug` and commit the changed PNGs in `app/src/test/screenshots`; `scripts/verify-goldens.sh` shows what drifted. It needs a JDK, which the work machine does not have, so the owner does it. CI does not check goldens (it runs `testDebugUnitTest`), only `check` does.
- [ ] **Bring "Reset all settings" in line with backup (decide what it should clear, then do it).** It clears only the default preferences, the main page tabs and the NSFW and spoiler file. A backup covers much more: the sort type, post layout, post details, navigation drawer, bottom app bar, user tags, post history, proxy, themes, security and the scrolled-position files, plus the custom themes, filters and font files. So after a reset, many settings are still set. Decide first whether accounts, themes, filters and read history are "settings" for this purpose, and whether the device-bound keys (download folders) are kept, as a restore keeps them. The list of what a backup covers is in `BackupSettings`.

## Removed experiments (so they are not tried again the same way)

All of these were built, judged not good enough on the owner's phone, and taken out. Each came out as one merge, so `git revert -m 1 <merge>` brings it back.

- **Vertical gallery list** in the gallery viewer (added before `.26`, removed in `27-beta.6`). Long galleries of GIFs were slow however they were loaded, even as mp4 video; the swipe viewer is back.
- **Shadowbox / Slide** (a one-post-per-screen pager over the feed, `27-beta.5` to `beta.8`, removed in `27-beta.9`). Embedding the pager in the feed to keep the top bar caused three problems that took several releases and never settled: a sideways swipe on the last slide leaked into the subreddit's About tab (the feed sits in a tabs pager); the feed screen is sized for a top bar that has scrolled away, so the pager hung below the screen; and short media was not centred. If a pager is ever wanted again, make it its own screen, not a child of the feed.
- **Gallery layout** (a two-column grid of previews, removed in PR #63). The owner found Card Clean (now Media Focused) does what was wanted. Its video autoplay (`27-beta.9`) was built and reverted with it. Gallery *posts* inside the card layouts (carousel and grid, `PostGalleryHost`, `GalleryGifAutoplay`) are a different thing and stay.
- **The Number of Columns in Post Feed** setting (removed in PR #66). The feed is one column; the owner will never use more. The staggered grid code went with it.
- Saved values: Slide was layout `7` and Gallery was `2`. A feed saved on either falls through to the Card Top rows. The ids are not reused for anything.

## Release housekeeping

- **Stable releases only (decided 2026-11-08).** Set `versionName` to a plain version, bump `versionCode`, give the `CHANGELOG.md` heading the same plain version, tag it with no suffix. No `-beta.N`, no pre-releases. The workflow treats a `-beta.N` suffix as a pre-release, so a plain tag is a normal release.
- [ ] The stray tag `v8.3.3.9.1.23-beta.1` is still on the remote (a deletion was denied once). `git push origin :refs/tags/v8.3.3.9.1.23-beta.1` removes it.
- [ ] Backups are plain zips and hold accounts, Client ID and the Giphy key; the English description says to keep the file private.
- [ ] **A script error on every git command in the work shell (not from this repo).** Each `git checkout` and `git pull` run from the Bash tool may print `Error: Cannot find module 'C:\Development\redditjr\redditPoster.js'` (Node `MODULE_NOT_FOUND`). The repo has no hooks and nothing in it mentions `redditPoster`, so something in the owner's shell or git setup (a profile, alias or wrapper) runs a script that is not in this checkout. It has never failed a command, but it is noise and nobody knows what the script is meant to do. Look at the shell startup files and the global git config to find it, then fix the path or remove the reference.
- The bottom app bar is gone (`26-beta.20`). Kept on purpose: the `bottom_app_bar` preference file, because it also stores what the floating button does (`MAIN_ACTIVITY_BOTTOM_APP_BAR_FAB`, `OTHER_ACTIVITIES_BOTTOM_APP_BAR_FAB`), so backup, restore and the per-account migration still handle it. In landscape and on tablets the floating button hides on scroll like in portrait.

## Backup and restore (audited 2026-11-08, PR #65)

- A backup holds every settings file except the bookkeeping ones (internal flags, cookies, the random-subreddit cache, the signed-in account, which `accounts.json` carries), plus the database tables the owner cannot get back from Reddit and the custom font files.
- Restoring one of this app's backups replaces each settings file key for key; an older backup (Infinity's among them) names no accounts and only adds to what is here. `BackupSettings.DEVICE_BOUND_KEYS` (the four download folders and two last-run times) is neither backed up nor overwritten.
- Not backed up, on purpose: subscriptions and multireddits of signed-in accounts (they come back from Reddit), image and feed caches, resume state.

## Settings reorganisation: phases 1 to 4 done, more possible

The settings home is grouped by scope, on purpose: "Account · ..." headings are per-account and "Global" is shared, and `AccountScopedScreenKeysTest` and `SettingsSearchRegistryTest` enforce that. Never regroup the home purely by topic.

- Done: Font, Posts, Post Details, Comments, Navigation Drawer, Main Page Tabs and Bottom App Bar were promoted out of Interface (`26-beta.7`). Time Format and Immersive Interface were folded into Interface (`26-beta.8`). The repeated volume-key rows are gone and Disable Swiping Between Tabs sits with Tab Switching Sensitivity (`26-beta.10`). Global Settings Management was merged into Backup and Restore, now "Backup, restore and reset" (`26-beta.14`). Data Saving Mode and Download Location are one screen ("Data saving and downloads"); Notifications and Proxy are one screen ("Notifications and proxy"; the proxy rows live in their own file, reached through `KeyRoutedPreferenceDataStore`) (`26-beta.21`).
- Not done, and why:
  - Security stays separate on purpose: it asks for the device lock before it shows, and joining it to another screen would either lock that screen too or show the app-lock settings without the lock.
  - Media settings are still in Video (per account) and Device (the preview max resolution; fold support is global and cannot move to a per-account screen).
  - Account Settings Management is per-account and cannot join the global screens.
- The Post Details screen is not a duplicate of Posts: they are the same options for the feed and for the detail view, stored in separate files.
- How: one screen group per release. Moving XML rows is safe; the screen's listeners have to move with them, and `SCREEN_RESOURCES` in `SettingsSearchRegistry` loses the screen. Deleting or renaming a key needs a migration. Every string removed from `values/strings.xml` must go from the 23 `values-*` files too, and `AccountScopedKeysTest` and `SettingsSearchRegistryTest` list keys, titles and breadcrumbs. New strings are English only; other languages fall back to it.

## `PostRecyclerViewAdapter` split and DRY pass: finished

The adapter went from 7,911 lines to about 3,700. Every family of rows is in its own files behind the host interfaces (`PostRowHost`, `PostCompactHost`, `PostGalleryHost`, `PostVideoHost`, `PostPreviewHost`); `RowHost` inside the adapter implements them and reads the adapter's fields at call time. The DRY steps are done too: one `vote(target, ...)` routine in `PostViewHolder`, one `keepViews` for the two `setBaseView` overloads.

- Left over, judged not worth the risk: each card type's 28-argument binding call (the generated view-binding classes share no type), and the one-line host getters in `RowHost` (the cost of the host pattern).
- Lessons, so they do not repeat:
  - A row's `setItemViewBackgroundColor` override can run inside its superclass constructor, before the row's own fields exist. Rows like that must read the host through `rowHost()`, not a field of their own.
  - Fields the adapter reads from a moved class must be package-private, not private.
  - NullAway wants `@SuppressWarnings("NullAway.Init")` on classes whose views or `post` are assigned after construction, and `@Nullable` on host methods that return nullable fields. A bare `Integer.MAX_VALUE` is not a valid Android menu order (use `Menu.CATEGORY_SECONDARY | 0xFFFF`). `ReadPostType` values are ints, not an enum.
  - The layout snapshot tests cover the XML layouts, not the adapter's binding, so a green build is not enough: rows are recycled, and the bind code sets and clears things in both directions. Keep every "set it back" line.
  - Do not change how the adapter picks a view type, the stickied one-line row, the card top border or the vote hiding without a phone check.

## Working notes

- There is no JDK on the work machine; CI (Compile and Unit tests, with NullAway and Error Prone) is the only compiler. A change that deletes a feature has to be searched for leftovers by hand (symbols, resources, strings in all 24 `values*` folders, tests, snapshot goldens in `app/src/test/screenshots`); CI only catches what no longer compiles.
- Open pull requests with `gh pr create --repo jayblah/redditjr`; without `--repo` they open in the upstream project. The owner says "merge and ship" each time; do not merge or tag before that.
- Make a feature that may be unwanted one self-contained change, so the merge can be reverted on its own. Do not force-push or amend a pushed branch (the permission classifier denies it): add a follow-up commit instead.
- Stacked pull requests: after the first merge, GitHub refuses to merge the next ("Base branch was modified"); retarget it to master with `gh pr edit N --base master`, merge master into the branch, and merge it. Check that master has every change before tagging.
- Release: the tag must equal `versionName` exactly (`.github/workflows/build.yml`); the `CHANGELOG.md` heading `X / date` supplies the notes. Changelog entries wait under "Unreleased" until the ship, when the heading takes the version.
- The work shell is Windows (Git Bash). Python run from it needs Windows paths; write scripts with the Write tool, because large heredocs with quotes in them fail. A string added to `values/strings.xml` by a script must be checked in the file afterwards.

## Audit leftovers

Found but left alone as larger or riskier than a quick win:

- `RestoreSettings` shares its repeated steps through `restoreRows` and `restoreFilters`; the remaining per-table blocks (anonymous subscriptions, themes, accounts) each have their own rules and the restore test is the safety net.
- The hand-written dp conversions are done; the remaining `getDisplayMetrics().density` reads are the adapters' `mScale` fields and two vendored library files.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
