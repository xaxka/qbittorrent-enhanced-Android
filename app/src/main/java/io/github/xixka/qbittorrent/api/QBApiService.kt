package io.github.xixka.qbittorrent.api

import io.github.xixka.qbittorrent.model.PeerSyncResponse
import io.github.xixka.qbittorrent.model.QBCategory
import io.github.xixka.qbittorrent.model.TorrentFile
import io.github.xixka.qbittorrent.model.TorrentInfo
import io.github.xixka.qbittorrent.model.TorrentProperties
import io.github.xixka.qbittorrent.model.Tracker
import io.github.xixka.qbittorrent.model.TransferInfo
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
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
    suspend fun addTorrent(
        @Field("urls") urls: String? = null,
        @Field("savepath") savePath: String? = null,
        @Field("category") category: String? = null,
        @Field("paused") paused: String? = null,
        @Field("sequentialDownload") sequential: String? = null,
        @Field("root_folder") rootFolder: String? = null,
    ): Response<ResponseBody>

    @Multipart
    @POST("api/v2/torrents/add")
    suspend fun addTorrentFile(
        @Part torrents: MultipartBody.Part,
        @Part("savepath") savePath: okhttp3.RequestBody? = null,
        @Part("category") category: okhttp3.RequestBody? = null,
        @Part("paused") paused: okhttp3.RequestBody? = null,
        @Part("sequentialDownload") sequential: okhttp3.RequestBody? = null,
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
}
