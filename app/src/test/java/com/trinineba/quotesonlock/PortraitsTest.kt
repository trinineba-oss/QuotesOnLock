package com.trinineba.quotesonlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PortraitsTest {
    private val dir = File("src/main/assets/portraits")

    @Test fun everyPortraitIsCreditedAndBelongsToAnAuthor() {
        val files = dir.listFiles().orEmpty().filter { it.name.endsWith(".webp") }.map { it.name.removeSuffix(".webp") }.toSet()
        val credits = File(dir, "credits.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.substringBefore('|') }.toSet()
        assertEquals(files, credits)
        val authors = File("src/main/res/raw/timeline.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { Portraits.slug(it.split('|')[1]) }.toSet()
        val stray = files - authors
        assertTrue("portraits with no author: $stray", stray.isEmpty())
        files.forEach { assertTrue("$it is too big", File(dir, "$it.webp").length() < 30_000) }
    }

    @Test fun namesBecomeFileNamesAndInitials() {
        assertEquals("sren_kierkegaard", Portraits.slug("Søren Kierkegaard"))
        assertEquals("honore_de_balzac", Portraits.slug("Honoré de Balzac"))
        assertEquals("w_e_b_du_bois", Portraits.slug("W. E. B. Du Bois"))
        assertEquals("CD", Portraits.initials("Charles Darwin"))
        assertEquals("E", Portraits.initials("Erasmus"))
        assertEquals("D", Portraits.initials("The Dhammapada"))
    }
}
