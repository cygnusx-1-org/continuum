package ml.docilealligator.infinityforreddit.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import ml.docilealligator.infinityforreddit.R;

/**
 * Reverse image search of an image URL on Google (Lens) and Yandex.
 */
public final class ReverseImageSearch {
    private ReverseImageSearch() {
        // Prevent instantiation
    }

    public static String googleUrl(String imageUrl) {
        return "https://lens.google.com/uploadbyurl?url=" + encode(imageUrl);
    }

    public static String yandexUrl(String imageUrl) {
        return "https://yandex.com/images/search?rpt=imageview&url=" + encode(imageUrl);
    }

    /**
     * Asks which engine to search with, anchored to the button that was tapped.
     */
    public static void showChooser(Context context, View anchor, String imageUrl) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, 0, 0, R.string.reverse_image_search_google);
        menu.getMenu().add(0, 1, 1, R.string.reverse_image_search_yandex);
        menu.getMenu().add(0, 2, 2, R.string.reverse_image_search_both);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    open(context, imageUrl, googleUrl(imageUrl));
                    break;
                case 1:
                    open(context, imageUrl, yandexUrl(imageUrl));
                    break;
                default:
                    open(context, imageUrl, googleUrl(imageUrl), yandexUrl(imageUrl));
                    break;
            }
            return true;
        });
        menu.show();
    }

    // Opens each search in the browser, in order.
    private static void open(Context context, String imageUrl, String... searchUrls) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            Toast.makeText(context, R.string.reverse_image_search_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            for (String url : searchUrls) {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(context, R.string.reverse_image_search_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
