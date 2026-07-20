package ml.docilealligator.infinityforreddit.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.adapters.CustomizeThemeRecyclerViewAdapter;
import ml.docilealligator.infinityforreddit.apis.ServerAPI;
import ml.docilealligator.infinityforreddit.asynctasks.GetCustomTheme;
import ml.docilealligator.infinityforreddit.asynctasks.InsertCustomTheme;
import ml.docilealligator.infinityforreddit.customtheme.CustomTheme;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeSettingsItem;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customtheme.OnlineCustomThemeMetadata;
import ml.docilealligator.infinityforreddit.databinding.ActivityCustomizeThemeBinding;
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import org.greenrobot.eventbus.EventBus;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class CustomizeThemeActivity extends BaseActivity {

    public static final String EXTRA_THEME_TYPE = "ETT";
    public static final int EXTRA_LIGHT_THEME = CustomThemeSharedPreferencesUtils.LIGHT;
    public static final int EXTRA_DARK_THEME = CustomThemeSharedPreferencesUtils.DARK;
    public static final int EXTRA_AMOLED_THEME = CustomThemeSharedPreferencesUtils.AMOLED;
    public static final String EXTRA_THEME_NAME = "ETN";
    public static final String EXTRA_ONLINE_CUSTOM_THEME_METADATA = "EOCTM";
    public static final String EXTRA_INDEX_IN_THEME_LIST = "EIITL";
    public static final String EXTRA_IS_PREDEFIINED_THEME = "EIPT";
    public static final String EXTRA_CREATE_THEME = "ECT";
    public static final String RETURN_EXTRA_THEME_NAME = "RETN";
    public static final String RETURN_EXTRA_PRIMARY_COLOR = "REPC";
    public static final String RETURN_EXTRA_INDEX_IN_THEME_LIST = "REIITL";
    private static final String CUSTOM_THEME_SETTINGS_ITEMS_STATE = "CTSIS";
    private static final String THEME_NAME_STATE = "TNS";

    @Inject
    @Named("online_custom_themes")
    Retrofit onlineCustomThemesRetrofit;
    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    @Named("light_theme")
    SharedPreferences lightThemeSharedPreferences;
    @Inject
    @Named("dark_theme")
    SharedPreferences darkThemeSharedPreferences;
    @Inject
    @Named("amoled_theme")
    SharedPreferences amoledThemeSharedPreferences;
    @Inject
    RedditDataRoomDatabase redditDataRoomDatabase;
    @Inject
    CustomThemeWrapper customThemeWrapper;
    @Inject
    Executor mExecutor;

    @SuppressWarnings("NullAway.Init") // Set from the intent or saved state in onCreate, or by the async theme load.
    private String themeName;
    @Nullable
    private OnlineCustomThemeMetadata onlineCustomThemeMetadata;
    private boolean isPredefinedTheme;
    @Nullable
    private ArrayList<CustomThemeSettingsItem> customThemeSettingsItems;
    @Nullable
    private CustomizeThemeRecyclerViewAdapter adapter;
    private ActivityCustomizeThemeBinding binding;
    @Nullable
    private Call<String> themeDownloadCall;
    @Nullable
    private AlertDialog activeDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicableBelowAndroid16();

        super.onCreate(savedInstanceState);
        binding = ActivityCustomizeThemeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyCustomTheme();

        if (isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutCustomizeThemeActivity);
            }

            ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), new OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
                    Insets allInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface());

                    setMargins(binding.toolbarCustomizeThemeActivity,
                            allInsets.left,
                            allInsets.top,
                            allInsets.right,
                            BaseActivity.IGNORE_MARGIN);

                    binding.recyclerViewCustomizeThemeActivity.setPadding(
                            allInsets.left,
                            0,
                            allInsets.right,
                            allInsets.bottom
                    );

                    return WindowInsetsCompat.CONSUMED;
                }
            });
        }

        setSupportActionBar(binding.toolbarCustomizeThemeActivity);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        if (getIntent().getBooleanExtra(EXTRA_CREATE_THEME, false)) {
            setTitle(R.string.customize_theme_activity_create_theme_label);
        }

        if (savedInstanceState != null) {
            // onSaveInstanceState writes these two together, so restore them together or not at all;
            // a partial restore would leave the theme name without the settings items it belongs to.
            ArrayList<CustomThemeSettingsItem> savedSettingsItems =
                    savedInstanceState.getParcelableArrayList(CUSTOM_THEME_SETTINGS_ITEMS_STATE);
            String savedThemeName = savedInstanceState.getString(THEME_NAME_STATE);
            if (savedSettingsItems != null && savedThemeName != null) {
                customThemeSettingsItems = savedSettingsItems;
                themeName = savedThemeName;
            }
        }

        binding.progressBarCustomizeThemeActivity.setVisibility(View.GONE);

        int androidVersion = Build.VERSION.SDK_INT;

        // Both are pure functions of the intent, which survives recreation, so read them on every pass:
        // a restored instance needs them as much as a fresh one, and reading them only on the
        // not-yet-loaded path left a rotated online theme with no metadata, which silently downgraded
        // "modify this theme" into "upload a duplicate".
        isPredefinedTheme = getIntent().getBooleanExtra(EXTRA_IS_PREDEFIINED_THEME, false);
        OnlineCustomThemeMetadata metadata = getIntent().getParcelableExtra(EXTRA_ONLINE_CUSTOM_THEME_METADATA);
        onlineCustomThemeMetadata = metadata;

        if (customThemeSettingsItems == null) {
            if (getIntent().hasExtra(EXTRA_THEME_TYPE)) {
                int themeType = getIntent().getIntExtra(EXTRA_THEME_TYPE, EXTRA_LIGHT_THEME);
                GetCustomTheme.getCustomTheme(mExecutor, new Handler(), redditDataRoomDatabase, themeType, customTheme -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    ArrayList<CustomThemeSettingsItem> settingsItems;
                    if (customTheme == null) {
                        isPredefinedTheme = true;
                        switch (themeType) {
                            case EXTRA_DARK_THEME:
                                settingsItems = CustomThemeSettingsItem.convertCustomThemeToSettingsItem(
                                        CustomizeThemeActivity.this,
                                        CustomThemeWrapper.getIndigoDark(CustomizeThemeActivity.this),
                                        androidVersion);
                                themeName = getString(R.string.theme_name_indigo_dark);
                                break;
                            case EXTRA_AMOLED_THEME:
                                settingsItems = CustomThemeSettingsItem.convertCustomThemeToSettingsItem(
                                        CustomizeThemeActivity.this,
                                        CustomThemeWrapper.getIndigoAmoled(CustomizeThemeActivity.this),
                                        androidVersion);
                                themeName = getString(R.string.theme_name_indigo_amoled);
                                break;
                            default:
                                settingsItems = CustomThemeSettingsItem.convertCustomThemeToSettingsItem(
                                        CustomizeThemeActivity.this,
                                        CustomThemeWrapper.getIndigo(CustomizeThemeActivity.this),
                                        androidVersion);
                                themeName = getString(R.string.theme_name_indigo);
                        }
                    } else {
                        settingsItems = CustomThemeSettingsItem.convertCustomThemeToSettingsItem(
                                CustomizeThemeActivity.this, customTheme, androidVersion);
                        themeName = customTheme.name;
                    }
                    customThemeSettingsItems = settingsItems;

                    CustomizeThemeRecyclerViewAdapter themeAdapter =
                            new CustomizeThemeRecyclerViewAdapter(this, customThemeWrapper, themeName);
                    adapter = themeAdapter;
                    binding.recyclerViewCustomizeThemeActivity.setAdapter(themeAdapter);
                    themeAdapter.setCustomThemeSettingsItem(settingsItems);
                });
            } else {
                // Every launch site that omits EXTRA_THEME_TYPE sets EXTRA_THEME_NAME.
                themeName = Objects.requireNonNull(getIntent().getStringExtra(EXTRA_THEME_NAME));

                CustomizeThemeRecyclerViewAdapter themeAdapter =
                        new CustomizeThemeRecyclerViewAdapter(this, customThemeWrapper, themeName);
                adapter = themeAdapter;
                binding.recyclerViewCustomizeThemeActivity.setAdapter(themeAdapter);
                if (isPredefinedTheme) {
                    customThemeSettingsItems = CustomThemeSettingsItem.convertCustomThemeToSettingsItem(
                            CustomizeThemeActivity.this,
                            CustomThemeWrapper.getPredefinedCustomTheme(this, themeName),
                            androidVersion);

                    themeAdapter.setCustomThemeSettingsItem(customThemeSettingsItems);
                } else {
                    if (metadata != null) {
                        binding.progressBarCustomizeThemeActivity.setVisibility(View.VISIBLE);
                        Call<String> downloadCall = onlineCustomThemesRetrofit.create(ServerAPI.class)
                                .getCustomTheme(metadata.name, metadata.username);
                        themeDownloadCall = downloadCall;
                        downloadCall.enqueue(new Callback<>() {
                                    @Override
                                    public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                                        if (isFinishing() || isDestroyed()) {
                                            return;
                                        }
                                        String responseBody = response.body();
                                        if (response.isSuccessful() && responseBody != null) {
                                            customThemeSettingsItems = CustomThemeSettingsItem.convertCustomThemeToSettingsItem(
                                                    CustomizeThemeActivity.this,
                                                    CustomTheme.fromJson(responseBody),
                                                    androidVersion);

                                            themeAdapter.setCustomThemeSettingsItem(customThemeSettingsItems);

                                            binding.progressBarCustomizeThemeActivity.setVisibility(View.GONE);
                                        } else {
                                            Toast.makeText(CustomizeThemeActivity.this, response.message(), Toast.LENGTH_SHORT).show();
                                            finish();
                                        }
                                    }

                                    @Override
                                    public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                                        if (isFinishing() || isDestroyed()) {
                                            return;
                                        }
                                        Toast.makeText(CustomizeThemeActivity.this, R.string.cannot_download_theme_data, Toast.LENGTH_SHORT).show();
                                        finish();
                                    }
                                });
                    } else {
                        GetCustomTheme.getCustomTheme(mExecutor, new Handler(), redditDataRoomDatabase,
                                themeName, customTheme -> {
                                    if (isFinishing() || isDestroyed()) {
                                        return;
                                    }
                                    customThemeSettingsItems = CustomThemeSettingsItem.convertCustomThemeToSettingsItem(
                                            CustomizeThemeActivity.this, customTheme, androidVersion);

                                    themeAdapter.setCustomThemeSettingsItem(customThemeSettingsItems);
                                });
                    }
                }
            }
        } else {
            CustomizeThemeRecyclerViewAdapter themeAdapter =
                    new CustomizeThemeRecyclerViewAdapter(this, customThemeWrapper, themeName);
            adapter = themeAdapter;
            binding.recyclerViewCustomizeThemeActivity.setAdapter(themeAdapter);
            themeAdapter.setCustomThemeSettingsItem(customThemeSettingsItems);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                activeDialog = new MaterialAlertDialogBuilder(CustomizeThemeActivity.this, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.discard)
                        .setPositiveButton(R.string.discard_dialog_button, (dialogInterface, i)
                                -> {
                            setEnabled(false);
                            triggerBackPress();
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.customize_theme_activity, menu);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            triggerBackPress();
            return true;
        } else if (itemId == R.id.action_preview_customize_theme_activity) {
            ArrayList<CustomThemeSettingsItem> settingsItems = customThemeSettingsItems;
            if (settingsItems == null) {
                // The online download has not landed yet; there is nothing to preview.
                Snackbar.make(binding.coordinatorCustomizeThemeActivity, R.string.theme_not_loaded_yet, Snackbar.LENGTH_SHORT).show();
                return true;
            }
            Intent intent = new Intent(this, CustomThemePreviewActivity.class);
            intent.putParcelableArrayListExtra(CustomThemePreviewActivity.EXTRA_CUSTOM_THEME_SETTINGS_ITEMS, settingsItems);
            startActivity(intent);

            return true;
        } else if (itemId == R.id.action_save_customize_theme_activity) {
            CustomizeThemeRecyclerViewAdapter themeAdapter = adapter;
            ArrayList<CustomThemeSettingsItem> settingsItems = customThemeSettingsItems;
            if (themeAdapter == null || settingsItems == null) {
                // Either the database read or the online download is still in flight, so there is
                // nothing to save yet. Both halves report the same way; a bare `adapter != null`
                // check used to make the database case a silent no-op.
                Snackbar.make(binding.coordinatorCustomizeThemeActivity, R.string.theme_not_loaded_yet, Snackbar.LENGTH_SHORT).show();
                return true;
            }
            themeName = themeAdapter.getThemeName();
            if (themeName.equals("")) {
                Snackbar.make(binding.coordinatorCustomizeThemeActivity, R.string.no_theme_name, Snackbar.LENGTH_SHORT).show();
                return true;
            }
            CustomTheme customTheme = CustomTheme.convertSettingsItemsToCustomTheme(settingsItems, themeName);
            if (onlineCustomThemeMetadata != null && onlineCustomThemeMetadata.username.equals(accountName)) {
                // This custom theme is uploaded by the current user
                final int[] option = {0};
                activeDialog = new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.save_theme_options_title)
                        //.setMessage(R.string.save_theme_options_message)
                        .setSingleChoiceItems(R.array.save_theme_options, 0, (dialog, which) -> option[0] = which)
                        .setPositiveButton(R.string.ok, (dialogInterface, which) -> {
                            switch (option[0]) {
                                case 0:
                                    saveThemeLocally(customTheme);
                                    break;
                                case 1:
                                    saveThemeOnline(customTheme, false);
                                    break;
                                case 2:
                                    saveThemeLocally(customTheme);
                                    saveThemeOnline(customTheme, false);
                                    break;
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else {
                /*// This custom theme is from the server but not uploaded by the current user, or it is local
                final int[] option = {0};
                new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                        .setTitle(R.string.save_theme_options_title)
                        //.setMessage(R.string.save_theme_options_message)
                        .setSingleChoiceItems(R.array.save_theme_options_anonymous_included, 0, (dialog, which) -> option[0] = which)
                        .setPositiveButton(R.string.ok, (dialogInterface, which) -> {
                            switch (option[0]) {
                                case 0:
                                    saveThemeLocally(customTheme);
                                    break;
                                case 1:
                                    saveThemeOnline(customTheme, false);
                                    break;
                                case 2:
                                    saveThemeOnline(customTheme, true);
                                    break;
                                case 3:
                                    saveThemeLocally(customTheme);
                                    saveThemeOnline(customTheme, false);
                                    break;
                                case 4:
                                    saveThemeLocally(customTheme);
                                    saveThemeOnline(customTheme, true);
                                    break;
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();*/
                saveThemeLocally(customTheme);
            }

            return true;
        }

        return false;
    }

    private void saveThemeLocally(CustomTheme customTheme) {
        InsertCustomTheme.insertCustomTheme(mExecutor, new Handler(), redditDataRoomDatabase, lightThemeSharedPreferences,
                darkThemeSharedPreferences, amoledThemeSharedPreferences, customTheme,
                false, () -> {
                    // Posted above the guard: the theme is already committed, so the rest of the app
                    // has to be told to re-render regardless of what became of this screen.
                    EventBus.getDefault().post(new RecreateActivityEvent());
                    // isFinishing(), deliberately not isDestroyed(): a configuration change destroys
                    // this instance but keeps the activity token, so finish() still closes the
                    // relaunched screen and must run. After a back-press it is already redundant.
                    if (isFinishing()) {
                        return;
                    }
                    Toast.makeText(CustomizeThemeActivity.this, R.string.theme_saved_locally, Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void saveThemeOnline(CustomTheme customTheme, boolean anonymous) {
        Call<String> request;
        OnlineCustomThemeMetadata metadata = onlineCustomThemeMetadata;
        // TODO server access token
        if (metadata != null) {
            request = onlineCustomThemesRetrofit.create(ServerAPI.class).modifyTheme(
                    APIUtils.getServerHeader("", accountName, anonymous),
                    metadata.id,
                    customTheme.name,
                    customTheme.getJSONModel()
            );
        } else {
            request = onlineCustomThemesRetrofit.create(ServerAPI.class).createTheme(
                    APIUtils.getServerHeader("", accountName, anonymous),
                    customTheme.name,
                    customTheme.getJSONModel()
            );
        }

        request.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                // isFinishing() rather than the isDestroyed() used by the download callbacks above:
                // a relaunched instance does not repeat this upload, so on a configuration change
                // setResult/finish() must still run or the theme sits on the server while the listing
                // keeps showing the old name and colour. Only a back-press means nobody is waiting.
                if (isFinishing()) {
                    return;
                }
                if (response.isSuccessful()) {
                    Toast.makeText(CustomizeThemeActivity.this, R.string.theme_saved_online, Toast.LENGTH_SHORT).show();
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra(RETURN_EXTRA_INDEX_IN_THEME_LIST, getIntent().getIntExtra(EXTRA_INDEX_IN_THEME_LIST, -1));
                    returnIntent.putExtra(RETURN_EXTRA_THEME_NAME, customTheme.name);
                    returnIntent.putExtra(RETURN_EXTRA_PRIMARY_COLOR, '#' + Integer.toHexString(customTheme.colorPrimary));
                    setResult(RESULT_OK, returnIntent);

                    finish();
                } else {
                    Toast.makeText(CustomizeThemeActivity.this, R.string.upload_theme_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable throwable) {
                // isFinishing() for the same reason as onResponse: nothing retries this upload, so
                // suppressing the message on a mere configuration change would make the failure silent.
                if (isFinishing()) {
                    return;
                }
                Toast.makeText(CustomizeThemeActivity.this, R.string.upload_theme_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        // The relaunched instance restarts this download itself, so let go of the in-flight one
        // rather than paying for a response nobody will read. Cancelling surfaces as onFailure,
        // which that callback's isDestroyed() guard absorbs. Deliberately NOT done for
        // saveThemeOnline's request: nothing retries an upload, so cancelling it would throw away
        // the theme the user just saved.
        Call<String> downloadCall = themeDownloadCall;
        if (downloadCall != null) {
            downloadCall.cancel();
            themeDownloadCall = null;
        }
        // These dialogs are raw AlertDialogs rather than DialogFragments, so one left showing
        // across a rotation leaks its window and this activity with it.
        AlertDialog dialog = activeDialog;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        activeDialog = null;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        CustomizeThemeRecyclerViewAdapter themeAdapter = adapter;
        ArrayList<CustomThemeSettingsItem> settingsItems = customThemeSettingsItems;
        if (themeAdapter != null && settingsItems != null) {
            outState.putParcelableArrayList(CUSTOM_THEME_SETTINGS_ITEMS_STATE, settingsItems);
            outState.putString(THEME_NAME_STATE, themeAdapter.getThemeName());
        }
    }

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return sharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mCurrentAccountSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return customThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutCustomizeThemeActivity,
                binding.collapsingToolbarLayoutCustomizeThemeActivity, binding.toolbarCustomizeThemeActivity);
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutCustomizeThemeActivity);
        binding.coordinatorCustomizeThemeActivity.setBackgroundColor(customThemeWrapper.getBackgroundColor());
        binding.progressBarCustomizeThemeActivity.setIndicatorColor(customThemeWrapper.getColorAccent());
    }
}
