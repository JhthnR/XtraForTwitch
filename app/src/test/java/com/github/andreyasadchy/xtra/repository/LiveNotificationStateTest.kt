package com.github.andreyasadchy.xtra.repository

import android.app.Application
import android.content.Context
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.tokenPrefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class LiveNotificationStateTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before fun clearState() {
        context.getSharedPreferences("live_notification_follows", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `notification preferences survive repository recreation without leaking across accounts`() {
        val snapshot = setOf("2")
        LiveNotificationState(context).save("viewer", snapshot)
        val restored = LiveNotificationState(context)
        assertEquals(snapshot, restored.load("viewer"))
        assertNull(restored.load("other-viewer"))
    }

    @Test fun `saved preferences replace older IDs and another account replaces the cache`() {
        val state = LiveNotificationState(context)
        state.save("viewer", setOf("1"))
        state.save("viewer", emptySet())
        assertTrue(LiveNotificationState(context).load("viewer")!!.isEmpty())
        state.save("other", setOf("2"))
        assertNull(LiveNotificationState(context).load("viewer"))
        assertEquals(setOf("2"), LiveNotificationState(context).load("other"))
    }

    @Test fun `pending baseline start and completed channel coverage survive process recreation`() {
        val state = LiveNotificationState(context)
        state.beginBaseline("viewer", 1_000L)
        val revision = state.revision()
        assertTrue(state.advanceBaseline("viewer", revision, setOf("1"), false))
        val restored = LiveNotificationState(context).pendingBaseline("viewer")!!
        assertEquals(setOf("1"), restored.completed)
        assertEquals(1_000L, restored.startedAt)
        assertNull(LiveNotificationState(context).pendingBaseline("other"))
        assertTrue(state.advanceBaseline("viewer", revision, setOf("1", "2"), true))
        assertNull(LiveNotificationState(context).pendingBaseline("viewer"))
    }

    @Test fun `account mute invalidates pending work and removes its cached ID durably`() {
        context.tokenPrefs().edit().putString(C.USER_ID, "viewer").commit()
        val state = LiveNotificationState(context)
        val snapshot = setOf("1", "2")
        state.save("viewer", snapshot)
        val revision = state.revision()
        val pendingRevision = state.invalidateAccount("viewer", "2", false)!!
        assertFalse(state.accountChannelAllowed("viewer", "2"))
        state.persistAccountNotificationChanged("viewer", "2", false, pendingRevision)
        assertTrue(state.revision() > pendingRevision)
        assertFalse(state.saveIfCurrent("viewer", revision, snapshot))
        assertEquals(setOf("1"), LiveNotificationState(context).load("viewer"))
    }

    @Test fun `account enable adds its channel durably for later Helix fallbacks`() {
        context.tokenPrefs().edit().putString(C.USER_ID, "viewer").commit()
        val state = LiveNotificationState(context)
        state.save("viewer", setOf("1"))
        val pendingRevision = state.invalidateAccount("viewer", "3", true)!!
        assertTrue(state.accountChannelAllowed("viewer", "3"))
        state.persistAccountNotificationChanged("viewer", "3", true, pendingRevision)
        assertEquals(setOf("1", "3"), LiveNotificationState(context).load("viewer"))
    }

    @Test fun `late old account mutation cannot change new account preferences`() {
        context.tokenPrefs().edit().putString(C.USER_ID, "new").commit()
        val state = LiveNotificationState(context)
        val snapshot = setOf("1")
        state.save("new", snapshot)
        val revision = state.revision()
        assertNull(state.invalidateAccount("old", "1", false))
        state.persistAccountNotificationChanged("old", "1", false, revision)
        assertEquals(revision, state.revision())
        assertEquals(snapshot, state.load("new"))
    }

    @Test fun `returned channel sets do not expose mutable SharedPreferences state`() {
        val state = LiveNotificationState(context)
        state.save("viewer", setOf("1", "2"))
        val loaded = state.load("viewer")!!
        (loaded as MutableSet).clear()
        assertEquals(setOf("1", "2"), state.load("viewer"))
    }
}
