package com.github.andreyasadchy.xtra.ui.search

import android.app.Application
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentSearchBinding
import com.google.android.material.tabs.TabLayoutMediator
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class, sdk = [28])
class SearchPagerLifecycleTest {
    private lateinit var activity: ActivityController<FragmentActivity>
    private lateinit var owner: PagerOwner
    private val containerId = View.generateViewId()

    @Before fun setup() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        activity.get().setContentView(FrameLayout(activity.get()).apply { id = containerId })
        owner = PagerOwner()
        activity.get().supportFragmentManager.beginTransaction().add(containerId, owner).commitNow()
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
    }

    @Test fun `destroyed Search view releases pager lifecycle and adapter observers while owner is retained`() {
        val fragment = SearchPagerFragment()
        val fixture = fixture(fragment)
        assertTrue(fixture.adapter.hasObservers())
        activity.get().supportFragmentManager.beginTransaction().detach(owner).commitNow()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
        val lifecycle = owner.lifecycle as LifecycleRegistry
        val observersBefore = lifecycle.observerCount

        fragment.onDestroyView()

        assertNull(fixture.binding.viewPager.adapter)
        assertFalse("The old adapter must not retain pager or tab mediator observers", fixture.adapter.hasObservers())
        assertTrue("The lifecycle enforcer must leave the retained fragment lifecycle", lifecycle.observerCount < observersBefore)
        fixture.dispatchPageSelected()
        assertEquals("A detached pager must not deliver the old page callback", 0, fixture.pageChanges)
    }

    @Test fun `recreated Search pager keeps new tabs responsive without callbacks into its old view`() {
        val fragment = SearchPagerFragment()
        val old = fixture(fragment)
        fragment.onDestroyView()
        owner.requireView().let { (it as ViewGroup).removeAllViews() }
        val fresh = fixture(fragment)
        assertNotSame(old.binding.root, fresh.binding.root)
        assertSame(fresh.adapter, fresh.binding.viewPager.adapter)
        assertEquals(3, fresh.binding.tabLayout.tabCount)

        old.dispatchPageSelected()
        assertEquals(0, old.pageChanges)
        fresh.binding.tabLayout.getTabAt(1)!!.select()
        assertEquals(1, fresh.binding.viewPager.currentItem)
        assertTrue(fresh.pageChanges > 0)
        assertFalse(old.adapter.hasObservers())
        fragment.onDestroyView()
    }

    private fun fixture(fragment: SearchPagerFragment): Fixture {
        val context = ContextThemeWrapper(activity.get(), R.style.BaseDarkTheme)
        val binding = FragmentSearchBinding.bind(fragment.onCreateView(LayoutInflater.from(context), null, null))
        (owner.requireView() as ViewGroup).addView(binding.root)
        val adapter = object : FragmentStateAdapter(owner) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int) = Fragment()
        }
        binding.viewPager.adapter = adapter
        val fixture = Fixture(binding, adapter)
        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { fixture.pageChanges++ }
        }
        binding.viewPager.registerOnPageChangeCallback(callback)
        val mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = listOf("Channels", "Streams", "Games")[position]
        }.apply { attach() }
        ReflectionHelpers.setField(fragment, "pageChangeCallback", callback)
        ReflectionHelpers.setField(fragment, "tabMediator", mediator)
        fixture.pageChanges = 0
        return fixture
    }

    private class Fixture(val binding: FragmentSearchBinding, val adapter: FragmentStateAdapter) {
        var pageChanges = 0
        fun dispatchPageSelected() {
            ReflectionHelpers.getField<ViewPager2.OnPageChangeCallback>(binding.viewPager, "mExternalPageChangeCallbacks")
                .onPageSelected(1)
        }
    }

    class PagerOwner : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(requireContext())
    }
}
