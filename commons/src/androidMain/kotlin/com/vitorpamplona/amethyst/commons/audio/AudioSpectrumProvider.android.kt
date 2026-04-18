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

import android.media.audiofx.Visualizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android implementation backed by [android.media.audiofx.Visualizer].
 *
 * Pass the ExoPlayer audio session id (`ExoPlayer.getAudioSessionId()`) as the
 * sessionKey. On some OEM builds the Visualizer API silently returns zeroed
 * data unless RECORD_AUDIO is granted — call sites should treat a flat
 * spectrum as "visualizer unavailable" and keep the static-art fallback.
 */
actual class AudioSpectrumProvider {
    actual fun spectrum(
        sessionKey: Any,
        binCount: Int,
    ): Flow<Spectrum> = callbackFlow<Spectrum> {
        val sessionId = sessionKey as? Int ?: run {
            trySend(silentSpectrum(binCount))
            close()
            return@callbackFlow
        }

        val vis =
            try {
                Visualizer(sessionId).apply {
                    val range = Visualizer.getCaptureSizeRange()
                    captureSize = range[1].coerceAtMost(1024)
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer,
                                waveform: ByteArray,
                                samplingRate: Int,
                            ) = Unit

                            override fun onFftDataCapture(
                                visualizer: Visualizer,
                                fft: ByteArray,
                                samplingRate: Int,
                            ) {
                                val mags = fftByteArrayToMagnitudes(fft)
                                trySend(Spectrum(mags.toLogBins(binCount)))
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true,
                    )
                    enabled = true
                }
            } catch (t: Throwable) {
                trySend(silentSpectrum(binCount))
                close(t)
                return@callbackFlow
            }

        awaitClose {
            try {
                vis.enabled = false
            } catch (_: Throwable) {
            }
            try {
                vis.release()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Android Visualizer FFT output layout is `[Rf0, Rf(N/2), Rf1, If1, Rf2, If2, ...]`
 * packed into signed bytes. Convert to float magnitudes normalized 0..1.
 */
private fun fftByteArrayToMagnitudes(fft: ByteArray): FloatArray {
    val floats = FloatArray(fft.size)
    for (i in fft.indices) floats[i] = fft[i].toFloat()
    return packedRealFftToMagnitudes(floats)
}
