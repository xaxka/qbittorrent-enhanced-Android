package io.github.xixka.qbittorrent.model

import com.google.gson.annotations.SerializedName

/**
 * Torrent row from GET /api/v2/torrents/info
 */
data class TorrentInfo(
    @SerializedName("hash") val hash: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("total_size") val totalSize: Long = 0L,
    @SerializedName("progress") val progress: Double = 0.0,
    @SerializedName("dlspeed") val dlSpeed: Long = 0L,
    @SerializedName("upspeed") val upSpeed: Long = 0L,
    @SerializedName("eta") val eta: Long = 8640000L,
    @SerializedName("state") val state: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("tags") val tags: String = "",
    @SerializedName("added_on") val addedOn: Long = 0L,
    @SerializedName("completion_on") val completionOn: Long = -1L,
    @SerializedName("amount_left") val amountLeft: Long = 0L,
    @SerializedName("completed") val completed: Long = 0L,
    @SerializedName("downloaded") val downloaded: Long = 0L,
    @SerializedName("downloaded_session") val downloadedSession: Long = 0L,
    @SerializedName("uploaded") val uploaded: Long = 0L,
    @SerializedName("uploaded_session") val uploadedSession: Long = 0L,
    @SerializedName("num_seeds") val numSeeds: Int = 0,
    @SerializedName("num_complete") val numSeedsTotal: Int = 0,
    @SerializedName("num_leechs") val numLeechs: Int = 0,
    @SerializedName("num_incomplete") val numLeechsTotal: Int = 0,
    @SerializedName("ratio") val ratio: Double = 0.0,
    @SerializedName("save_path") val savePath: String = "",
    @SerializedName("download_path") val downloadPath: String? = null,
    @SerializedName("magnet_uri") val magnetUri: String = "",
    @SerializedName("seq_dl") val sequential: Boolean = false,
    @SerializedName("f_l_piece_prio") val firstLastPiecePrio: Boolean = false,
    @SerializedName("force_start") val forceStart: Boolean = false,
    @SerializedName("super_seeding") val superSeeding: Boolean = false,
    @SerializedName("private") val isPrivate: Boolean = false,
) {
    val isPaused: Boolean
        get() = state.equals("pausedDL", ignoreCase = true) || state.equals("pausedUP", ignoreCase = true) ||
                state.equals("stoppedDL", ignoreCase = true) || state.equals("stoppedUP", ignoreCase = true)

    val isActive: Boolean
        get() = dlSpeed > 0 || upSpeed > 0 ||
                state.equals("downloading", ignoreCase = true) ||
                state.equals("uploading", ignoreCase = true) ||
                state.equals("metaDL", ignoreCase = true) ||
                state.equals("forcedDL", ignoreCase = true) ||
                state.equals("forcedUP", ignoreCase = true) ||
                state.equals("moving", ignoreCase = true)
}

/**
 * Torrent properties from GET /api/v2/torrents/properties
 */
data class TorrentProperties(
    @SerializedName("addition_date") val additionDate: Long = 0L,
    @SerializedName("comment") val comment: String = "",
    @SerializedName("completion_date") val completionDate: Long = -1L,
    @SerializedName("created_by") val createdBy: String = "",
    @SerializedName("creation_date") val creationDate: Long = 0L,
    @SerializedName("dl_limit") val dlLimit: Long = -1L,
    @SerializedName("download_path") val downloadPath: String = "",
    @SerializedName("downloaded") val downloaded: Long = 0L,
    @SerializedName("downloaded_session") val downloadedSession: Long = 0L,
    @SerializedName("eta") val eta: Long = 8640000L,
    @SerializedName("hash") val hash: String = "",
    @SerializedName("infohash_v1") val infohashV1: String = "",
    @SerializedName("infohash_v2") val infohashV2: String = "",
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("last_seen_complete") val lastSeenComplete: Long = 0L,
    @SerializedName("nb_connections") val connections: Long = 0L,
    @SerializedName("peers") val peers: Long = 0L,
    @SerializedName("peers_total") val peersTotal: Long = 0L,
    @SerializedName("piece_have") val piecesHave: Long = 0L,
    @SerializedName("pieces_num") val piecesNum: Long = 0L,
    @SerializedName("piece_size") val pieceSize: Long = 0L,
    @SerializedName("reannounce") val reannounce: Long = 0L,
    @SerializedName("save_path") val savePath: String = "",
    @SerializedName("seeding_time") val seedingTime: Long = 0L,
    @SerializedName("seeds") val seeds: Long = 0L,
    @SerializedName("seeds_total") val seedsTotal: Long = 0L,
    @SerializedName("share_ratio") val shareRatio: Double = 0.0,
    @SerializedName("super_seeding") val superSeeding: Boolean = false,
    @SerializedName("time_active") val timeActive: Long = 0L,
    @SerializedName("total_size") val totalSize: Long = 0L,
    @SerializedName("total_downloaded") val totalDownloaded: Long = 0L,
    @SerializedName("total_downloaded_session") val totalDownloadedSession: Long = 0L,
    @SerializedName("total_uploaded") val totalUploaded: Long = 0L,
    @SerializedName("total_uploaded_session") val totalUploadedSession: Long = 0L,
    @SerializedName("total_wasted") val totalWasted: Long = 0L,
    @SerializedName("tracker") val tracker: String = "",
    @SerializedName("up_limit") val upLimit: Long = -1L,
    @SerializedName("uploaded") val uploaded: Long = 0L,
    @SerializedName("uploaded_session") val uploadedSession: Long = 0L,
    @SerializedName("up_speed") val upSpeed: Long = 0L,
)

