package io.github.xixka.qbittorrent.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.DialogProgressBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * In-app update delivery: downloads the APK with [ApkDownloader] (parallel
 * HTTP ranges — no browser, no manual download) while showing live
 * progress, then hands the file to the system package installer through
 * a FileProvider Uri. Handles the Android 8+ "install unknown apps"
 * permission step.
 */
object UpdateInstaller {

    /** Starts the in-app download + install flow for [update]. */
    fun downloadAndInstall(activity: FragmentActivity, update: UpdateChecker.Update) {
        val url = update.apkUrl
        if (url == null) {
            // no matching APK asset — last resort: release page in browser
            openBrowser(activity, update.htmlUrl)
            return
        }
        val dest = File(
            File(activity.cacheDir, "updates"),
            "update-${update.versionCode}.apk",
        )

        val progressBinding = DialogProgressBinding.inflate(LayoutInflater.from(activity))
        progressBinding.progressBar.max = 1000
        progressBinding.progressBar.progress = 0
        progressBinding.progressText.text = activity.getString(R.string.update_downloading)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_available_title)
            .setView(progressBinding.root)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .show()

        var job: Job? = null
        job = activity.lifecycleScope.launch {
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            try {
                val file = ApkDownloader.download(url, dest) { p ->
                    activity.runOnUiThread {
                        val now = System.currentTimeMillis()
                        // throttle updates; without a known total fall back to
                        // an amount-only line so the dialog is not frozen
                        if (now - lastTime >= 250) {
                            val bps = (p.downloaded - lastBytes) * 1000 / (now - lastTime)
                            lastBytes = p.downloaded
                            lastTime = now
                            if (p.total > 0) {
                                progressBinding.progressBar.progress =
                                    ((p.downloaded * 1000) / p.total).toInt()
                                progressBinding.progressText.text = activity.getString(
                                    R.string.update_speed_fmt,
                                    Format.size(bps),
                                    Format.size(p.downloaded),
                                    Format.size(p.total),
                                )
                            } else {
                                progressBinding.progressText.text = activity.getString(
                                    R.string.update_downloading,
                                ) + " — " + Format.size(p.downloaded)
                            }
                        }
                    }
                }
                dialog.dismiss()
                promptInstall(activity, file)
            } catch (e: CancellationException) {
                // user pressed cancel — partial file already removed;
                // cancellation must keep propagating to honour the
                // structured-concurrency contract
                activity.runOnUiThread {
                    Toast.makeText(
                        activity, R.string.update_download_canceled, Toast.LENGTH_SHORT
                    ).show()
                }
                throw e
            } catch (e: Exception) {
                dialog.dismiss()
                dest.delete()
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.update_available_title)
                    .setMessage(
                        activity.getString(R.string.update_download_failed_fmt, e.message ?: "?")
                    )
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
        dialog.setOnDismissListener {
            job?.takeIf { j -> j.isActive }?.cancel()
        }
    }

    /** Checks the installer permission, then fires the install intent. */
    private fun promptInstall(activity: FragmentActivity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_install)
                .setMessage(R.string.update_install_permission_hint)
                .setPositiveButton(R.string.update_install_open_settings) { _, _ ->
                    runCatching {
                        activity.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${activity.packageName}"),
                            )
                        )
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        install(activity, file)
    }

    private fun install(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBrowser(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}
