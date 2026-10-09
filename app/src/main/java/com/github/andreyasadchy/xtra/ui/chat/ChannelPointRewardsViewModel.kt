package com.github.andreyasadchy.xtra.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointRewardRedemptionResponse.Outcome
import com.github.andreyasadchy.xtra.model.ui.ChannelPointRewardCatalog
import com.github.andreyasadchy.xtra.model.ui.ChannelPointRewardOption
import com.github.andreyasadchy.xtra.repository.ChannelPointRewardsRepository
import com.github.andreyasadchy.xtra.util.C
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

internal data class ChannelPointRewardsSession(
    val accountId: String?, val channelId: String?, val channelLogin: String?,
    val networkLibrary: String?, val headers: Map<String, String>,
) {
    val canRedeem get() = !accountId.isNullOrBlank() && !channelId.isNullOrBlank() && !channelLogin.isNullOrBlank() && !headers[C.HEADER_TOKEN].isNullOrBlank() && !headers[C.HEADER_CLIENT_ID].isNullOrBlank()
    fun rewardKey(id: String) = "$accountId:$channelId:$id"
}

internal enum class RewardNotice { LOGIN, LOAD_FAILED, UNAVAILABLE, INPUT_REQUIRED, INSUFFICIENT, CHANGED, REJECTED, UNKNOWN, SUCCESS }
internal data class ChannelPointRewardsState(
    val catalog: ChannelPointRewardCatalog? = null,
    val selected: ChannelPointRewardOption? = null,
    val input: String = "",
    val busy: Boolean = false,
    val sending: Boolean = false,
    val notice: RewardNotice? = null,
    val blockedRewardIds: Set<String> = emptySet(),
)

