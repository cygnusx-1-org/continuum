package ml.docilealligator.infinityforreddit.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DocumentTreeUtils] is what the download path uses instead of walking a SAF directory as
 * `DocumentFile`s. Two properties in here are load-bearing and fail silently rather than loudly:
 *
 *  - [DocumentTreeUtils.mimeTypeMatchingExtension] deciding a name's type. The download services
 *    skip checking the directory for a duplicate entirely and let the provider rename instead, and
 *    that only produces `name (n).ext` when the type handed to `createDocument` equals what
 *    `FileSystemProvider.splitFileName` derives from the name's own extension. Answer with a type
 *    that disagrees and files come out named `name.ext (n)`, extension stranded in the middle;
 *    answering null is safe, because that is the caller's signal to pick a free name itself.
 *  - [DocumentTreeUtils.findChildDocumentUri] building the child's URI. Its whole reason for
 *    existing is that `DocumentFile.fromTreeUri` silently resolves a child document URI back to
 *    the tree root when package visibility hides the provider from PackageManager, so a wrong URI
 *    here means reading and writing the wrong directory with no exception to notice.
 *
 * Document creation is not covered here, but not because it cannot be: `DocumentsContract`
 * routes `createDocument` through `ContentResolver.call`, and Robolectric delivers that to a
 * registered provider, so a fake can see the parent URI, MIME type and display name it is handed.
 * What a fake cannot attest to is the renaming itself — `name (n).ext` is `FileSystemProvider`'s
 * behaviour, not ours — so that half stays verified on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class DocumentTreeUtilsTest {

    private lateinit var context: Context

    /** The tree a user picks with `ACTION_OPEN_DOCUMENT_TREE`, as it is stored in preferences. */
    private val treeUri: Uri =
        Uri.parse("content://$EXTERNAL_STORAGE/tree/primary%3ADownload%2FContinuumDL")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.createRequests.clear()
        FakeDocumentsProvider.refuseNonAscii = false
        FakeDocumentsProvider.refuseAll = false
    }

    // ---- mimeTypeMatchingExtension -------------------------------------------------------------

    @Test
    fun `a name's extension decides its type, so the provider splits the name where we do`() {
        assertEquals("image/jpeg", DocumentTreeUtils.mimeTypeMatchingExtension("holiday.jpg"))
        assertEquals("image/jpeg", DocumentTreeUtils.mimeTypeMatchingExtension("holiday.jpeg"))
        assertEquals("image/png", DocumentTreeUtils.mimeTypeMatchingExtension("holiday.png"))
        assertEquals("image/gif", DocumentTreeUtils.mimeTypeMatchingExtension("holiday.gif"))
        assertEquals("video/mp4", DocumentTreeUtils.mimeTypeMatchingExtension("clip.mp4"))
        assertEquals("video/webm", DocumentTreeUtils.mimeTypeMatchingExtension("clip.webm"))
    }

    @Test
    fun `only the last dot counts, the way a filename's extension works`() {
        // Download names are built from post titles, which routinely contain dots.
        assertEquals(
            "video/mp4",
            DocumentTreeUtils.mimeTypeMatchingExtension("The way this adjusts to fit._1vz58to.mp4")
        )
        assertEquals("image/jpeg", DocumentTreeUtils.mimeTypeMatchingExtension("a.b.c.jpg"))
    }

    @Test
    fun `the extension is matched without regard to case`() {
        // Nothing in the call under test normalises case: `MimeUtils` lowercases inside
        // `getMimeTypeFromExtension`, and `FileSystemProvider` lowercases before its own lookup, so
        // the two agree on a mixed-case extension without our help. This pins that they do.
        assertEquals("image/jpeg", DocumentTreeUtils.mimeTypeMatchingExtension("HOLIDAY.JPG"))
        assertEquals("video/mp4", DocumentTreeUtils.mimeTypeMatchingExtension("Clip.Mp4"))
    }

    @Test
    fun `a name the platform table cannot type falls back rather than guessing`() {
        // Null is the signal for the caller to pick a free name itself. Answering a wildcard or a
        // plausible-looking guess instead is what strands the extension.
        assertNull(DocumentTreeUtils.mimeTypeMatchingExtension("holiday.unknown"))
        assertNull(DocumentTreeUtils.mimeTypeMatchingExtension("holiday.zzznotatype"))
        assertNull(DocumentTreeUtils.mimeTypeMatchingExtension("holiday"))
        assertNull(DocumentTreeUtils.mimeTypeMatchingExtension("holiday."))
        assertNull(DocumentTreeUtils.mimeTypeMatchingExtension(""))
    }

    @Test
    fun `every extension the app puts on a download can be typed, bar its own giving-up one`() {
        // The extensions MediaFileNameUtils.getExtension can emit: the ones its regex admits from a
        // media URL, then its per-media-type defaults, then ".unknown" when it cannot tell. This is
        // what decides whether a real download takes the fast path or the fallback, so it is worth
        // knowing which side of the line each one lands on rather than assuming.
        for (extension in listOf("jpg", "jpeg", "png", "gif", "mp4", "webm", "mov", "avi")) {
            assertNotNull(
                "no type for .$extension, so its downloads deduplicate the slow way",
                DocumentTreeUtils.mimeTypeMatchingExtension("holiday.$extension")
            )
        }

        // ".apng" dropped out of the platform MIME table at API 36. Answering with a type of our
        // own would be worse than the fallback: FileSystemProvider derives its own type from the
        // same table, and only deduplicates into "name (n).ext" when the two agree — disagree and
        // it produces "name.apng (1)", with the extension stranded mid-name.
        assertNull(DocumentTreeUtils.mimeTypeMatchingExtension("holiday.apng"))

        // ".unknown" is the one it emits when the media type is unrecognised, and nothing can type
        // it — which is exactly when falling back to checking the directory is the right answer.
        assertNull(DocumentTreeUtils.mimeTypeMatchingExtension("holiday.unknown"))
    }

    // ---- deduplicateFileName -------------------------------------------------------------------

    @Test
    fun `a name nothing else uses is left alone`() {
        assertEquals(
            "holiday.jpg",
            DocumentTreeUtils.deduplicateFileName("holiday.jpg", setOf("other.jpg"))
        )
        assertEquals("holiday.jpg", DocumentTreeUtils.deduplicateFileName("holiday.jpg", emptySet()))
    }

    @Test
    fun `a taken name gets its number before the extension, not after it`() {
        assertEquals(
            "holiday (1).jpg",
            DocumentTreeUtils.deduplicateFileName("holiday.jpg", setOf("holiday.jpg"))
        )
    }

    @Test
    fun `numbering walks up to the first free one`() {
        val taken = setOf("holiday.jpg", "holiday (1).jpg", "holiday (2).jpg")

        assertEquals("holiday (3).jpg", DocumentTreeUtils.deduplicateFileName("holiday.jpg", taken))
    }

    @Test
    fun `a gap in the numbering is filled rather than skipped past`() {
        val taken = setOf("holiday.jpg", "holiday (2).jpg")

        assertEquals("holiday (1).jpg", DocumentTreeUtils.deduplicateFileName("holiday.jpg", taken))
    }

    @Test
    fun `existing names are matched without regard to case`() {
        // The set is lowercased by listChildDisplayNamesLowercase, and the volume may be
        // case-insensitive, so a name differing only in case still counts as taken.
        assertEquals(
            "Holiday (1).JPG",
            DocumentTreeUtils.deduplicateFileName("Holiday.JPG", setOf("holiday.jpg"))
        )
    }

    @Test
    fun `a name with no extension takes the number at the end`() {
        assertEquals(
            "holiday (1)",
            DocumentTreeUtils.deduplicateFileName("holiday", setOf("holiday"))
        )
    }

    // ---- providerDeduplicatesOnCreate ----------------------------------------------------------

    @Test
    fun `only the provider whose renaming we know is trusted to do it`() {
        assertTrue(
            DocumentTreeUtils.providerDeduplicatesOnCreate(
                DocumentTreeUtils.treeRootDocumentUri(treeUri)
            )
        )

        // Anything else keeps the explicit check: a cloud backend is free to allow two children
        // with the same display name, which would leave both downloads sharing a name.
        assertFalse(
            DocumentTreeUtils.providerDeduplicatesOnCreate(
                Uri.parse("content://com.google.android.apps.docs.storage/tree/abc/document/def")
            )
        )
        assertFalse(DocumentTreeUtils.providerDeduplicatesOnCreate(Uri.parse("file:///sdcard/x")))
    }

    // ---- the cursor queries --------------------------------------------------------------------

    @Test
    fun `listing a directory reads the names out of one query`() {
        givenChildren("primary:Download/ContinuumDL/pics" to "pics", "…%2Fa.jpg" to "A.jpg")

        val names = DocumentTreeUtils.listChildDisplayNamesLowercase(context, directoryUri())

        // Lowercased, because that is what the duplicate check compares against.
        assertEquals(setOf("pics", "a.jpg"), names)
    }

    @Test
    fun `a directory the provider will not answer for reads as empty rather than throwing`() {
        givenNoAnswer()

        assertEquals(emptySet<String>(), DocumentTreeUtils.listChildDisplayNamesLowercase(context, directoryUri()))
    }

    @Test
    fun `a uri that is not tree-backed reads as empty rather than throwing`() {
        assertEquals(
            emptySet<String>(),
            DocumentTreeUtils.listChildDisplayNamesLowercase(context, Uri.parse("content://$EXTERNAL_STORAGE/nope"))
        )
        assertNull(
            DocumentTreeUtils.findChildDocumentUri(
                context, Uri.parse("content://$EXTERNAL_STORAGE/nope"), "pics"
            )
        )
    }

    @Test
    fun `finding a child addresses the child itself, not the tree it came from`() {
        givenChildren(
            "primary:Download/ContinuumDL/other" to "other",
            "primary:Download/ContinuumDL/pics" to "pics"
        )

        val child = DocumentTreeUtils.findChildDocumentUri(context, directoryUri(), "pics")

        // The tree segment is kept, so the persisted grant still covers it, and the document
        // segment is the child's own id. Resolving to the parent here is the failure that made
        // downloads land in the wrong folder with nothing thrown.
        assertEquals(
            "content://$EXTERNAL_STORAGE/tree/primary%3ADownload%2FContinuumDL" +
                "/document/primary%3ADownload%2FContinuumDL%2Fpics",
            child.toString()
        )
        assertEquals("primary:Download/ContinuumDL/pics", DocumentsContract.getDocumentId(child))
    }

    @Test
    fun `finding a child is case-sensitive and answers null when there is no match`() {
        givenChildren("primary:Download/ContinuumDL/pics" to "pics")

        assertNull(DocumentTreeUtils.findChildDocumentUri(context, directoryUri(), "Pics"))
        assertNull(DocumentTreeUtils.findChildDocumentUri(context, directoryUri(), "aww"))
    }

    // ---- fake provider -------------------------------------------------------------------------

    private fun directoryUri(): Uri = DocumentTreeUtils.treeRootDocumentUri(treeUri)

    // ---- createDocumentWithAsciiFallback -------------------------------------------------------

    @Test
    fun `a destination that accepts the name gets the name, untouched`() {
        registerFakeProvider()

        val created = DocumentTreeUtils.createDocumentWithAsciiFallback(
            context, treeUri, "video/mp4", "Cool_clip_\uD83D\uDC9A_abc123_def456.mp4"
        )

        assertNotNull(created)
        assertEquals("Cool_clip_\uD83D\uDC9A_abc123_def456.mp4", created!!.displayName)
        // Exactly one attempt. The happy path is every destination reachable on Android today, so
        // the fallback must not cost it a second round trip.
        assertEquals(1, FakeDocumentsProvider.createRequests.size)
    }

    @Test
    fun `a destination that refuses the name is retried with an ASCII one`() {
        registerFakeProvider()
        FakeDocumentsProvider.refuseNonAscii = true

        val created = DocumentTreeUtils.createDocumentWithAsciiFallback(
            context, treeUri, "video/mp4", "Caf\u00E9_\uD83D\uDC9A_abc123_def456.mp4"
        )

        assertNotNull(created)
        // The full name goes first; only the refusal reduces it.
        assertEquals(2, FakeDocumentsProvider.createRequests.size)
        assertEquals("Caf\u00E9_\uD83D\uDC9A_abc123_def456.mp4", FakeDocumentsProvider.createRequests[0])
        assertEquals("Cafe_abc123_def456.mp4", FakeDocumentsProvider.createRequests[1])
    }

    @Test
    fun `the retry keeps the ids, which are what stop two posts colliding`() {
        registerFakeProvider()
        FakeDocumentsProvider.refuseNonAscii = true

        // A title already at the byte budget, with both ids appended after it -- the shape a
        // length-capped name really has. An earlier version budgeted the whole name here and cut
        // the ids off the end.
        val name = "\u6f22".repeat(33) + "_abc123_def456.mp4"
        DocumentTreeUtils.createDocumentWithAsciiFallback(context, treeUri, "video/mp4", name)

        val retried = FakeDocumentsProvider.createRequests[1]
        assertTrue("lost the ids: $retried", retried.endsWith("abc123_def456.mp4"))
    }

    @Test
    fun `the name that succeeded is the one handed back`() {
        registerFakeProvider()
        FakeDocumentsProvider.refuseNonAscii = true

        val created = DocumentTreeUtils.createDocumentWithAsciiFallback(
            context, treeUri, "video/mp4", "Caf\u00E9_abc123.mp4"
        )

        // Callers keep using this name after creation, for the MediaStore record and the completion
        // notification, so handing back the name that was asked for rather than the one that was
        // written would leave them describing a file that is not there.
        assertNotNull(created)
        assertEquals("Cafe_abc123.mp4", created!!.displayName)
        assertTrue(created.uri.toString().endsWith("Cafe_abc123.mp4"))
    }

    @Test
    fun `an already-ASCII name is never attempted twice`() {
        registerFakeProvider()
        // Refuses everything, so the retry is only skipped if the code decides it would be futile
        // rather than because the first attempt happened to succeed.
        FakeDocumentsProvider.refuseAll = true

        // Quote stripping ran when the name was built, so this arrives pure ASCII. It is refused
        // for some other reason -- no space, no permission -- and an identical second attempt would
        // be wasted.
        val created = DocumentTreeUtils.createDocumentWithAsciiFallback(
            context, treeUri, "video/mp4", "Quoted_title_abc123.mp4"
        )

        assertNull(created)
        assertEquals(1, FakeDocumentsProvider.createRequests.size)
    }

    private fun givenChildren(vararg idToName: Pair<String, String>) {
        FakeDocumentsProvider.children = idToName.toList()
        FakeDocumentsProvider.answers = true
        registerFakeProvider()
    }

    private fun givenNoAnswer() {
        FakeDocumentsProvider.children = emptyList()
        FakeDocumentsProvider.answers = false
        registerFakeProvider()
    }

    private fun registerFakeProvider() {
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java)
            .create(ProviderInfo().apply { authority = EXTERNAL_STORAGE })
    }

    /**
     * Answers `queryChildDocuments` with whatever rows the test set up.
     *
     * Its cursor deliberately does not match the requested projection: an unrequested column comes
     * first, then the requested ones in reverse. A provider is free to return its own column set,
     * and this is why the code under test looks columns up by name — reading by position gets the
     * filler for every projection, including the single-column listing, so a positional regression
     * cannot pass here.
     */
    class FakeDocumentsProvider : ContentProvider() {

        companion object {
            /** Every display name `createDocument` was asked for, in order. */
            val createRequests = mutableListOf<String>()

            /** Makes the provider refuse any name holding a character it cannot store. */
            var refuseNonAscii = false

            /** Makes the provider refuse every name, whatever it contains. */
            var refuseAll = false

            /**
             * A name that is not a SAF document column at all, so no projection can ever ask for
             * it: position 0 is never a column a caller wanted, and it can neither collide with a
             * requested column nor be read as the long that a real column like `_size` declares.
             */
            const val UNREQUESTED_COLUMN: String = "_not_a_document_column"
            const val FILLER: String = "not the column you asked for"

            var children: List<Pair<String, String>> = emptyList()
            var answers: Boolean = true
        }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            if (!answers) {
                return null
            }
            val columns = listOf(UNREQUESTED_COLUMN) + (projection ?: emptyArray()).toList().reversed()
            val cursor = MatrixCursor(columns.toTypedArray())
            for ((documentId, displayName) in children) {
                cursor.addRow(
                    columns.map { column ->
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> displayName
                            else -> FILLER
                        }
                    }
                )
            }
            return cursor
        }

        /**
         * Answers `DocumentsContract.createDocument`, which reaches a provider as a `call`.
         *
         * With [refuseNonAscii] set the provider stands in for a filesystem that cannot store the
         * name it was handed: it returns no URI, which is the only signal SAF gives a caller, and
         * the one [DocumentTreeUtils.createDocumentWithAsciiFallback] exists to react to.
         */
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            if (method != "android:createDocument") {
                return null
            }
            val displayName = extras?.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                ?: return null
            createRequests.add(displayName)
            if (refuseAll || (refuseNonAscii && displayName.any { it.code > 0x7E })) {
                return null
            }
            return Bundle().apply {
                // DocumentsContract.EXTRA_URI is not public on every API level; "uri" is the key
                // it reads back. The name is encoded because a download name is a post title, and
                // an unencoded '#' or '?' in one would silently truncate the URI here -- a harness
                // that quietly drops part of what it was given makes tests pass for the wrong
                // reason.
                putParcelable(
                    "uri",
                    Uri.parse("content://$EXTERNAL_STORAGE/document/" + Uri.encode(displayName))
                )
            }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }

    private companion object {
        const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
    }
}
