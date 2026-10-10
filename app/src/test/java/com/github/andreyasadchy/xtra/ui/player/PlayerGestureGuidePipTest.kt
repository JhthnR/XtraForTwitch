package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentPlayerBinding
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

/** PiP reports a landscape configuration; the one-time gesture guide must not cover the small window. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28], qualifiers = "w800dp-h360dp-land")
class PlayerGestureGuidePipTest {
    private lateinit var activity: ActivityController<AppCompatActivity>
    private val context get() = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)

    @Before fun setup() {
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).apply {
            get().setTheme(R.style.BaseDarkTheme)
        }.setup()
        context.prefs().edit { clear() }
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
    }

    private fun player(host: AppCompatActivity, childManager: FragmentManager = mock()): PlayerFragment {
        val fragment = Mockito.mock(PlayerFragment::class.java, Mockito.withSettings().useConstructor().defaultAnswer(Mockito.CALLS_REAL_METHODS))
        doReturn(context).whenever(fragment).context
        doReturn(host).whenever(fragment).activity
        doReturn(Bundle()).whenever(fragment).arguments
        doReturn(childManager).whenever(fragment).childFragmentManager
        doNothing().whenever(fragment).showGestureGuide(anyOrNull())
        ReflectionHelpers.setField(fragment, "isPortrait", false)
        return fragment
    }

    private fun pipActivity(inPip: Boolean): AppCompatActivity = mock<AppCompatActivity>().also {
        doReturn(inPip).whenever(it).isInPictureInPictureMode
    }

    @Test fun `guide does not open inside picture-in-picture`() {
        val fragment = player(pipActivity(inPip = true))
        fragment.maybeShowGestureGuide()
        verify(fragment, never()).showGestureGuide(anyOrNull())
    }

    @Test fun `guide still opens on the first full-screen landscape playback`() {
        val fragment = player(pipActivity(inPip = false))
        fragment.maybeShowGestureGuide()
        verify(fragment).showGestureGuide(anyOrNull())
    }

    @Test fun `entering picture-in-picture closes an open guide`() {
        val guide = mock<DialogFragment>()
        val childManager = mock<FragmentManager>()
        doReturn(guide).whenever(childManager).findFragmentByTag("closeOnPip")
        val fragment = player(activity.get(), childManager)
        val binding = FragmentPlayerBinding.inflate(LayoutInflater.from(context))
        binding.playerLayout.layoutParams = FrameLayout.LayoutParams(800, 360)
        activity.get().setContentView(binding.root)
        ReflectionHelpers.setField(fragment, "_binding", binding)
        fragment.onPictureInPictureModeChanged(true)
        verify(guide).dismiss()
    }
}
