package ml.docilealligator.infinityforreddit.asynctasks;

import android.view.Menu;
import android.view.MenuItem;
import retrofit2.Retrofit;

/**
 * Drives the "flair is required for this subreddit" UI:
 *  - fetches /api/v1/{sub}/post_requirements when the target sub changes
 *  - keeps the submit menu item enabled iff (not posting) AND (flair present OR not required)
 *  - tells the host activity to refresh its flair label/color via the onStateChanged callback
 *
 * The host is responsible for actually rendering the flair label/color, since each activity has
 * its own binding name. It can call isFlairRequired() to decide whether to use the warning style.
 */
public class FlairRequirementController {

    public interface StateChangeListener {
        void onFlairRequirementStateChanged();
    }

    private final Retrofit oauthRetrofit;
    private final int sendItemId;
    private final StateChangeListener listener;

    private Menu menu;
    private boolean isFlairRequired = false;
    private boolean hasFlair = false;
    private boolean isPosting = false;
    private String currentSubreddit;

    public FlairRequirementController(Retrofit oauthRetrofit, int sendItemId, StateChangeListener listener) {
        this.oauthRetrofit = oauthRetrofit;
        this.sendItemId = sendItemId;
        this.listener = listener;
    }

    public boolean isFlairRequired() {
        return isFlairRequired;
    }

    public void setMenu(Menu menu) {
        this.menu = menu;
        updateMenu();
    }

    public void setPosting(boolean posting) {
        this.isPosting = posting;
        updateMenu();
    }

    public void setHasFlair(boolean hasFlair) {
        this.hasFlair = hasFlair;
        updateMenu();
    }

    public void onSubredditChanged(String subName, boolean isUser, String accessToken) {
        currentSubreddit = subName;
        isFlairRequired = false;
        notifyChanged();
        if (isUser || subName == null || subName.isEmpty() || accessToken == null) {
            return;
        }
        FetchPostRequirements.fetch(oauthRetrofit, accessToken, subName,
                new FetchPostRequirements.FetchPostRequirementsListener() {
                    @Override
                    public void onSuccess(boolean isFlairRequiredResult) {
                        if (subName.equalsIgnoreCase(currentSubreddit)) {
                            isFlairRequired = isFlairRequiredResult;
                            notifyChanged();
                        }
                    }

                    @Override
                    public void onFail() {
                        // Best-effort hint; Reddit will reject at submit if truly required.
                    }
                });
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onFlairRequirementStateChanged();
        }
        updateMenu();
    }

    private void updateMenu() {
        if (menu == null) return;
        MenuItem item = menu.findItem(sendItemId);
        if (item == null) return;
        boolean enabled = !isPosting && !(isFlairRequired && !hasFlair);
        item.setEnabled(enabled);
        if (item.getIcon() != null) {
            item.getIcon().setAlpha(enabled ? 255 : 130);
        }
    }
}
