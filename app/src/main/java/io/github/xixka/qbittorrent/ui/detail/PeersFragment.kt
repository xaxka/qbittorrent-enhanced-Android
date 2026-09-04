package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.xixka.qbittorrent.databinding.FragmentPeersBinding
import kotlinx.coroutines.launch

/**
 * Peers tab, ported from LibreTorrent's DetailPeersFragment (GPL-3.0).
 */
class PeersFragment : Fragment() {

    private var _binding: FragmentPeersBinding? = null
    private val binding get() = _binding!!

    // Resolve the shared state through the host activity so the
    // hash-carrying factory is always used (see DetailActivity.detailViewModel).
    private val viewModel: DetailViewModel
        get() = (requireActivity() as DetailActivity).detailViewModel
    private val adapter = PeersAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPeersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.peerList.layoutManager = LinearLayoutManager(requireContext())
        binding.peerList.adapter = adapter
        binding.peerList.setEmptyView(binding.emptyViewPeerList)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> adapter.submitList(state.peers) }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