@HiltViewModel
class ChannelPointRewardsViewModel @Inject constructor(
    private val repository: ChannelPointRewardsRepository,
    private val savedState: SavedStateHandle,
    private val sessions: ChannelPointRewardsSessionProvider,
) : ViewModel() {
    private val uncertain = savedState.get<ArrayList<String>>(UNCERTAIN)?.toMutableSet() ?: mutableSetOf()
    private val _state = MutableStateFlow(ChannelPointRewardsState())
    internal val state = _state.asStateFlow()
    private var session: ChannelPointRewardsSession? = null
    private var job: Job? = null
    private var revision = 0L

    internal fun open(newSession: ChannelPointRewardsSession) {
        if (session != newSession) {
            revision++
            job?.cancel()
            session = newSession
            _state.value = ChannelPointRewardsState()
        }
        if (!_state.value.busy && _state.value.catalog == null && _state.value.notice == null) refresh(newSession)
    }

    internal fun refresh(newSession: ChannelPointRewardsSession) {
        if (_state.value.busy) return
        session = newSession
        val requestRevision = ++revision
        if (!newSession.canRedeem) {
            _state.value = ChannelPointRewardsState(notice = RewardNotice.LOGIN)
            return
        }
        _state.value = _state.value.copy(busy = true, selected = null, input = "", notice = null)
        job = viewModelScope.launch {
            try {
                val catalog = load(newSession)
                if (session != newSession || revision != requestRevision) return@launch
                _state.value = ChannelPointRewardsState(
                    catalog = catalog,
                    notice = if (catalog == null) RewardNotice.LOAD_FAILED else null,
                    blockedRewardIds = blockedIds(newSession, catalog),
                )
            } catch (e: CancellationException) {
                if (session == newSession && revision == requestRevision) _state.value = _state.value.copy(busy = false)
                throw e
            } catch (e: Exception) {
                if (session == newSession && revision == requestRevision) _state.value = ChannelPointRewardsState(notice = RewardNotice.LOAD_FAILED)
            }
        }
    }

    internal fun select(reward: ChannelPointRewardOption) {
        val state = _state.value
        if (state.busy) return
        val catalog = state.catalog
        val current = catalog?.rewards?.find { it.id == reward.id }
        val notice = when {
            current == null || current != reward || !current.available -> RewardNotice.UNAVAILABLE
            reward.id in state.blockedRewardIds -> RewardNotice.UNKNOWN
            catalog == null || reward.cost > catalog.balance -> RewardNotice.INSUFFICIENT
            else -> null
        }
        _state.value = state.copy(selected = if (notice == null) reward else null, input = "", notice = notice)
    }

    internal fun inputChanged(text: String) {
        if (!_state.value.busy) _state.value = _state.value.copy(input = text.take(MAX_INPUT_LENGTH))
    }

    /** Only the dialog's explicit, cost-labelled confirmation invokes this method. */
    internal fun confirm(currentSession: ChannelPointRewardsSession) {
        val state = _state.value
        val reward = state.selected ?: return
        if (state.busy) return
        if (!currentSession.canRedeem || currentSession != session) {
            open(currentSession)
            return
        }
        val key = currentSession.rewardKey(reward.id)
        if (key in uncertain) {
            _state.value = state.copy(notice = RewardNotice.UNKNOWN)
            return
        }
        if (reward.requiresInput && state.input.isBlank()) {
            _state.value = state.copy(notice = RewardNotice.INPUT_REQUIRED)
            return
        }
        // Set synchronously, before launch, to reject repeated clicks/key repeats.
        _state.value = state.copy(busy = true, notice = null)
        val requestRevision = ++revision
        job = viewModelScope.launch {
            var dispatched = false
            try {
                val latest = load(currentSession)
                ensureActive()
                if (session != currentSession || revision != requestRevision) return@launch
                val fresh = latest?.rewards?.find { it.id == reward.id }
                val reason = when {
                    latest == null -> RewardNotice.LOAD_FAILED
                    fresh == null || !fresh.available -> RewardNotice.UNAVAILABLE
                    fresh != reward -> RewardNotice.CHANGED
                    latest.balance < reward.cost -> RewardNotice.INSUFFICIENT
                    else -> null
                }
                if (reason != null) {
                    _state.value = ChannelPointRewardsState(catalog = latest, notice = reason, blockedRewardIds = blockedIds(currentSession, latest))
                    return@launch
                }
                ensureActive()
                if (sessions.current(currentSession.channelId, currentSession.channelLogin) != currentSession) {
                    _state.value = ChannelPointRewardsState(notice = RewardNotice.LOGIN)
                    return@launch
                }
                // Persist an uncertain marker before dispatch. Restored UI never replays it.
                uncertain.add(key)
                saveUncertain()
                dispatched = true
                _state.value = _state.value.copy(sending = true)
                val response = repository.redeem(currentSession.headers, currentSession.channelId!!, reward, state.input.takeIf { reward.requiresInput }, UUID.randomUUID().toString())
                val outcome = response.outcome()
                if (outcome != Outcome.UNKNOWN) {
                    uncertain.remove(key)
                    saveUncertain()
                }
                if (session != currentSession || revision != requestRevision) return@launch
                _state.value = ChannelPointRewardsState(
                    // Require a fresh read before another purchase, never invent a balance.
                    notice = when (outcome) { Outcome.SUCCESS -> RewardNotice.SUCCESS; Outcome.REJECTED -> RewardNotice.REJECTED; Outcome.UNKNOWN -> RewardNotice.UNKNOWN },
                    blockedRewardIds = if (outcome == Outcome.UNKNOWN) setOf(reward.id) else emptySet(),
                )
            } catch (e: CancellationException) {
                if (session == currentSession && revision == requestRevision) {
                    _state.value = if (dispatched) ChannelPointRewardsState(notice = RewardNotice.UNKNOWN, blockedRewardIds = setOf(reward.id)) else state.copy(busy = false)
                }
                throw e
            } catch (e: Exception) {
                if (session == currentSession && revision == requestRevision) {
                    _state.value = ChannelPointRewardsState(notice = if (dispatched) RewardNotice.UNKNOWN else RewardNotice.LOAD_FAILED, blockedRewardIds = if (dispatched) setOf(reward.id) else emptySet())
                }
            }
        }
    }

    internal fun cancelSelection() {
        if (_state.value.sending) return
        revision++
        job?.cancel()
        _state.value = _state.value.copy(selected = null, input = "", busy = false)
    }

    private suspend fun load(session: ChannelPointRewardsSession) = repository.load(session.networkLibrary, session.headers, session.channelLogin!!)
    private fun blockedIds(session: ChannelPointRewardsSession, catalog: ChannelPointRewardCatalog?) = catalog?.rewards.orEmpty().filter { session.rewardKey(it.id) in uncertain }.map { it.id }.toSet()
    private fun saveUncertain() { savedState[UNCERTAIN] = ArrayList(uncertain) }

    companion object {
        internal const val MAX_INPUT_LENGTH = 500
        private const val UNCERTAIN = "uncertainRewardRedemptions"
    }
}
