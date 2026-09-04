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
    /**
     * Per-torrent share limits (engine semantics: -2 = follow the global
     * default, -1 = no limit, positive = the limit itself) and the action
     * taken when a limit is reached (enum name: Default / Stop / Remove /
     * EnableSuperSeeding / RemoveWithContent).
     */
    @SerializedName("ratio_limit") val ratioLimit: Double = -2.0,
    @SerializedName("seeding_time_limit") val seedingTimeLimit: Long = -2L,
    @SerializedName("inactive_seeding_time_limit") val inactiveSeedingTimeLimit: Long = -2L,
    @SerializedName("share_limit_action") val shareLimitAction: String = "Default",
    /** The engine answers null while metadata is not fetched yet. */
    @SerializedName("private") val isPrivate: Boolean? = false,
    /** Last chunk up/down (epoch seconds, 0 = never since start). */
    @SerializedName("last_activity") val lastActivity: Long = 0L,
    /** Swarm availability (-1/absent while metadata is missing). */
    @SerializedName("availability") val availability: Double = -1.0,
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
 * Web seed (HTTP source) from GET /api/v2/torrents/webseeds —
 * qBitController TorrentWebSeedsTab parity.
 */
data class WebSeed(
    @SerializedName("url") val url: String = "",
)

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
    // qB >= 4.2 serializes the peer connection type as "connection"
    // (BT / Web / µTP); "connection_status" was never a peer key — it is
    // the TRANSFER-level key, so the old name always read as blank.
    @SerializedName("connection") val connectionStatus: String = "",
    @SerializedName("downloaded") val downloaded: Long = 0L,
    @SerializedName("uploaded") val uploaded: Long = 0L,
    // peer download speed is "dl_speed" (matching the transfer endpoints);
    // "down_speed" never existed in the peers response and read as 0.
    @SerializedName("dl_speed") val downSpeed: Long = 0L,
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


// ---------------------------------------------------------------------------
// sync/maindata -> server_state (qBitController StatisticsDialog parity)
// ---------------------------------------------------------------------------

/**
 * Global server statistics from `GET /api/v2/sync/maindata` (`server_state`).
 * Mirrors qBitController's ServerState model field by field.
 */
data class ServerState(
    @SerializedName("alltime_ul") val allTimeUpload: Long = 0L,
    @SerializedName("alltime_dl") val allTimeDownload: Long = 0L,
    @SerializedName("global_ratio") val globalRatio: String = "",
    @SerializedName("total_wasted_session") val sessionWaste: Long = 0L,
    @SerializedName("total_peer_connections") val connectedPeers: Long = 0L,
    @SerializedName("read_cache_hits") val readCacheHits: String = "",
    @SerializedName("total_buffers_size") val bufferSize: Long = 0L,
    @SerializedName("write_cache_overload") val writeCacheOverload: String = "",
    @SerializedName("read_cache_overload") val readCacheOverload: String = "",
    @SerializedName("queued_io_jobs") val queuedIOJobs: Long = 0L,
    @SerializedName("average_time_queue") val averageTimeInQueue: Long = 0L,
    @SerializedName("total_queued_size") val queuedSize: Long = 0L,
    @SerializedName("dl_info_data") val downloadSession: Long = 0L,
    @SerializedName("dl_info_speed") val downloadSpeed: Long = 0L,
    @SerializedName("dl_rate_limit") val downloadSpeedLimit: Long = 0L,
    @SerializedName("up_info_data") val uploadSession: Long = 0L,
    @SerializedName("up_info_speed") val uploadSpeed: Long = 0L,
    @SerializedName("up_rate_limit") val uploadSpeedLimit: Long = 0L,
    @SerializedName("use_alt_speed_limits") val useAlternativeSpeedLimits: Boolean = false,
    @SerializedName("free_space_on_disk") val freeSpace: Long = 0L,
)

/** Wrapper of the sync/maindata payload (server_state + full update marker). */
data class MainData(
    @SerializedName("server_state") val serverState: ServerState? = null,
)

// ---------------------------------------------------------------------------
// RSS (qBitController parity)
// ---------------------------------------------------------------------------

