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
package com.vitorpamplona.amethyst.desktop.ui.chats

import com.vitorpamplona.amethyst.commons.ui.chat.DmBroadcastStatus
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.sendAndWaitForResponse
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DmSendTracker(
    private val client: INostrClient,
) {
    private val _status = MutableStateFlow<DmBroadcastStatus>(DmBroadcastStatus.Idle)
    val status: StateFlow<DmBroadcastStatus> = _status.asStateFlow()

    suspend fun sendAndTrack(
        event: Event,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (relays.isEmpty()) {
            _status.value = DmBroadcastStatus.Failed("No relays available")
            delay(3000)
            _status.value = DmBroadcastStatus.Idle
            return
        }

        _status.value = DmBroadcastStatus.Sending(0, relays.size)

        val success = client.sendAndWaitForResponse(event, relays, 10)

        _status.value =
            if (success) {
                DmBroadcastStatus.Sent(relays.size)
            } else {
                DmBroadcastStatus.Failed("No relay accepted the message")
            }

        delay(3000)
        _status.value = DmBroadcastStatus.Idle
    }
}
