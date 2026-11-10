package com.trinineba.quotesonlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundMixTest {
    @Test fun mixesSurviveSavingAndLoading() {
        val mix = listOf(SoundLayer(Soundscape.RAIN, 0.8f), SoundLayer(Soundscape.FIRE, 0.45f), SoundLayer(Soundscape.SATIE, 0.3f))
        assertEquals(mix, SoundMix.decode(SoundMix.encode(mix)))
    }

    @Test fun badOrOversizedMixesAreTrimmed() {
        val decoded = SoundMix.decode("rain:0.5,nonsense:1,rain:0.9,waves:2,fire:0.1,birds:0.4")
        assertEquals(listOf(Soundscape.RAIN, Soundscape.WAVES, Soundscape.FIRE), decoded.map { it.sound })
        assertTrue(decoded.all { it.volume in 0f..1f })
        assertTrue(SoundMix.decode("").isEmpty())
    }

    @Test fun everySoundHasACalmScene() {
        Soundscape.entries.forEach { assertTrue("${it.key} has no scene", it.scene != AppTheme.CLASSIC) }
        assertEquals(Soundscape.entries.size, Soundscape.entries.map { it.key }.toSet().size)
    }
}
