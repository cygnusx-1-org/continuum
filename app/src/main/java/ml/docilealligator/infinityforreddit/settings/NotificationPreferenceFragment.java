package ml.docilealligator.infinityforreddit.settings;

import static ml.docilealligator.infinityforreddit.utils.Utils.HOSTNAME_REGEX;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.HashSet;
import com.google.common.net.InetAddresses;
import androidx.preference.PreferenceDataStore;
import androidx.preference.EditTextPreference;
import android.widget.Toast;
import android.content.Context;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.worker.PullNotificationWorker;

/**
 * A simple {@link Fragment} subclass.
 */
public class NotificationPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;
    @Inject
    @Named("internal")
    SharedPreferences mInternalSharedPreferences;
    private boolean enableNotification;
    private long notificationInterval;

    /** The proxy rows are in their own file; everything else on this screen is in the default one. */
    @Nullable
    @Override
    protected PreferenceDataStore createPreferenceDataStore() {
        SharedPreferences primary = mActivity.accountScopedPreferencesFor(getPreferenceManager().getSharedPreferencesName());
        if (primary == null) {
            return null;
        }
        return new KeyRoutedPreferenceDataStore(primary,
                mActivity.getSharedPreferences(SharedPreferencesUtils.PROXY_SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE),
                new HashSet<>(Arrays.asList(SharedPreferencesUtils.PROXY_ENABLED, SharedPreferencesUtils.PROXY_TYPE,
                        SharedPreferencesUtils.PROXY_HOSTNAME, SharedPreferencesUtils.PROXY_PORT)));
    }

    private void setUpProxy() {
        EditTextPreference proxyHostnamePref = findPreference(SharedPreferencesUtils.PROXY_HOSTNAME);
        EditTextPreference proxyPortPref = findPreference(SharedPreferencesUtils.PROXY_PORT);

        if (proxyHostnamePref != null) {
            proxyHostnamePref.setOnPreferenceChangeListener(((preference, newValue) -> {
                boolean isHostname = Pattern.matches(HOSTNAME_REGEX, (String) newValue);
                boolean isInetAddress = InetAddresses.isInetAddress((String) newValue);

                if (!isInetAddress && !isHostname) {
                    Toast.makeText(mActivity, R.string.not_a_valid_ip_or_hostname, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }));
        }

        if (proxyPortPref != null) {
            proxyPortPref.setOnPreferenceChangeListener(((preference, newValue) -> {
                try {
                    int port = Integer.parseInt((String) newValue);
                    if (port < 0 || port > 65535) {
                        Toast.makeText(mActivity, R.string.not_a_valid_port, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(mActivity, R.string.not_a_valid_number, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }));
        }
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.notification_preferences, rootKey);

        WorkManager workManager = WorkManager.getInstance(mActivity);

        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);

        setUpProxy();

        SwitchPreference enableNotificationSwitchPreference = findPreference(SharedPreferencesUtils.ENABLE_NOTIFICATION_KEY);
        ListPreference notificationIntervalListPreference = findPreference(SharedPreferencesUtils.NOTIFICATION_INTERVAL_KEY);

        enableNotification = sharedPreferences.getBoolean(SharedPreferencesUtils.ENABLE_NOTIFICATION_KEY, true);
        notificationInterval = SharedPreferencesUtils.getLong(sharedPreferences, SharedPreferencesUtils.NOTIFICATION_INTERVAL_KEY, "1");

        if (enableNotification) {
            if (notificationIntervalListPreference != null) {
                notificationIntervalListPreference.setVisible(true);
            }
        }

        if (enableNotificationSwitchPreference != null) {
            enableNotificationSwitchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                enableNotification = ((Boolean) newValue);
                if (notificationIntervalListPreference != null) {
                    notificationIntervalListPreference.setVisible(enableNotification);
                }

                if (enableNotification) {
                    TimeUnit timeUnit = (notificationInterval == 15 || notificationInterval == 30) ? TimeUnit.MINUTES : TimeUnit.HOURS;

                    Constraints constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build();

                    PeriodicWorkRequest pullNotificationRequest =
                            new PeriodicWorkRequest.Builder(PullNotificationWorker.class,
                                    notificationInterval, timeUnit)
                                    .setConstraints(constraints)
                                    .setInitialDelay(notificationInterval, timeUnit)
                                    .build();

                    workManager.enqueueUniquePeriodicWork(PullNotificationWorker.UNIQUE_WORKER_NAME,
                            ExistingPeriodicWorkPolicy.REPLACE, pullNotificationRequest);
                } else {
                    workManager.cancelUniqueWork(PullNotificationWorker.UNIQUE_WORKER_NAME);
                }
                return true;
            });
        }

        if (notificationIntervalListPreference != null) {
            notificationIntervalListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                notificationInterval = Long.parseLong((String) newValue);

                if (enableNotification) {
                    TimeUnit timeUnit = (notificationInterval == 15 || notificationInterval == 30) ? TimeUnit.MINUTES : TimeUnit.HOURS;

                    Constraints constraints = new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build();

                    PeriodicWorkRequest pullNotificationRequest =
                            new PeriodicWorkRequest.Builder(PullNotificationWorker.class,
                                    notificationInterval, timeUnit)
                                    .setConstraints(constraints)
                                    .setInitialDelay(notificationInterval, timeUnit)
                                    .build();

                    workManager.enqueueUniquePeriodicWork(PullNotificationWorker.UNIQUE_WORKER_NAME,
                            ExistingPeriodicWorkPolicy.REPLACE, pullNotificationRequest);
                } else {
                    workManager.cancelUniqueWork(PullNotificationWorker.UNIQUE_WORKER_NAME);
                }

                return true;
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityResultLauncher<String> requestNotificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
                mInternalSharedPreferences.edit().putBoolean(SharedPreferencesUtils.HAS_REQUESTED_NOTIFICATION_PERMISSION, true).apply();
                if (!result) {
                    mActivity.showSnackbar(R.string.denied_notification_permission, R.string.go_to_settings, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            Uri uri = Uri.fromParts("package", mActivity.getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        }
                    });
                }
            });

            if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
}
