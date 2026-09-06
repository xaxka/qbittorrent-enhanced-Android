package io.github.xixka.qbittorrent.model

/**
 * Tracks engine capabilities that change the meaning of returned values.
 *
 * qBittorrent < 5.1 reports torrent-file and peer progress as 0..1, while
 * newer releases report 0..100. The flag is flipped by whatever parses the
 * engine's /app/version response (TorrentListViewModel), so the progress
 * accessors in [Models.kt] can disambiguate a raw value of exactly 1.0
 * (100% on the old scale vs 1% on the new one).
 */
object EngineScale {
    @Volatile
    var progressIsPercent: Boolean = false
}
