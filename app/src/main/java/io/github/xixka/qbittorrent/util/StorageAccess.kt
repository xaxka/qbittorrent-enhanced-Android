package io.github.xixka.qbittorrent.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Access state of the public shared storage the bundled engine downloads
 * into (`/storage/emulated/0/Download/qbittorrent`).
 *
 * The engine is a child process running under the app's UID, so whatever
 * storage access the APP has, the engine inherits:
 *  - Android 11+ (API 30+): "All files access" (MANAGE_EXTERNAL_STORAGE),
 *    granted through the special system settings screen — there is no
 *    runtime dialog for it.
 *  - Android 10 (API 29): legacy storage via WRITE_EXTERNAL_STORAGE
 *    (requestLegacyExternalStorage) with the normal runtime flow.
 *  - Android 8.x/9 (API 26-28): plain WRITE_EXTERNAL_STORAGE runtime grant.
 */
object StorageAccess {

    /** True when the engine can write the public Download folder. */
    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    /**
     * Intent that opens the system page where the user flips on "All files
     * access" for this app (API 30+), or null on older versions where the
     * normal runtime permission dialog is used instead.
     */
    fun allFilesSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        }.getOrElse {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    /** Permission to request at runtime on Android 10 and older. */
    val legacyRuntimePermission: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) null
        else Manifest.permission.WRITE_EXTERNAL_STORAGE
}
