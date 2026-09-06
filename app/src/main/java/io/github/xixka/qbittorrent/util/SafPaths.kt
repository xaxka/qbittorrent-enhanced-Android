package io.github.xixka.qbittorrent.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Maps SAF content URIs from the external-storage provider back to real
 * filesystem paths. The bundled engine (qbittorrent-nox child process)
 * only understands real paths, so "start seeding immediately" needs this
 * translation; picking from other providers (cloud, Downloads provider)
 * yields no real path and seeding stays disabled for that source.
 */
object SafPaths {

    /** Real path such as "/storage/emulated/0/Download/my folder", or null. */
    fun toRealPath(context: Context, uri: Uri, isTree: Boolean): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = runCatching {
            if (isTree) DocumentsContract.getTreeDocumentId(uri)
            else DocumentsContract.getDocumentId(uri)
        }.getOrNull() ?: return null
        val sep = docId.indexOf(':')
        if (sep <= 0) return null
        val volume = docId.substring(0, sep)
        val rawPath = docId.substring(sep + 1)
        if (rawPath.isBlank()) return null
        // docIds are always percent-encoded per the SAF contract: decoding
        // unconditionally (the old "contains '%'" heuristic mangled literal
        // names like "50%off.zip")
        val path = Uri.decode(rawPath)
        val volumeRoot = if (volume == "primary") {
            // /storage/emulated/0
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        val dir = java.io.File(volumeRoot, path)
        return if (dir.exists()) dir.absolutePath else null
    }

    /**
     * Seeding is only offered when the engine can actually open the source:
     * a resolved real path AND the app-level all-files / legacy storage
     * grant (the child process inherits the app's storage view).
     */
    fun engineCanRead(context: Context, uri: Uri, isTree: Boolean): Boolean =
        toRealPath(context, uri, isTree) != null && StorageAccess.isGranted(context)
}
