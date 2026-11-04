# TODO

Things deliberately left alone, and why.

- [ ] **OAuth redirect: nothing to do.** Reddit Jr. logs in with RedReader's built-in client ID and its `redreader://rr_oauth_redir` redirect, which belongs to RedReader and must not change. The manifest's own entry is now `redditjr://localhost`, used only if you turn on API key overrides and register your own Reddit app with that redirect.
- [ ] **Rename `r/ContinuumAll`: needs a subreddit first.** The feed's name is a real subreddit upstream registered on Reddit, which is what lets its About tab and Subscribe button work. A name Reddit does not know would 404 both. To rename it, create a subreddit with the new name (for example `r/RedditJrAll`) on Reddit, then change `Constants.REDDITJR_ALL_SUBREDDIT`, and add a migration that rewrites saved post filters (`post_filter_usage.name_of_usage`) from the old name. Everything in code already goes through that one constant.
- [ ] **First gallery slow to scroll.** Feed videos were already paused when another screen opens, so the likely culprit was a GIF card still animating in the feed behind the gallery. Those are now stopped too, and the list loads screen-sized pictures and preloads the next two. If the first gallery is still slower than later ones, it is something else; say so.
- [ ] **Split up `PostRecyclerViewAdapter`.** One file of about 8,000 lines holds every post layout: it builds the rows, binds them, and handles votes, hiding, autoplay and swipe actions. Splitting it into several files would make it easier to read and change, and does nothing for speed. It is a large move that can break a layout in ways only a phone shows, so it is left until there is a reason to touch that code.
- [ ] **Update notifications.** The app only checks when it opens, at most once a day.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
