package com.camelcreatives.rekodi.editor.model

data class AudioClip(
    val id: String = java.util.UUID.randomUUID().toString(),
    val filePath: String,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val durationMs: Long = 0,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val volumeGain: Float = 1.0f
)

data class WaveformPeaks(
    val samples: FloatArray,
    val sampleRate: Int,
    val totalDurationMs: Long
) {
    val peakCount: Int get() = samples.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveformPeaks) return false
        return samples.contentEquals(other.samples) &&
                sampleRate == other.sampleRate &&
                totalDurationMs == other.totalDurationMs
    }

    override fun hashCode(): Int {
        return samples.contentHashCode() * 31 + sampleRate
    }
}

enum class EditAction {
    TRIM, SPLIT, FADE_IN, FADE_OUT, VOLUME, NOISE_REDUCTION, SILENCE_TRIM
}

data class UndoState(
    val clips: List<AudioClip>,
    val description: String
)
