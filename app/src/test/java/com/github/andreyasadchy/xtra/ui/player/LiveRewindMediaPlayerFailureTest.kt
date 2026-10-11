package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.media.MediaPlayer
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.robolectric.RuntimeEnvironment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class LiveRewindMediaPlayerFailureTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()

    @Test fun `child source exception inside async Android MediaPlayer startup returns to live`() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("fixture")
                .body("#EXTM3U\n#EXT-X-STREAM-INF:IVS-NAME=\"720p60\",RESOLUTION=1280x720,FRAME-RATE=60,BANDWIDTH=1000\nhttps://example.test/child.m3u8".toResponseBody()).build()
        }.build()
        val vm = PlayerViewModel(mock(), mock(), mock(), mock(), mock(), mock(), null, null, mock(), client, mock(), mock(), mock())
        vm.started = true
        vm.liveRewindPending = true
        val activity: MainActivity = mock()
        val player: MediaPlayer = mock()
        val service: MediaPlayerService = mock()
        whenever(service.player).thenReturn(player)
        doThrow(IOException("child playlist unavailable")).whenever(player).setDataSource(any<String>())
        val recovered = CompletableDeferred<Unit>()
        doAnswer { recovered.complete(Unit); true }.whenever(activity).returnFromLiveRewind(any(), any())
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
        val fragment = Mockito.mock(MediaPlayerFragment::class.java, Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))
        doReturn(context).whenever(fragment).context
        doReturn(activity).whenever(fragment).activity
        doReturn(owner).whenever(fragment).viewLifecycleOwner
        doReturn(true).whenever(fragment).isResumed
        doNothing().whenever(fragment).changePlayerMode()
        doReturn(Bundle().apply {
            putString("videoId", "123")
            putParcelable(LiveRewind.ORIGIN, Stream(id = "live", channelLogin = "channel"))
        }).whenever(fragment).arguments
        ReflectionHelpers.setField(fragment, "viewModel\$delegate", lazyOf(vm))
        ReflectionHelpers.setField(fragment, "videoType", "video")
        ReflectionHelpers.setField(fragment, "playbackService", service)
        ReflectionHelpers.setField(fragment, "_binding", FragmentPlayerBinding.inflate(LayoutInflater.from(context)))
        fragment.startVideo("https://example.test/master.m3u8", 570_000L, true)
        // The real playlist loader uses Dispatchers.IO, outside the test scheduler.
        withContext(Dispatchers.Default) { withTimeout(10_000L) { recovered.await() } }
        assertTrue(vm.liveRewindFailed)
        verify(activity).returnFromLiveRewind(fragment, fragment.liveRewindOrigin()!!)
        verify(player).setDataSource("https://example.test/child.m3u8")
        verify(player, never()).prepareAsync()
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
    }
}
