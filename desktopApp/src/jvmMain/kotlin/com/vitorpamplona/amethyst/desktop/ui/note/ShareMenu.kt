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
package com.vitorpamplona.amethyst.desktop.ui.note

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.desktop.model.LocalDesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class ShareMenuState {
    var expanded by mutableStateOf(false)
        private set

    fun open() {
        expanded = true
    }

    fun dismiss() {
        expanded = false
    }
}

@Composable
fun rememberShareMenuState(): ShareMenuState = remember { ShareMenuState() }

@Composable
fun ShareMenu(
    state: ShareMenuState,
    event: Event,
    relayManager: DesktopRelayConnectionManager,
) {
    val account = LocalDesktopIAccount.current
    val scope = rememberCoroutineScope()
    var showReportDialog by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = state.expanded,
        onDismissRequest = { state.dismiss() },
    ) {
        DropdownMenuItem(
            text = { Text("Copy Text") },
            onClick = {
                copyToClipboard(event.content)
                state.dismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Copy Note ID") },
            onClick = {
                copyToClipboard("nostr:${NNote.create(event.id)}")
                state.dismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Copy Event Link") },
            onClick = {
                val relays = relayManager.connectedRelays.value.take(3)
                copyToClipboard("nostr:${NEvent.create(event.id, event.pubKey, event.kind, relays)}")
                state.dismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Copy Raw JSON") },
            onClick = {
                copyToClipboard(event.toJson())
                state.dismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Copy Web Link") },
            onClick = {
                val nevent = NEvent.create(event.id, event.pubKey, event.kind, emptyList())
                copyToClipboard("https://njump.me/$nevent")
                state.dismiss()
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Broadcast") },
            onClick = {
                relayManager.broadcastToAll(event)
                state.dismiss()
            },
        )

        // Moderation actions require a writeable account (a local signer).
        if (account != null && account.isWriteable()) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Mute user") },
                onClick = {
                    scope.launch { account.hideUser(event.pubKey) }
                    state.dismiss()
                },
            )
            DropdownMenuItem(
                text = { Text("Report…") },
                onClick = {
                    showReportDialog = true
                    state.dismiss()
                },
            )
        }
    }

    if (showReportDialog && account != null) {
        ReportNoteDialog(
            onDismiss = { showReportDialog = false },
            onReport = { type, comment ->
                scope.launch { account.reportEvent(event, type, comment) }
            },
            onBlockAndReport = { type, comment ->
                scope.launch {
                    account.reportEvent(event, type, comment)
                    account.hideUser(event.pubKey)
                }
            },
        )
    }
}

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}
