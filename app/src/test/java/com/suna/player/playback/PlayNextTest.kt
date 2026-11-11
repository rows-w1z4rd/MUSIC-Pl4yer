package com.suna.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayNextTest {

    /** Simulates a queue of flags and applies "Play next" inserts the way the app does. */
    private class FakeQueue(initial: List<String>, var current: Int) {
        val items = initial.map { it to false }.toMutableList()

        fun playNext(name: String) {
            val index = playNextInsertIndex(current, items.size) { items[it].second }
            items.add(index, name to true)
        }

        fun names() = items.map { it.first }
    }

    @Test
    fun emptyQueueInsertsAtStart() {
        assertEquals(0, playNextInsertIndex(currentIndex = 0, itemCount = 0) { false })
    }

    @Test
    fun firstPlayNextGoesRightAfterCurrent() {
        val q = FakeQueue(listOf("a", "b", "c"), current = 0)
        q.playNext("x")
        assertEquals(listOf("a", "x", "b", "c"), q.names())
    }

    @Test
    fun severalPlayNextKeepTheOrderTheyWerePicked() {
        val q = FakeQueue(listOf("a", "b", "c"), current = 1)
        q.playNext("x")
        q.playNext("y")
        q.playNext("z")
        assertEquals(listOf("a", "b", "x", "y", "z", "c"), q.names())
    }

    @Test
    fun afterAPlayNextSongStartsNewOnesFollowTheRemainingOnes() {
        val q = FakeQueue(listOf("a", "b"), current = 0)
        q.playNext("x")
        q.playNext("y")
        q.current = 1 // "x" is now playing
        q.playNext("z")
        assertEquals(listOf("a", "x", "y", "z", "b"), q.names())
    }

    @Test
    fun currentIsLastItem() {
        val q = FakeQueue(listOf("a", "b"), current = 1)
        q.playNext("x")
        q.playNext("y")
        assertEquals(listOf("a", "b", "x", "y"), q.names())
    }

    @Test
    fun removingAPendingSongNeedsNoBookkeeping() {
        val q = FakeQueue(listOf("a", "b"), current = 0)
        q.playNext("x")
        q.playNext("y")
        q.items.removeAt(1) // remove "x"
        q.playNext("z")
        assertEquals(listOf("a", "y", "z", "b"), q.names())
    }
}
