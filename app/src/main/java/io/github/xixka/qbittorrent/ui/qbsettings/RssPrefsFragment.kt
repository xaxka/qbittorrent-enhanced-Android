package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.gson.JsonObject
import io.github.xixka.qbittorrent.databinding.FragmentQbPrefsRssBinding
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.bool
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.int
import io.github.xixka.qbittorrent.ui.qbsettings.QBPrefBindings.str

/**
 * RSS tab — the WebUI Options "RSS" page: feed refresh/fetch settings and the
 * auto-downloader options (episode matching, smart filters).
 */
class RssPrefsFragment : QBPrefsTabFragment() {

    private var _binding: FragmentQbPrefsRssBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQbPrefsRssBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun populate(prefs: JsonObject) {
        binding.rssRefreshIntervalInput.setText(int(prefs, "rss_refresh_interval", 600).toString())
        binding.rssFetchDelayInput.setText(int(prefs, "rss_fetch_delay", 600).toString())
        binding.rssMaxArticlesInput.setText(int(prefs, "rss_max_articles_per_feed", 50).toString())
        binding.rssProcessingSwitch.isChecked = bool(prefs, "rss_processing_enabled", true)
        binding.rssAutoDownloadSwitch.isChecked = bool(prefs, "rss_auto_downloading_enabled", true)
        binding.rssRepackSwitch.isChecked =
            bool(prefs, "rss_download_repack_proper_episodes", true)
        binding.rssSmartFiltersInput.setText(str(prefs, "rss_smart_episode_filters"))
    }

    override fun collectValues(out: JsonObject) {
        binding.rssRefreshIntervalInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..9999999 }?.let { out.put("rss_refresh_interval", it) }
        binding.rssFetchDelayInput.text?.toString()?.trim()?.toLongOrNull()
            ?.takeIf { it in 0..9999999L }?.let { out.put("rss_fetch_delay", it) }
        binding.rssMaxArticlesInput.text?.toString()?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..9999 }?.let { out.put("rss_max_articles_per_feed", it) }
        out.put("rss_processing_enabled", binding.rssProcessingSwitch.isChecked)
        out.put("rss_auto_downloading_enabled", binding.rssAutoDownloadSwitch.isChecked)
        out.put("rss_download_repack_proper_episodes", binding.rssRepackSwitch.isChecked)
        out.put(
            "rss_smart_episode_filters",
            binding.rssSmartFiltersInput.text?.toString()?.trim().orEmpty(),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