/**
 * Torrent file from GET /api/v2/torrents/files
 */
data class TorrentFile(
    @SerializedName("index") val index: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("progress") val progress: Double = 0.0,
    @SerializedName("priority") val priority: Int = 1,
    @SerializedName("is_seed") val isSeed: Boolean = false,
    @SerializedName("availability") val availability: Double = -1.0,
) {
    /** progress is 0..1 on qBittorrent < 5.1 and 0..100 on newer releases */
    val progressFraction: Double
        get() = if (progress > 1.0) progress / 100.0 else progress

    val isSkipped: Boolean get() = priority == 0
}

/**
 * Tracker from GET /api/v2/torrents/trackers
 */
data class Tracker(
    @SerializedName("url") val url: String = "",
    @SerializedName("status") val status: Int = 0,
    @SerializedName("tier") val tier: Int = 0,
    @SerializedName("num_peers") val numPeers: Int = 0,
    @SerializedName("num_seeds") val numSeeds: Int = 0,
    @SerializedName("num_leeches") val numLeeches: Int = 0,
    @SerializedName("num_downloaded") val numDownloaded: Int = -1,
    @SerializedName("msg") val msg: String = "",
) {
    val isBuiltIn: Boolean get() = url.startsWith("**")

    val statusLabelRes: Int
        get() = when (status) {
            0 -> io.github.xixka.qbittorrent.R.string.tracker_status_disabled
            1 -> io.github.xixka.qbittorrent.R.string.tracker_status_not_contacted
            2 -> io.github.xixka.qbittorrent.R.string.tracker_status_working
            3 -> io.github.xixka.qbittorrent.R.string.tracker_status_updating
            4 -> io.github.xixka.qbittorrent.R.string.tracker_status_not_working
            5 -> io.github.xixka.qbittorrent.R.string.tracker_status_unreachable
            else -> io.github.xixka.qbittorrent.R.string.tracker_status_banned
        }
}

/**
 * Peer from GET /api/v2/sync/torrentPeers
 */
data class Peer(
    @SerializedName("ip") val ip: String = "",
    @SerializedName("port") val port: Int = 0,
    @SerializedName("client") val client: String = "",
    @SerializedName("progress") val progress: Double = 0.0,
    @SerializedName("connection_status") val connectionStatus: String = "",
    @SerializedName("downloaded") val downloaded: Long = 0L,
    @SerializedName("uploaded") val uploaded: Long = 0L,
    @SerializedName("down_speed") val downSpeed: Long = 0L,
    @SerializedName("up_speed") val upSpeed: Long = 0L,
    @SerializedName("flags") val flags: String = "",
    @SerializedName("relevance") val relevance: Double = 0.0,
    @SerializedName("files") val files: String = "",
    @SerializedName("country") val country: String = "",
    @SerializedName("country_code") val countryCode: String = "",
) {
    /** progress is 0..1 (older) or 0..100 (newer qBittorrent) */
    val progressFraction: Double
        get() = if (progress > 1.0) progress / 100.0 else progress

    val endpoint: String get() = "$ip:$port"
}

data class PeerSyncResponse(
    @SerializedName("peers") val peers: Map<String, Peer>? = null,
    @SerializedName("full_update") val fullUpdate: Boolean? = null,
)

/**
 * Global transfer info from GET /api/v2/transfer/info
 */
data class TransferInfo(
    @SerializedName("connection_status") val connectionStatus: String = "",
    @SerializedName("dht_nodes") val dhtNodes: Long = 0L,
    @SerializedName("dl_info_data") val dlInfoData: Long = 0L,
    @SerializedName("dl_info_speed") val dlInfoSpeed: Long = 0L,
    @SerializedName("dl_rate_limit") val dlRateLimit: Long = 0L,
    @SerializedName("up_info_data") val upInfoData: Long = 0L,
    @SerializedName("up_info_speed") val upInfoSpeed: Long = 0L,
    @SerializedName("up_rate_limit") val upRateLimit: Long = 0L,
    @SerializedName("use_alt_speed_limits") val useAltSpeedLimits: Boolean = false,
)

data class QBCategory(
    @SerializedName("name") val name: String = "",
    @SerializedName("savePath") val savePath: String = "",
)
