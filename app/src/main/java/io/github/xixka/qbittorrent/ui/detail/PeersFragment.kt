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
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentPeersBinding
import kotlinx.coroutines.launch

/**
 * Connected peers: endpoint, client, relevance progress and speeds.
 */
class PeersFragment : Fragment() {

    private var _binding: FragmentPeersBinding? = null
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
        _binding = FragmentPeersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.peersList.layoutManager = LinearLayoutManager(requireContext())
        val adapter = PeersAdapter()
        binding.peersList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.peers)
                    binding.emptyView.visibility =
                        if (state.peers.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
