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
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases";

    private UpdateChecker() {
        // Prevent instantiation
    }

    public static final class Release {
        public final String version;
        public final String notes;
        public final String apkUrl;
        public final long apkSize;

        Release(String version, String notes, String apkUrl, long apkSize) {
            this.version = version;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
        }
    }

    /**
     * Blocking; call off the main thread.
     *
     * @param includePrereleases whether a pre-release (a tag with a suffix, such as -beta.1) can
     *                           be offered. Otherwise only the latest full release is considered.
     * @return the newest release if it is newer than currentVersion and has an APK, else null.
     * @throws IOException if the lookup fails.
     */
    @Nullable
    public static Release fetchNewerRelease(String currentVersion, boolean includePrereleases) throws IOException {
        try {
            Release best = null;
            if (includePrereleases) {
                JSONArray releases = new JSONArray(get(API + "?per_page=15"));
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject json = releases.getJSONObject(i);
                    if (json.optBoolean("draft")) continue;
                    Release candidate = parseRelease(json);
                    if (candidate != null && (best == null || compareVersions(candidate.version, best.version) > 0)) {
                        best = candidate;
                    }
                }
            } else {
                best = parseRelease(new JSONObject(get(API + "/latest")));
            }
            if (best == null || compareVersions(best.version, currentVersion) <= 0) return null;
            return best;
        } catch (JSONException ex) {
            throw new IOException(ex);
        }
    }

    @Nullable
    private static Release parseRelease(JSONObject json) throws JSONException {
        String tag = json.getString("tag_name");
        JSONObject apk = pickApk(json.optJSONArray("assets"));
        if (apk == null) return null;
        return new Release(tag.replaceFirst("^[vV]", ""), json.optString("body", ""),
                apk.optString("browser_download_url"), apk.optLong("size", -1));
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
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
            return body.toString();
        } finally {
            connection.disconnect();
        }
    }

    // Prefers the APK built for this device's first supported ABI, then any APK.
    @Nullable
    static JSONObject pickApk(@Nullable JSONArray assets) {
        if (assets == null) return null;
        for (String abi : Build.SUPPORTED_ABIS) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name");
                if (name.endsWith(".apk") && name.contains(abi)) return asset;
            }
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && asset.optString("name").endsWith(".apk")) return asset;
        }
        return null;
    }

    /**
     * Compares two versions. The numbers decide first, so "v8.3.3.9.1.2" is newer than
     * "8.3.3.9.1.1". With equal numbers a pre-release suffix ranks below the plain version
     * ("8.3.3.9.1.8-beta.1" is older than "8.3.3.9.1.8"), and two suffixes compare by the numbers
     * in them.
     */
    static int compareVersions(String a, String b) {
        String[] left = splitSuffix(a);
        String[] right = splitSuffix(b);
        int[] leftParts = numericParts(left[0]);
        int[] rightParts = numericParts(right[0]);
        for (int i = 0; i < Math.max(leftParts.length, rightParts.length); i++) {
            int l = i < leftParts.length ? leftParts[i] : 0;
            int r = i < rightParts.length ? rightParts[i] : 0;
            if (l != r) return Integer.compare(l, r);
        }
        if (left[1] == null && right[1] == null) return 0;
        if (left[1] == null) return 1;
        if (right[1] == null) return -1;
        int[] leftSuffix = numericParts(left[1]);
        int[] rightSuffix = numericParts(right[1]);
        for (int i = 0; i < Math.max(leftSuffix.length, rightSuffix.length); i++) {
            int l = i < leftSuffix.length ? leftSuffix[i] : 0;
            int r = i < rightSuffix.length ? rightSuffix[i] : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }

    // {version numbers, suffix or null}, with a leading v removed.
    private static String[] splitSuffix(String version) {
        String[] parts = version.replaceFirst("^[vV]", "").split("-", 2);
        // The build type's own "-debug" suffix is not a pre-release marker.
        boolean hasSuffix = parts.length > 1 && !parts[1].equalsIgnoreCase("debug");
        return new String[]{parts[0], hasSuffix ? parts[1] : null};
    }

    // Every run of digits, so "beta.12" gives {12}.
    private static int[] numericParts(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(text);
        java.util.List<Integer> numbers = new java.util.ArrayList<>();
        while (matcher.find()) {
            try {
                numbers.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException ex) {
                numbers.add(0);
            }
        }
        int[] parts = new int[numbers.size()];
        for (int i = 0; i < parts.length; i++) parts[i] = numbers.get(i);
        return parts;
    }
}
