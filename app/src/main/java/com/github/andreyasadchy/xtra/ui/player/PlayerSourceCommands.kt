package com.github.andreyasadchy.xtra.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

/** Only the latest request from the still-attached controller may reject its UI. */
@OptIn(UnstableApi::class)
internal class PlayerSourceCommands(
    private val isCurrent: (MediaController) -> Boolean,
    private val rejected: (String, Int) -> Unit,
    private val executor: Executor = Handler(Looper.getMainLooper()).let { handler -> Executor { handler.post(it) } },
) {
    private var generation = 0
    private var pending: ListenableFuture<SessionResult>? = null

    fun send(controller: MediaController, command: SessionCommand) {
        cancel()
        val request = generation
        val result = try {
            controller.sendCustomCommand(command, Bundle.EMPTY)
        } catch (error: Exception) {
            Futures.immediateFailedFuture<SessionResult>(error)
        }
        pending = result
        result.addListener({
            if (pending === result) pending = null
            if (request == generation && isCurrent(controller)) {
                val code = runCatching { result.get().resultCode }.getOrDefault(SessionError.ERROR_UNKNOWN)
                if (code != SessionResult.RESULT_SUCCESS) rejected(command.customAction, code)
            }
        }, executor)
    }

    fun cancel() {
        generation++
        pending?.cancel(false)
        pending = null
    }
}
