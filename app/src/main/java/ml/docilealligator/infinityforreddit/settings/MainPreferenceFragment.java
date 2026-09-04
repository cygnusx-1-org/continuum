package ml.docilealligator.infinityforreddit.settings;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.preference.Preference;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.ApiStatisticsActivity;
import ml.docilealligator.infinityforreddit.activities.CommentFilterPreferenceActivity;
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity;
import ml.docilealligator.infinityforreddit.activities.PostFilterPreferenceActivity;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class MainPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.main_preferences, rootKey);
        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        Preference securityPreference = findPreference(SharedPreferencesUtils.SECURITY);
        Preference postFilterPreference = findPreference(SharedPreferencesUtils.POST_FILTER);
        Preference commentFilterPreference = findPreference(SharedPreferencesUtils.COMMENT_FILTER);
        Preference privacyPolicyPreference = findPreference(SharedPreferencesUtils.PRIVACY_POLICY_KEY);
        Preference apiStatisticsPreference = findPreference(SharedPreferencesUtils.API_STATISTICS);

        BiometricManager biometricManager = BiometricManager.from(mActivity);
        if (biometricManager.canAuthenticate(BIOMETRIC_STRONG | DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            if (securityPreference != null) {
                // Only hidden, not promoted: Security used to be the first row of its group and
                // handed the rounded top on to the next one. It sits in the middle now, so rounding
                // anything here would open a seam mid-group instead of closing one.
                securityPreference.setVisible(false);
            }
        }

        if (postFilterPreference != null) {
            postFilterPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(mActivity, PostFilterPreferenceActivity.class);
                mActivity.startActivity(intent);
                return true;
            });
        }

        if (commentFilterPreference != null) {
            commentFilterPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    Intent intent = new Intent(mActivity, CommentFilterPreferenceActivity.class);
                    mActivity.startActivity(intent);
                    return true;
                }
            });
        }

        if (apiStatisticsPreference != null) {
            apiStatisticsPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(mActivity, ApiStatisticsActivity.class);
                mActivity.startActivity(intent);
                return true;
            });
        }

        if (privacyPolicyPreference != null) {
            privacyPolicyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(mActivity, LinkResolverActivity.class);
                    intent.setData(Uri.parse("https://github.com/cygnusx-1-org/continuum"));
                    mActivity.startActivity(intent);
                    return true;
                }
            });
        }
    }
}
