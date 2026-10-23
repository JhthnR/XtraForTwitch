package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.model.chat.Emote
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EmoteNameIndexTest {

    @Test
    fun `finds emotes by exact name`() {
        val kekw = Emote(name = "KEKW", source = Emote.CHANNEL_BTTV)
        val index = EmoteNameIndex().apply { rebuild(listOf(kekw)) }

        assertSame(kekw, index["KEKW"])
        assertNull(index["kekw"])
        assertNull(index["KEK"])
    }

    @Test
    fun `the first emote with a name wins like the list lookup did`() {
        val channel = Emote(name = "Clap", source = Emote.CHANNEL_STV)
        val global = Emote(name = "Clap", source = Emote.GLOBAL_STV)
        val emotes = listOf(channel, global)
        val index = EmoteNameIndex().apply { rebuild(emotes) }

        assertSame(emotes.find { it.name == "Clap" }, index["Clap"])
    }

    @Test
    fun `rebuilding replaces removed emotes`() {
        val index = EmoteNameIndex()
        index.rebuild(listOf(Emote(name = "old")))
        val new = Emote(name = "new")
        index.rebuild(listOf(new))

        assertNull(index["old"])
        assertSame(new, index["new"])
    }

    @Test
    fun `emotes without a name are skipped`() {
        val index = EmoteNameIndex().apply { rebuild(listOf(Emote(name = null))) }

        assertNull(index[""])
    }
}
