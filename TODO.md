# TODO

Things deliberately left alone, and why.

- [ ] **Split up `PostRecyclerViewAdapter`: the moving is done; the DRY pass is parked.** The adapter went from 7,911 lines to about 4,030. Every family is moved and the owner confirmed all betas through `8.3.3.9.1.25-beta.6` on a phone (2026-11-05). **Order of work from here: feature enhancements and settings cleanup first, then the DRY pass below.** Each DRY step is its own pull request and release.

  **Where we left off (2026-11-05).** The last family (the card previews: `PostWithPreviewTypeViewHolder`, `PostBaseGalleryTypeViewHolder`, `PostGalleryTypeViewHolder` and the two Material3 rows) shipped as `beta.6`, behind `PostPreviewHost`. Only `StickiedCollapsedViewHolder` and `RowHost` stay inside the adapter on purpose. The "Still inside the adapter" bullet and the card previews notes below are history. `versionName` on master still carries `-beta.6`, so the next stable release needs a plain version and tag.

  **What the moves did (history).**
  - Compact Layout and Card Layout 2 were deleted, Card Layout became *Card Bottom*, Card Layout 3 became *Card Top*, and Compact Layout 2 became *Compact Layout* (display names only; the resource names and stored values `0`, `2`, `4`, `5` are unchanged, and `SharedPreferencesUtils.normalizePostLayout` maps the old `1` and `3` to Compact 2 and Card).
  - Moved out so far, each into its own file(s) in `adapters/`: `PostViewHolder`, the compact rows (`PostCompactBaseViewHolder` and `PostCompact2Left/RightThumbnailViewHolder`), the gallery rows (`PostGalleryViewHolder`, `PostGalleryBaseGalleryTypeViewHolder`, `PostGalleryGalleryTypeViewHolder`), `PostBaseViewHolder` with the text rows (`PostTextTypeViewHolder`, `PostMaterial3CardTextTypeViewHolder`), and the video rows (`VideoAutoplayImpl`, `PostBaseVideoAutoplayViewHolder` and its four leaf rows).
  - Shipped and confirmed on the phone: up to `8.3.3.9.1.25-beta.1` (PostViewHolder, compact, and the layout removal). Merged and released but **not yet confirmed on a phone**: gallery (`beta.3`), text and `PostBaseViewHolder` (`beta.4`), video (`beta.5`); `beta.2` is only the Compact Layout rename. Do not start the next family until the owner has tried those.
  - **Still inside the adapter:** `PostWithPreviewTypeViewHolder`, `PostBaseGalleryTypeViewHolder` (a Toro player), `PostGalleryTypeViewHolder`, and the Material3 preview and gallery-type rows (`PostMaterial3CardWithPreviewViewHolder`, `PostMaterial3CardGalleryTypeViewHolder`), plus `StickiedCollapsedViewHolder`. Moving them leaves only binding, recycling and view-type code in the adapter. `PostBaseViewHolder` still names `PostRecyclerViewAdapter.PostBaseGalleryTypeViewHolder` by its adapter-qualified name; that goes away with this family.

  **How a family is moved (it worked five times).**
  1. Read the family's classes and list every adapter field and method they touch. Put those on a host interface that extends `PostRowHost` (see `PostCompactHost`, `PostGalleryHost`, `PostVideoHost`). The adapter's named inner class `RowHost` implements all of them and reads the adapter's fields **at call time**, so settings the fragment changes while a feed is open still reach rows that already exist. Anything two families share goes up into `PostRowHost`.
  2. Move the classes to top-level classes in the same package, with no logic changes. Each class that needs the activity keeps its own `private final BaseActivity mActivity` and its own `host`; do not make them package-private, or the adapter's remaining inner subclasses will bind to the wrong `mActivity`.
  3. Change the adapter only where it creates the rows (pass `mRowHost` as the last constructor argument) and where it uses `instanceof` or casts. `PostFragmentBase` names some row classes too; update it and its imports.
  4. Bump the version, add a changelog entry, push a branch, open the pull request with `gh pr create --repo jayblah/redditjr` (without `--repo` it opens a pull request in the upstream project), and let CI compile it. There is no JDK on the work machine, so CI is the only compiler.

  **Things that went wrong, so they do not again.**
  - A row's `setItemViewBackgroundColor` override can run inside its superclass constructor, before the row's own fields exist. Rows like that must read the host through `rowHost()` on `PostViewHolder`, not a field of their own (a field would be null and crash the row on creation, and CI would not catch it).
  - Fields the adapter reads from a moved class (`toroPlayer.isManuallyPaused`, `mediaUri` and so on) were `private` while the class was inner; they must become package-private.
  - NullAway wants `@SuppressWarnings("NullAway.Init")` on classes whose views or `post` are assigned after construction, and `@Nullable` on host methods that return nullable fields.
  - Script edits left behind annotations of deleted classes (`@UnstableApi`) and doubled a `)` in a constructor call; read the diff for those.
  - A string removed from `values/strings.xml` must also be removed from every `values-*/strings.xml`, and `SettingsSearchRegistryTest` and `AccountScopedKeysTest` list preference keys and titles, so a removed setting needs them updated.

  **Take care with the card previews family.**
  - `PostBaseGalleryTypeViewHolder` is a Toro player. Its play, pause, release and "manually paused" state must move as they are; its logic lives in `GalleryGifAutoplay` and the adapter's bind and recycle code, not in the holder.
  - Rows are recycled. The bind code sets and clears things in both directions because a row can arrive carrying the last post's state. Keep every "set it back" line.
  - Do not change how the adapter picks a view type, the stickied one-line row, the card top border or the vote hiding.

  **How to check a family.** The layout snapshot tests (`RoborazziLayoutTest`, `PostPreviewSnapshotTest`) cover the XML layouts, not the adapter's binding, so a green build is not enough. After CI passes and the beta is out, the owner gets a checklist of what to open on the phone: every layout the family appears in (Card Bottom, Card Top, Compact Layout, Gallery) with a text post, an image, a GIF, a video, a gallery and an NSFW post, plus scrolling a long feed, voting, saving, hiding and undoing, read and unread swipes, and rotating the phone. Wait for the "looks good" before starting the next family.

  **After the last family: the real DRY work, one release each.** The moves above deliberately changed no logic, so the duplication is still there: upvote and downvote in `PostViewHolder` are two near-identical 100-line blocks, `PostViewHolder` has two near-identical `setBaseView` overloads, each card type repeats long binding lists, and the host getters are written twice (interface and `RowHost`). Merging the vote blocks is the biggest and best-tested win; give each its own release and a vote-by-vote phone check.

  **Stop and ask if** a row needs something from the adapter that cannot be passed in cleanly, or a family cannot be moved without changing how its rows behave.
