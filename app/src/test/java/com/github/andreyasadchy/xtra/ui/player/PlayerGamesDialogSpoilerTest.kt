package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentGamesListItemBinding
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Chapter positions and durations add up to the VoD length, so spoiler-free VoDs hide them. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class PlayerGamesDialogSpoilerTest {
    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
    private val game = Game(name = "Chess", vodPosition = 3_600_000, vodDuration = 5_400_000)

    @Before fun clearPrefs() {
        context.prefs().edit { clear() }
    }

    @Test fun `chapter times show by default`() {
        val row = bind()
        assertEquals(View.VISIBLE, row.viewers.visibility)
        assertEquals(View.VISIBLE, row.broadcastersCount.visibility)
    }

    @Test fun `spoiler-free VoDs hide chapter times but keep the game`() {
        context.prefs().edit { putBoolean(C.PLAYER_SPOILER_FREE_VODS, true) }
        val row = bind()
        assertEquals(View.VISIBLE, row.gameName.visibility)
        assertEquals(View.GONE, row.viewers.visibility)
        assertEquals(View.GONE, row.broadcastersCount.visibility)
    }

    private fun bind(): FragmentGamesListItemBinding {
        val fragment = mock<Fragment> { on { requireContext() } doReturn context }
        val holder = PlayerGamesDialogAdapter(fragment).onCreateViewHolder(FrameLayout(context), 0)
        holder.bind(game)
        return FragmentGamesListItemBinding.bind(holder.itemView)
    }
}
