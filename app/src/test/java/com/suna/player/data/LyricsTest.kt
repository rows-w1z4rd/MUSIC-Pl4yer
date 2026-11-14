package com.suna.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {

    @Test
    fun readsTimedLines() {
        val l = Lyrics.parse("[ar:Someone]\n[00:01.50]First\n[00:04.2]Second\n[01:02.345]Third")!!
        assertTrue(l.synced)
        assertEquals(listOf(1500L, 4200L, 62345L), l.lines.map { it.timeMs })
        assertEquals(listOf("First", "Second", "Third"), l.lines.map { it.text })
    }

    @Test
    fun aLineWithSeveralStampsRepeats() {
        val l = Lyrics.parse("[00:10.00][00:30.00]Chorus\n[00:20.00]Verse")!!
        assertEquals(listOf("Chorus", "Verse", "Chorus"), l.lines.map { it.text })
    }

    @Test
    fun appliesOffset() {
        val l = Lyrics.parse("[offset:500]\n[00:02.00]Line")!!
        assertEquals(1500L, l.lines.single().timeMs)
    }

    @Test
    fun plainTextIsUnsynced() {
        val l = Lyrics.parse("\nFirst line\nSecond line\n\n")!!
        assertFalse(l.synced)
        assertEquals(listOf("First line", "Second line"), l.lines.map { it.text })
    }

    @Test
    fun emptyTextHasNoLyrics() {
        assertNull(Lyrics.parse("   \n\n"))
        assertNull(Lyrics.parse("[ti:Only a title]"))
    }

    @Test
    fun findsTheLineBeingSung() {
        val l = Lyrics.parse("[00:01.00]A\n[00:05.00]B\n[00:09.00]C")!!
        assertEquals(-1, l.lineAt(500))
        assertEquals(0, l.lineAt(1000))
        assertEquals(1, l.lineAt(8999))
        assertEquals(2, l.lineAt(60_000))
    }
}
