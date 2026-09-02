package ml.docilealligator.infinityforreddit.comment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Pins {@link ParseComment#parseSingleComment}'s handling of {@code link_title}, the post title
 * that names a download of media embedded in a comment (issue #389).
 *
 * <p>Reddit sends it on comment <em>listings</em> — a user's comments, a subreddit's comments — but
 * omits it inside a thread, where the caller already holds the post. Both shapes have to parse:
 * the listing screens have no {@code Post} to fall back on, and the thread screens do.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ParseSingleCommentLinkTitleTest {

    @Test
    public void listingCommentKeepsItsLinkTitle() throws JSONException {
        assertEquals("Look at this",
                ParseComment.parseSingleComment(comment("\"Look at this\""), 0).getLinkTitle());
    }

    @Test
    public void threadCommentHasNoLinkTitle() throws JSONException {
        // A thread response omits the key entirely.
        assertNull(ParseComment.parseSingleComment(comment(null), 0).getLinkTitle());
    }

    @Test
    public void explicitNullLinkTitleParsesInsteadOfThrowing() throws JSONException {
        assertNull(ParseComment.parseSingleComment(comment("null"), 0).getLinkTitle());
    }

    @Test
    public void linkTitleSurvivesParcelling() throws JSONException {
        Comment original = ParseComment.parseSingleComment(comment("\"Look at this\""), 0);

        android.os.Parcel parcel = android.os.Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Comment restored = Comment.CREATOR.createFromParcel(parcel);
            // Guards the read/write ordering: a field appended to one half only would shift every
            // field after it rather than fail outright.
            assertEquals("Look at this", restored.getLinkTitle());
            assertEquals(original.getLinkId(), restored.getLinkId());
            assertEquals(original.getCommentRawText(), restored.getCommentRawText());
        } finally {
            parcel.recycle();
        }
    }

    private static JSONObject comment(String rawLinkTitleValue) throws JSONException {
        String linkTitle = rawLinkTitleValue == null ? "" : "\"link_title\":" + rawLinkTitleValue + ",";
        return new JSONObject("{"
                + "\"id\":\"def456\","
                + "\"name\":\"t1_def456\","
                + "\"author\":\"someone\","
                + "\"author_flair_text\":null,"
                + linkTitle
                + "\"link_id\":\"t3_abc123\","
                + "\"subreddit\":\"test\","
                + "\"parent_id\":\"t3_abc123\","
                + "\"is_submitter\":false,"
                + "\"distinguished\":null,"
                + "\"body\":\"a comment\","
                + "\"body_html\":\"&lt;p&gt;a comment&lt;/p&gt;\","
                + "\"permalink\":\"/r/test/comments/abc123/_/def456/\","
                + "\"score\":1,"
                + "\"likes\":true,"
                + "\"created_utc\":1754236800,"
                + "\"score_hidden\":false,"
                + "\"saved\":false,"
                + "\"send_replies\":true,"
                + "\"locked\":false,"
                + "\"can_mod_post\":false,"
                + "\"collapsed\":false,"
                + "\"replies\":\"\","
                + "\"edited\":false"
                + "}");
    }
}
