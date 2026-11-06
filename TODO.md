# TODO

Things deliberately left alone, and why. State as of 2026-11-06: `8.3.3.9.1.26-beta.14` is released; the layout-change fix in PR #41 (`beta.15`) is built and waiting for a merge.

## Waiting on the owner

- [ ] **Phone-check the betas from `8.3.3.9.1.26-beta.1` on.** Nothing since `8.3.3.9.1.25-beta.6` has been confirmed. Riskiest first: voting in every layout (the handlers were rewritten, `beta.12`); backup and restore, which no longer asks for a password; Card Clean (now built on Card Bottom, button row invisible); the settings home and settings search; the layout submenu; the number badges in the vertical gallery list. The full list was given in the chat on 2026-11-06; if it is lost, rebuild it from `CHANGELOG.md` (every entry since `8.3.3.9.1.25-beta.6` is something to look at).
- [ ] **Layout change stall (reported 2026-11-06).** Changing the layout from the three-dot submenu froze the screen, and swipe actions and touch scrolling seemed to fail. PR #41 (`beta.15`) delays the rebuild until the menu has closed and re-syncs swipe actions with the new column count, but the stall was never reproduced. If it persists, ask for: which layout from and to, which screen, whether the floating layout button (which still opens the bottom sheet) does the same, and whether touch comes back by itself. The rebuild itself (`PostFragment.refreshAdapter`) still resets the adapter and rebinds every visible row.
- [ ] **Which settings to cut.** Decode NATO Binary, Swap Tap and Long Press in Comments, Disable Profile Avatar Animation, the dozen "Collapse X Section" navigation drawer toggles, and the three font families that each have a custom variant. Already removed: Delete All Legacy Settings, Use /api/info the Old Way, the volume-key navigation settings and code. Do not remove anything else without the owner's say.
- [ ] **The feature enhancements** the owner mentioned at the start of the settings work. Not named yet.

- [ ] **Delete the dead bottom app bar code (settings are gone as of `beta.16`).** Enable, Customize and Lock Bottom App Bar were removed from Settings and the four activities that read the flag (`MainActivity`, `ViewSubredditDetailActivity`, `ViewUserDetailActivity`, `ViewMultiRedditDetailActivity`) now set `showBottomAppBar = false`, so an older stored "on" does nothing. Removed in `beta.17`: the option buttons, hide-on-scroll, rotation-hidden state, inbox badge and the two events, in those four activities and `NavigationWrapper` (which now only holds the FAB, the bar views for the inset code, and theming). Still in the code and worth a careful pass: the bar's layouts and the inset handling that reads the bar views in those activities, `NavigationWrapper`'s constructor and `applyCustomTheme`; `ChangeBottomAppBarEvent` and `ChangeLockBottomAppBarEvent` and their subscribers in `MainActivity`; the bottom-bar colours in `CustomTheme`, `CustomThemeWrapper`, `CustomThemeSettingsItem`, `MaterialYouUtils` and the theme database (`RedditDataRoomDatabase`; dropping those columns needs a Room migration); the `bottom_app_bar` preference file (it also stores which action the floating button does, so the file stays) in `AppModule`, `AccountSettings`, `AccountSettingsMigration`, `BackupSettings` and `RestoreSettings` (old backups still carry that file, so restore has to keep ignoring it); the bar's constants in `SharedPreferencesUtils` and the tests that use them.

## Release housekeeping

- [ ] `versionName` is still a beta (`8.3.3.9.1.26-beta.14` on master). The next stable release needs a plain version and tag.
- [ ] The stray tag `v8.3.3.9.1.23-beta.1` is still on the remote (a deletion was denied once). `git push origin :refs/tags/v8.3.3.9.1.23-beta.1` removes it.
- [ ] Backups are plain zips and hold accounts, Client ID and the Giphy key; the English description says to keep the file private.

## Settings reorganisation: phases 1 to 4 done, more possible

The settings home is grouped by scope, on purpose: "Account · ..." headings are per-account and "Global" is shared, and `AccountScopedScreenKeysTest` and `SettingsSearchRegistryTest` enforce that. Never regroup the home purely by topic.

