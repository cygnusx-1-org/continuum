package ml.docilealligator.infinityforreddit.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.BuildConfig;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

/**
 * A simple {@link PreferenceFragmentCompat} subclass.
 */
public class AboutPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.about_preferences, rootKey);

        Preference openSourcePreference = findPreference(SharedPreferencesUtils.OPEN_SOURCE_KEY);
        Preference sharePreference = findPreference(SharedPreferencesUtils.SHARE_KEY);
        Preference versionPreference = findPreference(SharedPreferencesUtils.VERSION_KEY);


        if (openSourcePreference != null) {
            openSourcePreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(mActivity, LinkResolverActivity.class);
                intent.setData(Uri.parse("https://github.com/jayblah/redditjr"));
                mActivity.startActivity(intent);
                return true;
            });
        }

        if (sharePreference != null) {
            sharePreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_this_app));
                if (intent.resolveActivity(mActivity.getPackageManager()) != null) {
                    mActivity.startActivity(intent);
                } else {
                    Toast.makeText(mActivity, R.string.no_app, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        if (versionPreference != null) {
            String summary = String.format("%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.APPLICATION_ID);
            versionPreference.setSummary(summary);

            versionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                int clickedTimes = 0;

                @Override
                public boolean onPreferenceClick(Preference preference) {
                    clickedTimes++;
                    if (clickedTimes > 6) {
                        Toast.makeText(mActivity, R.string.no_developer_easter_egg, Toast.LENGTH_SHORT).show();
                        clickedTimes = 0;
                    }
                    return true;
                }
            });
        }

    }
}
