package ml.docilealligator.infinityforreddit.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Pins {@link APIUtils#parseApiErrorMessage}, the shared helper that lets {@code EditPostActivity}
 * and {@code EditCommentActivity} detect a Reddit {@code api_type=json} failure returned inside an
 * otherwise-HTTP-200 body. Robolectric supplies the real {@code org.json} — the stub would return
 * defaults and make every case look error-free, which is precisely the failure the helper exists to
 * catch. The envelope shapes below are the ones {@code /api/editusertext} returns with
 * {@code api_type=json}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ParseApiErrorMessageTest {

    @Test
    public void errorEnvelopeReturnsCapitalizedMessage() {
        String body = "{\"json\":{\"errors\":[[\"ARCHIVED\",\"that comment is archived\",\"reason\"]],\"data\":{}}}";
        assertEquals("That comment is archived", APIUtils.parseApiErrorMessage(body));
    }

    @Test
    public void multipleErrorsReturnsTheLast() {
        String body = "{\"json\":{\"errors\":[[\"A\",\"first message\",null],[\"B\",\"second message\",null]]}}";
        assertEquals("Second message", APIUtils.parseApiErrorMessage(body));
    }

    @Test
    public void errorWithOnlyACodeFallsBackToTheCode() {
        String body = "{\"json\":{\"errors\":[[\"RATELIMIT\"]]}}";
        assertEquals("RATELIMIT", APIUtils.parseApiErrorMessage(body));
    }

    @Test
    public void successEnvelopeWithEmptyErrorsIsNull() {
        String body = "{\"json\":{\"errors\":[],\"data\":{\"things\":[{\"kind\":\"t1\",\"data\":{\"id\":\"abc\"}}]}}}";
        assertNull(APIUtils.parseApiErrorMessage(body));
    }

    @Test
    public void rawThingWithoutJsonEnvelopeIsNull() {
        String body = "{\"id\":\"abc\",\"body\":\"edited text\"}";
        assertNull(APIUtils.parseApiErrorMessage(body));
    }

    @Test
    public void nullBodyIsNull() {
        assertNull(APIUtils.parseApiErrorMessage(null));
    }

    @Test
    public void malformedBodyIsNull() {
        assertNull(APIUtils.parseApiErrorMessage("not json at all"));
    }
}
