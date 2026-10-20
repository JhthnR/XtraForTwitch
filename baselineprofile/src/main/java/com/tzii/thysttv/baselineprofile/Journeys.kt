package com.tzii.thysttv.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.tzii.thysttv.benchmark"
private const val TIMEOUT_MS = 10_000L

/** Stable resource IDs keep this independent of language and network content. */
internal fun MacrobenchmarkScope.launchHome() {
    startActivityAndWait()
    check(device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "navBar")), TIMEOUT_MS)) {
        "Main navigation did not appear. Use a clean benchmark install with default tabs."
    }
}

internal fun MacrobenchmarkScope.browseLocalStats() {
    checkNotNull(device.findObject(By.res(TARGET_PACKAGE, "statsFragment"))) { "Default Stats tab is missing" }.click()
    check(device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "statsRecyclerView")), TIMEOUT_MS))
    checkNotNull(device.findObject(By.res(TARGET_PACKAGE, "range30Days"))).click()
    checkNotNull(device.findObject(By.res(TARGET_PACKAGE, "rangeAllTime"))).click()
    repeat(2) { swipeStats(up = true) }
    repeat(2) { swipeStats(up = false) }
}

private fun MacrobenchmarkScope.swipeStats(up: Boolean) {
    device.waitForIdle()
    // Range updates/scrolling replace accessibility nodes. Resolve current bounds
    // for each gesture instead of retaining a UiObject2 through those updates.
    val bounds = checkNotNull(device.wait(Until.findObject(By.res(TARGET_PACKAGE, "statsRecyclerView")), TIMEOUT_MS)).visibleBounds
    // Once the app bar collapses, list bounds can reach behind the status bar.
    // Use the middle half so a downward gesture cannot open system notifications.
    val inset = (bounds.height() / 4).coerceAtLeast(32)
    val top = bounds.top + inset
    val bottom = bounds.bottom - inset
    check(bottom > top) { "Stats list has no usable gesture area" }
    check(device.swipe(bounds.centerX(), if (up) bottom else top,
        bounds.centerX(), if (up) top else bottom, 24))
}
