package com.openclaw.tv.feature.home

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.openclaw.tv.core.capability.CapabilityDetector
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel by viewModels<HomeViewModel>()
    private val adapter = AppRailAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = view.findViewById<TextView>(R.id.hero_title)
        val subtitle = view.findViewById<TextView>(R.id.assistant_status)
        val capabilityBadge = view.findViewById<TextView>(R.id.capability_badge)
        val primaryAction = view.findViewById<Button>(R.id.primary_action)
        val appRail = view.findViewById<RecyclerView>(R.id.app_rail)

        appRail.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        appRail.adapter = adapter

        val detector = CapabilityDetector(requireContext())
        viewModel.bindCapabilities(
            detector.snapshot(
                targetPackages = listOf(
                    "com.google.android.youtube.tv",
                    "com.netflix.ninja",
                    "com.amazon.amazonvideo.livingroom",
                    "com.disney.disneyplus",
                    "com.spotify.tv.android",
                ),
            ),
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    title.text = state.title
                    subtitle.text = state.subtitle
                    capabilityBadge.text = state.capabilityLabel
                    primaryAction.text = state.primaryActionLabel
                    adapter.submitList(state.featuredApps)
                }
            }
        }
    }
}
