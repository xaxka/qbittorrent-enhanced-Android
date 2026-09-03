package io.github.xixka.qbittorrent.ui.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ActivityDetailBinding
import io.github.xixka.qbittorrent.util.WindowInsetsSide
import io.github.xixka.qbittorrent.util.applyWindowInsets
import kotlinx.coroutines.launch

/**
 * Torrent details, ported from LibreTorrent's TorrentDetailsFragment:
 * toolbar with back navigation, scrollable tabs, ViewPager2 pages.
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val viewModel: DetailViewModel by viewModels {
        DetailViewModel.factory(application, intent.getStringExtra(EXTRA_HASH) ?: "")
    }

    private val title by lazy { intent.getStringExtra(EXTRA_NAME) ?: "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // keep list pages clear of the navigation bar / display cutouts
        applyWindowInsets(binding.viewPager, WindowInsetsSide.BOTTOM)

        // LibreTorrent-style: plain MaterialToolbar with app:menu, no setSupportActionBar
        binding.appBar.title = title

        binding.viewPager.adapter = DetailPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(TAB_TITLES[position])
        }.attach()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = viewModel.setTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.appBar.setNavigationOnClickListener { finishAfterTransition() }
        binding.appBar.setOnMenuItemClickListener { item -> onMenuItem(item.itemId) }
    }

    private fun onMenuItem(itemId: Int): Boolean = when (itemId) {
        R.id.pause_resume_torrent_menu -> {
            val paused = viewModel.state.value.info?.state?.lowercase() in
                setOf("pauseddl", "pausedup", "stoppeddl", "stoppedup")
            if (paused) viewModel.resume() else viewModel.pause()
            true
        }

        R.id.delete_torrent_menu -> {
            confirmDelete()
            true
        }

        R.id.force_recheck_torrent_menu -> {
            viewModel.recheck()
            true
        }

        R.id.force_announce_torrent_menu -> {
            viewModel.reannounce()
            true
        }

        R.id.share_magnet_menu -> {
            val hash = viewModel.hash
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "magnet:?xt=urn:btih:$hash")
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_magnet)))
            true
        }

        else -> false
    }

    private fun confirmDelete() {
        val view = layoutInflater.inflate(R.layout.dialog_delete_torrent, null)
        val deleteFiles = view.findViewById<MaterialCheckBox>(R.id.delete_with_downloaded_files)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.delete(deleteFiles.isChecked) { finish() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class DetailPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> InfoFragment()
            1 -> FilesFragment()
            2 -> TrackersFragment()
            else -> PeersFragment()
        }

        override fun getItemCount() = 4
    }

    companion object {
        private val TAB_TITLES = intArrayOf(
            R.string.tab_overview,
            R.string.tab_files,
            R.string.tab_trackers,
            R.string.tab_peers,
        )
        private const val EXTRA_HASH = "hash"
        private const val EXTRA_NAME = "name"

        fun start(context: Context, hash: String, name: String) {
            context.startActivity(
                Intent(context, DetailActivity::class.java)
                    .putExtra(EXTRA_HASH, hash)
                    .putExtra(EXTRA_NAME, name)
            )
        }
    }
}
