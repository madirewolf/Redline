package com.redline.app.tempo

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** A cue word found in the audio, timed at its start. Which word it was is decided over the whole set. */
data class DetectedCue(
    /** Index of the first sample of the frame the word started in, counted from the first sample fed. */
    val onsetSample: Long,
    /** How likely the word is "bottom" rather than "top", from its sound alone. */
    val bottomLikelihood: Float,
    /** 10 ms frames of hiss before the first vowel: the "t" of "top". */
    val hissFrames: Int,
    val syllables: Int,
    /** From the first vowel to the end of the word. */
    val durationMs: Int
)

/**
 * Finds the spoken "bottom" and "top" cues in 16-bit mono PCM, without a speech recogniser.
 *
 * Audio is cut into 10 ms frames, each measured for loudness and zero-crossing rate (how often
 * the wave changes sign: low for a voiced vowel, high for hiss). A cue is a burst that rises
 * [ON_DB] above the tracked noise floor and lasts as long as a short word, with enough voiced
 * frames that a plate clank or a cough doesn't count. It's timed at the start of the word,
 * so rep times are exact to the frame, and any fixed delay (Bluetooth, the time it takes to
 * start saying the word) cancels out between the two cues of a rep.
 *
 * The two words differ in three ways. "Top" opens with the hiss of an aspirated "t" before
 * the vowel, while "bottom" opens straight into a voiced "b". "Bottom" has two loud syllables
 * and "top" one. And "bottom" is longer, both outright and next to the word before it, which
 * holds whatever the speaker's pace. Together they give a likelihood for each word;
 * [VoiceTempoMath.label] weighs those against the cues alternating.
 */
class CueDetector(val sampleRate: Int = 16_000) {
    private class Frame(val db: Float, val zcr: Float)

    private val hop = sampleRate / FRAMES_PER_SECOND

    // High-pass at about 100 Hz: drops DC offset and the thud of a dropped plate.
    private val highPassR = (1.0 - 2.0 * PI * HIGH_PASS_HZ / sampleRate).toFloat()
    private var previousInput = 0f
    private var previousOutput = 0f

    private var filled = 0
    private var sumSquares = 0.0
    private var crossings = 0
    private var lastSign = 0
    private var frameIndex = 0L

    private var floorDb = Float.NaN
    private var aboveRun = 0
    /** The latest frames heard outside a word, newest last, to find the consonant a word opens with. */
    private val recent = ArrayDeque<Frame>()
    private var inUtterance = false
    private val frames = ArrayList<Frame>()
    private var leadFrames = 0
    private var onsetFrame = 0L
    private var quietRun = 0
    private var previousSeconds: Float? = null
    private var previousEndFrame = 0L

    /** The latest frame's loudness above the noise floor, 0 to 1, for a level meter. */
    var level: Float = 0f
        private set

    /** Feeds [count] samples and returns any cues that finished in them. */
    fun feed(samples: ShortArray, count: Int = samples.size): List<DetectedCue> {
        var found: MutableList<DetectedCue>? = null
        for (i in 0 until count) {
            val x = samples[i].toFloat()
            val y = x - previousInput + highPassR * previousOutput
            previousInput = x
            previousOutput = y

            sumSquares += (y * y).toDouble()
            val sign = if (y > 0f) 1 else if (y < 0f) -1 else 0
            if (sign != 0) {
                if (lastSign != 0 && sign != lastSign) crossings++
                lastSign = sign
            }
            if (++filled == hop) {
                val db = (10.0 * log10(sumSquares / hop + 1.0)).toFloat()
                val zcr = crossings.toFloat() / hop
                filled = 0
                sumSquares = 0.0
                crossings = 0
                onFrame(Frame(db, zcr))?.let { cue -> (found ?: mutableListOf<DetectedCue>().also { found = it }) += cue }
                frameIndex++
            }
        }
        return found ?: emptyList()
    }

    /** Forgets the previous word, so the next one isn't compared with it. */
    fun resetSequence() {
        previousSeconds = null
    }