- [ ] **Reorganise Settings (next, before the DRY pass above).** About 350 settings on 30 screens; the owner finds much of it clutter. Audit done 2026-11-05, nothing changed yet. **Blocked on the owner's list of settings to delete** (not decided yet); do not remove anything without it.
  - Findings: the same toggles live on several screens (Post Details repeats Hide Post Type, Hide Post Flair, Hide Subreddit/User Prefix, Hide Number of Votes, Hide Vote Buttons, Hide Number of Comments, Show Gallery Media as Grid from Post; Embedded Media Type is in both Comments and Post Details); Interface and Miscellaneous are catch-alls; Time Format, Immersive Interface, Notifications, Proxy, Security and Backup and Restore are tiny screens that cost a tap each; gestures are spread over Gestures and Buttons, Swipe Actions, Post Swipe Actions and Comment Swipe Actions (volume-key navigation appears twice, Disable Swiping Between Tabs sits apart); media settings are in Video, Data Saving, Download Location and Device; Account and Global Settings Management plus Backup and Restore could be one "Data and backup" screen.
  - Proposed top level: Appearance (theme, fonts, layout and columns), Feeds and posts, Comments, Media, Gestures and navigation, Content filters, Privacy and security, Accounts and data, About. Each setting in exactly one place.
  - Questionable entries to ask about: Delete All Legacy Settings, Use /api/info the Old Way, Decode NATO Binary, Swap Tap and Long Press in Comments, Disable Profile Avatar Animation, the dozen "Collapse X Section" drawer toggles, three font families each with a custom variant.
  - How: one screen group per release, like the adapter split. Moving a setting keeps its saved value; deleting or renaming a key needs a migration. `SettingsSearchRegistry`, `SettingsSearchRegistryTest` and `AccountScopedKeysTest` list keys and titles and need updating with each move, and every string removed from `values/strings.xml` must go from the 23 `values-*` files too.
- [ ] **Small follow-ups from 8.3.3.9.1.26.**
  - The Change Post Layout submenu does not tick the current layout; screens do not expose it. Add that if it is missed.
  - The Card Clean name is English only; the other languages fall back to English. Check the 16dp strip under the content on a phone.
  - Settings is added to the three-dot menu in `BaseActivity.applyMenuItemTheme`, so the five full-screen media viewers (image, gallery, video, Imgur) do not show it.
  - Backups are now plain zips (they hold accounts, Client ID and the Giphy key); the English description says so.
- [ ] **Audit leftovers.** Found but left alone as larger or riskier than a quick win:
  - `RestoreSettings` repeats the same "read a table, filter it to known accounts, insert it" steps about twenty times, each with small differences. The backup side now has a helper; the restore side handles your data and each table has its own rules, so change it with a restore test in hand.
  - Several places read `getDisplayMetrics().density` by hand where `Utils.convertDpToPixel` exists.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
