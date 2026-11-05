# TODO

Things deliberately left alone, and why.

- [ ] **Split up `PostRecyclerViewAdapter`.** The repeated casts are gone, but the file is still about 7,900 lines. Do this only when there is a reason to change that code. When you do, give me this prompt and I will follow it:

  > Split the post list's row classes out of `PostRecyclerViewAdapter` into their own files, one family per pull request: compact, then gallery, then text, then video, then card previews. Read the family's classes and everything in the adapter they use first, and tell me the plan before changing anything.

  **What is there.** About 3,800 lines (from `PostViewHolder` to the end of the file) are the row classes, one per layout and post type. They are inner classes, so they read the adapter's fields directly: `mActivity`, `mGlide`, the colors, the settings flags, the autoplay state and the click handling. The adapter's own code binds, recycles and updates them through `instanceof` checks.

  **Plan for each family.**
  1. List every adapter field and method the family's classes touch. That list is the whole job. Put the colors and settings the rows read in one small object that the adapter builds and passes in, and give actions such as vote, hide and open a post to the rows as interfaces or functions, not as references to the adapter.
  2. Move the family's classes to top-level classes in the same package. Keep their names, the fields other code reads (`post`, `animatingGif`, `preview`, the views), and every listener behaving the same. No renames, no reformatting and no logic changes in the same pull request.
  3. Change the adapter only where it creates, binds or recycles those rows.
  4. Settings that change while a feed is open (the `set...` methods the fragment calls, then `refreshAdapter()`) must still reach the rows, so check the object holding them is updated, not copied once.

  **Take care with.**
  - Video and gallery rows are Toro players. Their play, pause, release and "manually paused" state must move as they are. Do the video family last.
  - Rows are recycled. The bind code sets and clears things in both directions because a row can arrive carrying the last post's state. Keep every "set it back" line.
  - Do not change how the adapter picks a view type, the stickied one-line row, the card top border or the vote hiding.
  - The build uses NullAway and Error Prone. Run the build in CI before each step; a failing compile there is cheaper than finding it on a phone.

  **How to check it.** The layout snapshot tests (`RoborazziLayoutTest`, `PostPreviewSnapshotTest`) cover the XML layouts, not the adapter's binding, so a green build is not enough. For each family, after CI passes, I give you a checklist of exactly what to open on your phone: every layout that family appears in (Card, Card 2, Card 3, Compact, Compact 2, Gallery), with a text post, an image, a GIF, a video, a gallery and an NSFW post, plus scrolling through a long feed, voting, hiding and undoing, and rotating the phone. Ship each family as its own release and wait for your "looks good" before starting the next.

  **Stop and ask if** a row needs something from the adapter that cannot be passed in cleanly, or a family cannot be moved without changing how its rows behave.
- [ ] **Audit leftovers.** Found but left alone as larger or riskier than a quick win:
  - `RestoreSettings` repeats the same "read a table, filter it to known accounts, insert it" steps about twenty times, each with small differences. The backup side now has a helper; the restore side handles your data and each table has its own rules, so change it with a restore test in hand.
  - Several places read `getDisplayMetrics().density` by hand where `Utils.convertDpToPixel` exists.

Kept on purpose: the README credits Continuum and Infinity for Reddit as upstream, because their AGPL license requires it.
