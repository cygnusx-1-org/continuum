package ml.docilealligator.infinityforreddit.settings;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

/**
 * A simple {@link PreferenceFragmentCompat} subclass.
 */
public class DebugPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.debug_preferences, rootKey);

        Preference screenWidthDpPreference = findPreference(SharedPreferencesUtils.SCREEN_WIDTH_DP_KEY);
        Preference smallestScreenWidthDpPreference = findPreference(SharedPreferencesUtils.SMALLEST_SCREEN_WIDTH_DP_KEY);
        Preference isTabletPreference = findPreference(SharedPreferencesUtils.IS_TABLET_KEY);

        if (screenWidthDpPreference != null) {
            Configuration config = getResources().getConfiguration();
            int screenWidthDp = config.screenWidthDp;
            screenWidthDpPreference.setSummary(getString(R.string.settings_screen_width_dp_summary, screenWidthDp));
        }

        if (smallestScreenWidthDpPreference != null) {
            Configuration config = getResources().getConfiguration();
            int smallestScreenWidthDp = config.smallestScreenWidthDp;
            smallestScreenWidthDpPreference.setSummary(getString(R.string.settings_smallest_screen_width_dp_summary, smallestScreenWidthDp));
        }

        if (isTabletPreference != null) {
            boolean isTablet = getResources().getBoolean(R.bool.isTablet);
            isTabletPreference.setSummary(isTablet
                    ? getString(R.string.settings_is_tablet_summary_true)
                    : getString(R.string.settings_is_tablet_summary_false));
        }
    }
}
