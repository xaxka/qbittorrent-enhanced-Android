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
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentFilesBinding
import kotlinx.coroutines.launch

/**
 * Content files of the torrent; tapping a file opens the priority picker
 * (skip / normal / high / maximum) mapped to /api/v2/torrents/filePrio.
 */
class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
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
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.filesList.layoutManager = LinearLayoutManager(requireContext())
        val adapter = FilesAdapter { file -> showPriorityDialog(file.index, file.priority) }
        binding.filesList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.files)
                    binding.emptyView.visibility =
                        if (state.files.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun showPriorityDialog(index: Int, current: Int) {
        val options = listOf(
            R.string.priority_skip to 0,
            R.string.priority_normal to 1,
            R.string.priority_high to 6,
            R.string.priority_maximum to 7,
        )
        val labels = options.map { getString(it.first) }.toTypedArray()
        val checked = options.indexOfFirst { it.second == current }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.priority_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setFilePriority(listOf(index), options[which].second)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
