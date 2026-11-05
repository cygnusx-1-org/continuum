package ml.docilealligator.infinityforreddit.adapters;

/**
 * What the link, image and text-with-preview rows need on top of {@link PostRowHost}. Like it, the
 * value is read when asked for: the setting changes while a feed is open.
 */
interface PostPreviewHost extends PostRowHost {
    boolean fixedHeightPreviewInCard();
}
