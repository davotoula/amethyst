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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Decides which cited authors need a NIP-65 (kind 10002) backfill so a missing quoted event can be
 * routed through their outbox.
 *
 * A quoting post p-tags the author(s) of the events it cites. When the quoted event isn't in cache
 * and carries no relay hint, the only way to find it is the cited author's outbox — but that
 * requires their NIP-65 to be resolved. [outboxOf] returns the author's write relays, or null/empty
 * when their NIP-65 hasn't been fetched (or is read-only). Those are exactly the authors we must
 * fetch a kind-10002 for; an author whose outbox already resolves is left alone so we don't
 * re-request NIP-65 we already hold.
 *
 * Pure by design (no LocalCache / subscription state) so the decision is unit-testable; the caller
 * supplies [outboxOf] and acts on the returned set (e.g. by registering them with the user-outbox
 * finder).
 */
fun authorsNeedingNip65Backfill(
    citedAuthors: Set<HexKey>,
    outboxOf: (HexKey) -> Set<NormalizedRelayUrl>?,
): Set<HexKey> = citedAuthors.filterTo(mutableSetOf()) { outboxOf(it).isNullOrEmpty() }
