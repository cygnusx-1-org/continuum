package ml.docilealligator.infinityforreddit.fragments;

/**
 * Lists other users' multireddits (custom feeds) that the current account has saved/followed
 * locally. Reuses all of {@link MultiRedditListingFragment}; this subclass only exists so the
 * hosting pager can distinguish the two MultiReddit tabs via {@code instanceof}.
 */
public class FollowedMultiRedditListingFragment extends MultiRedditListingFragment {

    public FollowedMultiRedditListingFragment() {
        // Required empty public constructor
    }
}
