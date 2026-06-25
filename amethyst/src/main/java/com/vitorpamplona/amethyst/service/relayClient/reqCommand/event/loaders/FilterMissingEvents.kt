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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.mapOfSet
import java.util.concurrent.ConcurrentHashMap

fun potentialRelaysToFindEvent(note: Note): Set<NormalizedRelayUrl> {
    val set = mutableSetOf<NormalizedRelayUrl>()

    set.addAll(LocalCache.relayHints.hintsForEvent(note.idHex))

    note.author?.outboxRelays()?.let { set.addAll(it) }

    LocalCache.getAnyChannel(note)?.relays()?.let { set.addAll(it) }

    note.replyTo?.forEach { parentNote ->
        set.addAll(parentNote.relays)

        LocalCache.getAnyChannel(parentNote)?.relays()?.let { set.addAll(it) }

        parentNote.author?.inboxRelays()?.let { set.addAll(it) }
    }

    note.replies.forEach { childNote ->
        set.addAll(childNote.relays)

        LocalCache.getAnyChannel(childNote)?.relays()?.let { set.addAll(it) }

        childNote.author?.outboxRelays()?.let { set.addAll(it) }
    }

    note.reactions.map { reactionType ->
        reactionType.value.forEach { childNote ->
            set.addAll(childNote.relays)
            childNote.author?.outboxRelays()?.let { set.addAll(it) }
        }
    }

    note.boosts.forEach { childNote ->
        set.addAll(childNote.relays)
        childNote.author?.outboxRelays()?.let { set.addAll(it) }
    }

    note.inGatherers?.forEach { parent ->
        // loads from parent's relays, parent's authors relays and cited authors in the parent note.
        // as well as relays from the channel and other fixed places.
        when (parent) {
            is Note -> {
                set.addAll(parent.relays)
                parent.author?.outboxRelays()?.let { set.addAll(it) }
                parent.author?.inboxRelays()?.let { set.addAll(it) }

                val noteEvent = parent.event
                if (noteEvent is PubKeyHintProvider) {
                    noteEvent.linkedPubKeys().forEach { potentialAuthor ->
                        LocalCache.checkGetOrCreateUser(potentialAuthor)?.let { potentialAuthor ->
                            potentialAuthor.outboxRelays()?.let { set.addAll(it) }
                            potentialAuthor.inboxRelays()?.let { set.addAll(it) }
                        }
                    }
                }
            }

            is Channel -> {
                set.addAll(parent.relays())
            }
        }
    }

    return set
}

fun filterMissingEvents(keys: List<EventFinderQueryState>): List<RelayBasedFilter> {
    val eventsPerRelay =
        mapOfSet {
            keys.forEach { key ->
                val default = key.account.followPlusAllMineWithSearch.flow.value

                if (key.note !is AddressableNote && key.note.event == null) {
                    potentialRelaysToFindEvent(key.note).ifEmpty { default }.forEach { relayUrl ->
                        add(relayUrl, key.note.idHex)
                    }

                    key.account.searchRelayList.flow.value.forEach { relayUrl ->
                        add(relayUrl, key.note.idHex)
                    }
                }

                // loads threading that is event-based
                key.note.replyTo?.forEach { note ->
                    if (note !is AddressableNote && note.event == null) {
                        potentialRelaysToFindEvent(note).ifEmpty { default }.forEach { relayUrl ->
                            add(relayUrl, note.idHex)
                        }
                    }
                }
            }
        }

    return filterMissingEvents(eventsPerRelay) + nip65BackfillFilters(keys)
}

private const val NIP65_BACKFILL_BACKOFF_SECONDS = 300L
private val nip65BackfillAttempts = ConcurrentHashMap<HexKey, Long>()

/**
 * NIP-65 backfill for the authors a quoting post cites.
 *
 * [potentialRelaysToFindEvent] routes a missing quoted event through the outbox relays of the
 * p-tagged authors of the posts that quote it ([Note.inGatherers]) — but only when those authors'
 * NIP-65 is already resolved. For a long-tail / bridged author whose kind-10002 we never fetched,
 * `outboxRelays()` stays null and the event (which carries no relay hint and isn't on index/search
 * relays) stays unfindable. This emits a parallel kind-10002 request for those unresolved authors
 * against the default indexer + search relays so their outbox can resolve and the next pass finds
 * the event.
 *
 * [NoteEventLoaderSubAssembler] re-runs on every EOSE (`invalidateAfterEose`), so a per-author
 * backoff keeps an author whose NIP-65 is genuinely unreachable from being re-requested forever.
 */
fun nip65BackfillFilters(keys: List<EventFinderQueryState>): List<RelayBasedFilter> {
    val citedAuthors = mutableSetOf<HexKey>()
    keys.forEach { key ->
        val note = key.note
        if (note !is AddressableNote && note.event == null) {
            note.inGatherers?.forEach { parent ->
                if (parent is Note) {
                    (parent.event as? PubKeyHintProvider)?.linkedPubKeys()?.let(citedAuthors::addAll)
                }
            }
        }
    }

    val unresolved = authorsNeedingNip65Backfill(citedAuthors) { LocalCache.getUserIfExists(it)?.outboxRelays()?.toSet() }
    if (unresolved.isEmpty()) return emptyList()

    val now = TimeUtils.now()
    val toFetch =
        unresolved.filterTo(mutableSetOf()) { author ->
            val last = nip65BackfillAttempts[author]
            last == null || now - last >= NIP65_BACKFILL_BACKOFF_SECONDS
        }
    if (toFetch.isEmpty()) return emptyList()
    toFetch.forEach { nip65BackfillAttempts[it] = now }

    val authors = toFetch.sorted()
    return (DefaultIndexerRelayList + DefaultSearchRelayList).map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter = Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), authors = authors),
        )
    }
}

fun filterMissingEvents(missingEventIds: Map<NormalizedRelayUrl, Set<String>>): List<RelayBasedFilter> {
    if (missingEventIds.isEmpty()) return emptyList()

    return missingEventIds.mapNotNull {
        if (it.value.isNotEmpty()) {
            RelayBasedFilter(
                relay = it.key,
                filter = Filter(ids = it.value.sorted()),
            )
        } else {
            null
        }
    }
}
