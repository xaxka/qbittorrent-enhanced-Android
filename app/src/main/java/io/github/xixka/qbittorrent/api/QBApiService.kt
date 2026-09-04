package io.github.xixka.qbittorrent.api

import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.model.LogEntry
import io.github.xixka.qbittorrent.model.MainData
import io.github.xixka.qbittorrent.model.PeerSyncResponse
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.model.RssRule
import io.github.xixka.qbittorrent.model.SearchPlugin
import io.github.xixka.qbittorrent.model.SearchResults
import io.github.xixka.qbittorrent.model.SearchStartResponse
import io.github.xixka.qbittorrent.model.TorrentFile
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TorrentProperties
import io.github.xixka.qbittorrent.model.Tracker
import io.github.xixka.qbittorrent.model.TransferInfo
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Query

/**
 * qBittorrent Web API v2 (unchanged upstream API, default port 8080).
 *
 * See: https://github.com/qbittorrent/qBittorrent/wiki/WebUI-API-(qBittorrent-4.1)
 */
interface QBApiService {

    // ---------- auth / app ----------

    @FormUrlEncoded
    @POST("api/v2/auth/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
    ): Response<ResponseBody>

    @GET("api/v2/app/version")
    suspend fun appVersion(): String

    @GET("api/v2/app/webapiVersion")
    suspend fun webApiVersion(): String

    /** Default download location (`GET /api/v2/app/defaultSavePath`). */
    @GET("api/v2/app/defaultSavePath")
    suspend fun defaultSavePath(): String

    /**
     * Full preference snapshot of the connected qBittorrent instance
     * (the same object the WebUI's Tools-Options dialog reads).
     */
    @GET("api/v2/app/preferences")
    suspend fun appPreferences(): JsonObject

    /**
     * Applies a partial preference update. `json` is a URL-encoded JSON
     * object holding only the keys that should change — exactly how the
     * official WebUI saves the Options dialog.
     */
    @FormUrlEncoded
    @POST("api/v2/app/setPreferences")
    suspend fun setPreferences(@Field("json") json: String): Response<ResponseBody>

    // ---------- transfer ----------

    @GET("api/v2/transfer/info")
    suspend fun transferInfo(): TransferInfo

