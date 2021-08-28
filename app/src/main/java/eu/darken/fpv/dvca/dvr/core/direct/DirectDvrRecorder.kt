package eu.darken.fpv.dvca.dvr.core.direct

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.androidstarter.common.logging.i
import eu.darken.fpv.dvca.App
import eu.darken.fpv.dvca.dvr.core.DvrRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.Sink
import okio.sink
import javax.inject.Inject

class DirectDvrRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) : DvrRecorder {

    override fun record(storagePath: Uri): DvrRecorder.Session {
        i(TAG) { "Starting direct recording to $storagePath" }
        val recordingStart = System.currentTimeMillis()

        val outputStream = context.contentResolver.openOutputStream(storagePath)!!

        return object : DvrRecorder.Session {
            override val sink: Sink = outputStream.sink()

            override val stats: Flow<DvrRecorder.Session.Stats> = flow {
                while (true) {
                    emit(
                        DvrRecorder.Session.Stats(
                            length = (System.currentTimeMillis() - recordingStart) / 1000L,
                            size = 0L
                        )
                    )
                    delay(1000)
                }
            }

            override fun cancel() {
                i(TAG) { "Canceling session" }
                outputStream.close()
            }
        }
    }

    companion object {
        private val TAG = App.logTag("DVR", "Recorder", "Direct")
    }
}