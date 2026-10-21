package com.github.andreyasadchy.xtra.ui.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentChatBinding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class PipChatLayoutTest {
    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseDarkTheme)
    private val binding = FragmentChatBinding.inflate(LayoutInflater.from(context))
    private val fragment = ChatFragment().also { ReflectionHelpers.setField(it, "_binding", binding) }

    @Test fun `PiP fills chat with messages and restores composer draft and visible chrome`() {
        binding.messageView.visibility = View.VISIBLE
        binding.replyView.visibility = View.VISIBLE
        binding.editText.setText("Unsent draft with emotes")
        binding.replyText.text = "Reply target"
        binding.btnDown.visibility = View.VISIBLE
        measure(600, 900)
        assertTrue(binding.messageView.height > 0)
        fragment.setPipMode(true)
        measure(360, 780)
        assertEquals(780, binding.recyclerView.height)
        assertEquals(360, binding.recyclerView.width)
        assertEquals(0, binding.messageView.height)
        assertEquals(0, binding.replyView.height)
        assertEquals(0, binding.btnDown.height)
        assertEquals(View.VISIBLE, binding.messageView.visibility)
        assertEquals(View.VISIBLE, binding.btnDown.visibility)
        fragment.setPipMode(false)
        measure(600, 900)
        assertTrue(binding.messageView.height > 0)
        assertTrue(binding.replyView.height > 0)
        assertTrue(binding.btnDown.height > 0)
        assertEquals("Unsent draft with emotes", binding.editText.text.toString())
        assertEquals("Reply target", binding.replyText.text.toString())
    }

    @Test fun `asynchronous overlays stay suppressed and are excluded from accessibility`() {
        fragment.setPipMode(true)
        binding.chatStatus.visibility = View.VISIBLE
        binding.pollLayout.visibility = View.VISIBLE
        binding.predictionLayout.visibility = View.VISIBLE
        binding.emoteMenu.visibility = View.VISIBLE
        binding.btnDown.visibility = View.VISIBLE
        measure(360, 780)
        assertEquals(0, binding.chatStatus.height)
        assertEquals(0, binding.pollLayout.height)
        assertEquals(0, binding.predictionLayout.height)
        assertEquals(0, binding.emoteMenu.height)
        val rootChildren = arrayListOf<View>()
        binding.root.addChildrenForAccessibility(rootChildren)
        assertEquals(listOf(binding.chatContentColumn), rootChildren)
        val columnChildren = arrayListOf<View>()
        binding.chatContentColumn.addChildrenForAccessibility(columnChildren)
        assertEquals(listOf(binding.chatViewportFrame), columnChildren)
        val viewportChildren = arrayListOf<View>()
        binding.chatViewportFrame.addChildrenForAccessibility(viewportChildren)
        assertEquals(listOf(binding.recyclerView), viewportChildren)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        assertTrue(binding.root.dispatchTouchEvent(down))
        down.recycle()
        fragment.setPipMode(false)
        assertEquals(View.VISIBLE, binding.pollLayout.visibility)
        assertEquals(View.VISIBLE, binding.emoteMenu.visibility)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `overlays cannot paint over the message viewport during resize`() {
        binding.recyclerView.setBackgroundColor(Color.BLUE)
        binding.pollLayout.setBackgroundColor(Color.RED)
        binding.pollLayout.visibility = View.VISIBLE
        fragment.setPipMode(true)
        for ((width, height) in listOf(360 to 780, 540 to 1180, 360 to 780)) {
            measure(width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            binding.root.draw(Canvas(bitmap))
            assertEquals(Color.BLUE, bitmap.getPixel(width / 2, height / 2))
            assertEquals(Color.BLUE, bitmap.getPixel(width / 2, height - 1))
            bitmap.recycle()
        }
    }

    private fun measure(width: Int, height: Int) {
        shadowOf(Looper.getMainLooper()).idle()
        binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        binding.root.layout(0, 0, width, height)
    }
}
