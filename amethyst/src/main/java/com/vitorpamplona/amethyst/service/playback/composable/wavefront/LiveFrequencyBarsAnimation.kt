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
package com.vitorpamplona.amethyst.service.playback.composable.wavefront

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vitorpamplona.amethyst.commons.audio.AudioSpectrumProvider
import com.vitorpamplona.amethyst.commons.audio.FrequencyBars
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState

/**
 * Live FFT bars tied to the given ExoPlayer's audio session id. Rebuilds the
 * underlying [android.media.audiofx.Visualizer] whenever the session id changes
 * (e.g. when the pool reuses a player for a new track).
 */
@Composable
fun LiveFrequencyBarsAnimation(
    mediaControllerState: MediaControllerState,
    modifier: Modifier = Modifier,
    binCount: Int = 32,
) {
    val controller = mediaControllerState.controller
    val exo = controller as? ExoPlayer ?: return

    var sessionId by remember { mutableIntStateOf(exo.audioSessionId) }
    DisposableEffect(controller) {
        val listener =
            object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    sessionId = audioSessionId
                }
            }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    if (sessionId == 0) return

    val provider = remember { AudioSpectrumProvider() }
    val flow = remember(sessionId, binCount) { provider.spectrum(sessionId, binCount) }
    FrequencyBars(spectrum = flow, modifier = modifier)
}
