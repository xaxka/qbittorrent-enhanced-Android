package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentTrackersBinding
import kotlinx.coroutines.launch

/**
 * Trackers of the torrent: add via the button, remove by long-pressing an entry.
 */
class TrackersFragment : Fragment() {

    private var _binding: FragmentTrackersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by activityViewModels {
        DetailViewModel.factory(requireActivity().application, detailHash)
    }

    private val detailHash: String
        get() = (activity as? DetailActivity)?.torrentHash ?: ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTrackersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.trackersList.layoutManager = LinearLayoutManager(requireContext())
        val adapter = TrackersAdapter(onLongClick = { tracker -> confirmRemove(tracker) })
        binding.trackersList.adapter = adapter

        binding.addTrackerButton.setOnClickListener { showAddDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.trackers)
                    binding.emptyView.visibility =
                        if (state.trackers.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        input?.setHint(R.string.add_tracker_hint)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_tracker_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val url = input?.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty()) viewModel.addTracker(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(tracker: io.github.xixka.qbittorrent.model.Tracker) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_tracker_title)
            .setMessage(tracker.url)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.removeTracker(tracker.url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
