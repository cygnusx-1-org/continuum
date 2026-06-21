package ml.docilealligator.infinityforreddit.events;

// Posted when the locally stored subscription list for the Anonymous account changes (a subreddit
// is subscribed to or unsubscribed from). The anonymous home and multireddit feeds are assembled
// from this local list, so they listen for this event to reload instead of waiting for a restart.
public class ChangeAnonymousSubredditSubscriptionEvent {
}
