package ml.docilealligator.infinityforreddit.customtheme;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CustomThemeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CustomTheme customTheme);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CustomTheme> customThemes);

    @Query("SELECT * FROM custom_themes")
    LiveData<List<CustomTheme>> getAllCustomThemes();

    @Query("SELECT * FROM custom_themes")
    List<CustomTheme> getAllCustomThemesList();

    @Nullable
    @Query("SELECT * FROM custom_themes WHERE is_light_theme = 1 LIMIT 1")
    CustomTheme getLightCustomTheme();

    @Nullable
    @Query("SELECT * FROM custom_themes WHERE is_dark_theme = 1 LIMIT 1")
    CustomTheme getDarkCustomTheme();

    @Nullable
    @Query("SELECT * FROM custom_themes WHERE is_amoled_theme = 1 LIMIT 1")
    CustomTheme getAmoledCustomTheme();

    @Query("SELECT * FROM custom_themes WHERE is_light_theme = 1 LIMIT 1")
    LiveData<CustomTheme> getLightCustomThemeLiveData();

    @Query("SELECT * FROM custom_themes WHERE is_dark_theme = 1 LIMIT 1")
    LiveData<CustomTheme> getDarkCustomThemeLiveData();

    @Query("SELECT * FROM custom_themes WHERE is_amoled_theme = 1 LIMIT 1")
    LiveData<CustomTheme> getAmoledCustomThemeLiveData();

    @Nullable
    @Query("SELECT * FROM custom_themes WHERE name = :name COLLATE NOCASE LIMIT 1")
    CustomTheme getCustomTheme(String name);

    // Every row the name reaches case-insensitively, for callers that have to tell "this theme under a
    // different capitalisation" from "a second theme whose name differs from it in case alone".
    @Query("SELECT * FROM custom_themes WHERE name = :name COLLATE NOCASE")
    List<CustomTheme> getCustomThemesWithName(String name);

    @Query("UPDATE custom_themes SET is_light_theme = 0 WHERE is_light_theme = 1")
    void unsetLightTheme();

    @Query("UPDATE custom_themes SET is_dark_theme = 0 WHERE is_dark_theme = 1")
    void unsetDarkTheme();

    @Query("UPDATE custom_themes SET is_amoled_theme = 0 WHERE is_amoled_theme = 1")
    void unsetAmoledTheme();

    // Exact match: names differing only in case are separate rows, and a delete must take only the
    // one it was given. Callers that looked their row up case-insensitively pass back its stored name.
    @Query("DELETE FROM custom_themes WHERE name = :name")
    void deleteCustomTheme(String name);

    @Query("UPDATE custom_themes SET name = :newName WHERE name = :oldName")
    void updateName(String oldName, String newName);

    @Query("DELETE FROM custom_themes")
    void deleteAllCustomThemes();
}
