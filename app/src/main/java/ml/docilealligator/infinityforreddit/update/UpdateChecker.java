package ml.docilealligator.infinityforreddit.update;

import android.os.Build;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Looks up the latest GitHub release of this fork and compares it with the running version.
 */
public final class UpdateChecker {
    static final String REPO = "jayblah/continuumjr";
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";

    private UpdateChecker() {
        // Prevent instantiation
    }

    public static final class Release {
        public final String version;
        public final String notes;
        public final String apkUrl;

        Release(String version, String notes, String apkUrl) {
            this.version = version;
            this.notes = notes;
            this.apkUrl = apkUrl;
        }
    }

    /**
     * Blocking; call off the main thread.
     *
     * @return the latest release if it is newer than currentVersion and has an APK, else null.
     * @throws IOException if the lookup fails.
     */
    @Nullable
    public static Release fetchNewerRelease(String currentVersion) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "continuumjr-updater");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub returned " + connection.getResponseCode());
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) != -1) body.append(buffer, 0, read);
            }
            JSONObject json = new JSONObject(body.toString());
            String tag = json.getString("tag_name");
            if (compareVersions(tag, currentVersion) <= 0) return null;
            String apkUrl = pickApk(json.optJSONArray("assets"));
            if (apkUrl == null) return null;
            return new Release(tag.replaceFirst("^[vV]", ""), json.optString("body", ""), apkUrl);
        } catch (JSONException ex) {
            throw new IOException(ex);
        } finally {
            connection.disconnect();
        }
    }

    // Prefers the APK built for this device's first supported ABI, then any APK.
    @Nullable
    static String pickApk(@Nullable JSONArray assets) {
        if (assets == null) return null;
        for (String abi : Build.SUPPORTED_ABIS) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name");
                if (name.endsWith(".apk") && name.contains(abi)) {
                    return asset.optString("browser_download_url", null);
                }
            }
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && asset.optString("name").endsWith(".apk")) {
                return asset.optString("browser_download_url", null);
            }
        }
        return null;
    }

    /**
     * Compares the numeric parts of two versions, so "v8.3.3.9.1.2" is newer than
     * "8.3.3.9.1.1-debug".
     */
    static int compareVersions(String a, String b) {
        int[] left = numericParts(a);
        int[] right = numericParts(b);
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }

    private static int[] numericParts(String version) {
        // Anything after a dash is a suffix such as "-debug", not part of the version.
        String core = version.replaceFirst("^[vV]", "").split("-", 2)[0];
        String[] pieces = core.split("\\.");
        int[] parts = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            try {
                parts[i] = Integer.parseInt(pieces[i].trim());
            } catch (NumberFormatException ex) {
                parts[i] = 0;
            }
        }
        return parts;
    }
}
