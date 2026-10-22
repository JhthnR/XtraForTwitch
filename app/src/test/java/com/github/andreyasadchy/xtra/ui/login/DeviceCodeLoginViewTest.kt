package com.github.andreyasadchy.xtra.ui.login

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.ActivityLoginBinding
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w360dp-h640dp-mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceCodeLoginViewTest {
    @Test fun `denied invalid and failed sign-ins remove stale QR actions and show accurate feedback`() {
        for ((reason, message) in listOf(
            DeviceCodeLoginController.Failure.DENIED to R.string.device_code_denied,
            DeviceCodeLoginController.Failure.INVALID_CODE to R.string.device_code_invalid,
            DeviceCodeLoginController.Failure.VALIDATION_FAILED to R.string.device_code_validation_failed)) {
            val h = Harness()
            h.render(ready())
            h.render(DeviceCodeLoginController.State.Rejected(reason))
            assertNull(h.panel.codeQr.drawable)
            assertFalse(h.panel.next.isEnabled)
            assertFalse(h.panel.copyCode.isEnabled)
            assertFalse(h.panel.openUrl.isEnabled)
            assertEquals(View.VISIBLE, h.panel.retryCode.visibility)
            assertEquals(h.context.getString(message), h.panel.codeStatus.text.toString())
        }
    }

    @Test fun `browser login retains its viewport with the TV entry visible or gone`() {
        val h = Harness()
        h.panel.root.visibility = View.GONE
        // Robolectric's WebView reports a zero intrinsic height in this detached
        // chain. Test the unchanged XML constraints with an ordinary viewport;
        // actual WebView sizing is also checked on the API 36.1 emulator.
        val browser = android.widget.FrameLayout(h.context)
        val parent = h.binding.root
        val index = parent.indexOfChild(h.binding.webView)
        val params = h.binding.webView.layoutParams
        browser.id = h.binding.webView.id
        parent.removeView(h.binding.webView)
        parent.addView(browser, index, params)
        h.binding.havingTrouble.visibility = View.VISIBLE
        for (showEntry in listOf(false, true)) {
            h.binding.deviceCodeLogin.visibility = if (showEntry) View.VISIBLE else View.GONE
            h.measure(360, 640)
            assertTrue("entry=$showEntry browser=${browser.height}", browser.height >= 480)
            assertTrue(browser.bottom <= h.binding.havingTrouble.top)
            if (showEntry) assertTrue(h.binding.deviceCodeLogin.bottom <= h.binding.havingTrouble.top)
            assertEquals(640, h.binding.havingTrouble.bottom)
        }
    }

    @Test fun `actual QR pixels decode to the canonical activation route with a quiet zone`() {
        val url = DeviceCodeLoginController.activationUrl("TEST1234")
        val bitmap = DeviceCodeQr.create(url)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
        assertEquals(url, decoded.text)
        assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
        assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width / 2, 4))
        assertFalse(decoded.text.contains("private"))
    }

    @Test fun `real phone panel reuses QR on ticks then removes expired actions and pixels`() {
        val h = Harness()
        assertEquals(View.GONE, h.binding.deviceCodeLogin.visibility)
        h.render(ready())
        val qr = h.panel.codeQr.drawable
        h.render(ready().copy(seconds = 59))
        assertSame(qr, h.panel.codeQr.drawable)
        h.measure(360, 640)
        assertEquals("TEST1234", h.panel.codeText.text.toString())
        assertTrue(h.panel.next.isEnabled)
        h.save("device-login-phone")
        h.render(DeviceCodeLoginController.State.Expired)
        assertNull(h.panel.codeQr.drawable)
        assertEquals(View.GONE, h.panel.codeQr.visibility)
        assertFalse(h.panel.copyCode.isEnabled)
        assertFalse(h.panel.openUrl.isEnabled)
        assertFalse(h.panel.next.isEnabled)
        assertEquals(View.VISIBLE, h.panel.retryCode.visibility)
    }

    @Test fun `wide TV layout at enlarged text preserves scroll access to every action`() {
        RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-television-mdpi")
        val h = Harness(2f)
        h.panel.copyCode.visibility = View.GONE
        h.render(ready())
        h.measure(960, 540)
        val qrBounds = android.graphics.Rect()
        val textBounds = android.graphics.Rect()
        h.panel.codeQr.getGlobalVisibleRect(qrBounds)
        h.panel.codeInstructions.getGlobalVisibleRect(textBounds)
        assertTrue(qrBounds.right <= textBounds.left)
        for (button in listOf(h.panel.next, h.panel.openUrl, h.panel.browserSignIn)) {
            assertTrue(button.measuredHeight >= 48)
            assertTrue(button.isFocusable)
            assertTrue(button.measuredWidth > 0)
        }
        val last = android.graphics.Rect()
        h.panel.browserSignIn.getDrawingRect(last)
        h.panel.root.offsetDescendantRectToMyCoords(h.panel.browserSignIn, last)
        h.panel.root.scrollTo(0, last.bottom)
        assertTrue(h.panel.root.scrollY > 0)
        assertTrue(last.bottom - h.panel.root.scrollY <= h.panel.root.height)
        h.save("device-login-tv-200pct-scrolled")
        h.panel.root.scrollTo(0, 0)
        h.save("device-login-tv-200pct")
        val normal = Harness()
        normal.panel.copyCode.visibility = View.GONE
        normal.render(ready())
        normal.measure(960, 540)
        normal.panel.browserSignIn.getDrawingRect(last)
        normal.panel.root.offsetDescendantRectToMyCoords(normal.panel.browserSignIn, last)
        assertTrue(last.bottom <= normal.panel.root.height)
        normal.save("device-login-tv")
        normal.measure(360, 640)
        assertTrue(normal.panel.codeText.width > 0)
        val compactQr = android.graphics.Rect()
        val compactText = android.graphics.Rect()
        normal.panel.codeQr.getDrawingRect(compactQr)
        normal.binding.root.offsetDescendantRectToMyCoords(normal.panel.codeQr, compactQr)
        normal.panel.codeInstructions.getDrawingRect(compactText)
        normal.binding.root.offsetDescendantRectToMyCoords(normal.panel.codeInstructions, compactText)
        normal.save("device-login-compact-after-resize")
        assertTrue("QR=$compactQr text=$compactText contentWidth=${normal.panel.root.getChildAt(0).width} orientation=${(normal.panel.root.getChildAt(0) as android.widget.LinearLayout).orientation}", compactQr.bottom <= compactText.top)
        normal.measure(960, 540)
        normal.panel.codeQr.getGlobalVisibleRect(qrBounds)
        normal.panel.codeInstructions.getGlobalVisibleRect(textBounds)
        assertTrue(qrBounds.right <= textBounds.left)
    }

    private fun ready() = DeviceCodeLoginController.State.Ready("TEST1234",
        DeviceCodeLoginController.activationUrl("TEST1234"), 60, false, true,
        DeviceCodeLoginController.Status.APPROVE)

    private class Harness(fontScale: Float = 1f) {
        private val application = RuntimeEnvironment.getApplication()
        private val configuration = android.content.res.Configuration(application.resources.configuration).apply { this.fontScale = fontScale }
        val context = ContextThemeWrapper(application.createConfigurationContext(configuration), R.style.BaseDarkTheme)
        val binding = ActivityLoginBinding.inflate(LayoutInflater.from(context))
        val panel = binding.deviceCodePane
        private val activity: LoginActivity = mock(defaultAnswer = Mockito.CALLS_REAL_METHODS,
            useConstructor = UseConstructor.parameterless())
        init {
            doReturn(context.resources).whenever(activity).resources
            ReflectionHelpers.setField(activity, "binding", binding)
            binding.webView.visibility = View.GONE
            binding.deviceCodePane.root.visibility = View.VISIBLE
            binding.root.setBackgroundColor(Color.rgb(20, 20, 24))
        }
        fun render(state: DeviceCodeLoginController.State) {
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "renderDeviceCode",
                ReflectionHelpers.ClassParameter.from(DeviceCodeLoginController.State::class.java, state))
        }
        fun measure(width: Int, height: Int) {
            binding.root.requestLayout()
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, width, height)
        }
        fun save(name: String) {
            val bitmap = Bitmap.createBitmap(binding.root.width, binding.root.height, Bitmap.Config.ARGB_8888)
            binding.root.draw(Canvas(bitmap))
            val file = File("build/ui-previews/$name-${android.os.Build.VERSION.SDK_INT}.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
