# TODO

Things deliberately left alone, and why.

- [ ] **Rename the OAuth redirect URI.** It is still `continuum://localhost` (and `127.0.0.1`) in `app/src/main/AndroidManifest.xml` and the login code. Reddit only allows one redirect URI per Reddit app, so do not edit the existing app if anything else (RedReader, another client) uses the same client ID: that would break its next login. Instead create a second Reddit app for Reddit Jr. with the new URI, put its client ID in Reddit Jr., change the manifest and code to match, and log in again. Logins already made keep working either way until their tokens are refreshed.
- [ ] **Rename `r/ContinuumAll`.** The pseudo-subreddit name is in `Constants.java` (`CONTINUUM_ALL_SUBREDDIT`) and in the post filter text. Post filters and the database store the name, so renaming needs a migration of saved filters.
- [ ] **Rename the internal `Continuum` identifiers.** `ContinuumIssueRegistry` in `lint-checks`, `isContinuumAll` and similar. Invisible to users; only worth doing alongside the item above.
- [ ] **Recently Visited storage.** The sidebar row, its list screen, its Settings screen and the code that recorded visits are gone. The `recently_visited` database table, its DAO, and its place in Backup & Restore are still there, since removing them needs a database migration and would break restoring older backups. Remove them in a later schema change.
- [ ] **API statistics screen.** The Debug settings screen is gone, which was the only way to open `ApiStatisticsActivity`. Either put it back somewhere or delete the activity and the call counting behind it.
- [ ] **First gallery slow to scroll.** Feed videos were already paused when another screen opens, so the likely culprit was a GIF card still animating in the feed behind the gallery. Those are now stopped too, and the list loads screen-sized pictures and preloads the next two. If the first gallery is still slower than later ones, it is something else; say so.
- [ ] **Split up `PostRecyclerViewAdapter`.** One file of about 8,000 lines holds every post layout: it builds the rows, binds them, and handles votes, hiding, autoplay and swipe actions. Splitting it into several files would make it easier to read and change, and does nothing for speed. It is a large move that can break a layout in ways only a phone shows, so it is left until there is a reason to touch that code.
- [ ] **Update notifications.** The app only checks when it opens, at most once a day.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