/**
 * One article of a feed, from `GET /api/v2/rss/items?withData=true`.
 */
data class RssArticle(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("torrentUrl") val torrentUrl: String = "",
    @SerializedName("link") val link: String = "",
    @SerializedName("isRead") val isRead: Boolean = false,
    /** Seconds since epoch — the engine emits a plain number. */
    @SerializedName("date") val date: Long = 0L,
)

/**
 * A node in the RSS tree (`GET /api/v2/rss/items`): either a feed (has uid/url)
 * or a folder (has children). Parsed manually because the tree shape is
 * dynamic — see TorrentRepository.parseRssTree.
 */
data class RssFeedNode(
    val name: String,
    val uid: String? = null,
    val url: String? = null,
    val children: List<RssFeedNode> = emptyList(),
    /** Slash path of the parent folder chain (for building API item paths). */
    val path: List<String> = emptyList(),
    val articles: List<RssArticle> = emptyList(),
    val hasUnread: Boolean = false,
) {
    val isFeed: Boolean get() = uid != null

    /** Backslash-joined path the Web API uses to address this item. */
    val apiPath: String get() = (path + name).joinToString("\\")
}

/**
 * An automated download rule from `GET /api/v2/rss/rules`.
 */
data class RssRule(
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("mustContain") val mustContain: String = "",
    @SerializedName("mustNotContain") val mustNotContain: String = "",
    @SerializedName("useRegex") val useRegex: Boolean = false,
    @SerializedName("episodeFilter") val episodeFilter: String = "",
    @SerializedName("ignoreDays") val ignoreDays: Int = 0,
    @SerializedName("addPaused") val addPaused: Boolean? = null,
    @SerializedName("assignedCategory") val assignedCategory: String = "",
    @SerializedName("savePath") val savePath: String = "",
    @SerializedName("torrentContentLayout") val contentLayout: String? = null,
    @SerializedName("smartFilter") val smartFilter: Boolean = false,
    @SerializedName("affectedFeeds") val affectedFeeds: List<String> = emptyList(),
)

// ---------------------------------------------------------------------------
// Log (qBitController parity)
// ---------------------------------------------------------------------------

data class LogEntry(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("message") val message: String = "",
    /** Seconds since epoch. */
    @SerializedName("timestamp") val timestamp: Long = 0L,
    /** 1 NORMAL, 2 INFO, 4 WARNING, 8 CRITICAL. */
    @SerializedName("type") val type: Int = 1,
) {
    val typeRes: Int
        get() = when (type) {
            2 -> io.github.xixka.qbittorrent.R.string.log_type_info
            4 -> io.github.xixka.qbittorrent.R.string.log_type_warning
            8 -> io.github.xixka.qbittorrent.R.string.log_type_critical
            else -> io.github.xixka.qbittorrent.R.string.log_type_normal
        }

    val isWarning: Boolean get() = type and 4 != 0
    val isCritical: Boolean get() = type and 8 != 0
    val isInfo: Boolean get() = type == 2
}

// ---------------------------------------------------------------------------
// Search engine (qBitController parity)
// ---------------------------------------------------------------------------

data class SearchStartResponse(
    @SerializedName("id") val id: Int = -1,
)

data class SearchResultEntry(
    @SerializedName("descrLink") val descriptionLink: String = "",
    @SerializedName("fileName") val fileName: String = "",
    @SerializedName("fileSize") val fileSize: Long? = null,
    @SerializedName("fileUrl") val fileUrl: String = "",
    @SerializedName("nbLeechers") val leechers: Int? = null,
    @SerializedName("nbSeeders") val seeders: Int? = null,
    @SerializedName("siteUrl") val siteUrl: String = "",
)

data class SearchResults(
    @SerializedName("results") val results: List<SearchResultEntry> = emptyList(),
    /** "Running" while the engine is still collecting results. */
    @SerializedName("status") val status: String = "Stopped",
    @SerializedName("total") val total: Int = 0,
)

data class SearchPlugin(
    @SerializedName("name") val name: String = "",
    @SerializedName("fullName") val fullName: String = "",
    @SerializedName("version") val version: String = "",
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("supportedCategories") val supportedCategories: Map<String, String> = emptyMap(),
)
