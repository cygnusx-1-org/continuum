# TODO

Things deliberately left alone, and why.

- [ ] **Rename the OAuth redirect URI.** It is still `continuum://localhost` (and `127.0.0.1`) in `app/src/main/AndroidManifest.xml` and the login code. Your Reddit app is registered with that URI, so it has to change in both places at once: edit the Reddit app's redirect URI first, then the manifest and code, then log in again.
- [ ] **Rename `r/ContinuumAll`.** The pseudo-subreddit name is in `Constants.java` (`CONTINUUM_ALL_SUBREDDIT`) and in the post filter text. Post filters and the database store the name, so renaming needs a migration of saved filters.
- [ ] **Rename the internal `Continuum` identifiers.** `ContinuumIssueRegistry` in `lint-checks`, `isContinuumAll` and similar. Invisible to users; only worth doing alongside the item above.
- [ ] **Triangle indicator in the card layouts.** Only compact layouts have it.
- [ ] **Split up `PostRecyclerViewAdapter`.** Scrolling is fine, so this is only for readability.
- [ ] **Reverse image search on video frames.**
- [ ] **Update notifications.** The app only checks when it opens, at most once a day.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
