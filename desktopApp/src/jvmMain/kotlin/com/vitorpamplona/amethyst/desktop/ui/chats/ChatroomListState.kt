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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.privateChats.Chatroom
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Represents a conversation entry in the list pane.
 */
@Stable
data class ConversationItem(
    val roomKey: ChatroomKey,
    val chatroom: Chatroom,
    val users: List<User>,
    val displayName: String,
    val lastMessagePreview: String,
    val lastMessageTimestamp: Long,
    val isGroup: Boolean,
    val hasUnread: Boolean,
)

/**
 * Tab selection for the conversation list.
 */
enum class ConversationTab {
    KNOWN,
    NEW,
}

/**
 * Manages conversation list state for the desktop DM screen.
 *
 * Derives known/new rooms from IAccount.chatroomList:
 * - Known: rooms where the user has sent a message (ownerSentMessage = true)
 * - New: rooms where the user has NOT sent a message (incoming from unknown)
 *
 * Provides selectedTab and selectedRoom StateFlows that drive the UI.
 */
@Stable
class ChatroomListState(
    private val account: IAccount,
    private val cacheProvider: ICacheProvider,
    private val scope: CoroutineScope,
) {
    private val _selectedTab = MutableStateFlow(ConversationTab.KNOWN)
    val selectedTab: StateFlow<ConversationTab> = _selectedTab.asStateFlow()

    private val _selectedRoom = MutableStateFlow<ChatroomKey?>(null)
    val selectedRoom: StateFlow<ChatroomKey?> = _selectedRoom.asStateFlow()

    private val _knownRooms = MutableStateFlow<List<ConversationItem>>(emptyList())
    val knownRooms: StateFlow<List<ConversationItem>> = _knownRooms.asStateFlow()

    private val _newRooms = MutableStateFlow<List<ConversationItem>>(emptyList())
    val newRooms: StateFlow<List<ConversationItem>> = _newRooms.asStateFlow()

    init {
        // Periodically refresh the room list from the account's chatroom list.
        // This is a simple polling approach; a production implementation would
        // observe the chatroom list's changesFlow per room.
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshRooms()
                delay(2000)
            }
        }
    }

    fun selectTab(tab: ConversationTab) {
        _selectedTab.value = tab
    }

    fun selectRoom(roomKey: ChatroomKey) {
        _selectedRoom.value = roomKey
    }

    fun clearSelection() {
        _selectedRoom.value = null
    }

    private fun refreshRooms() {
        val chatroomList = account.chatroomList
        val known = mutableListOf<ConversationItem>()
        val new = mutableListOf<ConversationItem>()

        chatroomList.rooms.forEach { key, chatroom ->
            val users = key.users.mapNotNull { cacheProvider.getUserIfExists(it) as? User }
            val displayName =
                if (users.isNotEmpty()) {
                    users.joinToString(", ") { it.toBestDisplayName() }
                } else {
                    key.users
                        .firstOrNull()
                        ?.take(12)
                        ?.let { "$it..." } ?: "Unknown"
                }

            val newestMessage = chatroom.newestMessage
            val lastPreview = newestMessage?.event?.content?.take(80) ?: ""
            val lastTimestamp = newestMessage?.createdAt() ?: 0L

            // Skip rooms with no messages
            if (chatroom.messages.isEmpty()) return@forEach

            val item =
                ConversationItem(
                    roomKey = key,
                    chatroom = chatroom,
                    users = users,
                    displayName = displayName,
                    lastMessagePreview = lastPreview,
                    lastMessageTimestamp = lastTimestamp,
                    isGroup = key.users.size > 1,
                    hasUnread = !chatroom.ownerSentMessage && newestMessage != null,
                )

            if (chatroom.ownerSentMessage) {
                known.add(item)
            } else {
                new.add(item)
            }
        }

        // Sort by most recent message
        _knownRooms.value = known.sortedByDescending { it.lastMessageTimestamp }
        _newRooms.value = new.sortedByDescending { it.lastMessageTimestamp }
    }
}
