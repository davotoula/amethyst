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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.currentWord
import com.vitorpamplona.amethyst.commons.compose.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.compose.replaceCurrentWord
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.note.creators.draftTags.DraftTagState
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.EmojiSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.ui.note.creators.previews.PreviewState
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.IZapRaiser
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.SplitBuilder
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.toZapSplitSetup
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.experimental.zapPolls.closedAt
import com.vitorpamplona.quartz.experimental.zapPolls.consensusThreshold
import com.vitorpamplona.quartz.experimental.zapPolls.maxAmount
import com.vitorpamplona.quartz.experimental.zapPolls.minAmount
import com.vitorpamplona.quartz.experimental.zapPolls.tags.PollOptionTag
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip10Notes.tags.prepareETagsAsReplyTo
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip18Reposts.quotes.taggedQuoteIds
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dims
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.sensitiveContent
import com.vitorpamplona.quartz.nip94FileMetadata.size
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class UserSuggestionAnchor {
    MAIN_MESSAGE,
    FORWARD_ZAPS,
    TO_USERS,
}

@Stable
open class ShortNotePostViewModel :
    ViewModel(),
    ILocationGrabber,
    IMessageField,
    IZapField,
    IZapRaiser {
    val draftTag = DraftTagState()

    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    init {
        viewModelScope.launch(Dispatchers.IO) {
            draftTag.versions.collectLatest {
                // don't save the first
                if (it > 0) {
                    accountViewModel.runIOCatching {
                        sendDraftSync()
                    }
                }
            }
        }
    }

    var originalNote: Note? by mutableStateOf(null)
    var forkedFromNote: Note? by mutableStateOf(null)

    var pTags by mutableStateOf<List<User>?>(null)
    var eTags by mutableStateOf<List<Note>?>(null)

    val iMetaAttachments = IMetaAttachments()
    var nip95attachments by mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    override var message by mutableStateOf(TextFieldValue(""))

    val urlPreviews = PreviewState()

    var isUploadingImage by mutableStateOf(false)

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // Polls
    var canUsePoll by mutableStateOf(false)
    var wantsPoll by mutableStateOf(false)
    var zapRecipients = mutableStateListOf<HexKey>()
    var pollOptions = newStateMapPollOptions()
    var valueMaximum by mutableStateOf<Long?>(null)
    var valueMinimum by mutableStateOf<Long?>(null)
    var consensusThreshold: Int? = null
    var closedAt: Long? = null

    var isValidValueMaximum = mutableStateOf(true)
    var isValidValueMinimum = mutableStateOf(true)
    var isValidConsensusThreshold = mutableStateOf(true)
    var isValidClosedAt = mutableStateOf(true)

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    var wantsSecretEmoji by mutableStateOf(false)

    // Forward Zap to
    var wantsForwardZapTo by mutableStateOf(false)
    override var forwardZapTo = mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    override var forwardZapToEditting = mutableStateOf(TextFieldValue(""))

    // NSFW, Sensitive
    var wantsToMarkAsSensitive by mutableStateOf(false)

    // GeoHash
    var wantsToAddGeoHash by mutableStateOf(false)
    var location: StateFlow<LocationState.LocationResult>? = null
    var wantsExclusiveGeoPost by mutableStateOf(false)

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapRaiser by mutableStateOf(false)
    override val zapRaiserAmount = mutableStateOf<Long?>(null)

    fun lnAddress(): String? = account.userProfile().info?.lnAddress()

    fun hasLnAddress(): Boolean = account.userProfile().info?.lnAddress() != null

    fun user(): User? = account.userProfile()

    open fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddInvoice = hasLnAddress()
        this.canAddZapRaiser = hasLnAddress()

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountVM)

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM)
    }

    open fun load(
        replyingTo: Note?,
        quote: Note?,
        fork: Note?,
        version: Note?,
        draft: Note?,
    ) {
        val noteEvent = draft?.event
        val noteAuthor = draft?.author

        if (draft != null && noteEvent is DraftWrapEvent && noteAuthor != null) {
            viewModelScope.launch(Dispatchers.IO) {
                accountViewModel.createTempDraftNote(noteEvent)?.let { innerNote ->
                    val oldTag = (draft.event as? AddressableEvent)?.dTag()
                    if (oldTag != null) {
                        draftTag.set(oldTag)
                    }
                    loadFromDraft(innerNote)
                }
            }
        } else {
            originalNote = replyingTo
            replyingTo?.let { replyNote ->
                if (replyNote.event is BaseThreadedEvent) {
                    this.eTags = (replyNote.replyTo ?: emptyList()).plus(replyNote)
                } else {
                    this.eTags = listOf(replyNote)
                }

                if (replyNote.event !is CommunityDefinitionEvent) {
                    replyNote.author?.let { replyUser ->
                        val currentMentions =
                            (replyNote.event as? TextNoteEvent)
                                ?.mentions()
                                ?.map { LocalCache.getOrCreateUser(it.pubKey) }
                                ?: emptyList()

                        if (currentMentions.contains(replyUser)) {
                            this.pTags = currentMentions
                        } else {
                            this.pTags = currentMentions.plus(replyUser)
                        }
                    }
                }
            }
                ?: run {
                    eTags = null
                    pTags = null
                }

            val user = account.userProfile()

            canAddInvoice = user.info?.lnAddress() != null
            canAddZapRaiser = user.info?.lnAddress() != null
            canUsePoll = originalNote == null
            multiOrchestrator = null

            quote?.let { quotedNote ->
                message = TextFieldValue(message.text + "\nnostr:${quotedNote.toNEvent()}")

                quotedNote.author?.let { quotedUser ->
                    if (quotedUser.pubkeyHex != user.pubkeyHex) {
                        if (forwardZapTo.value.items.none { it.key.pubkeyHex == quotedUser.pubkeyHex }) {
                            forwardZapTo.value.addItem(quotedUser)
                        }
                        if (forwardZapTo.value.items.none { it.key.pubkeyHex == user.pubkeyHex }) {
                            forwardZapTo.value.addItem(user)
                        }

                        val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == quotedUser.pubkeyHex }
                        forwardZapTo.value.updatePercentage(pos, 0.9f)
                    }
                }
            }

            fork?.let { forkedNoted ->
                message = TextFieldValue(version?.event?.content ?: forkedNoted.event?.content ?: "")

                forkedNoted.event?.isSensitiveOrNSFW()?.let {
                    if (it) wantsToMarkAsSensitive = true
                }

                forkedNoted.event?.zapraiserAmount()?.let {
                    zapRaiserAmount.value = it
                }

                forkedNoted.event?.zapSplitSetup()?.let { setup ->
                    val totalWeight = setup.sumOf { if (it is ZapSplitSetupLnAddress) 0.0 else it.weight }

                    setup.forEach {
                        if (it is ZapSplitSetup) {
                            forwardZapTo.value.addItem(LocalCache.getOrCreateUser(it.pubKeyHex), (it.weight / totalWeight).toFloat())
                        }
                    }
                }

                // Only adds if it is not already set up.
                if (forwardZapTo.value.items.isEmpty()) {
                    forkedNoted.author?.let { forkedAuthor ->
                        if (forkedAuthor.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
                            if (forwardZapTo.value.items.none { it.key.pubkeyHex == forkedAuthor.pubkeyHex }) forwardZapTo.value.addItem(forkedAuthor)
                            if (forwardZapTo.value.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) forwardZapTo.value.addItem(accountViewModel.userProfile())

                            val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == forkedAuthor.pubkeyHex }
                            forwardZapTo.value.updatePercentage(pos, 0.8f)
                        }
                    }
                }

                forkedNoted.author?.let {
                    if (this.pTags == null) {
                        this.pTags = listOf(it)
                    } else if (this.pTags?.contains(it) != true) {
                        this.pTags = listOf(it) + (this.pTags ?: emptyList())
                    }
                }

                forkedFromNote = forkedNoted
            } ?: run {
                forkedFromNote = null
            }

            if (!forwardZapTo.value.items.isEmpty()) {
                wantsForwardZapTo = true
            }
        }

        urlPreviews.update(message)
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return
        if (draftEvent !is TextNoteEvent) return

        loadFromDraft(draftEvent)
    }

    private fun loadFromDraft(draftEvent: TextNoteEvent) {
        canAddInvoice = accountViewModel.userProfile().info?.lnAddress() != null
        canAddZapRaiser = accountViewModel.userProfile().info?.lnAddress() != null
        multiOrchestrator = null

        val localForwardZapTo = draftEvent.tags.filter { it.size > 1 && it[0] == "zap" }
        forwardZapTo.value = SplitBuilder()
        localForwardZapTo.forEach {
            val user = LocalCache.getOrCreateUser(it[1])
            val value = it.last().toFloatOrNull() ?: 0f
            forwardZapTo.value.addItem(user, value)
        }
        forwardZapToEditting.value = TextFieldValue("")
        wantsForwardZapTo = localForwardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
        if (geohash != null) {
            wantsExclusiveGeoPost = draftEvent.kind == CommentEvent.KIND
        }

        val zapRaiser = draftEvent.zapraiserAmount()
        wantsZapRaiser = zapRaiser != null
        zapRaiserAmount.value = null
        if (zapRaiser != null) {
            zapRaiserAmount.value = zapRaiser
        }

        eTags =
            draftEvent.tags.filter { it.size > 1 && (it[0] == "e" || it[0] == "a") && it.getOrNull(3) != "fork" }.mapNotNull {
                val note = LocalCache.checkGetOrCreateNote(it[1])
                note
            }

        pTags =
            draftEvent.tags.filter { it.size > 1 && it[0] == "p" }.map {
                LocalCache.getOrCreateUser(it[1])
            }

        draftEvent.tags.filter { it.size > 3 && (it[0] == "e" || it[0] == "a") && it[3] == "fork" }.forEach {
            val note = LocalCache.checkGetOrCreateNote(it[1])
            forkedFromNote = note
        }

        originalNote =
            draftEvent
                .tags
                .filter { it.size > 1 && (it[0] == "e" || it[0] == "a") && it.getOrNull(3) == "reply" }
                .map {
                    LocalCache.checkGetOrCreateNote(it[1])
                }.firstOrNull()

        if (originalNote == null) {
            originalNote =
                draftEvent
                    .tags
                    .filter { it.size > 1 && (it[0] == "e" || it[0] == "a") && it.getOrNull(3) == "root" }
                    .map {
                        LocalCache.checkGetOrCreateNote(it[1])
                    }.firstOrNull()
        }

        canUsePoll = originalNote == null

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        val polls = draftEvent.tags.filter { it.size > 1 && it[0] == "poll_option" }
        wantsPoll = polls.isNotEmpty()

        polls.forEach {
            pollOptions[it[1].toInt()] = it[2]
        }

        val minMax = draftEvent.tags.filter { it.size > 1 && (it[0] == "value_minimum" || it[0] == "value_maximum") }
        minMax.forEach {
            if (it[0] == "value_maximum") {
                valueMaximum = it[1].toLong()
            } else if (it[0] == "value_minimum") {
                valueMinimum = it[1].toLong()
            }
        }

        message = TextFieldValue(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreviews.update(message)
    }

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return
        val extraNotesToBroadcast = mutableListOf<Event>()

        if (nip95attachments.isNotEmpty()) {
            val usedImages = template.tags.taggedQuoteIds().toSet()
            nip95attachments.forEach {
                if (usedImages.contains(it.second.id)) {
                    extraNotesToBroadcast.add(it.first)
                    extraNotesToBroadcast.add(it.second)
                }
            }
        }

        val version = draftTag.current
        cancel()

        accountViewModel.account.signAndComputeBroadcast(template, extraNotesToBroadcast)
        accountViewModel.viewModelScope.launch(Dispatchers.IO) {
            accountViewModel.account.deleteDraftIgnoreErrors(version)
        }
    }

    suspend fun sendDraftSync() {
        if (message.text.isBlank()) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftTag.current)
        } else {
            val attachments = mutableSetOf<Event>()
            nip95attachments.forEach {
                attachments.add(it.first)
                attachments.add(it.second)
            }

            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template, attachments)
        }
    }

    private suspend fun createTemplate(): EventTemplate<out Event>? {
        val tagger =
            NewMessageTagger(
                message.text,
                pTags,
                eTags,
                accountViewModel,
            )
        tagger.run()

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val geoHash = if (wantsToAddGeoHash) (location?.value as? LocationState.LocationResult.Success)?.geoHash?.toString() else null
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null

        val emojis = findEmoji(tagger.message, account.emoji.myEmojis.value)
        val urls = findURLs(tagger.message)
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        val contentWarningReason = if (wantsToMarkAsSensitive) "" else null

        return if (wantsPoll) {
            val options = pollOptions.map { PollOptionTag(it.key, it.value) }

            if (options.isEmpty()) return null

            val quotes = findNostrUris(tagger.message)

            PollNoteEvent.build(tagger.message, options) {
                valueMinimum?.let { minAmount(it) }
                valueMaximum?.let { maxAmount(it) }
                closedAt?.let { closedAt(it) }
                consensusThreshold?.let { consensusThreshold(it / 100.0) }

                pTags(tagger.directMentionsUsers.map { it.toPTag() })
                quotes(quotes)
                hashtags(findHashtags(tagger.message))

                geoHash?.let { geohash(it) }
                localZapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                contentWarningReason?.let { contentWarning(it) }

                emojis(emojis)
                imetas(usedAttachments)
            }
        } else {
            TextNoteEvent.build(tagger.message) {
                val replyingTo = originalNote?.toEventHint<TextNoteEvent>()
                val forkingFrom = forkedFromNote?.toEventHint<TextNoteEvent>()

                if (replyingTo != null || forkingFrom != null) {
                    val tags = prepareETagsAsReplyTo(replyingTo, forkingFrom)
                    // fixes wrong tags from previous clients
                    tags.forEach {
                        val note = accountViewModel.getNoteIfExists(it.eventId)
                        val ourAuthor = note?.author?.pubkeyHex
                        val ourHint = note?.relayHintUrl()
                        if (it.author == null || it.author?.isBlank() == true) {
                            it.author = ourAuthor
                        } else {
                            if (ourAuthor != null && it.author != ourAuthor) {
                                it.author = ourAuthor
                            }
                        }
                        if (it.relay == null) {
                            it.relay = ourHint
                        } else {
                            if (ourHint != null && it.relay != ourHint) {
                                it.relay = ourHint
                            }
                        }
                    }
                    markedETags(tags)
                }

                tagger.pTags?.let { userList ->
                    val tags =
                        userList.map {
                            val tag = it.toPTag()
                            if (tag.relayHint == null) {
                                tag.copy(relayHint = LocalCache.relayHints.hintsForKey(it.pubkeyHex).firstOrNull())
                            } else {
                                tag
                            }
                        }
                    notify(tags)
                }

                hashtags(findHashtags(tagger.message))
                references(findURLs(tagger.message))
                quotes(findNostrUris(tagger.message))

                geoHash?.let { geohash(it) }
                localZapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                contentWarningReason?.let { contentWarning(it) }

                emojis(emojis)
                imetas(usedAttachments)
            }
        }
    }

    fun findEmoji(
        message: String,
        myEmojiSet: List<EmojiMedia>?,
    ): List<EmojiUrlTag> {
        if (myEmojiSet == null) return emptyList()
        return CustomEmoji.findAllEmojiCodes(message).mapNotNull { possibleEmoji ->
            myEmojiSet.firstOrNull { it.code == possibleEmoji }?.let { EmojiUrlTag(it.code, it.link.url) }
        }
    }

    fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        useH265: Boolean,
    ) = try {
        uploadUnsafe(alt, contentWarningReason, mediaQuality, server, onError, context, useH265)
    } catch (_: SignerExceptions.ReadOnlyException) {
        onError(
            stringRes(context, R.string.read_only_user),
            stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
        )
    }

    fun uploadUnsafe(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        useH265: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            isUploadingImage = true

            val results =
                myMultiOrchestrator.upload(
                    alt,
                    contentWarningReason,
                    MediaCompressor.intToCompressorQuality(mediaQuality),
                    server,
                    account,
                    context,
                    useH265,
                )

            if (results.allGood) {
                results.successful.forEach { state ->
                    if (state.result is UploadOrchestrator.OrchestratorResult.NIP95Result) {
                        val nip95 = account.createNip95(state.result.bytes, headerInfo = state.result.fileHeader, alt, contentWarningReason)
                        nip95attachments = nip95attachments + nip95
                        val note = nip95.let { it1 -> account.consumeNip95(it1.first, it1.second) }

                        note?.let {
                            message = message.insertUrlAtCursor("nostr:" + it.toNEvent())
                            urlPreviews.update(message)
                        }
                    } else if (state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        val iMeta =
                            IMetaTagBuilder(state.result.url)
                                .apply {
                                    hash(state.result.fileHeader.hash)
                                    size(state.result.fileHeader.size)
                                    state.result.fileHeader.mimeType
                                        ?.let { mimeType(it) }
                                    state.result.fileHeader.dim
                                        ?.let { dims(it) }
                                    state.result.fileHeader.blurHash
                                        ?.let { blurhash(it.blurhash) }
                                    state.result.magnet?.let { magnet(it) }
                                    state.result.uploadedHash?.let { originalHash(it) }

                                    alt?.let { alt(it) }
                                    contentWarningReason?.let { sensitiveContent(contentWarningReason) }
                                }.build()

                        iMetaAttachments.replace(iMeta.url, iMeta)

                        message = message.insertUrlAtCursor(state.result.url)
                        urlPreviews.update(message)
                    }
                }

                multiOrchestrator = null
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()
                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            isUploadingImage = false
        }
    }

    open fun cancel() {
        draftTag.rotate()

        message = TextFieldValue("")

        forkedFromNote = null

        multiOrchestrator = null
        isUploadingImage = false
        pTags = null

        wantsPoll = false
        zapRecipients = mutableStateListOf()
        pollOptions = newStateMapPollOptions()
        valueMaximum = null
        valueMinimum = null
        consensusThreshold = null
        closedAt = null

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        wantsToAddGeoHash = false
        wantsExclusiveGeoPost = false
        wantsSecretEmoji = false

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.value = TextFieldValue("")

        urlPreviews.reset()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        this.multiOrchestrator?.remove(selected)
    }

    open fun removeFromReplyList(userToRemove: User) {
        pTags = pTags?.filter { it != userToRemove }
    }

    open fun addToMessage(it: String) {
        updateMessage(TextFieldValue(message.text + " " + it))
    }

    override fun updateMessage(newMessage: TextFieldValue) {
        message = newMessage
        urlPreviews.update(message)

        if (message.selection.collapsed) {
            val lastWord = message.currentWord()

            userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
            userSuggestions?.processCurrentWord(lastWord)

            emojiSuggestions?.processCurrentWord(lastWord)
        }

        draftTag.newVersion()
    }

    override fun updateZapForwardTo(newZapForwardTo: TextFieldValue) {
        forwardZapToEditting.value = newZapForwardTo
        if (newZapForwardTo.selection.collapsed) {
            val lastWord = newZapForwardTo.text
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord = message.currentWord()
                message = userSuggestions.replaceCurrentWord(message, lastWord, item)
                urlPreviews.update(message)
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.value.addItem(item)
                forwardZapToEditting.value = TextFieldValue("")
            }

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }

        draftTag.newVersion()
    }

    open fun autocompleteWithEmoji(item: EmojiMedia) {
        val wordToInsert = ":${item.code}:"

        message = message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message)

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    open fun autocompleteWithEmojiUrl(item: EmojiMedia) {
        val wordToInsert = item.link.url + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(item.link.url) {
                Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForImage(item.link.url)
            }
        }

        message = message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message)

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    private fun newStateMapPollOptions(): SnapshotStateMap<Int, String> = mutableStateMapOf(Pair(0, ""), Pair(1, ""))

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            !isUploadingImage &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount.value != null) &&
            (
                !wantsPoll ||
                    (
                        pollOptions.values.all { it.isNotEmpty() } &&
                            isValidValueMinimum.value &&
                            isValidValueMaximum.value
                    )
            ) &&
            multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message = message.insertUrlAtCursor(newElement)
    }

    fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
    }

    override fun locationFlow(): StateFlow<LocationState.LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
    }

    fun updateMinZapAmountForPoll(textMin: String) {
        valueMinimum = textMin.toLongOrNull()?.takeIf { it > 0 }
        checkMinMax()
        draftTag.newVersion()
    }

    fun updateMaxZapAmountForPoll(textMax: String) {
        valueMaximum = textMax.toLongOrNull()?.takeIf { it > 0 }
        checkMinMax()
        draftTag.newVersion()
    }

    fun checkMinMax() {
        if ((valueMinimum ?: 0) > (valueMaximum ?: Long.MAX_VALUE)) {
            isValidValueMinimum.value = false
            isValidValueMaximum.value = false
        } else {
            isValidValueMinimum.value = true
            isValidValueMaximum.value = true
        }
    }

    override fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.value.updatePercentage(index, sliderValue)
    }

    override fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.IO) {
            val tagger =
                NewMessageTagger(message.text, emptyList(), emptyList(), accountViewModel)
            tagger.run()
            tagger.pTags?.forEach { taggedUser ->
                if (!forwardZapTo.value.items.any { it.key == taggedUser }) {
                    forwardZapTo.value.addItem(taggedUser)
                }
            }
        }
    }

    override fun updateZapRaiserAmount(newAmount: Long?) {
        zapRaiserAmount.value = newAmount
        draftTag.newVersion()
    }

    fun removePollOption(optionIndex: Int) {
        pollOptions.removeOrdered(optionIndex)
        draftTag.newVersion()
    }

    private fun MutableMap<Int, String>.removeOrdered(index: Int) {
        val keyList = keys
        val elementList = values.toMutableList()
        run stop@{
            for (i in index until elementList.size) {
                val nextIndex = i + 1
                if (nextIndex == elementList.size) return@stop
                elementList[i] = elementList[nextIndex].also { elementList[nextIndex] = "null" }
            }
        }
        elementList.removeAt(elementList.size - 1)
        val newEntries = keyList.zip(elementList) { key, content -> Pair(key, content) }
        this.clear()
        this.putAll(newEntries)
    }

    fun updatePollOption(
        optionIndex: Int,
        text: String,
    ) {
        pollOptions[optionIndex] = text
        draftTag.newVersion()
    }

    fun toggleMarkAsSensitive() {
        wantsToMarkAsSensitive = !wantsToMarkAsSensitive
        draftTag.newVersion()
    }

    override fun locationManager(): LocationState = Amethyst.instance.locationManager
}
