package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import java.util.Locale

/**
 * Cheap replacements for the `androidx.documentfile` directory walks.
 *
 * `DocumentFile.listFiles()` projects only `document_id`, so every following `getName()` is a
 * separate `ContentResolver.query` for `_display_name` — one Binder round trip per file. A
 * `findFile()` is that same walk plus a `getName()` on each entry until it matches. On a download
 * folder holding a couple of thousand files that costs seconds — measured on an emulator, 2 002
 * files took 433 ms for the listing and a further 6 643 ms for the per-file `getName()` calls.
 *
 * The download path used to pay that between receiving the response headers and reading the body,
 * leaving the transfer idle with its notification stuck at 0%. It now resolves the destination
 * before opening the response, so the cost no longer holds a socket open, but it is still on the
 * path a user waits through.
 *
 * Asking for `_display_name` in the projection up front collapses all of that into the single
 * query the listing already performs.
 */
object DocumentTreeUtils {

    private const val TAG = "DocumentTreeUtils"

    /**
     * AOSP's `ExternalStorageProvider`, the authority behind internal storage, SD cards and USB
     * volumes — and the only one whose collision handling is known here. See
     * [providerDeduplicatesOnCreate].
     */
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * Returns the lower-cased display names of every child of [directoryUri], for membership tests.
     *
     * @param directoryUri a tree-backed document URI for a directory, as [treeRootDocumentUri] and
     *                     [findChildDocumentUri] return. Not `DocumentFile.fromTreeUri`, which
     *                     [findChildDocumentUri] explains cannot address a child here.
     * @return the names, lower-cased with [Locale.US] to match the existing duplicate checks, or an
     *         empty set if the directory cannot be queried. An empty set means duplicates are not
     *         detected and the provider is left to resolve any collision, which is the same
     *         outcome the `DocumentFile` walk produced when its query failed.
     */
    @JvmStatic
    fun listChildDisplayNamesLowercase(context: Context, directoryUri: Uri): Set<String> {
        val childrenUri = childDocumentsUriOrNull(directoryUri) ?: return emptySet()

        val names = HashSet<String>()
        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn) ?: continue
                    names.add(name.lowercase(Locale.US))
                }
            }
        } catch (e: Exception) {
            // Matches DocumentFile.listFiles(), which logs and returns what it has so far rather
            // than propagating a provider failure.
            Log.w(TAG, "Failed to list children of $directoryUri", e)
        }
        return names
    }

    /**
     * Returns the document URI for the root of a picked tree, i.e. what `DocumentFile.fromTreeUri`
     * resolves a raw `ACTION_OPEN_DOCUMENT_TREE` result to.
     */
    @JvmStatic
    fun treeRootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

    /**
     * Returns the document URI of the child of [parentDocumentUri] whose display name is exactly
     * [displayName], or null if there is none.
     *
     * The replacement for `DocumentFile.findFile()`, which walks the directory as `DocumentFile`s
     * and pays a Binder round trip per entry for the name. Its worst case is a miss, because that
     * walks every child before returning null — which is precisely what a download into a
     * not-yet-created per-subreddit folder does.
     *
     * This deliberately returns a `Uri` rather than a `DocumentFile`. `DocumentFile.fromTreeUri` is
     * the only public way to wrap one, and it resolves a child document URI to the child only when
     * `DocumentsContract.isDocumentUri` says so — which asks PackageManager whether the authority
     * is a documents provider. Under Android 11+ package visibility this app cannot see
     * `com.android.externalstorage.documents`, so that check returns false and `fromTreeUri`
     * silently hands back the *tree root* instead of the child. Everything downstream then reads
     * and writes the wrong directory.
     *
     * Name matching is case-sensitive, as `findFile()` was.
     */
    @JvmStatic
    fun findChildDocumentUri(context: Context, parentDocumentUri: Uri, displayName: String): Uri? {
        val childrenUri = childDocumentsUriOrNull(parentDocumentUri) ?: return null

        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (displayName != cursor.getString(nameColumn)) {
                        continue
                    }
                    val documentId = cursor.getString(idColumn) ?: continue
                    return DocumentsContract.buildDocumentUriUsingTree(parentDocumentUri, documentId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to look up '$displayName' under $parentDocumentUri", e)
        }
        return null
    }

    /**
     * Creates a subdirectory named [displayName] under [parentDocumentUri] and returns its document
     * URI, or null if the provider refused.
     */
    @JvmStatic
    fun createDirectory(context: Context, parentDocumentUri: Uri, displayName: String): Uri? =
        createDocument(context, parentDocumentUri, DocumentsContract.Document.MIME_TYPE_DIR, displayName)

    /**
     * Creates a document of [mimeType] named [displayName] under [parentDocumentUri] and returns
     * its URI, or null if the provider refused. The provider may hand back a different name than
     * the one requested.
     */
    @JvmStatic
    fun createDocument(context: Context, parentDocumentUri: Uri, mimeType: String, displayName: String): Uri? {
        return try {
            DocumentsContract.createDocument(context.contentResolver, parentDocumentUri, mimeType, displayName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create '$displayName' under $parentDocumentUri", e)
            null
        }
    }

    /**
     * A document that was created, and the name it actually got -- which is not always the name
     * that was asked for.
     */
    data class CreatedDocument(@JvmField val uri: Uri, @JvmField val displayName: String)

    /**
     * Creates a document, retrying once with an ASCII-only name if the provider refuses the one
     * given.
     *
     * Emoji, CJK and accented Latin survive on every destination reachable on Android today, so the
     * full name is always tried first and is what a caller normally gets. There is no way to ask a
     * SAF provider in advance which characters its filesystem accepts, so a refusal is the signal:
     * a destination that cannot store them says so, and only then is the name reduced.
     *
     * The name that succeeded comes back with the URI because callers keep using it afterwards --
     * for the MediaStore record and the completion notification -- and silently substituting a
     * different one would leave them describing a file that is not there.
     */
    @JvmStatic
    fun createDocumentWithAsciiFallback(
        context: Context,
        parentDocumentUri: Uri,
        mimeType: String,
        displayName: String,
    ): CreatedDocument? {
        createDocument(context, parentDocumentUri, mimeType, displayName)?.let {
            return CreatedDocument(it, displayName)
        }

        val asciiName = MediaFileNameUtils.toAsciiFilename(displayName)
        if (asciiName == displayName) {
            return null
        }

        Log.w(TAG, "Retrying '$displayName' as '$asciiName' under $parentDocumentUri")
        return createDocument(context, parentDocumentUri, mimeType, asciiName)
            ?.let { CreatedDocument(it, asciiName) }
    }

    private fun childDocumentsUriOrNull(directoryUri: Uri): Uri? = try {
        DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri,
            DocumentsContract.getDocumentId(directoryUri)
        )
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Not a tree document URI: $directoryUri", e)
        null
    }

    /**
     * Whether the provider behind [documentUri] renames a colliding document on create, in the same
     * `name (n).ext` form this app uses, so a caller can skip checking the directory itself.
     *
     * True only for AOSP's `ExternalStorageProvider`, whose `FileSystemProvider.buildUniqueFile`
     * is where that form comes from. Other providers are free to do something else — a cloud
     * backend that allows two children with the same display name would leave both downloads
     * sharing a name — so they keep the explicit check.
     */
    @JvmStatic
    fun providerDeduplicatesOnCreate(documentUri: Uri): Boolean =
        EXTERNAL_STORAGE_AUTHORITY == documentUri.authority

    /**
     * Returns a MIME type that a documents provider will pair with [fileName]'s own extension, or
     * null if the extension maps to nothing known.
     *
     * This is what makes the provider's own collision handling usable. `FileSystemProvider`
     * deduplicates a name into `name (n).ext` only when the MIME type it is handed equals
     * `MimeTypeMap.getMimeTypeFromExtension()` of the name's extension; otherwise it treats the
     * whole `name.ext` as the base and appends the suffix after it, producing `name.ext (n)` — a
     * file with its extension stranded in the middle. Handing the name's own extension to that same
     * call is what makes the two agree; the arguments are not identical, since `FileSystemProvider`
     * lowercases first, but the lookup is case-insensitive, which `the extension is matched without
     * regard to case` pins.
     *
     * A wildcard subtype never matches, which is why the app had to deduplicate names itself.
     */
    @JvmStatic
    fun mimeTypeMatchingExtension(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "")
        if (extension.isEmpty()) {
            return null
        }
        // No lowercasing here: MimeUtils does it under getMimeTypeFromExtension, and so does
        // FileSystemProvider before its own lookup, so both sides already agree on a mixed-case
        // extension.
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    /**
     * Returns [fileName] if nothing in [existingNames] already uses it, otherwise the same name
     * with the first free " (n)" suffix inserted before the extension.
     *
     * Only needed when [mimeTypeMatchingExtension] comes back null and the provider therefore
     * cannot be trusted to produce this same form itself.
     */
    @JvmStatic
    fun deduplicateFileName(fileName: String, existingNames: Set<String>): String {
        if (!existingNames.contains(fileName.lowercase(Locale.US))) {
            return fileName
        }

        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex == -1) fileName else fileName.substring(0, dotIndex)
        val extension = if (dotIndex == -1) "" else fileName.substring(dotIndex)

        var num = 1
        var candidate: String
        do {
            candidate = "$baseName ($num)$extension"
            num++
        } while (existingNames.contains(candidate.lowercase(Locale.US)))
        return candidate
    }
}
