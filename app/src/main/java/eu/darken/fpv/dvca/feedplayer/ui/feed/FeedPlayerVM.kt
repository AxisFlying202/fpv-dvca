package eu.darken.fpv.dvca.feedplayer.ui.feed

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.view.View
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.androidstarter.common.logging.i
import eu.darken.androidstarter.common.logging.w
import eu.darken.fpv.dvca.App
import eu.darken.fpv.dvca.common.flow.combine
import eu.darken.fpv.dvca.common.viewmodel.SmartVM
import eu.darken.fpv.dvca.dvr.GeneralDvrSettings
import eu.darken.fpv.dvca.dvr.core.DvrController
import eu.darken.fpv.dvca.feedplayer.core.FeedPlayerSettings
import eu.darken.fpv.dvca.gear.GearManager
import eu.darken.fpv.dvca.gear.goggles.Goggles
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.lang.Exception
import javax.inject.Inject

@HiltViewModel
class FeedPlayerVM @Inject constructor(
    private val handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val gearManager: GearManager,
    private val dvrSettings: GeneralDvrSettings,
    private val feedPlayerSettings: FeedPlayerSettings,
    private val dvrController: DvrController,
) : SmartVM() {

    private val goggles = gearManager.availableGear
        .map { it.filterIsInstance<Goggles>() }

    private val goggle1 = goggles
        .map { gears ->
            gears.minByOrNull { it.firstSeenAt }
        }

    private val goggle1Feed: Flow<Goggles.VideoFeed?> = goggle1
        .flatMapLatest { goggles1 ->
            if (goggles1 == null) {
                Timber.tag(TAG).d("Goggle 1 unavailable")
                flowOf(null)
            } else {
                Timber.tag(TAG).d("Goggle 1 available: %s", goggles1.logId)
                goggles1.videoFeed
            }
        }
        .onEach { Timber.tag(TAG).d("Videofeed 1: %s", it) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val video1 = goggle1Feed.asLiveData2()

    val dvr1 = combine(goggle1, dvrController.recordings) { g1, recordings ->
        recordings.singleOrNull { it.goggle == g1 }
    }
        .flatMapLatest {
            it?.stats ?: flowOf(null)
        }
        .asLiveData2()

    private val goggle2 = goggles
        .map { gears ->
            if (gears.size < 2) {
                null
            } else {
                gears.maxByOrNull { it.firstSeenAt }
            }
        }

    private val google2Feed = goggle2
        .flatMapLatest { goggles2 ->
            if (goggles2 == null) {
                Timber.tag(TAG).d("Goggle 2 unavailable")
                flowOf(null)
            } else {
                Timber.tag(TAG).d("Goggle 2 available: %s", goggles2.logId)
                goggles2.videoFeed
            }
        }
        .onEach { Timber.tag(TAG).d("Videofeed 2: %s", it) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val video2 = google2Feed.asLiveData2()

    val dvr2 = combine(goggle2, dvrController.recordings) { g2, recordings ->
        recordings.singleOrNull { it.goggle == g2 }
    }
        .flatMapLatest {
            it?.stats ?: flowOf(null)
        }
        .asLiveData2()

    val isMultiplayerInLandscapeAllowed: Boolean
        get() = feedPlayerSettings.isLandscapeMultiplayerEnabled.value

    //jones val dvrStoragePathEvent = SingleLiveEvent<Unit>()

    private var outStandingToggle = 0
    fun onPlayer1RecordToggle() = launch {
        /* jones
        if (pathSetup()) {
            outStandingToggle = 1
            return@launch
        }
        */

        var path = getSDCardFolder(context)
        dvrSettings.dvrStoragePath.update { Uri.parse(path) }
        val goggle = goggle1.first()
        if (goggle == null) {
            w(TAG) { "Can't start Goggle 1 DVR, was null!" }
            return@launch
        } else {
            i(TAG) { "onPlayer1RecordToggle(): goggle=$goggle" }
        }

        val recording = dvrController.toggle(goggle)
    }

    fun onPlayer2RecordToggle() = launch {
        if (pathSetup()) {
            outStandingToggle = 2
            return@launch
        }

        val goggle = goggle2.first()
        if (goggle == null) {
            w(TAG) { "Can't start Goggle 2 DVR, was null!" }
            return@launch
        } else {
            i(TAG) { "onPlayer2RecordToggle(): goggle=$goggle" }
        }

        val recording = dvrController.toggle(goggle)
    }

    fun getDefaultFolder(context: Context): Uri? {
        var defaultPath = context.getExternalFilesDir("record").toString()
        return  Uri.parse(defaultPath)
    }

    fun getSDCardFolder(context: Context): String? {
        var sdcardMoviesPath = ""
        val mStorageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        try {
            val storeManagerClazz = Class.forName("android.os.storage.StorageManager")
            val getVolumesMethod = storeManagerClazz.getMethod("getVolumes")
            val volumeInfos = getVolumesMethod.invoke(mStorageManager) as List<*> //获取到了VolumeInfo的列表
            val volumeInfoClazz = Class.forName("android.os.storage.VolumeInfo")
            val VolumeInfo_getFsUuid = volumeInfoClazz.getMethod("getFsUuid")
            val VolumeInfo_GetDisk = volumeInfoClazz.getMethod("getDisk")
            val pathField = volumeInfoClazz.getDeclaredField("path")
            val diskInfoClaszz = Class.forName("android.os.storage.DiskInfo")
            val DiskInfo_IsUsb = diskInfoClaszz.getMethod("isUsb")
            val DiskInfo_IsSd = diskInfoClaszz.getMethod("isSd")
            if (volumeInfos != null) {
                for (volumeInfo in volumeInfos) {
                    val uuid = VolumeInfo_getFsUuid.invoke(volumeInfo)
                    if (uuid != null) {
                        val pathString = pathField[volumeInfo] as String
                        val diskInfo = VolumeInfo_GetDisk.invoke(volumeInfo) ?: continue
                        val isSd = DiskInfo_IsSd.invoke(diskInfo) as Boolean
                        if (isSd) {
                            sdcardMoviesPath = pathString
                        }
                    }
                }
            }
        } catch (e: Exception) {
            i(TAG) { "getVolumes error $e"}
        }
        if (sdcardMoviesPath !== "") {
            sdcardMoviesPath += "/" + Environment.DIRECTORY_MOVIES
        }
        i(TAG) {"sdcardMoviesPath = $sdcardMoviesPath"}
        return sdcardMoviesPath;
    }

    private fun pathSetup(): Boolean {
        if (dvrSettings.dvrStoragePath.value == null) {
        //jones    dvrStoragePathEvent.postValue(Unit)
            return true
        }
        return false
    }

    fun onStoragePathSelected(path: Uri) = launch {
        context.contentResolver.takePersistableUriPermission(
            path,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        dvrSettings.dvrStoragePath.update { path }
        val toToggle = outStandingToggle
        outStandingToggle = 0
        when (toToggle) {
            1 -> onPlayer1RecordToggle()
            2 -> onPlayer2RecordToggle()
        }
    }

    companion object {
        private val TAG = App.logTag("VideoFeed", "VM")
    }
}