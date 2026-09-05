package io.github.xixka.qbittorrent.ui.qbsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.xixka.qbittorrent.R
import io.github.xixka.qbittorrent.databinding.FragmentQbPrefsListBinding
import kotlinx.coroutines.launch

/**
 * One generated tab of the preferences editor: the rows of a single
 * [PrefSection] rendered by [QBPrefsAdapter]. All value state lives in the
 * shared activity-scoped [QBSettingsViewModel]; the fragment itself is a
 * pure view and can be recreated freely.
 */
class QBPrefsListFragment : Fragment() {

    private var _binding: FragmentQbPrefsListBinding? = null
    private val binding get() = _binding!!

    private val vm: QBSettingsViewModel by activityViewModels()

    private val sectionIndex: Int
        get() = requireArguments().getInt(ARG_SECTION, 0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQbPrefsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = QBPrefsAdapter(vm)
        binding.prefsList.layoutManager = LinearLayoutManager(requireContext())
        binding.prefsList.adapter = adapter
        // Scroll smoothness for the generated form: inflated TextInputLayout
        // rows are relatively heavy, so keep a wider off-screen view cache
        // (rows are then reused instead of created as they scroll in — the
        // "values pop out of nowhere" effect), and drop the item animator
        // whose cross-fade made rebound rows flash while scrolling.
        binding.prefsList.itemAnimator = null
        binding.prefsList.setItemViewCacheSize(12)
        viewLifecycleOwner.lifecycleScope.launch {
            vm.sections.collect { sections ->
                adapter.entries = sections.getOrNull(sectionIndex)?.entries ?: emptyList()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_SECTION = "section"

        fun newInstance(sectionIndex: Int): QBPrefsListFragment =
            QBPrefsListFragment().apply {
                arguments = bundleOf(ARG_SECTION to sectionIndex)
            }
    }
}
