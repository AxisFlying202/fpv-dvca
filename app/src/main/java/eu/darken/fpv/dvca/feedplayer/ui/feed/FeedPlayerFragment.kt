package eu.darken.fpv.dvca.feedplayer.ui.feed

import android.content.ContentResolver
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.fpv.dvca.App
import eu.darken.fpv.dvca.R
import eu.darken.fpv.dvca.common.BuildConfigWrap
import eu.darken.fpv.dvca.common.hideSystemUI
import eu.darken.fpv.dvca.common.navigation.doNavigate
import eu.darken.fpv.dvca.common.observe2
import eu.darken.fpv.dvca.common.smart.SmartFragment
import eu.darken.fpv.dvca.common.viewbinding.viewBindingLazy
import eu.darken.fpv.dvca.databinding.VideofeedFragmentBinding
import eu.darken.fpv.dvca.feedplayer.core.exo.ExoFeedPlayer
import eu.darken.fpv.dvca.feedplayer.core.exo.RenderInfo
import eu.darken.fpv.dvca.gear.goggles.Goggles
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import eu.darken.androidstarter.common.logging.i
import eu.darken.androidstarter.common.logging.e
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.Toast
import androidx.annotation.RequiresApi
import android.os.StrictMode
import android.os.StrictMode.VmPolicy


@AndroidEntryPoint
class FeedPlayerFragment : SmartFragment(R.layout.videofeed_fragment) {

    private val vm: FeedPlayerVM by viewModels()
    private val binding: VideofeedFragmentBinding by viewBindingLazy()

    @Inject lateinit var exoPlayer1: ExoFeedPlayer
    @Inject lateinit var exoPlayer2: ExoFeedPlayer

    private var latestVideoFile = ""
    private val TARGET_SIZE_MICRO_THUMBNAIL = 96

