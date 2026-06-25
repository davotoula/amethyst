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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.authorsNeedingNip65Backfill
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reproduces the "missing quoted note" failure mode at the resolution layer.
 *
 * Vitor's merged fix routes a missing quoted event's loading through the outbox relays of the
 * authors p-tagged by the quoting post. That only works when those authors' NIP-65 (kind 10002)
 * is already resolved; when it isn't, the path yields no relays and the note stays missing (the
 * Atelier `7535eba9…` case). The fix is to backfill the unresolved authors' NIP-65.
 *
 * [authorsNeedingNip65Backfill] is the pure decision: given the cited authors and a resolver for
 * their outbox, return the ones we still can't resolve and therefore must fetch.
 */
class QuotedAuthorNip65BackfillTest {
    private val atelier = "725f8be0246fb6f730dabb747372b25814cae667bc5864ab1d97d89aadd267ef"
    private val resolved = "1af54955936be804f95010647ea5ada5c7627eddf0734a7f813bba0e31eed960"

    private val atelierOutbox =
        setOf(
            NormalizedRelayUrl("wss://relay.damus.io/"),
            NormalizedRelayUrl("wss://nos.lol/"),
        )

    @Test
    fun unresolvedOutboxIsSelectedForBackfill() {
        // The bug: cited author's NIP-65 not yet cached -> outbox null -> needs a backfill.
        val result =
            authorsNeedingNip65Backfill(
                citedAuthors = setOf(atelier),
                outboxOf = { null },
            )

        assertEquals(setOf(atelier), result)
    }

    @Test
    fun emptyOutboxIsSelectedForBackfill() {
        // A read-only NIP-65 (no write relays) resolves to an empty outbox: still unusable.
        val result =
            authorsNeedingNip65Backfill(
                citedAuthors = setOf(atelier),
                outboxOf = { emptySet() },
            )

        assertEquals(setOf(atelier), result)
    }

    @Test
    fun resolvedOutboxIsNotBackfilled() {
        // Already usable -> no fetch (avoids re-requesting NIP-65 we already have).
        val result =
            authorsNeedingNip65Backfill(
                citedAuthors = setOf(resolved),
                outboxOf = { atelierOutbox },
            )

        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun onlyUnresolvedAuthorsAreSelected() {
        val result =
            authorsNeedingNip65Backfill(
                citedAuthors = setOf(atelier, resolved),
                outboxOf = { pubkey -> if (pubkey == resolved) atelierOutbox else null },
            )

        assertEquals(setOf(atelier), result)
    }

    @Test
    fun noCitedAuthorsYieldsNothing() {
        val result =
            authorsNeedingNip65Backfill(
                citedAuthors = emptySet(),
                outboxOf = { error("must not be called for an empty input") },
            )

        assertEquals(emptySet<String>(), result)
    }
}
