package io.github.xixka.qbittorrent.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.ActivityDetailBinding
import kotlinx.coroutines.launch

/**
 * Torrent detail screen: swipeable pages Overview / Files / Trackers / Peers
 * (LibreTorrent detail tabs), plus a toolbar with torrent actions.
 */
class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    val torrentHash: String get() = intent?.getStringExtra(EXTRA_HASH) ?: ""

    private val viewModel: DetailViewModel by viewModels {
        DetailViewModel.factory(application, torrentHash)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent?.getStringExtra(EXTRA_NAME) ?: torrentHash

        binding.detailPager.adapter = DetailPagerAdapter(this)
        TabLayoutMediator(binding.detailTabs, binding.detailPager) { tab, position ->
            tab.setText(
                when (position) {
                    0 -> R.string.detail_tab_overview
                    1 -> R.string.detail_tab_files
                    2 -> R.string.detail_tab_trackers
                    else -> R.string.detail_tab_peers
                }
            )
        }.attach()

        binding.detailTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // keep the tab data fresh whenever the visible page changes
        binding.detailPager.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    viewModel.setTab(position)
                }
            }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ---------------- toolbar actions ----------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_pause -> {
            viewModel.pause(); true
        }

        R.id.action_resume -> {
            viewModel.resume(); true
        }

        R.id.action_recheck -> {
            viewModel.recheck(); true
        }

        R.id.action_reannounce -> {
            viewModel.reannounce(); true
        }

        R.id.action_sequential -> {
            viewModel.toggleSequential(); true
        }

        R.id.action_super_seeding -> {
            viewModel.toggleSuperSeeding(); true
        }

        R.id.action_delete -> {
            confirmDelete()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmDelete() {
        val view = layoutInflater.inflate(R.layout.dialog_delete, null)
        val deleteFiles =
            view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.delete_files)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    viewModel.delete(deleteFiles.isChecked) { }
                    finish()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private class DetailPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> OverviewFragment()
            1 -> FilesFragment()
            2 -> TrackersFragment()
            else -> PeersFragment()
        }
    }

    companion object {
        private const val EXTRA_HASH = "hash"
        private const val EXTRA_NAME = "name"

        fun start(context: Context, hash: String, name: String?) {
            context.startActivity(
                Intent(context, DetailActivity::class.java)
                    .putExtra(EXTRA_HASH, hash)
                    .putExtra(EXTRA_NAME, name)
            )
        }
    }
}
