package ml.docilealligator.infinityforreddit.events

/**
 * Comment swipe actions were switched on or off.
 *
 * Its own event, and not [ChangeEnableSwipeActionSwitchEvent]: the post feed and the two comment
 * surfaces are switched separately now, and attaching a touch helper to the wrong one is exactly
 * the mistake one shared event would invite.
 */
class ChangeEnableCommentSwipeActionSwitchEvent(@JvmField val enableSwipeAction: Boolean)
