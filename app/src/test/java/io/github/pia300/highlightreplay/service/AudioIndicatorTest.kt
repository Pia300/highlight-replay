package io.github.pia300.highlightreplay.service

import io.github.pia300.highlightreplay.service.NotificationFactory.AudioIndicator
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioIndicatorTest {

    @Test
    fun monitorDisabledNeverIndicatesAudio() {
        for (enabled in BOOLEANS) {
            for (sampled in BOOLEANS) {
                for (active in BOOLEANS) {
                    assertEquals(
                        AudioIndicator.DISABLED,
                        resolveAudioIndicator(
                            audioMonitored = false,
                            audioEnabled = enabled,
                            audioSampled = sampled,
                            audioActive = active
                        )
                    )
                }
            }
        }
    }

    @Test
    fun audioNotConfiguredReportsNotConfigured() {
        for (sampled in BOOLEANS) {
            for (active in BOOLEANS) {
                assertEquals(
                    AudioIndicator.NOT_CONFIGURED,
                    resolveAudioIndicator(
                        audioMonitored = true,
                        audioEnabled = false,
                        audioSampled = sampled,
                        audioActive = active
                    )
                )
            }
        }
    }

    @Test
    fun unsampledAudioIsAssumedActive() {
        assertEquals(
            AudioIndicator.ACTIVE,
            resolveAudioIndicator(
                audioMonitored = true,
                audioEnabled = true,
                audioSampled = false,
                audioActive = false
            )
        )
    }

    @Test
    fun sampledAudioFollowsReading() {
        assertEquals(
            AudioIndicator.ACTIVE,
            resolveAudioIndicator(
                audioMonitored = true,
                audioEnabled = true,
                audioSampled = true,
                audioActive = true
            )
        )
        assertEquals(
            AudioIndicator.SILENT,
            resolveAudioIndicator(
                audioMonitored = true,
                audioEnabled = true,
                audioSampled = true,
                audioActive = false
            )
        )
    }

    private companion object {
        val BOOLEANS = listOf(false, true)
    }
}
