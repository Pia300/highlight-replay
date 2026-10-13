package io.github.pia300.highlightreplay.engine

internal class AudioPtsClock(
    sampleRate: Int,
    private val channelCount: Int
) {

    private val framesPerSecond = sampleRate

    var framesQueued: Long = 0L
        private set
    var framesDropped: Long = 0L
        private set

    fun currentPtsUs(): Long = framesQueued * 1_000_000L / framesPerSecond

    fun advanceQueued(bytes: Int) {
        framesQueued += bytes / frameSizeBytes()
    }

    fun countDropped(bytes: Int) {
        framesDropped += bytes / frameSizeBytes()
    }

    fun reset() {
        framesQueued = 0L
        framesDropped = 0L
    }

    private fun frameSizeBytes(): Int = channelCount * 2
}
