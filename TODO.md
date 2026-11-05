# TODO

Things deliberately left alone, and why.

- [ ] **OAuth redirect: nothing to do.** Reddit Jr. logs in with RedReader's built-in client ID and its `redreader://rr_oauth_redir` redirect, which belongs to RedReader and must not change. The manifest's own entry is now `redditjr://localhost`, used only if you turn on API key overrides and register your own Reddit app with that redirect.
- [ ] **First gallery slow to scroll.** Feed videos were already paused when another screen opens, so the likely culprit was a GIF card still animating in the feed behind the gallery. Those are now stopped too, and the list loads screen-sized pictures and preloads the next two. If the first gallery is still slower than later ones, it is something else; say so.
- [ ] **Split up `PostRecyclerViewAdapter`.** The repeated casts are gone, but the file is still about 7,900 lines. Roughly 3,800 of them are the row classes (one per layout and post type), written as inner classes that reach straight into the adapter's colors, settings and listeners. Moving them to their own files means turning them into separate classes that are handed those values, which touches every row and can break a layout in ways only a phone shows. Do it one family at a time (compact, gallery, video, text), with a screenshot check of each layout, when there is a reason to change that code.
- [ ] **Comment media button.** It lists the images and GIFs in the comments that are loaded and not folded away, from the pictures Reddit attached and the image links in the text. Videos and links to other sites such as Imgur albums are not included.
- [ ] **Decoder forum search is two buttons.** The userscript has one button that searches titles first and the whole forum on the next tap, with the icon changing from ◎ to ◉. The app has `TITLE` and `ALL` buttons, which you asked for, and which reach the same two searches. Switch to the single button if you want it to match the userscript exactly.
- [ ] **Audit leftovers.** Found but left alone as larger or riskier than a quick win:
  - `RestoreSettings` repeats the same "read a table, filter it to known accounts, insert it" steps about twenty times, each with small differences. The backup side now has a helper; the restore side handles your data and each table has its own rules, so change it with a restore test in hand.
  - Several places read `getDisplayMetrics().density` by hand where `Utils.convertDpToPixel` exists.
- [ ] **Update notifications.** The app only checks when it opens, at most once a day.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
