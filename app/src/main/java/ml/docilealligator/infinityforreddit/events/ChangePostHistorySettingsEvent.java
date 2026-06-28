package ml.docilealligator.infinityforreddit.events;

public class ChangePostHistorySettingsEvent {
    public boolean markPostsAsRead;
    public boolean markPostsAsReadAfterVoting;
    public boolean markPostsAsReadOnScroll;

    public ChangePostHistorySettingsEvent(boolean markPostsAsRead, boolean markPostsAsReadAfterVoting,
                                          boolean markPostsAsReadOnScroll) {
        this.markPostsAsRead = markPostsAsRead;
        this.markPostsAsReadAfterVoting = markPostsAsReadAfterVoting;
        this.markPostsAsReadOnScroll = markPostsAsReadOnScroll;
    }
}
