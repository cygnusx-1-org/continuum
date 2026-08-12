package ml.docilealligator.infinityforreddit.settings;

import android.os.Bundle;
import androidx.annotation.Nullable;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;

public class SortTypePreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.sort_type_preferences, rootKey);
    }
}