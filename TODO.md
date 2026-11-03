# TODO

Things deliberately left alone, and why.

- [ ] **Rename the OAuth redirect URI.** It is still `continuum://localhost` (and `127.0.0.1`) in `app/src/main/AndroidManifest.xml` and the login code. Reddit only allows one redirect URI per Reddit app, so do not edit the existing app if anything else (RedReader, another client) uses the same client ID: that would break its next login. Instead create a second Reddit app for Reddit Jr. with the new URI, put its client ID in Reddit Jr., change the manifest and code to match, and log in again. Logins already made keep working either way until their tokens are refreshed.
- [ ] **Rename `r/ContinuumAll`.** The pseudo-subreddit name is in `Constants.java` (`CONTINUUM_ALL_SUBREDDIT`) and in the post filter text. Post filters and the database store the name, so renaming needs a migration of saved filters.
- [ ] **Rename the internal `Continuum` identifiers.** `ContinuumIssueRegistry` in `lint-checks`, `isContinuumAll` and similar. Invisible to users; only worth doing alongside the item above.
- [ ] **Triangle indicator in the card layouts.** Only compact layouts have it. Card Layout 1 and 3 now have the Post Type Card Border instead; Card Layout 2 has neither.
- [ ] **Recently Visited and Debug.** Decide whether to remove the sidebar's Recently Visited row (Recents covers it) and the Debug settings screen.
- [ ] **GIFs in the vertical gallery list** show as stills; tapping one plays it in the viewer.
- [ ] **Split up `PostRecyclerViewAdapter`.** Scrolling is fine, so this is only for readability.
- [ ] **Reverse image search on video frames.**
- [ ] **Update notifications.** The app only checks when it opens, at most once a day.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
