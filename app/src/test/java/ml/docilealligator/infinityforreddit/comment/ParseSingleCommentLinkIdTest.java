package ml.docilealligator.infinityforreddit.comment;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Pins {@link ParseComment#parseSingleComment}'s handling of {@code link_id}. Reddit intermittently
 * returns an empty {@code link_id} in the response to a freshly sent comment; the old fixed
 * {@code substring(3)} threw StringIndexOutOfBoundsException on that value, which killed the app
 * from a background executor after the comment had already been posted (issue #352). Robolectric
 * supplies the real {@code org.json} and {@code android.text.Html} that the parser needs.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ParseSingleCommentLinkIdTest {

    @Test
    public void fullnameLinkIdIsStripped() throws JSONException {
        assertEquals("abc123", ParseComment.parseSingleComment(sentComment("\"t3_abc123\""), 0).getLinkId());
    }

    @Test
    public void emptyLinkIdParsesInsteadOfThrowing() throws JSONException {
        assertEquals("", ParseComment.parseSingleComment(sentComment("\"\""), 0).getLinkId());
    }

    @Test
    public void nullLinkIdParsesInsteadOfThrowing() throws JSONException {
        assertEquals("", ParseComment.parseSingleComment(sentComment("null"), 0).getLinkId());
    }

    @Test
    public void missingLinkIdParsesInsteadOfThrowing() throws JSONException {
        assertEquals("", ParseComment.parseSingleComment(sentComment(null), 0).getLinkId());
    }

    @Test
    public void unprefixedLinkIdIsPassedThrough() throws JSONException {
        assertEquals("abc123", ParseComment.parseSingleComment(sentComment("\"abc123\""), 0).getLinkId());
    }

    /**
     * The comment object Reddit returns from {@code /api/comment}, with {@code link_id} set to the
     * given raw JSON value, or omitted entirely when it is null.
     */
    private static JSONObject sentComment(String rawLinkIdValue) throws JSONException {
        String linkId = rawLinkIdValue == null ? "" : "\"link_id\":" + rawLinkIdValue + ",";
        return new JSONObject("{"
                + "\"id\":\"abc123\","
                + "\"name\":\"t1_abc123\","
                + "\"author\":\"someone\","
                + "\"author_flair_text\":null,"
                + linkId
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
