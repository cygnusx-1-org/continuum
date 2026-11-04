# TODO

Things deliberately left alone, and why.

- [ ] **Rename the OAuth redirect URI.** It is still `continuum://localhost` (and `127.0.0.1`) in `app/src/main/AndroidManifest.xml` and the login code. Reddit only allows one redirect URI per Reddit app, so do not edit the existing app if anything else (RedReader, another client) uses the same client ID: that would break its next login. Instead create a second Reddit app for Reddit Jr. with the new URI, put its client ID in Reddit Jr., change the manifest and code to match, and log in again. Logins already made keep working either way until their tokens are refreshed.
- [ ] **Rename `r/ContinuumAll`.** The pseudo-subreddit name is in `Constants.java` (`CONTINUUM_ALL_SUBREDDIT`) and in the post filter text. Post filters and the database store the name, so renaming needs a migration of saved filters.
- [ ] **Rename the internal `Continuum` identifiers.** `ContinuumIssueRegistry` in `lint-checks`, `isContinuumAll` and similar. Invisible to users; only worth doing alongside the item above.
- [ ] **Triangle indicator in the card layouts.** Only compact layouts have it. Card Layout 1 and 3 now have the Post Type Card Border instead; Card Layout 2 has neither.
- [ ] **Recently Visited.** Decide whether to remove the sidebar's Recently Visited row and its Settings screen, since Recents covers most of it.
- [ ] **API statistics screen.** The Debug settings screen is gone, which was the only way to open `ApiStatisticsActivity`. Either put it back somewhere or delete the activity and the call counting behind it.
- [ ] **Hide Post Type Icon.** It hides the play badge on video and GIF previews, which card layouts only show when Video Autoplay is off or the post is not autoplayed. If it still seems to do nothing in a card layout, say which icon you expected it to hide.
- [ ] **First gallery slow to scroll.** The vertical list now loads screen-sized pictures and preloads the next two. If the first gallery after opening the app is still slower than later ones, look at what the feed behind it is doing (autoplaying video).
- [ ] **Split up `PostRecyclerViewAdapter`.** Scrolling is fine, so this is only for readability.
- [ ] **Reverse image search on video frames.**
- [ ] **Update notifications.** The app only checks when it opens, at most once a day.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
