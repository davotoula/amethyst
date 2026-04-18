/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.audio

import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

/** One frame of frequency-domain magnitudes, ordered low→high Hz and normalized 0f..1f. */
class Spectrum(
    val bins: FloatArray,
)

/**
 * Platform-specific source of live audio spectrum frames.
 *
 * The [sessionKey] identifies the currently-playing native session:
 *  - Android: the ExoPlayer audio session id (Int).
 *  - Desktop: the VLCJ MediaPlayer instance.
 */
expect class AudioSpectrumProvider() {
    fun spectrum(
        sessionKey: Any,
        binCount: Int = 32,
    ): Flow<Spectrum>
}

/**
 * Groups a linear-frequency magnitude array (index 0 = DC, last index = Nyquist)
 * into [binCount] log-spaced buckets so bass/mid get more bars than the treble
 * tail. Output is normalized 0f..1f using [floorDb] as the silence floor.
 */
fun FloatArray.toLogBins(
    binCount: Int,
    floorDb: Float = -60f,
): FloatArray {
    if (isEmpty() || binCount <= 0) return FloatArray(0)

    val step = ln(size.toDouble()) / binCount

    val out = FloatArray(binCount)
    for (b in 0 until binCount) {
        val lo = exp(step * b).toInt().coerceAtLeast(1)
        val hi = exp(step * (b + 1)).toInt().coerceAtMost(size).coerceAtLeast(lo + 1)
        var peak = 0f
        for (i in lo until hi) if (this[i] > peak) peak = this[i]
        val db = if (peak > 0f) 20f * log10(peak) else floorDb
        out[b] = ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
    }
    return out
}

/**
 * Converts the interleaved real FFT layout `[re0, re(N/2), re1, im1, ...]`
 * (used by Android's Visualizer and JTransforms `realForward`) into
 * magnitudes, peak-normalized to 0f..1f.
 */
internal fun packedRealFftToMagnitudes(fft: FloatArray): FloatArray {
    val n = fft.size
    if (n < 2) return FloatArray(0)
    val out = FloatArray(n / 2 + 1)
    out[0] = abs(fft[0])
    out[n / 2] = abs(fft[1])
    var k = 1
    while (k < n / 2) {
        val re = fft[2 * k]
        val im = fft[2 * k + 1]
        out[k] = sqrt(re * re + im * im)
        k++
    }
    var peak = 0f
    for (v in out) if (v > peak) peak = v
    if (peak > 0f) {
        val inv = 1f / peak
        for (i in out.indices) out[i] *= inv
    }
    return out
}

internal fun silentSpectrum(binCount: Int): Spectrum = Spectrum(FloatArray(binCount))
