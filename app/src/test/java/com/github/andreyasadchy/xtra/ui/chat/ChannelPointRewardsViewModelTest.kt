package com.github.andreyasadchy.xtra.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointRewardRedemptionResponse
import com.github.andreyasadchy.xtra.model.ui.ChannelPointRewardCatalog
import com.github.andreyasadchy.xtra.model.ui.ChannelPointRewardOption
import com.github.andreyasadchy.xtra.repository.ChannelPointRewardsRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelPointRewardsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository: ChannelPointRewardsRepository = mock()
    private val sessions: ChannelPointRewardsSessionProvider = mock()
    private val saved = SavedStateHandle()
    private val session = ChannelPointRewardsSession("viewer", "channel", "streamer", "OkHttp", mapOf(C.HEADER_TOKEN to "fixture", C.HEADER_CLIENT_ID to "web-client"))
    private val reward = ChannelPointRewardOption("reward", "Say hello", 100, "Your message", true, true)
    private val catalog = ChannelPointRewardCatalog(1000, listOf(reward))
    private lateinit var vm: ChannelPointRewardsViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        whenever(sessions.current("channel", "streamer")).thenReturn(session)
        vm = ChannelPointRewardsViewModel(repository, saved, sessions)
    }
    @After fun teardown() = Dispatchers.resetMain()
    private suspend fun ready() {
        whenever(repository.load("OkHttp", session.headers, "streamer")).thenReturn(catalog)
        vm.open(session)
        dispatcher.scheduler.advanceUntilIdle()
        vm.select(reward)
        vm.inputChanged("Hello streamer")
    }
    private fun success() = ChannelPointRewardRedemptionResponse(data = ChannelPointRewardRedemptionResponse.Data(ChannelPointRewardRedemptionResponse.Payload(null)))

    @Test fun `opening selecting typing and cancelling never spend`() = runTest {
        ready()
        vm.cancelSelection()
        advanceUntilIdle()
        verify(repository).load("OkHttp", session.headers, "streamer")
        verifyNoMoreInteractions(repository)
        assertNull(vm.state.value.selected)
    }
    @Test fun `web token and account identity are both required`() = runTest {
        vm.open(session.copy(headers = emptyMap()))
        advanceUntilIdle()
        assertEquals(RewardNotice.LOGIN, vm.state.value.notice)
        vm.open(session.copy(accountId = null))
        advanceUntilIdle()
        verifyNoInteractions(repository)
    }
    @Test fun `missing whitespace input does not spend`() = runTest {
        ready()
        vm.inputChanged("  \n")
        vm.confirm(session)
        advanceUntilIdle()
        assertEquals(RewardNotice.INPUT_REQUIRED, vm.state.value.notice)
        verify(repository, never()).redeem(any(), any(), any(), anyOrNull(), any())
    }
    @Test fun `double confirm sends exactly once after preflight`() = runTest {
        ready()
        whenever(repository.redeem(any(), any(), any(), anyOrNull(), any())).thenReturn(success())
        vm.confirm(session)
        vm.confirm(session)
        advanceUntilIdle()
        verify(repository, times(1)).redeem(eq(session.headers), eq("channel"), eq(reward), eq("Hello streamer"), any())
        assertEquals(RewardNotice.SUCCESS, vm.state.value.notice)
        assertNull(vm.state.value.catalog)
    }
    @Test fun `price title prompt or required input changes need fresh consent`() = runTest {
        for (changed in listOf(reward.copy(cost = 150), reward.copy(title = "Changed"), reward.copy(prompt = "New prompt"), reward.copy(requiresInput = false))) {
            ready()
            whenever(repository.load("OkHttp", session.headers, "streamer")).thenReturn(catalog.copy(rewards = listOf(changed)))
            vm.confirm(session)
            advanceUntilIdle()
            assertEquals(RewardNotice.CHANGED, vm.state.value.notice)
            assertNull(vm.state.value.selected)
            vm = ChannelPointRewardsViewModel(repository, saved, sessions)
        }
        verify(repository, never()).redeem(any(), any(), any(), anyOrNull(), any())
    }
    @Test fun `missing paused unavailable and unaffordable rewards cannot spend`() = runTest {
        for (newCatalog in listOf(catalog.copy(rewards = emptyList()), catalog.copy(rewards = listOf(reward.copy(available = false))), catalog.copy(balance = 99))) {
            ready()
            whenever(repository.load("OkHttp", session.headers, "streamer")).thenReturn(newCatalog)
            vm.confirm(session)
            advanceUntilIdle()
            assertTrue(vm.state.value.notice in listOf(RewardNotice.UNAVAILABLE, RewardNotice.INSUFFICIENT))
            vm = ChannelPointRewardsViewModel(repository, saved, sessions)
        }
        verify(repository, never()).redeem(any(), any(), any(), anyOrNull(), any())
    }
    @Test fun `refresh failure clears stale catalog`() = runTest {
        ready()
        vm.cancelSelection()
        whenever(repository.load(anyOrNull(), any(), any())).doSuspendableAnswer { throw IOException() }
        vm.refresh(session)
        advanceUntilIdle()
        assertNull(vm.state.value.catalog)
        assertEquals(RewardNotice.LOAD_FAILED, vm.state.value.notice)
    }
    @Test fun `preflight failure does not create uncertain purchase marker`() = runTest {
        ready()
        whenever(repository.load(anyOrNull(), any(), any())).doSuspendableAnswer { throw IOException() }
        vm.confirm(session)
        advanceUntilIdle()
        assertEquals(RewardNotice.LOAD_FAILED, vm.state.value.notice)
        assertTrue(saved.get<ArrayList<String>>("uncertainRewardRedemptions").isNullOrEmpty())
        verify(repository, never()).redeem(any(), any(), any(), anyOrNull(), any())
    }
    @Test fun `cancel during preflight never submits or restores selection`() = runTest {
        ready()
        val gate = CompletableDeferred<ChannelPointRewardCatalog?>()
        whenever(repository.load(anyOrNull(), any(), any())).doSuspendableAnswer { gate.await() }
        vm.confirm(session)
        runCurrent()
        vm.cancelSelection()
        gate.complete(catalog)
        advanceUntilIdle()
        assertNull(vm.state.value.selected)
        assertFalse(vm.state.value.busy)
        verify(repository, never()).redeem(any(), any(), any(), anyOrNull(), any())
    }
    @Test fun `rotation and background reopening retain typed draft`() = runTest {
        ready()
        vm.open(session)
        advanceUntilIdle()
        assertEquals(reward, vm.state.value.selected)
        assertEquals("Hello streamer", vm.state.value.input)
        verify(repository, times(1)).load(anyOrNull(), any(), any())
    }
    @Test fun `account logout during preflight invalidates confirmation`() = runTest {
        ready()
        whenever(sessions.current("channel", "streamer")).thenReturn(session.copy(headers = emptyMap()))
        vm.confirm(session)
        advanceUntilIdle()
        assertEquals(RewardNotice.LOGIN, vm.state.value.notice)
        verify(repository, never()).redeem(any(), any(), any(), anyOrNull(), any())
    }
    @Test fun `uncertain response blocks repeat even after viewmodel restoration`() = runTest {
        ready()
        whenever(repository.redeem(any(), any(), any(), anyOrNull(), any())).doSuspendableAnswer { throw IOException() }
        vm.confirm(session)
        advanceUntilIdle()
        assertEquals(RewardNotice.UNKNOWN, vm.state.value.notice)
        vm = ChannelPointRewardsViewModel(repository, saved, sessions)
        vm.open(session)
        advanceUntilIdle()
        vm.select(reward)
        vm.confirm(session)
        advanceUntilIdle()
        assertEquals(RewardNotice.UNKNOWN, vm.state.value.notice)
        verify(repository, times(1)).redeem(any(), any(), any(), anyOrNull(), any())
    }
    @Test fun `explicit server rejection requires fresh selection and confirmation to retry`() = runTest {
        ready()
        whenever(repository.redeem(any(), any(), any(), anyOrNull(), any())).thenReturn(ChannelPointRewardRedemptionResponse(data = ChannelPointRewardRedemptionResponse.Data(ChannelPointRewardRedemptionResponse.Payload(ChannelPointRewardRedemptionResponse.RedemptionError("denied")))))
        vm.confirm(session)
        advanceUntilIdle()
        assertEquals(RewardNotice.REJECTED, vm.state.value.notice)
        vm.confirm(session)
        advanceUntilIdle()
        verify(repository, times(1)).redeem(any(), any(), any(), anyOrNull(), any())
        assertTrue(saved.get<ArrayList<String>>("uncertainRewardRedemptions").isNullOrEmpty())
    }
    @Test fun `cancellation after dispatch is uncertain and never retried`() = runTest {
        ready()
        whenever(repository.redeem(any(), any(), any(), anyOrNull(), any())).thenThrow(CancellationException())
        vm.confirm(session)
        advanceUntilIdle()
        assertEquals(RewardNotice.UNKNOWN, vm.state.value.notice)
        assertEquals(listOf("viewer:channel:reward"), saved.get<ArrayList<String>>("uncertainRewardRedemptions"))
    }
    @Test fun `closing during submitted request does not claim cancellation or submit again`() = runTest {
        ready()
        val gate = CompletableDeferred<ChannelPointRewardRedemptionResponse>()
        whenever(repository.redeem(any(), any(), any(), anyOrNull(), any())).doSuspendableAnswer { gate.await() }
        vm.confirm(session)
        runCurrent()
        assertTrue(vm.state.value.sending)
        vm.cancelSelection()
        vm.open(session)
        vm.confirm(session)
        gate.complete(success())
        advanceUntilIdle()
        assertEquals(RewardNotice.SUCCESS, vm.state.value.notice)
        verify(repository, times(1)).redeem(any(), any(), any(), anyOrNull(), any())
    }
}