    @FormUrlEncoded
    @POST("api/v2/transfer/setDownloadLimit")
    suspend fun setDownloadLimit(@Field("limit") limit: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/transfer/setUploadLimit")
    suspend fun setUploadLimit(@Field("limit") limit: String): Response<ResponseBody>

    @POST("api/v2/transfer/toggleSpeedLimitsMode")
    suspend fun toggleAltSpeedLimits(): Response<ResponseBody>

    // ---------- torrents ----------

    @GET("api/v2/torrents/info")
    suspend fun torrents(
        @Query("filter") filter: String? = null,
        @Query("category") category: String? = null,
        @Query("sort") sort: String? = null,
        @Query("reverse") reverse: Boolean? = null,
    ): List<TorrentInfo>

    @GET("api/v2/torrents/properties")
    suspend fun torrentProperties(@Query("hash") hash: String): TorrentProperties

    @GET("api/v2/torrents/files")
    suspend fun torrentFiles(@Query("hash") hash: String): List<TorrentFile>

    @GET("api/v2/torrents/trackers")
    suspend fun torrentTrackers(@Query("hash") hash: String): List<Tracker>

    @GET("api/v2/sync/torrentPeers")
    suspend fun torrentPeers(@Query("hash") hash: String): PeerSyncResponse

    @GET("api/v2/torrents/categories")
    suspend fun categories(): Map<String, QBCategory>

    @FormUrlEncoded
    @POST("api/v2/torrents/add")
    suspend fun addTorrent(@FieldMap fields: Map<String, String>): Response<ResponseBody>

    @Multipart
    @POST("api/v2/torrents/add")
    suspend fun addTorrentFile(
        @Part torrents: MultipartBody.Part,
        @PartMap fields: Map<String, okhttp3.RequestBody> = emptyMap(),
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/pause")
    suspend fun pause(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/resume")
    suspend fun resume(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/delete")
    suspend fun delete(
        @Field("hashes") hashes: String,
        @Field("deleteFiles") deleteFiles: Boolean,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/recheck")
    suspend fun recheck(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/reannounce")
    suspend fun reannounce(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/filePrio")
    suspend fun setFilePriority(
        @Field("hash") hash: String,
        @Field("id") fileIndex: String,
        @Field("priority") priority: Int,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/topPrio")
    suspend fun topPriority(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/bottomPrio")
    suspend fun bottomPriority(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/increasePrio")
    suspend fun increasePriority(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/decreasePrio")
    suspend fun decreasePriority(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/setCategory")
    suspend fun setCategory(
        @Field("hashes") hashes: String,
        @Field("category") category: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/createCategory")
    suspend fun createCategory(
        @Field("category") category: String,
        @Field("savePath") savePath: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/editCategory")
    suspend fun editCategory(
        @Field("category") category: String,
        @Field("savePath") savePath: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/removeCategories")
    suspend fun removeCategories(
        @Field("categories") categories: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/setSequentialDownload")
    suspend fun setSequentialDownload(
        @Field("hashes") hashes: String,
        @Field("enabled") enabled: Boolean? = null,
    ): Response<ResponseBody>

    @FormUrlEncoded

    @POST("api/v2/torrents/toggleFirstLastPiecePrio")
    suspend fun toggleFirstLastPiecePriority(@Field("hashes") hashes: String): Response<ResponseBody>

    @POST("api/v2/torrents/toggleSequentialDownload")
    suspend fun toggleSequentialDownload(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/toggleSuperSeeding")
    suspend fun toggleSuperSeeding(@Field("hashes") hashes: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/setForceStart")
    suspend fun setForceStart(
        @Field("hashes") hashes: String,
        @Field("value") value: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/addTrackers")
    suspend fun addTrackers(
        @Field("hash") hash: String,
        @Field("urls") urls: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/removeTrackers")
    suspend fun removeTrackers(
        @Field("hash") hash: String,
        @Field("urls") urls: String,
    ): Response<ResponseBody>
    // =====================================================================
    // Extended endpoints (qBitController parity): tags, per-torrent limits,
    // rename/location, tracker edit, pieces, maindata, log, RSS, search.
    // =====================================================================

    /** Global server state + statistics snapshot (`GET /api/v2/sync/maindata`). */
    @GET("api/v2/sync/maindata")
    suspend fun mainData(): MainData

    // ---------- tags ----------

    @GET("api/v2/torrents/tags")
    suspend fun tags(): List<String>

    @FormUrlEncoded
    @POST("api/v2/torrents/createTags")
    suspend fun createTags(@Field("tags") tags: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/deleteTags")
    suspend fun deleteTags(@Field("tags") tags: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/addTags")
    suspend fun addTags(
        @Field("hashes") hashes: String,
        @Field("tags") tags: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/removeTags")
    suspend fun removeTags(
        @Field("hashes") hashes: String,
        @Field("tags") tags: String,
    ): Response<ResponseBody>

    // ---------- per-torrent rename / location / limits ----------

    @FormUrlEncoded
    @POST("api/v2/torrents/rename")
    suspend fun renameTorrent(
        @Field("hash") hash: String,
        @Field("name") name: String,
    ): Response<ResponseBody>

    /** qBitController parity: rename a single file inside the torrent. */
    @FormUrlEncoded
    @POST("api/v2/torrents/renameFile")
    suspend fun renameFile(
        @Field("hash") hash: String,
        @Field("id") fileId: String,
        @Field("name") name: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/setLocation")
    suspend fun setLocation(
        @Field("hashes") hashes: String,
        @Field("location") location: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/setDownloadLimit")
    suspend fun setTorrentDownloadLimit(
        @Field("hashes") hashes: String,
        @Field("limit") limit: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/setUploadLimit")
    suspend fun setTorrentUploadLimit(
        @Field("hashes") hashes: String,
        @Field("limit") limit: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/torrents/setShareLimits")
    suspend fun setShareLimits(
        @Field("hashes") hashes: String,
        @Field("ratioLimit") ratioLimit: String,
        @Field("seedingTimeLimit") seedingTimeLimit: String,
        @Field("inactiveSeedingTimeLimit") inactiveSeedingTimeLimit: String,
        /** Required since qBittorrent 5.x (engine enum name, e.g. "Default"). */
        @Field("shareLimitAction") shareLimitAction: String,
    ): Response<ResponseBody>

    /** Direct super-seeding switch (the toggle variant cannot show state). */
    @FormUrlEncoded
    @POST("api/v2/torrents/setSuperSeeding")
    suspend fun setSuperSeeding(
        @Field("hashes") hashes: String,
        @Field("value") value: String,
    ): Response<ResponseBody>

    // ---------- trackers edit ----------

    @FormUrlEncoded
    @POST("api/v2/torrents/editTracker")
    suspend fun editTracker(
        @Field("hash") hash: String,
        @Field("origUrl") origUrl: String,
        @Field("newUrl") newUrl: String,
    ): Response<ResponseBody>

    // ---------- pieces ----------

    /** Per-piece state list: 0 missing, 1 downloading, 2 done. */
    @GET("api/v2/torrents/pieceStates")
    suspend fun pieceStates(@Query("hash") hash: String): List<Int>

    // ---------- log ----------

    @GET("api/v2/log/main")
    suspend fun logMain(
        @Query("last_known_id") lastKnownId: Long = -1,
        @Query("normal") normal: Boolean = true,
        @Query("info") info: Boolean = true,
        @Query("warning") warning: Boolean = true,
        @Query("critical") critical: Boolean = true,
    ): List<LogEntry>

    // ---------- RSS ----------

    /** Tree of feeds/folders; with `withData` the feeds embed their articles. */
    @GET("api/v2/rss/items")
    suspend fun rssItems(@Query("withData") withData: Boolean = false): JsonObject

    @FormUrlEncoded
    @POST("api/v2/rss/markAsRead")
    suspend fun rssMarkAsRead(
        @Field("itemPath") itemPath: String,
        @Field("articleId") articleId: String?,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/rss/refreshItem")
    suspend fun rssRefreshItem(@Field("itemPath") itemPath: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/rss/addFeed")
    suspend fun rssAddFeed(
        @Field("url") url: String,
        @Field("path") path: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/rss/setFeedURL")
    suspend fun rssSetFeedUrl(
        @Field("path") path: String,
        @Field("url") url: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/rss/addFolder")
    suspend fun rssAddFolder(@Field("path") path: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/rss/moveItem")
    suspend fun rssMoveItem(
        @Field("itemPath") itemPath: String,
        @Field("destPath") destPath: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/rss/removeItem")
    suspend fun rssRemoveItem(@Field("path") path: String): Response<ResponseBody>

    @GET("api/v2/rss/rules")
    suspend fun rssRules(): Map<String, RssRule>

    @FormUrlEncoded
    @POST("api/v2/rss/setRule")
    suspend fun rssSetRule(
        @Field("ruleName") ruleName: String,
        @Field("ruleDef") ruleDefinition: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/rss/renameRule")
    suspend fun rssRenameRule(
        @Field("ruleName") ruleName: String,
        @Field("newRuleName") newRuleName: String,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/rss/removeRule")
    suspend fun rssRemoveRule(@Field("ruleName") ruleName: String): Response<ResponseBody>

    // ---------- search engine ----------

    @FormUrlEncoded
    @POST("api/v2/search/start")
    suspend fun searchStart(
        @Field("pattern") pattern: String,
        @Field("category") category: String,
        @Field("plugins") plugins: String,
    ): SearchStartResponse

    @FormUrlEncoded
    @POST("api/v2/search/stop")
    suspend fun searchStop(@Field("id") id: Int): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/search/delete")
    suspend fun searchDelete(@Field("id") id: Int): Response<ResponseBody>

    @GET("api/v2/search/results")
    suspend fun searchResults(
        @Query("id") id: Int,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 500,
    ): SearchResults

    @GET("api/v2/search/plugins")
    suspend fun searchPlugins(): List<SearchPlugin>

    @FormUrlEncoded
    @POST("api/v2/search/enablePlugin")
    suspend fun searchEnablePlugin(
        @Field("names") names: String,
        @Field("enable") enable: Boolean,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/search/installPlugin")
    suspend fun searchInstallPlugin(@Field("sources") sources: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST("api/v2/search/uninstallPlugin")
    suspend fun searchUninstallPlugin(@Field("names") names: String): Response<ResponseBody>

    @POST("api/v2/search/updatePlugins")
    suspend fun searchUpdatePlugins(): Response<ResponseBody>
}
