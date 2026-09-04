package io.github.xixka.qbittorrent.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentPiecesBinding
import io.github.xixka.qbittorrent.util.Format
import kotlinx.coroutines.launch

/**
 * Piece-state visualization tab (qBitController parity): heatmap of
 * /torrents/pieceStates with a summary line fed by the torrent properties.
 */
class PiecesFragment : Fragment() {

    private var _binding: FragmentPiecesBinding? = null
    private val binding get() = _binding!!

    // Resolve the shared state through the host activity so the
    // hash-carrying factory is always used (see DetailActivity.detailViewModel).
    private val viewModel: DetailViewModel
        get() = (requireActivity() as DetailActivity).detailViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPiecesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: DetailUiState) {
        val pieces = state.pieceStates
        val props = state.properties
        val have = pieces.count { it == 2 }
        binding.piecesSummary.text = when {
            pieces.isEmpty() && props != null && props.piecesNum > 0 ->
                getString(
                    R.string.pieces_summary,
                    props.piecesHave.toString(),
                    props.piecesNum.toString(),
                    Format.size(props.pieceSize),
                )
            pieces.isEmpty() -> getString(R.string.pieces_unavailable)
            else -> getString(
                R.string.pieces_summary,
                have.toString(),
                pieces.size.toString(),
                if (props != null && props.pieceSize > 0) Format.size(props.pieceSize) else "—",
            )
        }
        binding.pieceHeatmap.submit(pieces)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
