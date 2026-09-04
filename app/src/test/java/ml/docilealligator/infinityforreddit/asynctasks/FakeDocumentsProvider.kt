package ml.docilealligator.infinityforreddit.asynctasks

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/**
 * Just enough of a documents provider for `DocumentFile` to walk a directory and make a file in it.
 *
 * `BackupSettings` writes through a SAF tree `Uri`, so a test of it needs something on the other end
 * of that `Uri`. Document ids are paths under [root], with the root itself as [ROOT_DOCUMENT_ID];
 * everything a backup does — list the directory, delete a file of the same name, create one, write
 * to it — goes to a real temporary directory the test can then read back.
 */
class FakeDocumentsProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "ml.docilealligator.infinityforreddit.test.documents"
        const val ROOT_DOCUMENT_ID = "root"

        /** The directory every document lives under. Set before the provider is created. */
        lateinit var root: File
    }

    private val defaultProjection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
    )

    private fun fileOf(documentId: String): File =
        if (documentId == ROOT_DOCUMENT_ID) {
            root
        } else {
            File(root, documentId.removePrefix("$ROOT_DOCUMENT_ID/"))
        }

    private fun idOf(file: File): String =
        if (file == root) ROOT_DOCUMENT_ID else ROOT_DOCUMENT_ID + "/" + file.toRelativeString(root)

    private fun addRow(cursor: MatrixCursor, file: File) {
        val row = cursor.newRow()
        for (column in cursor.columnNames) {
            when (column) {
                Document.COLUMN_DOCUMENT_ID -> row.add(column, idOf(file))
                Document.COLUMN_DISPLAY_NAME -> row.add(column, file.name)
                Document.COLUMN_MIME_TYPE -> row.add(
                    column,
                    if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream",
                )
                Document.COLUMN_SIZE -> row.add(column, file.length())
                Document.COLUMN_LAST_MODIFIED -> row.add(column, file.lastModified())
                Document.COLUMN_FLAGS -> row.add(
                    column,
                    Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE or
                        Document.FLAG_SUPPORTS_WRITE,
                )
                else -> row.add(column, null)
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: defaultProjection).also { addRow(it, fileOf(documentId)) }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: defaultProjection).also { cursor ->
        fileOf(parentDocumentId).listFiles()?.forEach { addRow(cursor, it) }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith(parentDocumentId)

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val file = File(fileOf(parentDocumentId), displayName)
        if (mimeType == Document.MIME_TYPE_DIR) file.mkdirs() else file.createNewFile()
        return idOf(file)
    }

    override fun deleteDocument(documentId: String) {
        fileOf(documentId).delete()
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileOf(documentId), ParcelFileDescriptor.parseMode(mode))
}
