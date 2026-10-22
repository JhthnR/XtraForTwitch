package com.github.andreyasadchy.xtra.ui.login

import com.github.andreyasadchy.xtra.model.id.DeviceCodeResponse
import com.github.andreyasadchy.xtra.ui.login.DeviceCodeLoginController.Approval
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceCodeLoginControllerTest {
    @Test fun `provider metadata is decoded and device secret never enters UI state`() = runTest {
        val h = Harness(this)
        h.response = Json.decodeFromString<DeviceCodeResponse>("""{"device_code":"private-test-code","user_code":"TEST1234","expires_in":60,"interval":8}""")
        h.start()
        assertEquals(60L, h.ready().seconds)
        assertEquals("https://www.twitch.tv/activate?public=true&device-code=TEST1234", h.ready().url)
        assertFalse(h.states.toString().contains("private-test-code"))
        assertEquals(1800, Json.decodeFromString<DeviceCodeResponse>("{}").expiresIn)
    }

    @Test fun `checks are explicit and respect the provider interval without polling`() = runTest {
        val h = Harness(this)
        h.response = DeviceCodeResponse("private", "TEST1234", interval = 8)
        h.start()
        h.controller.checkApproval()
        runCurrent()
        assertEquals(DeviceCodeLoginController.Status.PENDING, h.ready().status)
        h.controller.checkApproval()
        advanceTimeBy(7999)
        runCurrent()
        assertEquals(1, h.checks)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(h.ready().canCheck)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, h.checks)
        h.controller.checkApproval()
        runCurrent()
        assertEquals(2, h.checks)
    }

    @Test fun `duplicate checks stay single and authorize once`() = runTest {
        val result = CompletableDeferred<Approval>()
        val h = Harness(this)
        h.check = { result.await() }
        h.start()
        h.controller.checkApproval()
        runCurrent()
        h.controller.checkApproval()
        assertTrue(h.ready().checking)
        assertEquals(1, h.checks)
        result.complete(Approval.Token("synthetic-token"))
        runCurrent()
        h.controller.checkApproval()
        assertEquals(listOf("synthetic-token"), h.authorized)
    }

    @Test fun `expiry advances while stopped and resume rejects the expired code`() = runTest {
        val h = Harness(this)
        h.response = DeviceCodeResponse("private", "TEST1234", expiresIn = 2)
        h.start()
        h.controller.pause()
        val count = h.states.size
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(count, h.states.size)
        h.controller.resume()
        assertEquals(DeviceCodeLoginController.State.Expired, h.states.last())
        h.controller.checkApproval()
        runCurrent()
        assertEquals(0, h.checks)
    }

    @Test fun `new request rejects old noncancellable authorization completion`() = runTest {
        val result = CompletableDeferred<Approval>()
        val h = Harness(this)
        h.check = { withContext(NonCancellable) { result.await() } }
        h.start()
        h.controller.checkApproval()
        runCurrent()
        h.response = DeviceCodeResponse("new-private", "NEWCODE1")
        h.controller.requestCode()
        runCurrent()
        result.complete(Approval.Token("old-token"))
        runCurrent()
        assertTrue(h.authorized.isEmpty())
        assertEquals("NEWCODE1", h.ready().userCode)
    }

    @Test fun `expiry and disposal reject late completions and stop future requests`() = runTest {
        for (dispose in listOf(false, true)) {
            val result = CompletableDeferred<Approval>()
            val h = Harness(this)
            h.response = DeviceCodeResponse("private", "TEST1234", expiresIn = 1)
            h.check = { withContext(NonCancellable) { result.await() } }
            h.start()
            h.controller.checkApproval()
            runCurrent()
            if (dispose) h.controller.dispose() else { advanceTimeBy(1000); runCurrent() }
            val count = h.states.size
            result.complete(Approval.Token("late-token"))
            runCurrent()
            assertTrue(h.authorized.isEmpty())
            assertEquals(count, h.states.size)
            h.controller.dispose()
            h.controller.requestCode()
            runCurrent()
            assertEquals(count, h.states.size)
        }
    }

    @Test fun `request and check failures allow explicit retry`() = runTest {
        val h = Harness(this)
        h.requestFailure = true
        h.start()
        assertEquals(DeviceCodeLoginController.State.Error, h.states.last())
        h.requestFailure = false
        h.controller.requestCode()
        runCurrent()
        h.check = { throw IOException() }
        h.controller.checkApproval()
        runCurrent()
        assertEquals(DeviceCodeLoginController.Status.CONNECTION_ERROR, h.ready().status)
        advanceTimeBy(5000)
        runCurrent()
        assertTrue(h.ready().canCheck)
        assertEquals(1, h.checks)
    }

    @Test fun `invalid or missing provider codes never become actionable`() = runTest {
        val h = Harness(this)
        for (response in listOf(DeviceCodeResponse(), DeviceCodeResponse("", "TEST1234"),
            DeviceCodeResponse("private", "../redirect"), DeviceCodeResponse("private", "TEST1234", expiresIn = 0))) {
            h.response = response
            h.controller.requestCode()
            runCurrent()
            assertEquals(DeviceCodeLoginController.State.Error, h.states.last())
        }
        assertEquals(0, h.checks)
    }

    @Test fun `provider rejection immediately clears the code and enables a fresh request`() = runTest {
        val h = Harness(this)
        h.check = { throw DeviceCodeLoginController.Rejected() }
        h.start()
        h.controller.checkApproval()
        runCurrent()
        assertEquals(DeviceCodeLoginController.State.Rejected(DeviceCodeLoginController.Failure.INVALID_CODE), h.states.last())
        assertTrue(h.authorized.isEmpty())
        h.controller.requestCode()
        runCurrent()
        assertTrue(h.states.last() is DeviceCodeLoginController.State.Ready)
    }

    @Test fun `denial validation failure and expiry stay distinct and clear the current code`() = runTest {
        for (reason in DeviceCodeLoginController.Failure.entries) {
            val h = Harness(this)
            h.check = { throw DeviceCodeLoginController.Rejected(reason) }
            h.start()
            h.controller.checkApproval()
            runCurrent()
            assertEquals(DeviceCodeLoginController.State.Rejected(reason), h.states.last())
            assertTrue(h.authorized.isEmpty())
            h.controller.dispose()
        }
        val expired = Harness(this)
        expired.check = { throw DeviceCodeLoginController.CodeExpired() }
        expired.start()
        expired.controller.checkApproval()
        runCurrent()
        assertEquals(DeviceCodeLoginController.State.Expired, expired.states.last())
    }

    @Test fun `a held validated account can finish once without inventing a device token`() = runTest {
        val h = Harness(this)
        h.check = { Approval.ExistingAccount }
        h.start()
        h.controller.checkApproval()
        runCurrent()
        h.controller.checkApproval()
        runCurrent()
        assertEquals(listOf<String?>(null), h.authorized)
        assertFalse(h.states.toString().contains("private"))
    }

    private class Harness(private val scope: TestScope) {
        val states = mutableListOf<DeviceCodeLoginController.State>()
        val authorized = mutableListOf<String?>()
        var response = DeviceCodeResponse("private", "TEST1234")
        var requestFailure = false
        var check: suspend (String) -> Approval = { Approval.Pending }
        var checks = 0
        val controller = DeviceCodeLoginController(scope.backgroundScope, { scope.testScheduler.currentTime },
            { if (requestFailure) throw IOException() else response },
            { checks++; check(it) }, { authorized.add(it) }, { states.add(it) })
        fun start() { controller.resume(); controller.requestCode(); scope.runCurrent() }
        fun ready() = states.last() as DeviceCodeLoginController.State.Ready
    }
}