    private fun onFrame(frame: Frame): DetectedCue? {
        if (floorDb.isNaN()) floorDb = frame.db
        level = ((frame.db - floorDb) / LEVEL_RANGE_DB).coerceIn(0f, 1f)

        if (!inUtterance) {
            recent.addLast(frame)
            if (recent.size > HISTORY_FRAMES) recent.removeFirst()
            val loud = frameIndex >= WARMUP_FRAMES && frame.db > max(floorDb + ON_DB, MIN_ON_DB)
            if (loud) {
                if (++aboveRun >= ONSET_FRAMES) startWord()
            } else {
                aboveRun = 0
                trackFloor(frame.db)
            }
            return null
        }

        frames += frame
        quietRun = if (frame.db < floorDb + OFF_DB) quietRun + 1 else 0
        return when {
            quietRun >= OFFSET_FRAMES -> finish()
            frames.size >= STUCK_FRAMES -> {
                // The room got louder and stayed loud; that's the new floor, not a word.
                floorDb = frames.takeLast(OFFSET_FRAMES * 2).minOf { it.db }
                inUtterance = false
                aboveRun = 0
                null
            }
            else -> null
        }
    }

    /** The last [ONSET_FRAMES] frames were loud. Reaches back for a quieter consonant before them. */
    private fun startWord() {
        val history = recent.toList()
        val loudStart = history.size - ONSET_FRAMES
        var start = loudStart
        while (start > 0 && loudStart - start < MAX_LEAD_FRAMES && history[start - 1].db > floorDb + LEAD_DB) start--
        frames.clear()
        frames.addAll(history.subList(start, history.size))
        leadFrames = loudStart - start
        onsetFrame = frameIndex - (history.size - 1 - start)
        inUtterance = true
        quietRun = 0
        recent.clear()
    }

    private fun trackFloor(db: Float) {
        val rate = when {
            frameIndex < WARMUP_FRAMES -> 0.3f
            db < floorDb -> FLOOR_FALL
            else -> FLOOR_RISE
        }
        floorDb += (db - floorDb) * rate
    }

    private fun isVoiced(frame: Frame) = frame.zcr < VOICED_ZCR && frame.db > floorDb + OFF_DB

    private fun finish(): DetectedCue? {
        inUtterance = false
        aboveRun = 0
        val word = frames.subList(0, frames.size - quietRun)
        val loudFrames = word.size - leadFrames
        if (loudFrames !in MIN_FRAMES..MAX_FRAMES || word.count(::isVoiced) < MIN_VOICED_FRAMES) return null

        // The vowel starts at the first voiced frame near the word's peak; a quiet hum before it isn't it.
        val vowelDb = word.maxOf { it.db } - VOWEL_RANGE_DB
        val vowelStart = word.indexOfFirst { isVoiced(it) && it.db > vowelDb }.coerceAtLeast(0)
        val hiss = word.subList(0, vowelStart).count { it.zcr >= HISS_ZCR && it.db > floorDb + HISS_DB }
        val vowels = word.subList(vowelStart, word.size).map { it.db }
        val syllables = countSyllables(smooth(vowels))
        val seconds = vowels.size / FRAMES_PER_SECOND.toFloat()

        // Compare with the word before only within a set; after a long pause it's a new start.
        val previous = previousSeconds?.takeIf { onsetFrame - previousEndFrame <= CONTRAST_WINDOW_FRAMES }
        previousSeconds = seconds
        previousEndFrame = frameIndex
        return DetectedCue(
            onsetSample = onsetFrame * hop,
            bottomLikelihood = bottomLikelihood(hiss, syllables, seconds, previous),
            hissFrames = hiss,
            syllables = syllables,
            durationMs = vowels.size * 1000 / FRAMES_PER_SECOND
        )
    }

