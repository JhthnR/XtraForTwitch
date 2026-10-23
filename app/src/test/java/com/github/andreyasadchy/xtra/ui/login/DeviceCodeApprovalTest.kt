package com.github.andreyasadchy.xtra.ui.login

import android.app.Application
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.model.id.ValidationResponse
import com.github.andreyasadchy.xtra.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class DeviceCodeApprovalTest {
    @Test fun `failed device validation retains the already validated Helix account`() = runTest {
        val repository = mock<AuthRepository>()
        val activity = LoginActivity().apply { authRepository = repository }
        ReflectionHelpers.setField(activity, "helixToken", "synthetic-validated-helix")
        whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse("synthetic-device-token"))
        whenever(repository.validate(anyOrNull(), any())).thenReturn(ValidationResponse("different-client"))
        assertEquals(DeviceCodeLoginController.Approval.ExistingAccount,
            activity.checkDeviceApproval("OkHttp", "client", "private-code"))
        assertNull(ReflectionHelpers.getField<String?>(activity, "gqlToken"))
    }

    @Test fun `a device token must pass matching-client validation before it can be authorized`() = runTest {
        val repository = mock<AuthRepository>()
        val activity = LoginActivity().apply { authRepository = repository }
        whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse("synthetic-device-token"))
        whenever(repository.validate(anyOrNull(), any())).thenReturn(ValidationResponse("client", "viewer", "123"))
        assertEquals(DeviceCodeLoginController.Approval.Token("synthetic-device-token"),
            activity.checkDeviceApproval("OkHttp", "client", "private-code"))
        whenever(repository.validate(anyOrNull(), any())).thenReturn(ValidationResponse("different-client"))
        val failure = runCatching { activity.checkDeviceApproval("OkHttp", "client", "private-code") }.exceptionOrNull()
        assertEquals(DeviceCodeLoginController.Failure.VALIDATION_FAILED,
            (failure as DeviceCodeLoginController.Rejected).reason)
    }

    @Test fun `provider denial invalidity pending and expiry retain their actual meaning`() = runTest {
        val repository = mock<AuthRepository>()
        val activity = LoginActivity().apply { authRepository = repository }
        for ((message, reason) in listOf("access_denied" to DeviceCodeLoginController.Failure.DENIED,
            "invalid device code" to DeviceCodeLoginController.Failure.INVALID_CODE)) {
            whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse(message = message))
            val failure = runCatching { activity.checkDeviceApproval("OkHttp", "client", "private-code") }.exceptionOrNull()
            assertEquals(reason, (failure as DeviceCodeLoginController.Rejected).reason)
        }
        whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse(message = "expired_token"))
        assertTrue(runCatching { activity.checkDeviceApproval("OkHttp", "client", "private-code") }.exceptionOrNull()
            is DeviceCodeLoginController.CodeExpired)
        whenever(repository.getToken(anyOrNull(), any())).thenReturn(TokenResponse(message = "authorization_pending"))
        assertEquals(DeviceCodeLoginController.Approval.Pending,
            activity.checkDeviceApproval("OkHttp", "client", "private-code"))
        verify(repository, never()).validate(anyOrNull(), any())
    }
}
