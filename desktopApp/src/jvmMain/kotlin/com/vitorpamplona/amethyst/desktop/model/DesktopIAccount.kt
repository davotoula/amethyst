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
package com.vitorpamplona.amethyst.desktop.model

import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.INwcSignerState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Request
import com.vitorpamplona.quartz.nip47WalletConnect.Response
import com.vitorpamplona.quartz.nip57Zaps.IPrivateZapsDecryptionCache
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.DualCase

/**
 * Desktop implementation of IAccount.
 *
 * Bridges the desktop AccountState.LoggedIn and DesktopLocalCache to the
 * shared IAccount interface used by commons ViewModels (ChatroomFeedViewModel,
 * ChatNewMessageState, etc.).
 *
 * For now, DM sending is a no-op stub that logs intent. Full send support
 * requires wiring through the relay client, which is a follow-up step.
 */
class DesktopIAccount(
    private val accountState: AccountState.LoggedIn,
    private val localCache: DesktopLocalCache,
) : IAccount {
    override val signer: NostrSigner = accountState.signer

    override val pubKey: String = accountState.pubKeyHex

    override val showSensitiveContent: Boolean? = null

    override val hiddenWordsCase: List<DualCase> = emptyList()

    override val hiddenUsersHashCodes: Set<Int> = emptySet()

    override val spammersHashCodes: Set<Int> = emptySet()

    override val chatroomList: ChatroomList = ChatroomList(accountState.pubKeyHex)

    override val nip47SignerState: INwcSignerState =
        object : INwcSignerState {
            override suspend fun decryptResponse(event: LnZapPaymentResponseEvent): Response? = null

            override suspend fun decryptRequest(event: LnZapPaymentRequestEvent): Request? = null

            override fun isNIP47Author(pubKey: String?): Boolean = false
        }

    override val privateZapsDecryptionCache: IPrivateZapsDecryptionCache =
        object : IPrivateZapsDecryptionCache {
            override fun cachedPrivateZap(zapRequest: LnZapRequestEvent): com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent? = null

            override suspend fun decryptPrivateZap(zapRequest: LnZapRequestEvent): com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent? = null
        }

    override fun userProfile(): User = localCache.getOrCreateUser(pubKey)

    override fun isWriteable(): Boolean = !accountState.isReadOnly

    override fun followingKeySet(): Set<String> = emptySet()

    override fun isHidden(user: User): Boolean = false

    override fun isAcceptable(note: Note): Boolean {
        // Accept all notes on desktop for now
        val event = note.event ?: return true
        return !localCache.hasBeenDeleted(event)
    }

    override suspend fun sendNip04PrivateMessage(eventTemplate: EventTemplate<PrivateDmEvent>) {
        // TODO: Wire through relay client for actual NIP-04 DM sending
        com.vitorpamplona.quartz.utils.Log
            .d("DesktopIAccount", "sendNip04PrivateMessage (stub)")
    }

    override suspend fun sendNip17PrivateMessage(template: EventTemplate<ChatMessageEvent>) {
        // TODO: Wire through relay client for actual NIP-17 DM sending
        com.vitorpamplona.quartz.utils.Log
            .d("DesktopIAccount", "sendNip17PrivateMessage (stub)")
    }
}