    companion object {
        const val FRAMES_PER_SECOND = 100
        const val HIGH_PASS_HZ = 100.0

        /** dB above the floor that starts a cue, and the level it must fall under to end. */
        const val ON_DB = 10f
        const val OFF_DB = 6f
        /** A quieter consonant just before the loud part still belongs to the word above this. */
        const val LEAD_DB = 6f
        /** A cue must also be at least this loud (dB re one 16-bit step), so hiss never counts. */
        const val MIN_ON_DB = 40f
        const val LEVEL_RANGE_DB = 30f

        const val WARMUP_FRAMES = 20
        const val ONSET_FRAMES = 3
        const val OFFSET_FRAMES = 12
        const val MAX_LEAD_FRAMES = 8
        const val HISTORY_FRAMES = ONSET_FRAMES + MAX_LEAD_FRAMES
        /** A word lasts 0.1 to 0.9 s; shorter is a clank, longer is talking. */
        const val MIN_FRAMES = 10
        const val MAX_FRAMES = 90
        const val STUCK_FRAMES = 300
        const val MIN_VOICED_FRAMES = 5
        const val VOICED_ZCR = 0.25f
        /** A vowel is within this many dB of the word's loudest frame. */
        const val VOWEL_RANGE_DB = 15f
        /**
         * A frame of the "t" hiss crosses zero this often and stands this far above the floor,
         * so a noisy room's own hiss under a quiet frame doesn't count as one.
         */
        const val HISS_ZCR = 0.3f
        const val HISS_DB = 8f

        const val FLOOR_FALL = 0.2f
        const val FLOOR_RISE = 0.02f

        /** A dip this deep between two loudness peaks splits them into two syllables. */
        const val DIP_DB = 3.5f
        /** A peak this far under the loudest is a consonant's release, not a syllable. */
        const val PEAK_RANGE_DB = 12f

        const val HISS_WEIGHT = 2f
        const val SYLLABLE_WEIGHT = 1.5f
        const val SPLIT_SECONDS = 0.24f
        const val DURATION_SCALE = 0.07f
        const val MAX_DURATION_EVIDENCE = 2f
        const val CONTRAST_WEIGHT = 2f
        const val MAX_CONTRAST_EVIDENCE = 1.5f
        const val CONTRAST_WINDOW_FRAMES = 10 * FRAMES_PER_SECOND

        /** Three-frame moving average, to keep frame-to-frame jitter from counting as syllables. */
        fun smooth(envelope: List<Float>): List<Float> = envelope.indices.map { i ->
            val from = max(0, i - 1)
            val to = min(envelope.lastIndex, i + 1)
            var sum = 0f
            for (j in from..to) sum += envelope[j]
            sum / (to - from + 1)
        }

        /**
         * Counts loudness peaks that rise and fall at least [DIP_DB] either side and come within
         * [PEAK_RANGE_DB] of the loudest one.
         */
        fun countSyllables(envelope: List<Float>): Int {
            if (envelope.isEmpty()) return 0
            val lowest = envelope.max() - PEAK_RANGE_DB
            var count = 0
            var rising = true
            var extreme = envelope.first()
            for (v in envelope) {
                if (rising) {
                    if (v > extreme) extreme = v
                    else if (extreme - v >= DIP_DB) {
                        if (extreme >= lowest) count++
                        rising = false
                        extreme = v
                    }
                } else {
                    if (v < extreme) extreme = v
                    else if (v - extreme >= DIP_DB) {
                        rising = true
                        extreme = v
                    }
                }
            }
            if (rising && extreme >= lowest) count++
            return max(count, 1)
        }

        /**
         * How likely a word is "bottom" rather than "top" from its sound alone: frames of hiss
         * before the vowel, loud syllables, seconds from the vowel to the end, and how that
         * compares with the word before. Two of the same word in a row are about as long, so
         * the comparison only speaks when the lengths differ.
         */
        fun bottomLikelihood(hissFrames: Int, syllables: Int, seconds: Float, previousSeconds: Float? = null): Float {
            var z = HISS_WEIGHT * (1 - min(hissFrames, 4)) +
                SYLLABLE_WEIGHT * (min(syllables, 2) - 1.5f) +
                ((seconds - SPLIT_SECONDS) / DURATION_SCALE).coerceIn(-MAX_DURATION_EVIDENCE, MAX_DURATION_EVIDENCE)
            if (previousSeconds != null && previousSeconds > 0f && seconds > 0f) {
                z += (CONTRAST_WEIGHT * ln(seconds / previousSeconds)).coerceIn(-MAX_CONTRAST_EVIDENCE, MAX_CONTRAST_EVIDENCE)
            }
            return (1.0 / (1.0 + exp(-z.toDouble()))).toFloat().coerceIn(0.02f, 0.98f)
        }
    }
}
