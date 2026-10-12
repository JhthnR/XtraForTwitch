package com.github.andreyasadchy.xtra.util

import com.github.andreyasadchy.xtra.R
import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialThemeTest {
    @Test fun `rounded corners list picks the matching theme variant`() {
        assertEquals(R.style.DarkTheme, materialTheme("0", "0"))
        assertEquals(R.style.DarkTheme, materialTheme("0", null))
        assertEquals(R.style.LightThemeSmallCorners, materialTheme("2", "1"))
        assertEquals(R.style.AmoledThemeNoCorners, materialTheme("6", "2"))
        assertEquals(R.style.BlueTheme, materialTheme("3", "0"))
    }
}
