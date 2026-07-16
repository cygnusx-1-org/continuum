package ml.docilealligator.infinityforreddit.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.QRCodeScannerActivity;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontEditTextPreference;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.AppRestartHelper;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

public class APIKeysPreferenceFragment extends CustomFontPreferenceFragmentCompat {

    private static final String TAG = "APIKeysPrefFragment";
    private static final int CLIENT_ID_LENGTH = 22;
    private static final int GIPHY_API_KEY_LENGTH = 32;

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;

    private ActivityResultLauncher<Intent> qrCodeScannerLauncher;
    private CustomFontEditTextPreference clientIdPref;
    private EditText currentClientIdEditText;

    // Restart is deferred until the user leaves this screen, so a single restart covers
    // however many keys they edited. This callback fires on back button / back gesture / Up.
    private boolean mPendingRestart = false;
    private OnBackPressedCallback mRestartOnBackCallback;
    private TextView mRestartWarning;

    public APIKeysPreferenceFragment() {}

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRestartOnBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                AppRestartHelper.triggerAppRestart(requireContext());
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mRestartOnBackCallback);

        // Initialize the launcher for QR code scanning
        qrCodeScannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                        String qrCodeResult = result.getData().getStringExtra(QRCodeScannerActivity.EXTRA_QR_CODE_RESULT);
                        if (qrCodeResult != null && qrCodeResult.length() == CLIENT_ID_LENGTH) {
                            // Just set the text in the dialog EditText if it's available
                            if (currentClientIdEditText != null) {
                                currentClientIdEditText.setText(qrCodeResult);
                                Toast.makeText(requireContext(), R.string.qr_code_scanned_press_ok, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireContext(), R.string.qr_code_scanned_dialog_closed, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(requireContext(), "Invalid QR code. Client ID must be " + CLIENT_ID_LENGTH + " characters.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View preferenceView = super.onCreateView(inflater, container, savedInstanceState);

        // Wrap the preference list with a warning banner across the top, matching the
        // "pending changes" affordance used elsewhere (e.g. CustomizeMainPageTabsFragment).
        // The banner stays hidden until a restart-requiring key is edited.
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);

        mRestartWarning = new TextView(requireContext());
        // Warning sign glyph in front (enlarged 2x) so the message reads as an alert at a glance.
        String symbol = "⚠";
        SpannableString warningText = new SpannableString(symbol + "  " + getString(R.string.app_will_restart));
        warningText.setSpan(new RelativeSizeSpan(2f), 0, symbol.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        mRestartWarning.setText(warningText);
        mRestartWarning.setTypeface(mRestartWarning.getTypeface(), Typeface.BOLD);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                getResources().getDisplayMetrics());
        mRestartWarning.setPadding(padding, padding, padding, padding);
        if (mActivity != null && mActivity.customThemeWrapper != null) {
            mRestartWarning.setBackgroundColor(mActivity.customThemeWrapper.getCardViewBackgroundColor());
            // Accent is the theme's alert/attention color, so the text stands out.
            mRestartWarning.setTextColor(mActivity.customThemeWrapper.getColorAccent());
        }
        mRestartWarning.setVisibility(mPendingRestart ? View.VISIBLE : View.GONE);

        wrapper.addView(mRestartWarning, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        wrapper.addView(preferenceView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return wrapper;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        PreferenceManager preferenceManager = getPreferenceManager();
        // Use default shared preferences file for client ID
        preferenceManager.setSharedPreferencesName(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE);
        setPreferencesFromResource(R.xml.api_keys_preferences, rootKey);
        ((Infinity) requireActivity().getApplication()).getAppComponent().inject(this);

        setupEnableOverridesPreference();
        setupClientIdPreference();
        setupGiphyApiKeyPreference();
        setupUserAgentPreference();
        setupRedirectUriPreference();
    }

    // The summary explains the built-in defaults are RedReader's keys; color that word red so it
    // stands out. Done in code (and app:summary is intentionally not set in XML) because
    // Preference.setSummary compares with TextUtils.equals, which ignores spans — so a spanned
    // summary with the same text as an existing XML summary would be dropped as "unchanged".
    private void setupEnableOverridesPreference() {
        Preference enableOverridesPref = findPreference(SharedPreferencesUtils.ENABLE_API_KEY_OVERRIDES_PREF_KEY);
        if (enableOverridesPref == null) {
            Log.e(TAG, "Could not find Enable Overrides preference: " + SharedPreferencesUtils.ENABLE_API_KEY_OVERRIDES_PREF_KEY);
            return;
        }
        String summary = getString(R.string.settings_enable_api_key_overrides_summary);
        String highlight = "RedReader";
        int start = summary.lastIndexOf(highlight);
        if (start >= 0) {
            SpannableString styled = new SpannableString(summary);
            styled.setSpan(new ForegroundColorSpan(Color.RED), start, start + highlight.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            enableOverridesPref.setSummary(styled);
        }
    }

    private void setupClientIdPreference() {
        clientIdPref = findPreference(SharedPreferencesUtils.CLIENT_ID_PREF_KEY);
        if (clientIdPref != null) {
            android.graphics.Typeface robotoMonoMedium = ResourcesCompat.getFont(requireContext(), R.font.roboto_mono_medium);
            if (robotoMonoMedium != null) {
                clientIdPref.setSummaryTypeface(robotoMonoMedium);
            }

            // Set input type to visible password to prevent suggestions, but allow any string
            clientIdPref.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                editText.setSingleLine(true);
                if (robotoMonoMedium != null) {
                    editText.setTypeface(robotoMonoMedium);
                }

                // Store a reference to the current EditText
                currentClientIdEditText = editText;

                // Get current and default values
                String currentValue = clientIdPref.getText();
                String defaultValue = editText.getContext().getString(R.string.default_client_id);

                // Clear the text field only if the current value is the default value
                if (currentValue == null || currentValue.isEmpty() || currentValue.equals(defaultValue)) {
                    editText.setText("");
                }
                // Otherwise, the EditText will automatically show the non-default current value

                // Setup validation for the specific length, disallowing the default Client ID
                setupLengthValidation(editText, CLIENT_ID_LENGTH, true, defaultValue);

                // Add QR code scanning button to dialog
                View rootView = editText.getRootView();
                if (rootView != null) {
                    // Find the parent container for the EditText
                    View parent = (View) editText.getParent();
                    if (parent instanceof LinearLayout) {
                        LinearLayout layout = (LinearLayout) parent;

                        // Create a horizontal layout
                        LinearLayout horizontalLayout = new LinearLayout(getContext());
                        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);

                        // Setup layout params
                        LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

                        // Remove the EditText from its current parent
                        layout.removeView(editText);

                        // Create a scan button
                        ImageButton scanButton = new ImageButton(getContext());
                        scanButton.setImageResource(android.R.drawable.ic_menu_camera);
                        scanButton.setContentDescription(getString(R.string.content_description_scan_qr_code));

                        // Set onClick listener for the scan button
                        scanButton.setOnClickListener(v -> {
                            Intent intent = new Intent(getActivity(), QRCodeScannerActivity.class);
                            // Launch QR code scanner as a result activity
                            qrCodeScannerLauncher.launch(intent);
                        });

                        // Add EditText and button to horizontal layout
                        horizontalLayout.addView(editText, editTextParams);
                        horizontalLayout.addView(scanButton, buttonParams);

                        // Add the horizontal layout to the original parent
                        layout.addView(horizontalLayout, new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
                    }
                }
            });

            // Set a summary provider that hides the default value but shows custom ones
            clientIdPref.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String currentValue = preference.getText();
                String defaultValue = preference.getContext().getString(R.string.default_client_id);
                if (currentValue == null || currentValue.isEmpty() || currentValue.equals(defaultValue)) {
                    // Show generic message if value is null, empty, or the default
                    return preference.getContext().getString(R.string.tap_to_set_client_id);
                } else {
                    // Show the actual custom client ID
                    return currentValue;
                }
            });

            clientIdPref.setOnPreferenceChangeListener(((preference, newValue) -> {
                // Reset the current EditText reference since the dialog will be dismissed
                currentClientIdEditText = null;

                String value = stripWhitespace((String) newValue);

                // Final validation check (redundant due to button state, but safe)
                String defaultValue = preference.getContext().getString(R.string.default_client_id);
                if (value == null || value.length() != CLIENT_ID_LENGTH || value.equals(defaultValue)) {
                    return false; // Should not happen if button logic is correct
                }

                // Manually save the preference value *before* restarting
                // Get the specific SharedPreferences instance used by the PreferenceManager
                SharedPreferences prefs = preference.getContext().getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(SharedPreferencesUtils.CLIENT_ID_PREF_KEY, value);
                boolean success = editor.commit(); // Use commit() for synchronous saving

                if (success) {
                    Log.i(TAG, "Client ID manually saved successfully.");
                    // Push the value into the preference so getText() and the summary reflect it;
                    // we return false, so the framework won't store it for us.
                    clientIdPref.setText(value);
                    markRestartPendingAndWarn();
                } else {
                    Log.e(TAG, "Failed to save Client ID manually.");
                    Toast.makeText(getContext(), "Error saving Client ID.", Toast.LENGTH_SHORT).show();
                    // Don't restart if save failed
                }

                // Return false because we handled the saving manually (or attempted to)
                return false;
            }));

            // Add dialog click listener to reset the current EditText reference when dialog is canceled
            clientIdPref.setOnPreferenceClickListener(preference -> {
                // This will be called before the dialog appears
                // We'll set the EditText reference in setOnBindEditTextListener
                return false; // Return false to allow normal processing
            });
        } else {
            Log.e(TAG, "Could not find Client ID preference: " + SharedPreferencesUtils.CLIENT_ID_PREF_KEY);
        }
    }

    private void setupGiphyApiKeyPreference() {
        EditTextPreference giphyApiKeyPref = findPreference(SharedPreferencesUtils.GIPHY_API_KEY_PREF_KEY);
        if (giphyApiKeyPref != null) {
            // Set input type to visible password to prevent suggestions
            giphyApiKeyPref.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                editText.setSingleLine(true);
                // No need to clear the text field like for client ID, as there's no "default" shown

                // Setup validation for the specific length
                setupLengthValidation(editText, GIPHY_API_KEY_LENGTH, false);
            });

            // Set a summary provider that hides the default value but shows custom ones
            giphyApiKeyPref.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String currentValue = preference.getText();
                // Use the default Giphy key from resources as the "default" for comparison, even though we don't display it.
                String defaultValue = preference.getContext().getString(R.string.default_giphy_api_key); // Need to add this string resource

                if (currentValue == null || currentValue.isEmpty() || currentValue.equals(defaultValue)) {
                    // Show generic message if value is null, empty, or the (unseen) default
                    return preference.getContext().getString(R.string.tap_to_set_giphy_api_key);
                } else {
                    // Show the actual custom Giphy API Key
                    return currentValue;
                }
            });

            giphyApiKeyPref.setOnPreferenceChangeListener(((preference, newValue) -> {
                String value = (String) newValue;

                // Final validation check (redundant due to button state, but safe)
                if (value == null || value.length() != GIPHY_API_KEY_LENGTH) {
                    // Also allow empty string to revert to default
                    if (value != null && value.isEmpty()) {
                         // Handled below
                    } else {
                        Toast.makeText(getContext(), R.string.giphy_api_key_length_error, Toast.LENGTH_SHORT).show();
                        return false; // Prevent saving if invalid length (and not empty)
                    }
                }

                // Allow empty string to revert to default
                if (value == null) {
                    value = ""; // Treat null as empty
                }

                // Get the specific SharedPreferences instance used by the PreferenceManager
                SharedPreferences prefs = preference.getContext().getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(SharedPreferencesUtils.GIPHY_API_KEY_PREF_KEY, value);
                boolean success = editor.commit(); // Use commit() for synchronous saving

                if (success) {
                    Log.i(TAG, "Giphy API Key saved successfully.");
                    // Re-set the SummaryProvider to trigger a summary update
                    preference.setSummaryProvider(giphyApiKeyPref.getSummaryProvider());
                    Toast.makeText(getContext(), "Giphy API Key saved.", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Failed to save Giphy API Key.");
                    Toast.makeText(getContext(), "Error saving Giphy API Key.", Toast.LENGTH_SHORT).show();
                }

                // Return false because we handle the saving and summary update manually
                return true;
            }));

        } else {
            Log.e(TAG, "Could not find Giphy API Key preference: " + SharedPreferencesUtils.GIPHY_API_KEY_PREF_KEY);
        }
    }

    private void setupUserAgentPreference() {
        EditTextPreference userAgentPref = findPreference(SharedPreferencesUtils.USER_AGENT_PREF_KEY);
        if (userAgentPref != null) {
            userAgentPref.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                editText.setSingleLine(true);

                String currentValue = userAgentPref.getText();
                if (currentValue == null || currentValue.isEmpty() || currentValue.equals(APIUtils.DEFAULT_USER_AGENT)) {
                    editText.setText("");
                }
            });

            userAgentPref.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String currentValue = preference.getText();
                if (currentValue == null || currentValue.isEmpty() || currentValue.equals(APIUtils.DEFAULT_USER_AGENT)) {
                    return preference.getContext().getString(R.string.tap_to_set_user_agent);
                } else {
                    return currentValue;
                }
            });

            userAgentPref.setOnPreferenceChangeListener(((preference, newValue) -> {
                String value = stripLeadingTrailingWhitespace((String) newValue);
                if (value == null) {
                    value = "";
                }

                SharedPreferences prefs = preference.getContext().getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(SharedPreferencesUtils.USER_AGENT_PREF_KEY, value);
                boolean success = editor.commit();

                if (success) {
                    Log.i(TAG, "User Agent saved successfully.");
                    // Push the value into the preference so getText() and the summary reflect it;
                    // we return false, so the framework won't store it for us.
                    userAgentPref.setText(value);
                    markRestartPendingAndWarn();
                } else {
                    Log.e(TAG, "Failed to save User Agent.");
                    Toast.makeText(getContext(), "Error saving User Agent.", Toast.LENGTH_SHORT).show();
                }

                return false;
            }));
        } else {
            Log.e(TAG, "Could not find User Agent preference: " + SharedPreferencesUtils.USER_AGENT_PREF_KEY);
        }
    }

    private void setupRedirectUriPreference() {
        EditTextPreference redirectUriPref = findPreference(SharedPreferencesUtils.REDIRECT_URI_PREF_KEY);
        if (redirectUriPref != null) {
            redirectUriPref.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                editText.setSingleLine(true);

                String currentValue = redirectUriPref.getText();
                String defaultValue = editText.getContext().getString(R.string.default_redirect_uri);
                if (currentValue == null || currentValue.isEmpty() || currentValue.equals(defaultValue)) {
                    editText.setText("");
                }
            });

            redirectUriPref.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String currentValue = preference.getText();
                String defaultValue = preference.getContext().getString(R.string.default_redirect_uri);
                if (currentValue == null || currentValue.isEmpty() || currentValue.equals(defaultValue)) {
                    return preference.getContext().getString(R.string.tap_to_set_redirect_uri);
                } else {
                    return currentValue;
                }
            });

            redirectUriPref.setOnPreferenceChangeListener(((preference, newValue) -> {
                String value = stripWhitespace((String) newValue);
                if (value == null) {
                    value = "";
                }

                SharedPreferences prefs = preference.getContext().getSharedPreferences(SharedPreferencesUtils.DEFAULT_PREFERENCES_FILE, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(SharedPreferencesUtils.REDIRECT_URI_PREF_KEY, value);
                boolean success = editor.commit();

                if (success) {
                    Log.i(TAG, "Redirect URI saved successfully.");
                    // Push the value into the preference so getText() and the summary reflect it;
                    // we return false, so the framework won't store it for us.
                    redirectUriPref.setText(value);
                    markRestartPendingAndWarn();
                } else {
                    Log.e(TAG, "Failed to save Redirect URI.");
                    Toast.makeText(getContext(), "Error saving Redirect URI.", Toast.LENGTH_SHORT).show();
                }

                return false;
            }));
        } else {
            Log.e(TAG, "Could not find Redirect URI preference: " + SharedPreferencesUtils.REDIRECT_URI_PREF_KEY);
        }
    }

    // Records that a restart-requiring key changed, arms the back callback so leaving the
    // screen restarts the app, and reveals the top banner so the deferred restart isn't a surprise.
    private void markRestartPendingAndWarn() {
        mPendingRestart = true;
        if (mRestartOnBackCallback != null) {
            mRestartOnBackCallback.setEnabled(true);
        }
        if (mRestartWarning != null) {
            mRestartWarning.setVisibility(View.VISIBLE);
        }
    }

    // Strips all whitespace from the string (leading, trailing, and internal).
    private static String stripWhitespace(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // Strips only leading and trailing whitespace; preserves internal spaces (e.g. in user agents).
    private static String stripLeadingTrailingWhitespace(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    // Reusable helper method for setting up length validation on an EditTextPreference dialog
    private void setupLengthValidation(android.widget.EditText editText, final int requiredLength, final boolean stripWhitespaceBeforeCheck) {
        setupLengthValidation(editText, requiredLength, stripWhitespaceBeforeCheck, null);
    }

    /**
     * Enables/disables the dialog's OK button based on the entered text.
     *
     * @param disallowedValue if non-null, the OK button stays disabled while the (whitespace-stripped)
     *                        entered value equals this value. Used to prevent saving the default Client ID.
     */
    private void setupLengthValidation(android.widget.EditText editText, final int requiredLength, final boolean stripWhitespaceBeforeCheck, final String disallowedValue) {
        // Add TextWatcher to enable/disable OK button based on length
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                updatePositiveButtonState(editText, requiredLength, stripWhitespaceBeforeCheck, disallowedValue, "afterTextChanged");
            }
        });

        // Ensure the button state is correct initially based on the current value
        editText.post(() -> updatePositiveButtonState(editText, requiredLength, stripWhitespaceBeforeCheck, disallowedValue, "post"));
    }

    private void updatePositiveButtonState(android.widget.EditText editText, final int requiredLength,
                                           final boolean stripWhitespaceBeforeCheck, final String disallowedValue,
                                           final String caller) {
        View rootView = editText.getRootView();
        if (rootView == null) {
            Log.w(TAG, "Could not get root view from EditText to find positive button (" + caller + ").");
            return;
        }
        Button positiveButton = rootView.findViewById(android.R.id.button1);
        if (positiveButton == null) {
            Log.w(TAG, "Could not find positive button (android.R.id.button1) in dialog root view (" + caller + ").");
            return;
        }
        String effectiveValue = stripWhitespaceBeforeCheck ? stripWhitespace(editText.getText().toString()) : editText.getText().toString();
        boolean lengthOk = effectiveValue.length() == requiredLength || (requiredLength == GIPHY_API_KEY_LENGTH && effectiveValue.isEmpty()); // Allow empty for Giphy
        boolean isDisallowed = disallowedValue != null && disallowedValue.equals(effectiveValue);
        boolean isEnabled = lengthOk && !isDisallowed;
        positiveButton.setEnabled(isEnabled);
        positiveButton.setAlpha(isEnabled ? 1.0f : 0.5f); // Adjust alpha for visual feedback
    }

    // Inject dependencies
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ((Infinity) requireActivity().getApplication()).getAppComponent().inject(this);
    }
}
