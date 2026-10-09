package com.github.andreyasadchy.xtra.ui.main

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class LiveNotificationWorkerTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private val shown: ShownNotificationsRepository = mock()
    private val local: NotificationUsersRepository = mock()
    private val gql: GraphQLRepository = mock()
    private val helix: HelixRepository = mock()
    private var streams = listOf(stream("1"))
    private var beforePost: () -> Unit = {}
    private val deliveryResults = mutableListOf<Boolean>()

    @Before fun setup(): Unit = runBlocking {
        context = RuntimeEnvironment.getApplication()
        context.prefs().edit().clear().putBoolean(C.LIVE_NOTIFICATIONS_ENABLED, true).commit()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
        whenever(shown.notifyNewStreams(eq(local), anyOrNull(), any(), eq(gql), any(), eq(helix), any()))
            .thenAnswer { call ->
                beforePost()
                val post = call.getArgument<(Stream) -> Boolean>(6)
                streams.forEach { deliveryResults.add(post(it)) }
                false
            }
    }

    private fun worker() = LiveNotificationWorker(context, mock<WorkerParameters>(), shown, local, gql, helix)
    private fun children() = manager.activeNotifications.filter { it.id == 1 }

    @Test fun `disabled global preference does not fetch or acknowledge streams`() = runBlocking {
        context.prefs().edit().putBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false).commit()
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        verifyNoInteractions(shown)
        assertTrue(children().isEmpty())
    }

    @Test fun `disabled Android channel does not fetch or acknowledge streams`() = runBlocking {
        manager.createNotificationChannel(NotificationChannel(context.getString(R.string.notification_live_channel_id), "Live", NotificationManager.IMPORTANCE_NONE))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        verifyNoInteractions(shown)
    }

    @Test fun `setting disabled while fetching refuses delivery`() = runBlocking {
        beforePost = { context.prefs().edit().putBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false).commit() }
        worker().doWork()
        assertEquals(listOf(false), deliveryResults)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun `colliding channel hashes have distinct notification and PendingIntent identities`() = runBlocking {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        streams = listOf(stream("Aa"), stream("BB"))
        worker().doWork()
        val notifications = children()
        assertEquals(2, notifications.size)
        val intents = notifications.map { shadowOf(it.notification.contentIntent).savedIntent }
        assertEquals(2, intents.map { it.data }.toSet().size)
        assertEquals(setOf("Aa", "BB"), intents.map { it.getParcelableExtra<Stream>(MainActivity.KEY_VIDEO)!!.channelId }.toSet())
    }

    @Test fun `same event retry is silent but a later broadcast may alert`() = runBlocking {
        worker().doWork()
        assertEquals(0, children().single().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        worker().doWork()
        assertTrue(children().single().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        streams = listOf(stream("1", "2026-10-04T11:00:00Z"))
        worker().doWork()
        assertEquals(1, children().size)
        assertEquals(0, children().single().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test fun `fetch failure requests WorkManager retry`() = runBlocking {
        doAnswer { throw IOException("offline") }
            .whenever(shown).notifyNewStreams(eq(local), anyOrNull(), any(), eq(gql), any(), eq(helix), any())
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertTrue(children().isEmpty())
    }

    @Test fun `persistent failure returns to the periodic schedule after quick retries`() = runBlocking {
        doAnswer { throw IOException("offline") }
            .whenever(shown).notifyNewStreams(eq(local), anyOrNull(), any(), eq(gql), any(), eq(helix), any())
        val parameters = mock<WorkerParameters> { on { runAttemptCount } doReturn 3 }
        assertEquals(ListenableWorker.Result.success(), LiveNotificationWorker(context, parameters, shown, local, gql, helix).doWork())
    }

    @Test fun `cancellation escapes rather than becoming a retry`() = runBlocking {
        doAnswer { throw CancellationException("stopped") }
            .whenever(shown).notifyNewStreams(eq(local), anyOrNull(), any(), eq(gql), any(), eq(helix), any())
        try { worker().doWork(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }

    @Test fun `same broadcast remains silent when backend timestamp precision changes`() = runBlocking {
        streams = listOf(Stream(id = "same", channelId = "1", createdAt = "2026-10-04T10:00:00Z"))
        worker().doWork()
        streams = listOf(Stream(id = "same", channelId = "1", createdAt = "2026-10-04T10:00:00.123Z"))
        worker().doWork()
        assertTrue(children().single().notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test @Config(sdk = [23]) fun `API 23 delivers child notifications without a group summary`() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(1, children().size)
        assertEquals(1, manager.activeNotifications.size)
    }

    companion object {
        private fun stream(id: String, startedAt: String = "2026-10-04T10:00:00Z") =
            Stream(channelId = id, channelLogin = "user$id", channelName = "User$id", createdAt = startedAt, title = "Live")
    }
}
