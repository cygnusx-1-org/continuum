package ml.docilealligator.infinityforreddit.readpost

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Whether a post is drawn as already read. "Mark posts as read" being off is not a display choice
 * -- it means the app must not go looking, because the answer would come from a table the user
 * asked it to stop keeping.
 */
class ReadPostsListTest {

    private val ids = listOf("abc123", "def456")

    @Test
    fun `nothing is read when the feature is switched off`() {
        val dao: ReadPostDao = mock()

        val readPosts = ReadPostsList(dao, "Alice", true)

        assertEquals(emptySet<String>(), readPosts.getReadPostsIdsByIds(ids))
        verify(dao, never()).getReadPostsIdsByIds(any(), any())
    }

    @Test
    fun `the account's own read posts come back when the feature is on`() {
        val dao: ReadPostDao = mock()
        whenever(dao.getReadPostsIdsByIds(ids, "Alice")).thenReturn(listOf("abc123"))

        val readPosts = ReadPostsList(dao, "Alice", false)

        assertEquals(setOf("abc123"), readPosts.getReadPostsIdsByIds(ids))
    }
}
