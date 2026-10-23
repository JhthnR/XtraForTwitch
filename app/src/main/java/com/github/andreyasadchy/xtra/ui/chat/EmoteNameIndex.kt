package com.github.andreyasadchy.xtra.ui.chat

import com.github.andreyasadchy.xtra.model.chat.Emote

/**
 * Looks up third-party emotes by name without scanning the whole list for every word.
 *
 * Rebuild it whenever the source list changes. When two emotes share a name, the one that
 * comes first in the list wins, matching the previous `find { it.name == word }` lookup.
 */
class EmoteNameIndex {

    @Volatile
    private var emotesByName: Map<String, Emote> = emptyMap()

    fun rebuild(emotes: List<Emote>) {
        val index = HashMap<String, Emote>(emotes.size * 2)
        emotes.forEach { emote ->
            val name = emote.name
            if (name != null && !index.containsKey(name)) {
                index[name] = emote
            }
        }
        emotesByName = index
    }

    operator fun get(name: String): Emote? = emotesByName[name]
}