    private val versionTag: String by lazy {
        "DVCA ${BuildConfigWrap.VERSION_NAME}(${BuildConfigWrap.GITSHA})"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isInLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        //jones if (isInLandscape) enterImmersive()
        hideSystemUI(view) //jones
        updateThumbnail() //jones

        binding.apply {
            toolbar.alpha = 0.6f
            settingsButton.setOnClickListener(){
                doNavigate(FeedPlayerFragmentDirections.actionVideoFeedFragmentToSettingsFragment())
            }

            thumbnail.setOnClickListener(){
                val builder = VmPolicy.Builder()
                StrictMode.setVmPolicy(builder.build())

                updateThumbnail()
                if (latestVideoFile.isEmpty()) {
                    Toast.makeText(context, getText(R.string.no_dvr_sdcard), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val intent = Intent()
                intent.action = Intent.ACTION_VIEW
                val file = File(latestVideoFile)
                intent.setDataAndType(Uri.fromFile(file), "video/*")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            root.setOnClickListener {
                if(toolbar.visibility == View.VISIBLE) {
                    binding.apply {
                        toolbar.visibility = View.GONE;
                    }
                }else{
                    binding.apply {
                        toolbar.visibility = View.VISIBLE;
                    }

                }
            }
        /*
            root.setOnClickListener {
                if (toolbar.isGone) exitImmersive() else enterImmersive()
            }
            toolbar.inflateMenu(R.menu.feedplayer_menu)
            toolbar.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.vrmode -> {
                        doNavigate(FeedPlayerFragmentDirections.actionVideoFeedFragmentToVrFragment())
                        true
                    }
                    R.id.settings -> {
                        doNavigate(FeedPlayerFragmentDirections.actionVideoFeedFragmentToSettingsFragment())
                        true
                    }
                    R.id.info -> {
                        doNavigate(FeedPlayerFragmentDirections.actionVideoFeedFragmentToInfoFragment())
                        true
                    }
                    R.id.donate -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/tydarken"))
                            .run { startActivity(this) }
                        true
                    }
                    else -> super.onOptionsItemSelected(it)
                }
            }

            playerContainer.orientation = when (isInLandscape) {
                true -> LinearLayout.HORIZONTAL
                false -> LinearLayout.VERTICAL
            }
         */
        }

        // Player 1
        binding.apply {
            player1Placeholder.text = getString(R.string.waiting_for_usb_device)
            player1DvrRecord.setOnClickListener {
                if (context?.let { it1 -> vm.getSDCardFolder(it1)} === "") {
                    Toast.makeText(context, getText(R.string.no_dvr_sdcard), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (player1Placeholder.isGone == false) {
                    Toast.makeText(context, getText(R.string.waiting_for_video), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                vm.onPlayer1RecordToggle()
            }

            vm.video1.observe2(this@FeedPlayerFragment) { feed ->
                Timber.tag(TAG).d("google1Feed.observe2(): %s", feed)
                if (feed != null) {
                    i(TAG) {"jones exoPlayer1 start"}
                    exoPlayer1.start(
                        feed = feed,
                        surfaceView = player1Canvas,
                        renderInfoListener = { info ->
                            if (!isResumed) {
                                Timber.tag(TAG).v("View was null?")
                                return@start
                            }
                            val isRunning = feed.source.isRunning
                            i(TAG) {"jones running is $isRunning"}
                            /* jones
                            val metadataInfo = createMetaDataDesc(info, feed)
                            binding.player1Metadata.text = createMetaDataDesc(info, feed)
                             */
                        }
                    )
                } else {
                    i(TAG) {"jones exoPlayer1 stop"}
                    exoPlayer1.stop()
                }
                player1Image.isGone = feed != null
                player1Placeholder.isGone = feed != null
                player1Canvas.isInvisible = feed == null
                //jones player1Metadata.isInvisible = feed == null
                player1Dvr.isGone = false
            }
            vm.dvr1.observe2(this@FeedPlayerFragment) { stats ->
                player1DvrRecord.setImageResource(
                    if (stats != null) R.drawable.stop else R.drawable.record
                )
                if(stats == null){
                    updateThumbnail()
                }
                player1DvrInfo.isGone = stats == null
                player1DvrInfo.text = stats?.let {
                    val seconds = it.length.inWholeSeconds
                    String.format("%02dm %02ds", (seconds % 3600) / 60, (seconds % 60))
                }
            }
        }
/*
        // Player 2
        binding.apply {
            player2Container.isGone = isInLandscape && !vm.isMultiplayerInLandscapeAllowed
            player2Placeholder.text = getString(R.string.video_feed_player_tease, "2")
            player2DvrRecord.setOnClickListener { vm.onPlayer2RecordToggle() }

            if (isInLandscape && !vm.isMultiplayerInLandscapeAllowed) {
                exoPlayer2.stop()
                return@apply
            }

            vm.video2.observe2(this@FeedPlayerFragment) { feed ->
                Timber.tag(TAG).d("google2Feed.observe2(): %s", feed)
                if (feed != null) {
                    exoPlayer2.start(
                        feed = feed,
                        surfaceView = player2Canvas,
                        renderInfoListener = { info ->
                            if (!isResumed) {
                                Timber.tag(TAG).v("View was null?")
                                return@start
                            }
                            binding.player2Metadata.text = createMetaDataDesc(info, feed)
                        }
                    )
                } else {
                    exoPlayer2.stop()
                }
                player2Placeholder.isGone = feed != null
                player2Canvas.isInvisible = feed == null
                player2Metadata.isInvisible = feed == null
                player2Dvr.isGone = feed == null
            }
            vm.dvr2.observe2(this@FeedPlayerFragment) { stats ->
                player2DvrRecord.setImageResource(
                    if (stats != null) R.drawable.ic_round_stop_24 else R.drawable.ic_video_file_24
                )
                player2DvrInfo.isGone = stats == null
                player2DvrInfo.text = stats?.let {
                    val seconds = it.length.inWholeSeconds
                    String.format("%02dm %02ds", (seconds % 3600) / 60, (seconds % 60))
                }
            }
        }

        val safLauncher = registerForActivityResult(SAFPathPickerContract()) {
            Timber.i("Selected storage uri: %s", it)
            if (it == null) return@registerForActivityResult

            vm.onStoragePathSelected(it)
        }
        vm.dvrStoragePathEvent.observe2(this) { safLauncher.launch(null) }
*/
    }
/* jones
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
*/

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateThumbnail() {
        if (context?.let { vm.getSDCardFolder(it).toString() } === "") {
            binding.apply {
                thumbnail.setImageBitmap(null)
            }
            latestVideoFile = ""
            return
        }

        try {
            var sdcardFileFolder = File(context?.let { vm.getSDCardFolder(it) })

            // Add a specific media item.
            val resolver: ContentResolver = context?.let { it.contentResolver }!!
            val PROJECTION = arrayOf(MediaStore.Video.VideoColumns._ID, MediaStore.Video.Media.DISPLAY_NAME)
            val SELECTION = MediaStore.Video.VideoColumns.DATA + " like ? "

            val selectionArgs = arrayOf("%" + sdcardFileFolder.getAbsolutePath() + "%")
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val orderBy = MediaStore.Video.VideoColumns._ID + " DESC"
            val cursor = resolver.query(uri, PROJECTION, SELECTION, selectionArgs, orderBy)
            var bitmap: Bitmap? = null
            if (cursor != null && cursor.moveToFirst()) {
                val videoId = cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media._ID))
                // it's the only & first thing in projection, so it is 0
                //long videoId = cursor.getLong(0);
                latestVideoFile = sdcardFileFolder.getAbsolutePath() + "/" +
                        cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME))
                bitmap = ThumbnailUtils.createVideoThumbnail(File(latestVideoFile), Size(48, 48), null)
            }

            if (bitmap == null) {
                binding.apply {
                    thumbnail.setImageBitmap(null)
                }
                latestVideoFile = ""
                return
            }
            i(TAG) { "update lastest video file $latestVideoFile to thumbnail" }

            binding.apply {
                thumbnail.setImageBitmap(bitmap)
            }
        }catch(e:Exception){
            e(TAG) { "updateThumbnail failed: $e" }
        }

    }

    private fun createMetaDataDesc(info: RenderInfo, feed: Goggles.VideoFeed): String {
        val sb = StringBuilder()
        sb.append("$versionTag @ ${feed.deviceIdentifier}\n")
        sb.append(info.toString())
        sb.append(" [USB ${feed.videoUsbReadMbs} | BUFFER ${feed.videoBufferReadMbs} MB/s ~ ${feed.usbReadMode}]")

        return sb.toString()
    }

    override fun onDestroyView() {
        exoPlayer1.stop()
        exoPlayer2.stop()
        super.onDestroyView()
    }

    companion object {
        private val TAG = App.logTag("VideoFeed", "Fragment")
    }
}