- Done: Font, Posts, Post Details, Comments, Navigation Drawer, Main Page Tabs and Bottom App Bar were promoted out of Interface (`beta.7`). Time Format and Immersive Interface were folded into Interface (`beta.8`). The repeated volume-key rows are gone and Disable Swiping Between Tabs sits with Tab Switching Sensitivity (`beta.10`). Global Settings Management was merged into Backup and Restore, now "Backup, restore and reset" (`beta.14`).
- Not done, and why:
  - Media settings are still in Video, Data Saving Mode, Download Location and Device.
  - Notifications, Proxy and Security are small separate Global screens. Merge them only among Global screens.
  - Account Settings Management is per-account and cannot join the global screens.
- The Post Details screen is not a duplicate of Posts: they are the same options for the feed and for the detail view, stored in separate files.
- How: one screen group per release. Moving XML rows is safe; the screen's listeners have to move with them, and `SCREEN_RESOURCES` in `SettingsSearchRegistry` loses the screen. Deleting or renaming a key needs a migration. Every string removed from `values/strings.xml` must go from the 23 `values-*` files too, and `AccountScopedKeysTest` and `SettingsSearchRegistryTest` list keys, titles and breadcrumbs. New strings are English only; other languages fall back to it.

## `PostRecyclerViewAdapter` split and DRY pass: finished, with a little left over

The adapter went from 7,911 lines to about 4,030. Every family of rows is in its own files behind the host interfaces (`PostRowHost`, `PostCompactHost`, `PostGalleryHost`, `PostVideoHost`, `PostPreviewHost`); `RowHost` inside the adapter implements them and reads the adapter's fields at call time. The DRY steps are done too: one `vote(target, ...)` routine in `PostViewHolder` (`beta.12`), one `keepViews` for the two `setBaseView` overloads (`beta.13`).

- Left over, judged not worth the risk: each card type's 28-argument binding call (the generated view-binding classes share no type), and the one-line host getters in `RowHost` (the cost of the host pattern).
- `InfoUse.Only` and the `useOldApiInfo` parameters are gone; `InfoUse` is now `First` and `Never`.
- Lessons, so they do not repeat:
  - A row's `setItemViewBackgroundColor` override can run inside its superclass constructor, before the row's own fields exist. Rows like that must read the host through `rowHost()`, not a field of their own.
  - Fields the adapter reads from a moved class must be package-private, not private.
  - NullAway wants `@SuppressWarnings("NullAway.Init")` on classes whose views or `post` are assigned after construction, and `@Nullable` on host methods that return nullable fields. A bare `Integer.MAX_VALUE` is not a valid Android menu order (use `Menu.CATEGORY_SECONDARY | 0xFFFF`). `ReadPostType` values are ints, not an enum.
  - The layout snapshot tests cover the XML layouts, not the adapter's binding, so a green build is not enough: rows are recycled, and the bind code sets and clears things in both directions. Keep every "set it back" line.
  - Do not change how the adapter picks a view type, the stickied one-line row, the card top border or the vote hiding without a phone check.

## Working notes

- There is no JDK on the work machine; CI (Compile and Unit tests, with NullAway and Error Prone) is the only compiler.
- Open pull requests with `gh pr create --repo jayblah/redditjr`; without `--repo` they open in the upstream project.
- Stacked pull requests: after the first merge, GitHub refuses to merge the next ("Base branch was modified"); retarget it to master with `gh pr edit N --base master` and merge it. Check that master has every change before tagging.
- Release: the tag must equal `versionName` exactly (`.github/workflows/build.yml`); a `-beta.N` suffix makes a pre-release; the `CHANGELOG.md` heading `X / date` supplies the notes.

## Audit leftovers

Found but left alone as larger or riskier than a quick win:

- `RestoreSettings` shares its repeated steps through `restoreRows` and `restoreFilters`; the remaining per-table blocks (anonymous subscriptions, themes, accounts) each have their own rules and the restore test is the safety net.
- The hand-written dp conversions are done; the remaining `getDisplayMetrics().density` reads are the adapters' `mScale` fields and two vendored library files.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
