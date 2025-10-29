/**
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
package com.vitorpamplona.amethyst.model

import android.util.LruCache
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.observables.LatestByKindAndAuthor
import com.vitorpamplona.amethyst.model.observables.LatestByKindWithETag
import com.vitorpamplona.amethyst.model.privateChats.ChatroomList
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.note.dateFormatter
import com.vitorpamplona.ammolite.relays.BundledInsert
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.experimental.relationshipStatus.ContactCardEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.tagValueContains
import com.vitorpamplona.quartz.nip01Core.crypto.checkSignature
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.taggedAddresses
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag
import com.vitorpamplona.quartz.nip01Core.tags.events.forEachTaggedEventId
import com.vitorpamplona.quartz.nip01Core.tags.events.isTaggedEvent
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUsers
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip03Timestamp.VerificationState
import com.vitorpamplona.quartz.nip03Timestamp.VerificationStateCache
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionIndex
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.isATag
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeProfilesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.WrappedEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90StatusEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90UserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90UserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

interface ILocalCache {
    fun markAsSeen(
        eventId: String,
        relay: NormalizedRelayUrl,
    ) {}
}

object LocalCache : ILocalCache {
    val antiSpam = AntiSpamFilter()

    val users = LargeSoftCache<HexKey, User>()
    val notes = LargeSoftCache<HexKey, Note>()
    val addressables = LargeSoftCache<Address, AddressableNote>()

    val chatroomList = LargeCache<HexKey, ChatroomList>()
    val publicChatChannels = LargeCache<HexKey, PublicChatChannel>()
    val liveChatChannels = LargeCache<Address, LiveActivitiesChannel>()
    val ephemeralChannels = LargeCache<RoomId, EphemeralChatChannel>()

    val awaitingPaymentRequests = ConcurrentHashMap<HexKey, Pair<Note?, suspend (LnZapPaymentResponseEvent) -> Unit>>(10)

    val relayHints = HintIndexer()

    val deletionIndex = DeletionIndex()

    val observablesByKindAndETag = ConcurrentHashMap<Int, ConcurrentHashMap<HexKey, LatestByKindWithETag<Event>>>(10)
    val observablesByKindAndAuthor = ConcurrentHashMap<Int, ConcurrentHashMap<HexKey, LatestByKindAndAuthor<Event>>>(10)

    fun <T : Event> observeETag(
        kind: Int,
        eventId: HexKey,
        scope: CoroutineScope,
    ): LatestByKindWithETag<T> {
        var eTagList = observablesByKindAndETag.get(kind)

        if (eTagList == null) {
            eTagList = ConcurrentHashMap<HexKey, LatestByKindWithETag<T>>(1) as ConcurrentHashMap<HexKey, LatestByKindWithETag<Event>>
            observablesByKindAndETag.put(kind, eTagList)
        }

        val value = eTagList.get(eventId)

        return if (value != null) {
            value
        } else {
            val newObject = LatestByKindWithETag<T>(kind, eventId) as LatestByKindWithETag<Event>
            val obj = eTagList.putIfAbsent(eventId, newObject) ?: newObject
            if (obj == newObject) {
                // initialize
                scope.launch(Dispatchers.IO) {
                    obj.init()
                }
            }
            obj
        } as LatestByKindWithETag<T>
    }

    fun <T : Event> observeAuthor(
        kind: Int,
        pubkey: HexKey,
        scope: CoroutineScope,
    ): LatestByKindAndAuthor<T> {
        var authorObsList = observablesByKindAndAuthor.get(kind)

        if (authorObsList == null) {
            authorObsList = ConcurrentHashMap<HexKey, LatestByKindAndAuthor<T>>(1) as ConcurrentHashMap<HexKey, LatestByKindAndAuthor<Event>>
            observablesByKindAndAuthor.put(kind, authorObsList)
        }

        val value = authorObsList.get(pubkey)

        return if (value != null) {
            value
        } else {
            val newObject = LatestByKindAndAuthor<T>(kind, pubkey) as LatestByKindAndAuthor<Event>
            val obj = authorObsList.putIfAbsent(pubkey, newObject) ?: newObject
            if (obj == newObject) {
                // initialize
                scope.launch(Dispatchers.IO) {
                    obj.init()
                }
            }
            obj
        } as LatestByKindAndAuthor<T>
    }

    private fun updateObservables(event: Event) {
        observablesByKindAndETag[event.kind]?.let { observablesOfKind ->
            event.forEachTaggedEventId {
                observablesOfKind[it]?.updateIfMatches(event)
            }
        }

        observablesByKindAndAuthor[event.kind]?.get(event.pubKey)?.updateIfMatches(event)
    }

    fun checkGetOrCreateUser(key: String): User? = getOrCreateUser(key)

    fun getOrCreateUser(key: HexKey): User {
        require(isValidHex(key = key)) { "$key is not a valid hex" }

        return users.getOrCreate(key) {
            val nip65RelayListNote = getOrCreateAddressableNoteInternal(AdvertisedRelayListEvent.createAddress(key))
            val dmRelayListNote = getOrCreateAddressableNoteInternal(ChatMessageRelayListEvent.createAddress(key))
            User(it, nip65RelayListNote, dmRelayListNote)
        }
    }

    fun getUserIfExists(key: String): User? {
        if (key.isEmpty()) return null
        return users.get(key)
    }

    fun getAddressableNoteIfExists(key: String): AddressableNote? = Address.parse(key)?.let { addressables.get(it) }

    fun getAddressableNoteIfExists(address: Address): AddressableNote? = addressables.get(address)

    fun getNoteIfExists(key: String): Note? = if (key.length == 64) notes.get(key) else Address.parse(key)?.let { addressables.get(it) }

    fun getNoteIfExists(key: ETag): Note? = notes.get(key.eventId)

    fun getPublicChatChannelIfExists(key: String): PublicChatChannel? = publicChatChannels.get(key)

    fun getEphemeralChatChannelIfExists(key: RoomId): EphemeralChatChannel? = ephemeralChannels.get(key)

    fun getLiveActivityChannelIfExists(key: Address): LiveActivitiesChannel? = liveChatChannels.get(key)

    fun getNoteIfExists(event: Event): Note? =
        if (event is AddressableEvent) {
            getAddressableNoteIfExists(event.addressTag())
        } else {
            getNoteIfExists(event.id)
        }

    fun getOrCreateNote(event: Event): Note =
        if (event is AddressableEvent) {
            getOrCreateAddressableNote(event.address())
        } else {
            getOrCreateNote(event.id)
        }

    fun checkGetOrCreateNote(etag: ETag): Note? {
        if (isValidHex(etag.eventId)) {
            return getOrCreateNote(etag)
        }
        return null
    }

    fun checkGetOrCreateNote(key: String): Note? {
        if (ATag.isATag(key)) {
            return checkGetOrCreateAddressableNote(key)
        }
        if (isValidHex(key)) {
            val note = getOrCreateNote(key)
            val noteEvent = note.event
            return if (noteEvent is AddressableEvent) {
                // upgrade to the latest
                val newNote = checkGetOrCreateAddressableNote(noteEvent.aTag().toTag())

                if (newNote != null && newNote.event == null) {
                    val author = note.author ?: getOrCreateUser(noteEvent.pubKey)
                    newNote.loadEvent(noteEvent as Event, author, emptyList())
                    note.moveAllReferencesTo(newNote)
                }

                newNote
            } else {
                note
            }
        }
        return null
    }

    fun getOrAddAliasNote(
        idHex: String,
        note: Note,
    ): Note {
        require(isValidHex(idHex)) { "$idHex is not a valid hex" }

        return notes.getOrCreate(idHex) {
            note
        }
    }

    fun getOrCreateNote(idHex: String): Note {
        require(isValidHex(idHex)) { "$idHex is not a valid hex" }

        return notes.getOrCreate(idHex) {
            Note(idHex)
        }
    }

    fun getOrCreateChatroomList(key: HexKey): ChatroomList = chatroomList.getOrCreate(key) { ChatroomList(key) }

    fun getOrCreatePublicChatChannel(key: HexKey): PublicChatChannel = publicChatChannels.getOrCreate(key) { PublicChatChannel(key) }

    fun getOrCreateLiveChannel(key: Address): LiveActivitiesChannel = liveChatChannels.getOrCreate(key) { LiveActivitiesChannel(key) }

    fun getOrCreateEphemeralChannel(key: RoomId): EphemeralChatChannel = ephemeralChannels.getOrCreate(key) { EphemeralChatChannel(key) }

    fun checkGetOrCreatePublicChatChannel(key: String): PublicChatChannel? {
        if (isValidHex(key)) {
            return getOrCreatePublicChatChannel(key)
        }
        return null
    }

    private fun isValidHex(key: String): Boolean {
        if (key.length != 64) return false
        return Hex.isHex64(key)
    }

    fun checkGetOrCreateAddressableNote(key: String): AddressableNote? =
        try {
            val addr = Address.parse(key)
            if (addr != null) {
                getOrCreateAddressableNote(addr)
            } else {
                null
            }
        } catch (e: IllegalArgumentException) {
            Log.e("LocalCache", "Invalid Key to create channel: $key", e)
            null
        }

    fun getOrCreateAddressableNoteInternal(key: Address): AddressableNote = addressables.getOrCreate(key) { AddressableNote(key) }

    fun getOrCreateAddressableNote(key: Address): AddressableNote {
        val note = getOrCreateAddressableNoteInternal(key)
        // Loads the user outside a Syncronized block to avoid blocking
        if (note.author == null) {
            note.author = checkGetOrCreateUser(key.pubKeyHex)
        }
        return note
    }

    fun getOrCreateNote(key: GenericETag): Note {
        val note = getOrCreateNote(key.eventId)
        // Loads the user outside a Syncronized block to avoid blocking
        val possibleAuthor = key.author
        if (note.author == null && possibleAuthor != null) {
            note.author = checkGetOrCreateUser(possibleAuthor)
        }
        val relayHint = key.relay
        if (relayHint != null) {
            note.addRelay(relayHint)
        }
        return note
    }

    fun consume(
        event: MetadataEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        // new event
        val oldUser = getOrCreateUser(event.pubKey)
        val currentMetadata = oldUser.latestMetadata

        if (currentMetadata == null || event.createdAt > currentMetadata.createdAt) {
            oldUser.latestMetadata = event

            val newUserMetadata = event.contactMetaData()
            if (newUserMetadata != null && (wasVerified || justVerify(event))) {
                oldUser.updateUserInfo(newUserMetadata, event)
                if (relay != null) {
                    oldUser.addRelayBeingUsed(relay, event.createdAt)
                    if (!relay.isLocalHost()) {
                        oldUser.latestMetadataRelay = relay
                    }
                }

                return true
            }
        }

        return false
    }

    fun consume(
        event: ContactListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val user = getOrCreateUser(event.pubKey)

        // avoids processing empty contact lists.
        if (event.createdAt > (user.latestContactList?.createdAt ?: 0) && !event.tags.isEmpty() && (wasVerified || justVerify(event))) {
            user.updateContactList(event)
            // Log.d("CL", "Consumed contact list ${user.toNostrUri()} ${event.relays()?.size}")

            updateObservables(event)

            return true
        }

        return false
    }

    fun consume(
        event: BookmarkListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: TextNoteEvent,
        relay: NormalizedRelayUrl? = null,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: PublicMessageEvent,
        relay: NormalizedRelayUrl? = null,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: TorrentEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: InteractiveStoryPrologueEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: InteractiveStorySceneEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: InteractiveStoryReadingStateEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consumeRegularEvent(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (event is BaseNoteEvent && antiSpam.isSpam(event, relay)) {
            return false
        }

        if (wasVerified || justVerify(event)) {
            val replyTo = computeReplyTo(event)

            note.loadEvent(event, author, replyTo)

            // Counts the replies
            replyTo.forEach { it.addReply(note) }

            refreshNewNoteObservers(note)

            return true
        } else {
            return false
        }
    }

    fun consume(
        event: PictureEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: VoiceEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: VoiceReplyEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    @Suppress("DEPRECATION")
    fun consume(
        event: TorrentCommentEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90ContentDiscoveryResponseEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90ContentDiscoveryRequestEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90StatusEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90UserDiscoveryResponseEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: NIP90UserDiscoveryRequestEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: GoalEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: GitPatchEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: GitIssueEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    @Suppress("DEPRECATION")
    fun consume(
        event: GitReplyEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: LongTextNoteEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event?.id == event.id) return wasVerified

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if (isVerified || justVerify(event)) {
            val replyTo = computeReplyTo(event)

            if (event.createdAt > (note.createdAt() ?: 0L)) {
                note.loadEvent(event, author, replyTo)

                refreshNewNoteObservers(note)

                return true
            }
        }

        return false
    }

    fun consume(
        event: WikiNoteEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event?.id == event.id) return wasVerified

        if (antiSpam.isSpam(event, relay)) {
            return false
        }

        if (isVerified || justVerify(event)) {
            if (event.createdAt > (note.createdAt() ?: 0L)) {
                val replyTo = computeReplyTo(event)

                note.loadEvent(event, author, replyTo)

                refreshNewNoteObservers(note)

                return true
            }
        }

        return false
    }

    @Suppress("DEPRECATION")
    fun computeReplyTo(event: Event): List<Note> =
        when (event) {
            is PollNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is WikiNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is LongTextNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is GitReplyEvent -> event.tagsWithoutCitations().filter { it != event.repository()?.toTag() }.mapNotNull { checkGetOrCreateNote(it) }
            is TextNoteEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }
            is CommentEvent -> event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }

            is VoiceReplyEvent -> event.markedReplyTos().mapNotNull { checkGetOrCreateNote(it) }

            is ChatMessageEvent -> event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }
            is ChatMessageEncryptedFileHeaderEvent -> event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }

            is LnZapEvent ->
                event.zappedPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) } +
                    (event.zapRequest?.taggedAddresses()?.map { getOrCreateAddressableNote(it) } ?: emptyList())
            is LnZapRequestEvent ->
                event.zappedPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            is BadgeProfilesEvent ->
                event.badgeAwardEvents().mapNotNull { checkGetOrCreateNote(it) } +
                    event.badgeAwardDefinitions().map { getOrCreateAddressableNote(it) }
            is BadgeAwardEvent -> event.awardDefinition().map { getOrCreateAddressableNote(it) }
            is PrivateDmEvent -> event.taggedEvents().mapNotNull { checkGetOrCreateNote(it) }
            is RepostEvent ->
                listOfNotNull(
                    event.boostedEventId()?.let { checkGetOrCreateNote(it) },
                    event.boostedAddress()?.let { getOrCreateAddressableNote(it) },
                )
            is GenericRepostEvent ->
                listOfNotNull(
                    event.boostedEventId()?.let { checkGetOrCreateNote(it) },
                    event.boostedAddress()?.let { getOrCreateAddressableNote(it) },
                )
            is CommunityPostApprovalEvent ->
                event.approvedEvents().mapNotNull { checkGetOrCreateNote(it) } +
                    event.approvedAddresses().map { getOrCreateAddressableNote(it) }
            is ReactionEvent ->
                event.originalPost().mapNotNull { checkGetOrCreateNote(it) } +
                    event.taggedAddresses().map { getOrCreateAddressableNote(it) }
            is ReportEvent ->
                event.reportedPost().mapNotNull { checkGetOrCreateNote(it.eventId) } +
                    event.reportedAddresses().map { getOrCreateAddressableNote(it.address) }
            is ChannelMessageEvent ->
                event
                    .tagsWithoutCitations()
                    .filter { it != event.channelId() }
                    .mapNotNull { checkGetOrCreateNote(it) }
            is LiveActivitiesChatMessageEvent ->
                event
                    .tagsWithoutCitations()
                    .filter { it != event.activity()?.toTag() }
                    .mapNotNull { checkGetOrCreateNote(it) }
            is TorrentCommentEvent ->
                event.tagsWithoutCitations().mapNotNull { checkGetOrCreateNote(it) }

            else -> emptyList()
        }

    fun consume(
        event: PollNoteEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    private fun consume(
        event: LiveActivitiesEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        if (note.event?.id == event.id) return false

        if (event.createdAt > (note.createdAt() ?: 0L) && (isVerified || justVerify(event))) {
            note.loadEvent(event, author, emptyList())

            val channel = getOrCreateLiveChannel(note.address)

            if (relay != null) {
                channel.addRelay(relay)
            }

            val creator = event.host()?.let { checkGetOrCreateUser(it.pubKey) } ?: author

            channel.updateChannelInfo(creator, event, note)

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: MuteListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: CommunityListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: GitRepositoryEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: ChannelListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: BlossomServersEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: FileServersEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: PeopleListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: EphemeralChatListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: FollowListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: AdvertisedRelayListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: ChatMessageRelayListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: PrivateOutboxRelayListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: HashtagListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: GeohashListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: SearchRelayListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: BlockedRelayListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: TrustedRelayListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: ProxyRelayListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: IndexerRelayListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: BroadcastRelayListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CommunityDefinitionEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: EmojiPackSelectionEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: EmojiPackEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: ClassifiedsEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: PinListEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: RelaySetEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: AudioTrackEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: VideoVerticalEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: VideoHorizontalEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: VideoNormalEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    private fun consume(
        event: VideoShortEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: StatusEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val note = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(note)
                true
            } else {
                wasVerified
            }

        // Already processed this event.
        if (note.event?.id == event.id) return false

        if (event.createdAt > (note.createdAt() ?: 0L) && (isVerified || justVerify(event))) {
            note.loadEvent(event, author, emptyList())

            author.flowSet?.statuses?.invalidateData()

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: ContactCardEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: OtsEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (version.event?.id == event.id) return false

        if (wasVerified || justVerify(event)) {
            if (version.event == null) {
                version.loadEvent(event, author, emptyList())
                version.flowSet?.ots?.invalidateData()
            }

            refreshNewNoteObservers(version)
            return true
        }

        return false
    }

    fun consume(
        event: BadgeDefinitionEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: BadgeProfilesEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: BadgeAwardEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    private fun consume(
        event: NNSEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: AppDefinitionEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CalendarEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CalendarDateSlotEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CalendarTimeSlotEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consume(
        event: CalendarRSVPEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    private fun consumeBaseReplaceable(
        event: BaseAddressableEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val version = getOrCreateNote(event.id)
        val replaceableNote = getOrCreateAddressableNote(event.address())
        val author = getOrCreateUser(event.pubKey)

        val isVerified =
            if (version.event == null && (wasVerified || justVerify(event))) {
                version.loadEvent(event, author, emptyList())
                version.moveAllReferencesTo(replaceableNote)
                true
            } else {
                wasVerified
            }

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            replaceableNote.addRelay(relay)
        }

        // Already processed this event.
        if (replaceableNote.event?.id == event.id) return isVerified

        if (event.createdAt > (replaceableNote.createdAt() ?: 0L) && (isVerified || justVerify(event))) {
            // clear index from previous tags
            replaceableNote.replyTo?.forEach {
                it.removeNote(replaceableNote)
            }

            replaceableNote.loadEvent(event, author, computeReplyTo(event))

            refreshNewNoteObservers(replaceableNote)

            return true
        } else {
            return false
        }
    }

    fun consume(
        event: AppRecommendationEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: AppSpecificDataEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(
        event: PrivateDmEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: DeletionEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            if (deletionIndex.add(event, wasVerified)) {
                event
                    .deleteEvents()
                    .mapNotNull { getNoteIfExists(it) }
                    .forEach { deleteNote ->
                        val deleteNoteEvent = deleteNote.event
                        if (deleteNoteEvent is AddressableEvent) {
                            val addressableNote = getAddressableNoteIfExists(deleteNoteEvent.addressTag())
                            if (addressableNote?.author?.pubkeyHex == event.pubKey && (addressableNote.createdAt() ?: 0L) <= event.createdAt) {
                                // Counts the replies
                                deleteNote(addressableNote)

                                addressables.remove(addressableNote.address)
                            }
                        }

                        // must be the same author
                        if (deleteNote.author?.pubkeyHex == event.pubKey) {
                            // reverts the add
                            deleteNote(deleteNote)
                        }
                    }

                val addressList = event.deleteAddressIds()
                val addressSet = addressList.toSet()

                addressList
                    .mapNotNull { getAddressableNoteIfExists(it) }
                    .forEach { deleteNote ->
                        // must be the same author
                        if (deleteNote.author?.pubkeyHex == event.pubKey && (deleteNote.createdAt() ?: 0L) <= event.createdAt) {
                            // Counts the replies
                            deleteNote(deleteNote)

                            addressables.remove(deleteNote.address)
                        }
                    }

                notes.forEach { key, note ->
                    val noteEvent = note.event
                    if (noteEvent is AddressableEvent && noteEvent.addressTag() in addressSet) {
                        if (noteEvent.pubKey == event.pubKey && noteEvent.createdAt <= event.createdAt) {
                            deleteNote(note)
                        }
                    }
                }
            }

            refreshNewNoteObservers(note)

            return true
        } else {
            return false
        }
    }

    fun getAnyChannel(note: Note): Channel? = note.event?.let { getAnyChannel(it) }

    fun getAnyChannel(noteEvent: Event): Channel? =
        when (noteEvent) {
            is ChannelCreateEvent -> getPublicChatChannelIfExists(noteEvent.id)
            is ChannelMetadataEvent -> noteEvent.channelId()?.let { getPublicChatChannelIfExists(it) }
            is ChannelMessageEvent -> noteEvent.channelId()?.let { getPublicChatChannelIfExists(it) }
            is LiveActivitiesChatMessageEvent -> noteEvent.activityAddress()?.let { getLiveActivityChannelIfExists(it) }
            is LiveActivitiesEvent -> getLiveActivityChannelIfExists(noteEvent.address())
            is EphemeralChatEvent -> noteEvent.roomId()?.let { getEphemeralChatChannelIfExists(it) }
            else -> null
        }

    @Suppress("DEPRECATION")
    private fun deleteNote(deleteNote: Note) {
        val deletedEvent = deleteNote.event

        val mentions =
            deleteNote.event
                ?.tags
                ?.filter { it.firstOrNull() == "p" }
                ?.mapNotNull { it.getOrNull(1) }
                ?.mapNotNull { checkGetOrCreateUser(it) }

        mentions?.forEach { user -> user.removeReport(deleteNote) }

        // Counts the replies
        deleteNote.replyTo?.forEach { masterNote ->
            masterNote.removeNote(deleteNote)
        }

        deleteNote.inGatherers?.forEach { it.removeNote(deleteNote) }

        getAnyChannel(deleteNote)?.removeNote(deleteNote)

        (deletedEvent as? TorrentCommentEvent)?.torrentIds()?.let {
            getNoteIfExists(it)?.removeReply(deleteNote)
        }

        notes.remove(deleteNote.idHex)

        if (deletedEvent is WrappedEvent) {
            deleteWraps(deletedEvent)
        }

        deleteNote.clearFlow()

        refreshDeletedNoteObservers(deleteNote)
    }

    fun deleteWraps(event: WrappedEvent) {
        event.host?.let { hostStub ->
            // seal
            getNoteIfExists(hostStub.id)?.let { hostNote ->
                val noteEvent = hostNote.event
                if (noteEvent is WrappedEvent) {
                    deleteWraps(noteEvent)
                }
                hostNote.clearFlow()
                refreshDeletedNoteObservers(hostNote)
            }

            notes.remove(hostStub.id)
        }
    }

    fun consume(
        event: RepostEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            // Counts the replies
            repliesTo.forEach { it.addBoost(note) }

            event.containedPost()?.let {
                checkDeletionAndConsume(it, relay, false)
            }

            refreshNewNoteObservers(note)

            return true
        }
        return false
    }

    fun consume(
        event: GenericRepostEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            // Counts the replies
            repliesTo.forEach { it.addBoost(note) }

            event.containedPost()?.let {
                checkDeletionAndConsume(it, relay, false)
            }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: CommunityPostApprovalEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)

            val communities = event.communityAddresses()
            val eventsApproved = computeReplyTo(event)

            val repliesTo = communities.map { getOrCreateAddressableNote(it) }

            note.loadEvent(event, author, eventsApproved)

            // Counts the replies
            repliesTo.forEach { it.addBoost(note) }

            eventsApproved.forEach { it.addBoost(note) }

            event.containedPost()?.let {
                checkDeletionAndConsume(it, relay, false)
            }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: ReactionEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return true

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            repliesTo.forEach { it.addReaction(note) }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: ReportEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val mentions = event.reportedAuthor().mapNotNull { checkGetOrCreateUser(it.pubkey) }
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            // Log.d("RP", "New Report ${event.content} by ${note.author?.toBestDisplayName()}
            // ${formattedDateTime(event.createdAt)}")
            // Adds notifications to users.
            if (repliesTo.isEmpty()) {
                mentions.forEach { it.addReport(note) }
            } else {
                repliesTo.forEach { it.addReport(note) }

                mentions.forEach {
                    // doesn't add to reports, but triggers recounts
                    it.flowSet?.reports?.invalidateData()
                }
            }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: ChannelCreateEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        // Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
        val oldChannel = getOrCreatePublicChatChannel(event.id)
        val author = getOrCreateUser(event.pubKey)
        val note = getOrCreateNote(event.id)

        val isVerified =
            if (note.event == null && (wasVerified || justVerify(event))) {
                oldChannel.addNote(note, relay)
                note.loadEvent(event, author, emptyList())

                refreshNewNoteObservers(note)
                true
            } else {
                wasVerified
            }

        if (event.createdAt <= oldChannel.updatedMetadataAt) {
            return false // older data, does nothing
        }

        if (oldChannel.creator == null || oldChannel.creator == author) {
            if (isVerified || justVerify(event)) {
                oldChannel.updateChannelInfo(author, event, note)
            }
        }

        return isVerified
    }

    fun consume(
        event: ChannelMetadataEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val channelId = event.channelId()
        if (channelId.isNullOrBlank()) return false

        // new event
        val oldChannel = checkGetOrCreatePublicChatChannel(channelId) ?: return false

        val author = getOrCreateUser(event.pubKey)
        val note = getOrCreateNote(event.id)

        val isVerified =
            if (event.createdAt > oldChannel.updatedMetadataAt) {
                if (wasVerified || justVerify(event)) {
                    oldChannel.updateChannelInfo(author, event, note)
                    true
                } else {
                    false
                }
            } else {
                wasVerified
            }

        if (note.event == null && (isVerified || justVerify(event))) {
            oldChannel.addNote(note, relay)
            note.loadEvent(event, author, emptyList())

            refreshNewNoteObservers(note)
        }

        return isVerified
    }

    fun consume(
        event: ChannelMessageEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val channelId = event.channelId() ?: return false

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val channel = checkGetOrCreatePublicChatChannel(channelId)
            if (channel == null) {
                Log.w("LocalCache", "Unable to create public chat channel for event ${event.toJson()}")
                return false
            }

            val note = getOrCreateNote(event.id)
            channel.addNote(note, relay)
        }

        return new
    }

    fun consume(
        event: EphemeralChatEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val roomId = event.roomId() ?: return false

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val note = getOrCreateNote(event.id)
            val channel = getOrCreateEphemeralChannel(roomId)
            channel.addNote(note, relay)
        }

        return new
    }

    fun consume(
        event: LiveActivitiesChatMessageEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val activityAddress = event.activityAddress() ?: return false

        val new = consumeRegularEvent(event, relay, wasVerified)

        if (new) {
            val channel = getOrCreateLiveChannel(activityAddress)
            val note = getOrCreateNote(event.id)
            channel.addNote(note, relay)
        }

        return new
    }

    fun consume(
        event: CommentEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    @Suppress("UNUSED_PARAMETER")
    fun consume(
        event: ChannelHideMessageEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun consume(
        event: ChannelMuteUserEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean = false

    fun consume(
        event: LnZapEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val existingZapRequest = event.zapRequest?.id?.let { getNoteIfExists(it) }
            if (existingZapRequest == null || existingZapRequest.event == null) {
                // tries to add it
                event.zapRequest?.let {
                    checkDeletionAndConsume(it, relay, false)
                }
            }

            val zapRequest = event.zapRequest?.id?.let { getNoteIfExists(it) }

            if (zapRequest == null || zapRequest.event !is LnZapRequestEvent) {
                Log.e("ZP", "Zap Request not found. Unable to process Zap {${event.toJson()}}")
                return false
            }

            val author = getOrCreateUser(event.pubKey)
            val mentions = event.zappedAuthor().mapNotNull { checkGetOrCreateUser(it) }
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            repliesTo.forEach { it.addZap(zapRequest, note) }
            mentions.forEach { it.addZap(zapRequest, note) }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: LnZapRequestEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            val author = getOrCreateUser(event.pubKey)
            val mentions = event.zappedAuthor().mapNotNull { checkGetOrCreateUser(it) }
            val repliesTo = computeReplyTo(event)

            note.loadEvent(event, author, repliesTo)

            repliesTo.forEach { it.addZap(note, null) }
            mentions.forEach { it.addZap(note, null) }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: AudioHeaderEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: FileHeaderEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: ProfileGalleryEntryEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: FileStorageHeaderEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: FhirResourceEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: TextNoteModificationEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            event.editedNote()?.let {
                checkGetOrCreateNote(it.eventId)?.let { editedNote ->
                    modificationCache.remove(editedNote.idHex)
                    // must update list of Notes to quickly update the user.
                    editedNote.flowSet?.edits?.invalidateData()
                }
            }

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: HighlightEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: FileStorageEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        if (relay != null) {
            author.addRelayBeingUsed(relay, event.createdAt)
            note.addRelay(relay)
        }

        val isVerified =
            try {
                val cachePath = Amethyst.instance.nip95cache
                cachePath.mkdirs()
                val file = File(cachePath, event.id)
                if (!file.exists() && (wasVerified || justVerify(event))) {
                    FileOutputStream(file).use { stream ->
                        stream.write(event.decode())
                    }
                    Log.i(
                        "FileStorageEvent",
                        "NIP95 File received from $relay and saved to disk as $file",
                    )
                    true
                } else {
                    wasVerified
                }
            } catch (e: IOException) {
                Log.e("FileStorageEvent", "FileStorageEvent save to disk error: " + event.id, e)
                wasVerified
            }

        // Already processed this event.
        if (note.event != null) return false

        if (isVerified || justVerify(event)) {
            // this is an invalid event. But we don't need to keep the data in memory.
            val eventNoData =
                FileStorageEvent(event.id, event.pubKey, event.createdAt, event.tags, "", event.sig)

            note.loadEvent(eventNoData, author, emptyList())

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    private fun consume(
        event: ChatMessageEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    private fun consume(
        event: ChatMessageEncryptedFileHeaderEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: SealedRumorEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: GiftWrapEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ) = consumeRegularEvent(event, relay, wasVerified)

    fun consume(
        event: LnZapPaymentRequestEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        // Does nothing without a response callback.
        return true
    }

    fun consume(
        event: LnZapPaymentRequestEvent,
        zappedNote: Note?,
        wasVerified: Boolean,
        relay: NormalizedRelayUrl?,
        onResponse: suspend (LnZapPaymentResponseEvent) -> Unit,
    ): Boolean {
        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            relay?.let {
                note.addRelay(relay)
            }

            zappedNote?.addZapPayment(note, null)

            awaitingPaymentRequests.put(event.id, Pair(zappedNote, onResponse))

            refreshNewNoteObservers(note)

            return true
        }

        return false
    }

    fun consume(
        event: LnZapPaymentResponseEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val requestId = event.requestId()
        val pair = awaitingPaymentRequests[requestId] ?: return false

        val (zappedNote, responseCallback) = pair

        val requestNote = requestId?.let { checkGetOrCreateNote(requestId) }

        val note = getOrCreateNote(event.id)
        val author = getOrCreateUser(event.pubKey)

        // Already processed this event.
        if (note.event != null) return false

        if (wasVerified || justVerify(event)) {
            note.loadEvent(event, author, emptyList())

            requestNote?.let { request -> zappedNote?.addZapPayment(request, note) }

            GlobalScope.launch(Dispatchers.IO) {
                responseCallback(event)
            }

            return true
        }

        return false
    }

    fun findUsersStartingWith(
        username: String,
        forAccount: Account?,
    ): List<User> {
        if (username.isBlank()) return emptyList()

        checkNotInMainThread()

        val key = decodePublicKeyAsHexOrNull(username)

        if (key != null) {
            val user = getUserIfExists(key)
            if (user != null) {
                return listOfNotNull(user)
            }
        }

        val finds =
            users.filter { _, user: User ->
                (
                    (user.anyNameStartsWith(username)) ||
                        user.pubkeyHex.startsWith(username, true) ||
                        user.pubkeyNpub().startsWith(username, true)
                ) &&
                    (forAccount == null || (!forAccount.isHidden(user) && !user.containsAny(forAccount.hiddenUsers.flow.value.hiddenWordsCase)))
            }

        return finds.sortedWith(
            compareBy(
                { forAccount?.isFollowing(it) == false },
                { !it.toBestDisplayName().startsWith(username, ignoreCase = true) },
                { it.toBestDisplayName().lowercase() },
                { it.pubkeyHex },
            ),
        )
    }

    /**
     * Will return true if supplied note is one of events to be excluded from
     * search results.
     */
    private fun excludeNoteEventFromSearchResults(note: Note): Boolean =
        (
            note.event is GenericRepostEvent ||
                note.event is RepostEvent ||
                note.event is CommunityPostApprovalEvent ||
                note.event is ReactionEvent ||
                note.event is LnZapEvent ||
                note.event is LnZapRequestEvent ||
                note.event is FileHeaderEvent
        )

    fun findNotesStartingWith(
        text: String,
        hiddenUsers: HiddenUsersState,
    ): List<Note> {
        checkNotInMainThread()

        if (text.isBlank()) return emptyList()

        val key = decodeEventIdAsHexOrNull(text)

        if (key != null) {
            val note = getNoteIfExists(key)
            if ((note != null) && !excludeNoteEventFromSearchResults(note)) {
                return listOfNotNull(note)
            }
        }

        return notes.filter { _, note ->
            if (excludeNoteEventFromSearchResults(note)) {
                return@filter false
            }

            if (note.event?.tags?.tagValueContains(text, true) == true ||
                note.idHex.startsWith(text, true)
            ) {
                return@filter !note.isHiddenFor(hiddenUsers.flow.value)
            }

            if (note.event?.isContentEncoded() == false) {
                if (!note.isHiddenFor(hiddenUsers.flow.value)) {
                    return@filter note.event?.content?.contains(text, true) ?: false
                } else {
                    return@filter false
                }
            }

            return@filter false
        } +
            addressables.filter { _, addressable ->
                if (excludeNoteEventFromSearchResults(addressable)) {
                    return@filter false
                }

                if (addressable.event?.tags?.tagValueContains(text, true) == true ||
                    addressable.idHex.startsWith(text, true)
                ) {
                    return@filter !addressable.isHiddenFor(hiddenUsers.flow.value)
                }

                if (addressable.event?.isContentEncoded() == false) {
                    if (!addressable.isHiddenFor(hiddenUsers.flow.value)) {
                        return@filter addressable.event?.content?.contains(text, true) ?: false
                    } else {
                        return@filter false
                    }
                }

                return@filter false
            }
    }

    fun findPublicChatChannelsStartingWith(text: String): List<PublicChatChannel> {
        if (text.isBlank()) return emptyList()

        val key = decodeEventIdAsHexOrNull(text)
        if (key != null) {
            getPublicChatChannelIfExists(key)?.let {
                return listOf(it)
            }
        }

        return publicChatChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun findEphemeralChatChannelsStartingWith(text: String): List<EphemeralChatChannel> {
        if (text.isBlank()) return emptyList()

        return ephemeralChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun findLiveActivityChannelsStartingWith(text: String): List<LiveActivitiesChannel> {
        if (text.isBlank()) return emptyList()

        try {
            val parsed = Nip19Parser.uriToRoute(text)?.entity
            if (parsed is NAddress && parsed.kind == LiveActivitiesEvent.KIND) {
                return listOf(getOrCreateLiveChannel(parsed.address()))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

        return liveChatChannels.filter { _, channel ->
            channel.anyNameStartsWith(text)
        }
    }

    fun getPeopleListNotesFor(user: User): List<AddressableNote> = addressables.filter(PeopleListEvent.KIND, user.pubkeyHex)

    fun findStatusesForUser(user: User): ImmutableList<AddressableNote> {
        checkNotInMainThread()

        return addressables
            .filter { _, it ->
                val noteEvent = it.event
                (
                    noteEvent is StatusEvent &&
                        noteEvent.pubKey == user.pubkeyHex &&
                        !noteEvent.isExpired() &&
                        noteEvent.content.isNotBlank()
                )
            }.sortedWith(compareBy({ it.event?.expiration() ?: it.event?.createdAt }, { it.idHex }))
            .reversed()
            .toImmutableList()
    }

    suspend fun findEarliestOtsForNote(
        note: Note,
        otsVerifCache: VerificationStateCache,
    ): Long? {
        checkNotInMainThread()

        var minTime: Long? = null
        val time = TimeUtils.now()

        val candidates =
            notes.mapNotNull { _, item ->
                val noteEvent = item.event
                if ((noteEvent is OtsEvent && noteEvent.isTaggedEvent(note.idHex) && !noteEvent.isExpirationBefore(time))) {
                    val cachedTime = (otsVerifCache.justCache(noteEvent) as? VerificationState.Verified)?.verifiedTime
                    if (cachedTime != null) {
                        if (minTime == null || cachedTime < (minTime ?: Long.MAX_VALUE)) {
                            minTime = cachedTime
                        }
                        null
                    } else {
                        // tries to verify again
                        noteEvent
                    }
                } else {
                    null
                }
            }

        candidates.forEach { noteEvent ->
            (otsVerifCache.cacheVerify(noteEvent) as? VerificationState.Verified)?.verifiedTime?.let { stampedTime ->
                if (minTime == null || stampedTime < (minTime ?: Long.MAX_VALUE)) {
                    minTime = stampedTime
                }
            }
        }

        return minTime
    }

    val modificationCache = LruCache<HexKey, List<Note>>(20)

    fun cachedModificationEventsForNote(note: Note): List<Note>? = modificationCache[note.idHex]

    fun findLatestModificationForNote(note: Note): List<Note> {
        checkNotInMainThread()

        val noteAuthor = note.author ?: return emptyList()

        modificationCache[note.idHex]?.let {
            return it
        }

        val time = TimeUtils.now()

        val newNotes =
            notes
                .filter { _, item ->
                    val noteEvent = item.event

                    noteEvent is TextNoteModificationEvent && noteAuthor == item.author && noteEvent.isTaggedEvent(note.idHex) && !noteEvent.isExpirationBefore(time)
                }.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))

        modificationCache.put(note.idHex, newNotes)

        return newNotes
    }

    fun cleanMemory() {
        Log.d("LargeCache", "Notes cleanup started. Current size: ${notes.size()}")
        notes.cleanUp()
        Log.d("LargeCache", "Notes cleanup completed. Remaining size: ${notes.size()}")

        Log.d("LargeCache", "Addressables cleanup started. Current size: ${addressables.size()}")
        addressables.cleanUp()
        Log.d("LargeCache", "Addressables cleanup completed. Remaining size: ${addressables.size()}")

        Log.d("LargeCache", "Users cleanup started. Current size: ${users.size()}")
        users.cleanUp()
        Log.d("LargeCache", "Users cleanup completed. Remaining size: ${users.size()}")
    }

    fun cleanObservers() {
        notes.forEach { _, it -> it.clearFlow() }
        addressables.forEach { _, it -> it.clearFlow() }
        users.forEach { _, it -> it.clearFlow() }

        observablesByKindAndETag.forEach { _, list ->
            list.forEach { key, value ->
                if (value.canDelete()) {
                    list.remove(key)
                }
            }
        }

        observablesByKindAndAuthor.forEach { _, list ->
            list.forEach { key, value ->
                if (value.canDelete()) {
                    list.remove(key)
                }
            }
        }
    }

    fun pruneHiddenMessagesChannel(
        channel: Channel,
        account: Account,
    ) {
        val toBeRemoved = channel.pruneHiddenMessages(account)

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            removeFromCache(it)

            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        if (toBeRemoved.size > 100 || channel.notes.size() > 100) {
            println(
                "PRUNE: ${toBeRemoved.size} hidden messages removed from ${channel.toBestDisplayName()}. ${channel.notes.size()} kept",
            )
        }
    }

    fun pruneHiddenMessages(account: Account) {
        ephemeralChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        liveChatChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        publicChatChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }
    }

    fun pruneOldMessagesChannel(channel: Channel) {
        val toBeRemoved = channel.pruneOldMessages()

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            removeFromCache(it)

            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        if (toBeRemoved.size > 100 || channel.notes.size() > 100) {
            println(
                "PRUNE: ${toBeRemoved.size} old messages removed from ${channel.toBestDisplayName()}. ${channel.notes.size()} kept",
            )
        }
    }

    fun pruneOldMessages() {
        checkNotInMainThread()

        ephemeralChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        liveChatChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        publicChatChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        chatroomList.forEach { userHex, room ->
            room.rooms.map { key, chatroom ->
                val toBeRemoved = chatroom.pruneMessagesToTheLatestOnly()

                val childrenToBeRemoved = mutableListOf<Note>()

                toBeRemoved.forEach {
                    childrenToBeRemoved.addAll(removeIfWrap(it))
                    removeFromCache(it)

                    childrenToBeRemoved.addAll(it.removeAllChildNotes())
                }

                removeFromCache(childrenToBeRemoved)

                if (toBeRemoved.size > 1) {
                    println(
                        "PRUNE: ${toBeRemoved.size} private messages from $userHex to ${key.users.joinToString()} removed. ${chatroom.messages.size} kept",
                    )
                }
            }
        }
    }

    fun removeIfWrap(note: Note): List<Note> {
        val noteEvent = note.event

        val children =
            if (noteEvent is WrappedEvent) {
                noteEvent.host?.id?.let {
                    getNoteIfExists(it)?.let { it2 ->
                        removeFromCache(it2)
                        it2.removeAllChildNotes()
                    }
                }
            } else {
                null
            }

        return children ?: emptyList()
    }

    fun prunePastVersionsOfReplaceables() {
        val toBeRemoved =
            notes.filter { _, note ->
                val noteEvent = note.event
                if (noteEvent is AddressableEvent) {
                    noteEvent.createdAt <
                        (addressables.get(noteEvent.address())?.event?.createdAt ?: 0)
                } else {
                    false
                }
            }

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            val newerVersion = (it.event as? AddressableEvent)?.address()?.let { tag -> addressables.get(tag) }
            if (newerVersion != null) {
                it.moveAllReferencesTo(newerVersion)
            }

            removeFromCache(it)
            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} old version of addressables removed.")
        }
    }

    fun pruneRepliesAndReactions(accounts: Set<HexKey>) {
        checkNotInMainThread()

        val toBeRemoved =
            notes.filter { _, note ->
                (
                    (note.event is TextNoteEvent && !note.isNewThread()) ||
                        note.event is ReactionEvent ||
                        note.event is LnZapEvent ||
                        note.event is LnZapRequestEvent ||
                        note.event is ReportEvent ||
                        note.event is GenericRepostEvent
                ) &&
                    note.replyTo?.any { it.flowSet?.isInUse() == true } != true &&
                    note.flowSet?.isInUse() != true &&
                    // don't delete if observing.
                    note.author?.pubkeyHex !in
                    accounts &&
                    // don't delete if it is the logged in account
                    note.event?.isTaggedUsers(accounts) !=
                    true // don't delete if it's a notification to the logged in user
            }

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            removeFromCache(it)
            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} thread replies removed.")
        }
    }

    private fun removeFromCache(note: Note) {
        note.replyTo?.forEach { masterNote ->
            masterNote.removeNote(note)
        }

        note.inGatherers?.forEach { it.removeNote(note) }

        val noteEvent = note.event

        if (noteEvent is LnZapEvent) {
            noteEvent.zappedAuthor().forEach {
                val author = getUserIfExists(it)
                author?.removeZap(note)
            }
        }
        if (noteEvent is LnZapRequestEvent) {
            noteEvent.zappedAuthor().mapNotNull {
                val author = getUserIfExists(it)
                author?.removeZap(note)
            }
        }
        if (noteEvent is ReportEvent) {
            noteEvent.reportedAuthor().mapNotNull {
                val author = getUserIfExists(it.pubkey)
                author?.removeReport(note)
            }
        }

        note.clearFlow()

        notes.remove(note.idHex)

        refreshDeletedNoteObservers(note)
    }

    fun removeFromCache(nextToBeRemoved: List<Note>) {
        nextToBeRemoved.forEach { note -> removeFromCache(note) }
    }

    fun pruneExpiredEvents() {
        checkNotInMainThread()

        val now = TimeUtils.now()
        val toBeRemoved = notes.filter { _, it -> it.event?.isExpirationBefore(now) == true }

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            removeFromCache(it)
            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} thread replies removed.")
        }
    }

    fun pruneHiddenEvents(account: Account) {
        checkNotInMainThread()

        val childrenToBeRemoved = mutableListOf<Note>()

        val toBeRemoved =
            account.hiddenUsers.flow.value.hiddenUsers
                .map { userHex ->
                    (notes.filter { _, it -> it.event?.pubKey == userHex } + addressables.filter { _, it -> it.event?.pubKey == userHex }).toSet()
                }.flatten()

        toBeRemoved.forEach {
            removeFromCache(it)
            childrenToBeRemoved.addAll(it.removeAllChildNotes())
        }

        removeFromCache(childrenToBeRemoved)

        println("PRUNE: ${toBeRemoved.size} messages removed because they were Hidden")
    }

    fun pruneContactLists(loggedIn: Set<HexKey>) {
        checkNotInMainThread()

        var removingContactList = 0
        users.forEach { _, user ->
            if (
                user.pubkeyHex !in loggedIn &&
                (user.flowSet == null || user.flowSet?.isInUse() == false) &&
                user.latestContactList != null
            ) {
                user.latestContactList = null
                removingContactList++
            }
        }

        println("PRUNE: $removingContactList contact lists")
    }

    override fun markAsSeen(
        eventId: String,
        relay: NormalizedRelayUrl,
    ) {
        val note = getNoteIfExists(eventId)

        note?.event?.let { noteEvent ->
            if (noteEvent is AddressableEvent) {
                getAddressableNoteIfExists(noteEvent.aTag().toTag())?.addRelay(relay)
            }
        }

        note?.addRelay(relay)
    }

    // Observers line up here.
    val live: LocalCacheFlow = LocalCacheFlow()

    private fun refreshNewNoteObservers(newNote: Note) {
        val event = newNote.event as Event
        updateObservables(event)
        live.newNote(newNote)
    }

    private fun refreshDeletedNoteObservers(newNote: Note) {
        live.removedNote(newNote)
    }

    fun justVerify(event: Event): Boolean {
        checkNotInMainThread()

        return if (!event.verify()) {
            try {
                event.checkSignature()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("Event Verification Failed", "Kind: ${event.kind} from ${dateFormatter(event.createdAt, "", "")} with message ${e.message}")
            }
            false
        } else {
            true
        }
    }

    fun consume(
        event: DraftWrapEvent,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean = !event.isDeleted() && consumeBaseReplaceable(event, relay, wasVerified)

    fun consume(nip19: Entity) {
        when (nip19) {
            is NSec -> getOrCreateUser(nip19.toPubKeyHex())
            is NPub -> getOrCreateUser(nip19.hex)
            is NProfile -> {
                nip19.relay.forEach { relayHint ->
                    relayHints.addKey(nip19.hex, relayHint)
                }
                getOrCreateUser(nip19.hex)
            }
            is NNote -> {
                getOrCreateNote(nip19.hex)
            }
            is NEvent -> {
                nip19.relay.forEach { relayHint ->
                    relayHints.addEvent(nip19.hex, relayHint)
                }
                getOrCreateNote(nip19.hex)
            }
            is NEmbed -> {
                justConsume(nip19.event, null, false)
            }
            is NRelay -> {}
            is NAddress -> {
                val aTag = nip19.aTag()
                nip19.relay.forEach { relayHint ->
                    relayHints.addAddress(aTag, relayHint)
                }
                getOrCreateAddressableNote(nip19.address())
            }
            else -> { }
        }
    }

    fun justConsumeMyOwnEvent(event: Event) = justConsumeAndUpdateIndexes(event, null, true)

    fun justConsume(
        event: Event,
        relay: IRelayClient?,
        wasVerified: Boolean,
    ): Boolean {
        if (deletionIndex.hasBeenDeleted(event)) {
            // update relay with deletion event from another.
            if (relay != null) {
                deletionIndex.hasBeenDeletedBy(event)?.let { deletionEvent ->
                    getNoteIfExists(deletionEvent.id)?.let { note ->
                        if (!note.hasRelay(relay.url)) {
                            if (isDebug) {
                                Log.d("LocalCache", "Updating ${relay.url.url} with a Deletion Event ${event.id} ${deletionEvent.id} because of ${event.toJson()} with ${deletionEvent.toJson()}")
                            }
                            relay.sendIfConnected(EventCmd(deletionEvent))
                            note.addRelay(relay.url)
                        }
                    }
                }
            }
            return false
        }

        if (event is AddressableEvent && relay != null) {
            // updates relay with a new event.
            getAddressableNoteIfExists(event.address())?.let { note ->
                note.event?.let { existingEvent ->
                    if (existingEvent.createdAt > event.createdAt && !note.hasRelay(relay.url) && !deletionIndex.hasBeenDeleted(event) && !event.isExpired()) {
                        if (isDebug) {
                            Log.d("LocalCache", "Updating ${relay.url.url} with a new version of ${event.kind} ${event.id} to ${existingEvent.id}")
                        }

                        relay.sendIfConnected(EventCmd(existingEvent))
                        // only send once.
                        note.addRelay(relay.url)
                    }
                }
            }
        }

        return justConsumeAndUpdateIndexes(event, relay?.url, wasVerified)
    }

    private fun checkDeletionAndConsume(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        if (!deletionIndex.hasBeenDeleted(event)) {
            justConsumeAndUpdateIndexes(event, relay, wasVerified)
        } else {
            false
        }

    private fun justConsumeAndUpdateIndexes(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean {
        val wasNew = justConsumeInnerInner(event, relay, wasVerified)

        if (wasNew) {
            updateHintIndexes(event)
        }

        if (relay != null) {
            // uses the internal event to avoid reprocessing cached items.
            val note =
                if (event is AddressableEvent) {
                    getAddressableNoteIfExists(event.address())
                } else {
                    getNoteIfExists(event.id)
                }

            note?.event?.let { consumedEvent ->
                addIncomingRelayAsHintToAllRelatedEvents(consumedEvent, relay)
            }
        }

        return wasNew
    }

    fun addIncomingRelayAsHintToAllRelatedEvents(
        event: Event,
        relay: NormalizedRelayUrl,
    ) {
        relayHints.addEvent(event.id, relay)
        if (event is AddressableEvent) {
            relayHints.addAddress(event.addressTag(), relay)
        }

        if (event is EventHintProvider) {
            event.linkedEventIds().forEach {
                relayHints.addEvent(it, relay)
            }
        }
        if (event is AddressHintProvider) {
            event.linkedAddressIds().forEach {
                relayHints.addAddress(it, relay)
            }
        }
        if (event is PubKeyHintProvider) {
            event.linkedPubKeys().forEach {
                relayHints.addKey(it, relay)
            }
        }
    }

    fun updateHintIndexes(event: Event) {
        if (event is EventHintProvider) {
            event.eventHints().forEach {
                relayHints.addEvent(it.eventId, it.relay)
            }
        }
        if (event is AddressHintProvider) {
            event.addressHints().forEach {
                relayHints.addAddress(it.addressId, it.relay)
            }
        }
        if (event is PubKeyHintProvider) {
            event.pubKeyHints().forEach {
                relayHints.addKey(it.pubkey, it.relay)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun justConsumeInnerInner(
        event: Event,
        relay: NormalizedRelayUrl?,
        wasVerified: Boolean,
    ): Boolean =
        try {
            when (event) {
                is AdvertisedRelayListEvent -> consume(event, relay, wasVerified)
                is AppDefinitionEvent -> consume(event, relay, wasVerified)
                is AppRecommendationEvent -> consume(event, relay, wasVerified)
                is AppSpecificDataEvent -> consume(event, relay, wasVerified)
                is AudioHeaderEvent -> consume(event, relay, wasVerified)
                is AudioTrackEvent -> consume(event, relay, wasVerified)
                is BadgeAwardEvent -> consume(event, relay, wasVerified)
                is BadgeDefinitionEvent -> consume(event, relay, wasVerified)
                is BadgeProfilesEvent -> consume(event, relay, wasVerified)
                is BlockedRelayListEvent -> consume(event, relay, wasVerified)
                is BlossomServersEvent -> consume(event, relay, wasVerified)
                is BroadcastRelayListEvent -> consume(event, relay, wasVerified)
                is BookmarkListEvent -> consume(event, relay, wasVerified)
                is CalendarEvent -> consume(event, relay, wasVerified)
                is CalendarDateSlotEvent -> consume(event, relay, wasVerified)
                is CalendarTimeSlotEvent -> consume(event, relay, wasVerified)
                is CalendarRSVPEvent -> consume(event, relay, wasVerified)
                is ChannelCreateEvent -> consume(event, relay, wasVerified)
                is ChannelListEvent -> consume(event, relay, wasVerified)
                is ChannelHideMessageEvent -> consume(event, relay, wasVerified)
                is ChannelMessageEvent -> consume(event, relay, wasVerified)
                is ChannelMetadataEvent -> consume(event, relay, wasVerified)
                is ChannelMuteUserEvent -> consume(event, relay, wasVerified)
                is ChatMessageEncryptedFileHeaderEvent -> consume(event, relay, wasVerified)
                is ChatMessageEvent -> consume(event, relay, wasVerified)
                is ChatMessageRelayListEvent -> consume(event, relay, wasVerified)
                is ClassifiedsEvent -> consume(event, relay, wasVerified)
                is CommentEvent -> consume(event, relay, wasVerified)
                is CommunityDefinitionEvent -> consume(event, relay, wasVerified)
                is CommunityListEvent -> consume(event, relay, wasVerified)
                is CommunityPostApprovalEvent -> consume(event, relay, wasVerified)
                is ContactListEvent -> consume(event, relay, wasVerified)
                is DeletionEvent -> consume(event, relay, wasVerified)
                is DraftWrapEvent -> consume(event, relay, wasVerified)
                is EmojiPackEvent -> consume(event, relay, wasVerified)
                is EmojiPackSelectionEvent -> consume(event, relay, wasVerified)
                is EphemeralChatEvent -> consume(event, relay, wasVerified)
                is EphemeralChatListEvent -> consume(event, relay, wasVerified)
                is GenericRepostEvent -> consume(event, relay, wasVerified)
                is FhirResourceEvent -> consume(event, relay, wasVerified)
                is FileHeaderEvent -> consume(event, relay, wasVerified)
                is ProfileGalleryEntryEvent -> consume(event, relay, wasVerified)
                is FileServersEvent -> consume(event, relay, wasVerified)
                is FileStorageEvent -> consume(event, relay, wasVerified)
                is FileStorageHeaderEvent -> consume(event, relay, wasVerified)
                is FollowListEvent -> consume(event, relay, wasVerified)
                is GeohashListEvent -> consume(event, relay, wasVerified)
                is GoalEvent -> consume(event, relay, wasVerified)
                is GiftWrapEvent -> consume(event, relay, wasVerified)
                is GitIssueEvent -> consume(event, relay, wasVerified)
                is GitReplyEvent -> consume(event, relay, wasVerified)
                is GitPatchEvent -> consume(event, relay, wasVerified)
                is GitRepositoryEvent -> consume(event, relay, wasVerified)
                is HashtagListEvent -> consume(event, relay, wasVerified)
                is HighlightEvent -> consume(event, relay, wasVerified)
                is IndexerRelayListEvent -> consume(event, relay, wasVerified)
                is InteractiveStoryPrologueEvent -> consume(event, relay, wasVerified)
                is InteractiveStorySceneEvent -> consume(event, relay, wasVerified)
                is InteractiveStoryReadingStateEvent -> consume(event, relay, wasVerified)
                is LiveActivitiesEvent -> consume(event, relay, wasVerified)
                is LiveActivitiesChatMessageEvent -> consume(event, relay, wasVerified)
                is LnZapEvent -> consume(event, relay, wasVerified)
                is LnZapRequestEvent -> consume(event, relay, wasVerified)
                is NIP90StatusEvent -> consume(event, relay, wasVerified)
                is NIP90ContentDiscoveryResponseEvent -> consume(event, relay, wasVerified)
                is NIP90ContentDiscoveryRequestEvent -> consume(event, relay, wasVerified)
                is NIP90UserDiscoveryResponseEvent -> consume(event, relay, wasVerified)
                is NIP90UserDiscoveryRequestEvent -> consume(event, relay, wasVerified)
                is LnZapPaymentRequestEvent -> consume(event, relay, wasVerified)
                is LnZapPaymentResponseEvent -> consume(event, relay, wasVerified)
                is LongTextNoteEvent -> consume(event, relay, wasVerified)
                is MetadataEvent -> consume(event, relay, wasVerified)
                is MuteListEvent -> consume(event, relay, wasVerified)
                is NNSEvent -> consume(event, relay, wasVerified)
                is OtsEvent -> consume(event, relay, wasVerified)
                is PictureEvent -> consume(event, relay, wasVerified)
                is PrivateDmEvent -> consume(event, relay, wasVerified)
                is PrivateOutboxRelayListEvent -> consume(event, relay, wasVerified)
                is ProxyRelayListEvent -> consume(event, relay, wasVerified)
                is PinListEvent -> consume(event, relay, wasVerified)
                is PublicMessageEvent -> consume(event, relay, wasVerified)
                is PeopleListEvent -> consume(event, relay, wasVerified)
                is PollNoteEvent -> consume(event, relay, wasVerified)
                is ReactionEvent -> consume(event, relay, wasVerified)
                is ContactCardEvent -> consume(event, relay, wasVerified)
                is RelaySetEvent -> consume(event, relay, wasVerified)
                is ReportEvent -> consume(event, relay, wasVerified)
                is RepostEvent -> consume(event, relay, wasVerified)
                is SealedRumorEvent -> consume(event, relay, wasVerified)
                is SearchRelayListEvent -> consume(event, relay, wasVerified)
                is StatusEvent -> consume(event, relay, wasVerified)
                is TextNoteEvent -> consume(event, relay, wasVerified)
                is TextNoteModificationEvent -> consume(event, relay, wasVerified)
                is TorrentEvent -> consume(event, relay, wasVerified)
                is TorrentCommentEvent -> consume(event, relay, wasVerified)
                is TrustedRelayListEvent -> consume(event, relay, wasVerified)
                is VideoHorizontalEvent -> consume(event, relay, wasVerified)
                is VideoNormalEvent -> consume(event, relay, wasVerified)
                is VideoVerticalEvent -> consume(event, relay, wasVerified)
                is VideoShortEvent -> consume(event, relay, wasVerified)
                is VoiceEvent -> consume(event, relay, wasVerified)
                is VoiceReplyEvent -> consume(event, relay, wasVerified)
                is WikiNoteEvent -> consume(event, relay, wasVerified)
                else -> {
                    Log.w("Event Not Supported", "From ${relay?.url}: ${event.toJson()}")
                    false
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("LocalCache", "Cannot consume ${event.kind}", e)
            false
        }

    fun hasConsumed(notificationEvent: Event): Boolean =
        if (notificationEvent is AddressableEvent) {
            val note = addressables.get(notificationEvent.address())
            val noteEvent = note?.event
            noteEvent != null && notificationEvent.createdAt <= noteEvent.createdAt
        } else {
            val note = notes.get(notificationEvent.id)
            note?.event != null
        }

    fun copyRelaysFromTo(
        from: Note,
        to: Event,
    ) {
        val toNote = getOrCreateNote(to)
        from.relays.forEach {
            toNote.addRelay(it)
        }
    }

    fun copyRelaysFromTo(
        from: Note,
        to: HexKey,
    ) {
        val toNote = getOrCreateNote(to)
        from.relays.forEach {
            toNote.addRelay(it)
        }
    }
}

@Stable
class LocalCacheFlow {
    private val _newEventBundles = MutableSharedFlow<Set<Note>>(0, 100, BufferOverflow.DROP_OLDEST)
    val newEventBundles = _newEventBundles.asSharedFlow() // read-only public view

    private val _deletedEventBundles = MutableSharedFlow<Set<Note>>(0, 100, BufferOverflow.DROP_OLDEST)
    val deletedEventBundles = _deletedEventBundles.asSharedFlow() // read-only public view

    // Refreshes observers in batches.
    private val bundler = BundledInsert<Note>(1000, Dispatchers.IO)

    // Refreshes observers in batches.
    private val bundler2 = BundledInsert<Note>(1000, Dispatchers.IO)

    fun newNote(newNote: Note) {
        bundler.invalidateList(newNote) { bundledNewNotes ->
            _newEventBundles.emit(bundledNewNotes)
        }
    }

    fun removedNote(newNote: Note) {
        bundler2.invalidateList(newNote) { bundledNewNotes ->
            _deletedEventBundles.emit(bundledNewNotes)
        }
    }
}
