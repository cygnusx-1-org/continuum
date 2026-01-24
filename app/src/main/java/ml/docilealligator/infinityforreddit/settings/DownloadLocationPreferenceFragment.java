package ml.docilealligator.infinityforreddit.settings;

import static android.content.Intent.ACTION_OPEN_DOCUMENT_TREE;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

// Imports for long click handling
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class DownloadLocationPreferenceFragment extends CustomFontPreferenceFragmentCompat {
    private static final int IMAGE_DOWNLOAD_LOCATION_REQUEST_CODE = 10;
    private static final int GIF_DOWNLOAD_LOCATION_REQUEST_CODE = 11;
    private static final int VIDEO_DOWNLOAD_LOCATION_REQUEST_CODE = 12;
    private static final int NSFW_DOWNLOAD_LOCATION_REQUEST_CODE = 13;

    Preference imageDownloadLocationPreference;
    Preference gifDownloadLocationPreference;
    Preference videoDownloadLocationPreference;
    Preference nsfwDownloadLocationPreference;
    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;

    // Flag to track if a long press occurred
    private boolean isLongPressing = false;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        ((Infinity) mActivity.getApplication()).getAppComponent().inject(this);
        setPreferencesFromResource(R.xml.download_location_preferences, rootKey);

        imageDownloadLocationPreference = findPreference(SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION);
        gifDownloadLocationPreference = findPreference(SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION);
        videoDownloadLocationPreference = findPreference(SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION);
        nsfwDownloadLocationPreference = findPreference(SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION);

        if (nsfwDownloadLocationPreference != null) {
            String downloadLocation = sharedPreferences.getString(SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION, "");
            if (!downloadLocation.equals("")) {
                nsfwDownloadLocationPreference.setSummary(formatDownloadPath(downloadLocation));
            } else {
                nsfwDownloadLocationPreference.setSummary(R.string.settings_download_location_not_set);
            }

            nsfwDownloadLocationPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, NSFW_DOWNLOAD_LOCATION_REQUEST_CODE);
                return true;
            });
            // No long click for NSFW location for now, can be added if needed.
        }
        if (imageDownloadLocationPreference != null) {
            String downloadLocation = sharedPreferences.getString(SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION, "");
            if (!downloadLocation.equals("")) {
                imageDownloadLocationPreference.setSummary(formatDownloadPath(downloadLocation));
            } else {
                imageDownloadLocationPreference.setSummary(R.string.settings_download_location_not_set);
            }

            imageDownloadLocationPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, IMAGE_DOWNLOAD_LOCATION_REQUEST_CODE);
                return true;
            });
        }

        if (gifDownloadLocationPreference != null) {
            String downloadLocation = sharedPreferences.getString(SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION, "");
            if (!downloadLocation.equals("")) {
                gifDownloadLocationPreference.setSummary(formatDownloadPath(downloadLocation));
            } else {
                gifDownloadLocationPreference.setSummary(R.string.settings_download_location_not_set);
            }

            gifDownloadLocationPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, GIF_DOWNLOAD_LOCATION_REQUEST_CODE);
                return true;
            });
        }

        if (videoDownloadLocationPreference != null) {
            String downloadLocation = sharedPreferences.getString(SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION, "");
            if (!downloadLocation.equals("")) {
                videoDownloadLocationPreference.setSummary(formatDownloadPath(downloadLocation));
            } else {
                videoDownloadLocationPreference.setSummary(R.string.settings_download_location_not_set);
            }

            videoDownloadLocationPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, VIDEO_DOWNLOAD_LOCATION_REQUEST_CODE);
                return true;
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == IMAGE_DOWNLOAD_LOCATION_REQUEST_CODE) {
                mActivity.getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sharedPreferences.edit().putString(SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION, data.getDataString()).apply();
                if (imageDownloadLocationPreference != null) {
                    imageDownloadLocationPreference.setSummary(formatDownloadPath(data.getDataString()));
                }
            } else if (requestCode == GIF_DOWNLOAD_LOCATION_REQUEST_CODE) {
                mActivity.getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sharedPreferences.edit().putString(SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION, data.getDataString()).apply();
                if (gifDownloadLocationPreference != null) {
                    gifDownloadLocationPreference.setSummary(formatDownloadPath(data.getDataString()));
                }
            } else if (requestCode == VIDEO_DOWNLOAD_LOCATION_REQUEST_CODE) {
                mActivity.getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sharedPreferences.edit().putString(SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION, data.getDataString()).apply();
                if (videoDownloadLocationPreference != null) {
                    videoDownloadLocationPreference.setSummary(formatDownloadPath(data.getDataString()));
                }
            } else if (requestCode == NSFW_DOWNLOAD_LOCATION_REQUEST_CODE) {
                mActivity.getContentResolver().takePersistableUriPermission(data.getData(),
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                sharedPreferences.edit().putString(SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION, data.getDataString()).apply();
                if (nsfwDownloadLocationPreference != null) {
                    nsfwDownloadLocationPreference.setSummary(formatDownloadPath(data.getDataString()));
                }
            }
        }
    }

    private String formatDownloadPath(String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            return "";
        }
        String prefix = "content://com.android.externalstorage.documents/tree/primary%3A";
        if (uriString.startsWith(prefix)) {
            String encodedPath = uriString.substring(prefix.length());
            try {
                // Decode URL encoding (e.g., %2F -> /, %20 -> space)
                return "/" + URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                // Fallback to original string if decoding fails
                return uriString;
            }
        }
        // Return original string if prefix doesn't match
        return uriString;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent e) {
                    View childView = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (childView != null) {
                        int position = recyclerView.getChildAdapterPosition(childView);
                        if (position != RecyclerView.NO_POSITION) {
                            Preference preference = getPreferenceScreen().getPreference(position);
                            if (preference != null) {
                                String key = preference.getKey();
                                boolean handled = false;
                                if (SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION.equals(key)) {
                                    sharedPreferences.edit().remove(SharedPreferencesUtils.IMAGE_DOWNLOAD_LOCATION).apply();
                                    preference.setSummary(R.string.settings_download_location_not_set);
                                    handled = true;
                                } else if (SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION.equals(key)) {
                                    sharedPreferences.edit().remove(SharedPreferencesUtils.GIF_DOWNLOAD_LOCATION).apply();
                                    preference.setSummary(R.string.settings_download_location_not_set);
                                    handled = true;
                                } else if (SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION.equals(key)) {
                                    sharedPreferences.edit().remove(SharedPreferencesUtils.VIDEO_DOWNLOAD_LOCATION).apply();
                                    preference.setSummary(R.string.settings_download_location_not_set);
                                    handled = true;
                                } else if (SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION.equals(key)) {
                                    sharedPreferences.edit().remove(SharedPreferencesUtils.NSFW_DOWNLOAD_LOCATION).apply();
                                    preference.setSummary(R.string.settings_download_location_not_set);
                                    handled = true;
                                }
                                if (handled) {
                                    isLongPressing = true; // Set the flag when long press is handled
                                }
                            }
                        }
                    }
                }
            });

            recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                    // Let the GestureDetector analyze the event first.
                    boolean detected = gestureDetector.onTouchEvent(e);

                    // IMPORTANT: Reset flag on ACTION_DOWN to start fresh for each gesture.
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        isLongPressing = false;
                    }

                    // If gesture detector detected something OR if we are in a long press state,
                    // intercept the event (especially the ACTION_UP after the long press).
                    // Returning true consumes the event and prevents children (like the preference item)
                    // from receiving it.
                    return isLongPressing || detected; // Let's try just isLongPressing for interception
                }

                @Override
                public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                    // Let the GestureDetector handle the event.
                    gestureDetector.onTouchEvent(e);

                    // If a long press was handled (flag is set) and the event is ACTION_UP or ACTION_CANCEL,
                    // we simply reset the flag. Returning true from onInterceptTouchEvent should handle consumption.
                    if (isLongPressing && (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)) {
                        isLongPressing = false;
                    }
                }

                @Override
                public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                    // No-op
                }
            });
        }
    }
}