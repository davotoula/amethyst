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
import kotlinx.coroutines.flow.flowOf

/**
 * Desktop placeholder. VLCJ 4.8.3's [uk.co.caprica.vlcj.player.base.AudioApi.callback]
 * installs a PCM callback that REPLACES libvlc's audio output and, per vlcj docs,
 * cannot be unregistered once enabled. Hooking it on the shared audio-player pool
 * would force every subsequent track to be re-emitted through JavaSound, which is
 * beyond the scope of a visualizer.
 *
 * Until a separate "sniffer" player or a libvlc filter is wired up, the desktop
 * provider emits a single silent frame so call sites fall back to their static
 * art or placeholder UI.
 */
actual class AudioSpectrumProvider {
    actual fun spectrum(
        sessionKey: Any,
        binCount: Int,
    ): Flow<Spectrum> = flowOf(silentSpectrum(binCount))
}
