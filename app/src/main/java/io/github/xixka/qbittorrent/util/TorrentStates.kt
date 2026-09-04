package io.github.xixka.qbittorrent.util

import io.github.xixka.qbittorrent.R

/**
 * Maps the upstream `state` field of /api/v2/torrents/info onto localized labels,
 * mirroring the state naming of the qBittorrent WebUI. Covers both the legacy
 * `paused*` spellings (qB < 5) and the `stopped*` spellings (qB >= 5).
 */
object TorrentStates {

    fun labelRes(state: String): Int = when (state.lowercase()) {
        "error" -> R.string.state_error
        "missingfiles" -> R.string.state_missing_files
        "uploading" -> R.string.state_uploading
        "stoppedup", "pausedup" -> R.string.state_paused_up
        "queuedup" -> R.string.state_queued_up
        "stalledup" -> R.string.state_stalled_up
        "checkingup" -> R.string.state_checking_up
        "forcedup" -> R.string.state_forced_up
        "allocating" -> R.string.state_allocating
        "downloading" -> R.string.state_downloading
        "metadl" -> R.string.state_meta_dl
        "forcedmetadl" -> R.string.state_meta_dl
        "stoppeddl", "pauseddl" -> R.string.state_paused_dl
        "queueddl" -> R.string.state_queued_dl
        "stalleddl" -> R.string.state_stalled_dl
        "checkingdl" -> R.string.state_checking_dl
        "forceddl" -> R.string.state_forced_dl
        "checkingresumedata" -> R.string.state_checking_resume_data
        "moving" -> R.string.state_moving
        else -> R.string.state_unknown
    }
}
