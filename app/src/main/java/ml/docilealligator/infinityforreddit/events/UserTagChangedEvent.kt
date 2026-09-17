package ml.docilealligator.infinityforreddit.events

/**
 * Posted after a user tag is set, changed or removed, so every open screen showing that user's
 * name redraws it (issue #413). The tag is set from the profile, which is stacked on top of
 * whatever feed, thread or inbox the name was tapped in, and those are the screens that have to
 * show the new tag when the profile is backed out of.
 *
 * [username] is the user whose tag changed, or null when every tag went at once, as the Account
 * Settings Management row does. Subscribers redraw wholesale either way: a name can be on screen
 * any number of times, and a full rebind is cheaper than finding them.
 */
class UserTagChangedEvent(val username: String?)
