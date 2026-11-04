# TODO

Things deliberately left alone, and why.

- [ ] **OAuth redirect URI: leave it.** It is still `continuum://localhost` (and `127.0.0.1`) in `app/src/main/AndroidManifest.xml` and the login code. Reddit Jr. depends on the same Reddit access RedReader uses, so changing it could break login. Only revisit it if that stops being true.
- [ ] **Rename `r/ContinuumAll`.** The pseudo-subreddit name is in `Constants.java` (`CONTINUUM_ALL_SUBREDDIT`) and in the post filter text. Post filters and the database store the name, so renaming needs a migration of saved filters.
- [ ] **Rename the internal `Continuum` identifiers.** `ContinuumIssueRegistry` in `lint-checks`, `isContinuumAll` and similar. Invisible to users; only worth doing alongside the item above.
- [ ] **First gallery slow to scroll.** Feed videos were already paused when another screen opens, so the likely culprit was a GIF card still animating in the feed behind the gallery. Those are now stopped too, and the list loads screen-sized pictures and preloads the next two. If the first gallery is still slower than later ones, it is something else; say so.
- [ ] **Split up `PostRecyclerViewAdapter`.** One file of about 8,000 lines holds every post layout: it builds the rows, binds them, and handles votes, hiding, autoplay and swipe actions. Splitting it into several files would make it easier to read and change, and does nothing for speed. It is a large move that can break a layout in ways only a phone shows, so it is left until there is a reason to touch that code.
- [ ] **Update notifications.** The app only checks when it opens, at most once a day.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
