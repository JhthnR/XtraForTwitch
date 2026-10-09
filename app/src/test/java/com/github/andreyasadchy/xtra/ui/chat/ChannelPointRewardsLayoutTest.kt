package com.github.andreyasadchy.xtra.ui.chat

import android.app.Application
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import androidx.core.widget.addTextChangedListener
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.DialogChannelPointRewardsBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class ChannelPointRewardsLayoutTest {
    private fun context(fontScale: Float = 1f): ContextThemeWrapper {
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { this.fontScale = fontScale }
        return ContextThemeWrapper(app.createConfigurationContext(config), R.style.BaseDarkTheme)
    }
    @Test fun `reward entry has accessible label and 48dp keyboard target`() {
        val context = context()
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_chat, null)
        val button = root.findViewById<ImageButton>(R.id.channelRewards)
        val dp = context.resources.displayMetrics.density
        assertTrue(button.layoutParams.width >= 48 * dp)
        assertTrue(button.layoutParams.height >= 48 * dp)
        assertEquals(context.getString(R.string.channel_rewards_title), button.contentDescription)
        assertTrue(button.isFocusable)
    }
    @Test fun `required input is labelled and limits text without submitting`() {
        val context = context()
        val binding = DialogChannelPointRewardsBinding.inflate(LayoutInflater.from(context))
        var changes = 0
        binding.input.addTextChangedListener { changes++ }
        binding.input.setText("x".repeat(700))
        assertEquals(500, binding.input.text!!.length)
        assertTrue(changes > 0)
        assertEquals(context.getString(R.string.channel_rewards_text_input), binding.inputLayout.hint)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, binding.status.accessibilityLiveRegion)
    }
    @Test fun `short and wide layouts retain scroll viewport at enlarged fonts`() {
        for (scale in listOf(1f, 2f)) {
            val context = context(scale)
            for ((width, height) in listOf(280 to 260, 600 to 500, 280 to 260)) {
                val binding = DialogChannelPointRewardsBinding.inflate(LayoutInflater.from(context))
                binding.balance.text = "Balance: 1,234,567 points"
                binding.status.text = "Twitch did not confirm the outcome. Points may have been spent."
                binding.summary.text = "A very long reward title and prompt. ".repeat(20)
                binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                binding.root.layout(0, 0, width, height)
                assertTrue("viewport should remain visible ($width, $height, $scale)", binding.scroll.height > 0)
                assertTrue(binding.scroll.bottom <= binding.root.height)
                assertEquals(1, binding.scroll.childCount)
            }
        }
    }
}
