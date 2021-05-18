package eu.darken.fpv.dvca.videofeed.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.fpv.dvca.App
import eu.darken.fpv.dvca.R
import eu.darken.fpv.dvca.common.BuildConfigWrap
import eu.darken.fpv.dvca.common.hideSystemUI
import eu.darken.fpv.dvca.common.observe2
import eu.darken.fpv.dvca.common.showSystemUI
import eu.darken.fpv.dvca.common.smart.SmartFragment
import eu.darken.fpv.dvca.common.viewbinding.viewBindingLazy
import eu.darken.fpv.dvca.databinding.VideofeedFragmentBinding
import eu.darken.fpv.dvca.gear.GearManager
import eu.darken.fpv.dvca.gear.goggles.Goggles
import eu.darken.fpv.dvca.videofeed.core.FPVFeedPlayer
import eu.darken.fpv.dvca.videofeed.core.RenderInfo
import javax.inject.Inject


@AndroidEntryPoint
class VideoFeedFragment : SmartFragment(R.layout.videofeed_fragment) {

    private val vm: VideoFeedVM by viewModels()
    private val binding: VideofeedFragmentBinding by viewBindingLazy()

    @Inject lateinit var feedPlayer: FPVFeedPlayer
    @Inject lateinit var gearManager: GearManager

    private var reconnectToast: Snackbar? = null

    private val versionTag: String by lazy {
        "DVCA ${BuildConfigWrap.VERSION_NAME}(${BuildConfigWrap.GITSHA})"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isInLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isInLandscape) enterImmersive()

        binding.apply {
            root.setOnClickListener {
                if (toolbar.isGone) exitImmersive() else enterImmersive()
            }
        }

        // Player 1
        binding.apply {
            player1Placeholder.text = getString(R.string.video_feed_player_tease, "1")
        }

        // Player 2
        binding.apply {
            player2Container.isGone = isInLandscape

            player2Placeholder.text = getString(R.string.video_feed_player_tease, "2")
        }

        vm.feedAvailability.observe2(this) { feed ->
            if (feed != null) {
                reconnectToast?.dismiss()
                reconnectToast = null

                feedPlayer.start(
                    feed = feed,
                    surfaceView = binding.player1Canvas,
                    renderInfoListener = { info -> updateMetaData(info, feed) }
                )
            } else {
                feedPlayer.stop()

                reconnectToast?.dismiss()
                Snackbar.make(
                    requireView(),
                    getString(R.string.status_message_waiting_for_device),
                    Snackbar.LENGTH_INDEFINITE
                ).also {
                    reconnectToast = it
                }.show()
            }
            binding.player1Placeholder.isGone = feed != null
            binding.player1Canvas.isInvisible = feed == null
            binding.player1Metadata.isInvisible = feed == null
        }
    }

    private fun enterImmersive() {
        binding.apply {
            toolbar.isGone = true
            hideSystemUI(root)
        }
    }

    private fun exitImmersive() {
        binding.apply {
            toolbar.isGone = false
            showSystemUI(root)
        }
    }

    private fun updateMetaData(info: RenderInfo, feed: Goggles.VideoFeed) {
        val sb = StringBuilder(versionTag)
        sb.append(" ")
        sb.append(info.toString())
        sb.append(" [MB/s USB ${feed.videoUsbReadMbs} | Buffer ${feed.videoBufferReadMbs}]")

        binding.player1Metadata.text = sb.toString()
    }

    override fun onDestroyView() {
        feedPlayer.stop()
        super.onDestroyView()
    }

    companion object {
        private val TAG = App.logTag("VideoFeed", "Fragment")
    }
}
