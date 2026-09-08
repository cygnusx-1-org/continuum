package ml.docilealligator.infinityforreddit.events

/**
 * A random subreddit is being opened, so the search screen underneath is done.
 *
 * Posted rather than returned as an activity result: the search screen is stopped by the time the
 * pick is made, and a result is not delivered to a stopped activity -- it waits for the next resume,
 * which is the user coming back from the subreddit, and closes the screen in front of them with its
 * keyboard already on the way up. A posted event reaches the subscriber straight away, whatever
 * lifecycle state it is in, so the screen is gone before anything can show it.
 */
class RandomSubredditOpenedEvent
