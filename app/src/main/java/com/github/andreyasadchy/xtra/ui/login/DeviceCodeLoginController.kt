package com.github.andreyasadchy.xtra.ui.login

import com.github.andreyasadchy.xtra.model.id.DeviceCodeResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** Manual approval checks only. No background polling or token/code persistence. */
internal class DeviceCodeLoginController(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val request: suspend () -> DeviceCodeResponse,
    private val check: suspend (String) -> String?,
    private val authorized: (String) -> Unit,
    private val changed: (State) -> Unit,
) {
    sealed interface State {
        data object Loading : State
        data object Error : State
        data object Expired : State
        data class Ready(val userCode: String, val url: String, val seconds: Long,
                         val checking: Boolean, val canCheck: Boolean, val status: Status) : State
    }
    enum class Status { APPROVE, PENDING, CONNECTION_ERROR }
    class Rejected : Exception()
    private class Session(val deviceCode: String, val userCode: String, val url: String,
                          val expiresAt: Long, val intervalMs: Long)
    private var session: Session? = null
    private var work: Job? = null
    private var clock: Job? = null
    private var generation = 0
    private var active = false
    private var disposed = false
    private var checking = false
    private var nextCheckAt = 0L
    private var status = Status.APPROVE

    fun requestCode() {
        if (disposed) return
        val requestGeneration = ++generation
        work?.cancel()
        clock?.cancel()
        session = null
        checking = false
        changed(State.Loading)
        work = scope.launch {
            try {
                val response = request()
                if (generation != requestGeneration || disposed) return@launch
                val deviceCode = requireNotNull(response.deviceCode).takeIf { it.isNotBlank() } ?: error("Missing device code")
                val userCode = requireNotNull(response.userCode).trim()
                require(userCode.matches(Regex("[A-Za-z0-9-]{1,32}")) && response.expiresIn > 0)
                session = Session(deviceCode, userCode, activationUrl(userCode),
                    now() + response.expiresIn.coerceAtMost(3600) * 1000L,
                    response.interval.coerceIn(5, 60) * 1000L)
                nextCheckAt = 0
                status = Status.APPROVE
                render()
                startClock()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == requestGeneration && !disposed) changed(State.Error)
            }
        }
    }

    fun checkApproval() {
        val current = session ?: return
        if (disposed || checking || now() < nextCheckAt) return
        if (now() >= current.expiresAt) return expire()
        val requestGeneration = generation
        checking = true
        nextCheckAt = now() + current.intervalMs
        render()
        work = scope.launch {
            try {
                val token = check(current.deviceCode)
                if (disposed || generation != requestGeneration || session !== current) return@launch
                if (now() >= current.expiresAt) return@launch expire()
                if (!token.isNullOrBlank()) {
                    session = null
                    clock?.cancel()
                    authorized(token)
                } else status = Status.PENDING
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Rejected) {
                if (session === current) expire()
            } catch (_: Exception) {
                if (session === current) status = Status.CONNECTION_ERROR
            } finally {
                if (!disposed && generation == requestGeneration && session === current) {
                    checking = false
                    render()
                }
            }
        }
    }

    fun resume() { active = true; render(); startClock() }
    fun pause() { active = false; clock?.cancel(); clock = null }
    fun dispose() {
        disposed = true
        generation++
        session = null
        work?.cancel()
        pause()
    }

    private fun startClock() {
        if (!active || session == null || clock?.isActive == true) return
        clock = scope.launch {
            while (session != null) { delay(1000); render() }
        }
    }

    private fun render() {
        val current = session ?: return
        val remaining = current.expiresAt - now()
        if (remaining <= 0) return expire()
        changed(State.Ready(current.userCode, current.url, (remaining + 999) / 1000,
            checking, !checking && now() >= nextCheckAt, status))
    }

    private fun expire() {
        session = null
        work?.cancel()
        clock?.cancel()
        checking = false
        changed(State.Expired)
    }

    companion object {
        // Construct the documented Twitch activation route locally; never render
        // a server-controlled URL or send the device code to a QR image service.
        fun activationUrl(userCode: String) = "https://www.twitch.tv/activate?public=true&device-code=" +
            URLEncoder.encode(userCode, "UTF-8")
    }
}
