/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.experimental.nip95

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip31Alts.AltTagSerializer
import com.vitorpamplona.quartz.nip36SensitiveContent.ContentWarningSerializer
import com.vitorpamplona.quartz.nip94FileMetadata.Dimension
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class FileStorageHeaderEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun dataEventId() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun mimeType() = tags.firstOrNull { it.size > 1 && it[0] == MIME_TYPE }?.get(1)

    fun hash() = tags.firstOrNull { it.size > 1 && it[0] == HASH }?.get(1)

    fun size() = tags.firstOrNull { it.size > 1 && it[0] == FILE_SIZE }?.get(1)

    fun alt() = tags.firstOrNull { it.size > 1 && it[0] == ALT }?.get(1)

    fun dimensions() = tags.firstOrNull { it.size > 1 && it[0] == DIMENSION }?.get(1)?.let { Dimension.parse(it) }

    fun magnetURI() = tags.firstOrNull { it.size > 1 && it[0] == MAGNET_URI }?.get(1)

    fun torrentInfoHash() = tags.firstOrNull { it.size > 1 && it[0] == TORRENT_INFOHASH }?.get(1)

    fun blurhash() = tags.firstOrNull { it.size > 1 && it[0] == BLUR_HASH }?.get(1)

    fun isOneOf(mimeTypes: Set<String>) = tags.any { it.size > 1 && it[0] == FileHeaderEvent.MIME_TYPE && mimeTypes.contains(it[1]) }

    companion object {
        const val KIND = 1065
        const val ALT_DESCRIPTION = "Descriptors for a binary file"

        private const val ENCRYPTION_KEY = "aes-256-gcm"
        private const val MIME_TYPE = "m"
        private const val FILE_SIZE = "size"
        private const val DIMENSION = "dim"
        private const val HASH = "x"
        private const val MAGNET_URI = "magnet"
        private const val TORRENT_INFOHASH = "i"
        private const val BLUR_HASH = "blurhash"
        private const val ALT = "alt"

        fun create(
            storageEvent: FileStorageEvent,
            mimeType: String? = null,
            alt: String? = null,
            hash: String? = null,
            size: String? = null,
            dimensions: Dimension? = null,
            blurhash: String? = null,
            magnetURI: String? = null,
            torrentInfoHash: String? = null,
            sensitiveContent: Boolean? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (FileStorageHeaderEvent) -> Unit,
        ) {
            val tags =
                listOfNotNull(
                    arrayOf("e", storageEvent.id),
                    mimeType?.let { arrayOf(MIME_TYPE, mimeType) },
                    hash?.let { arrayOf(HASH, it) },
                    alt?.let { arrayOf(ALT, it) } ?: AltTagSerializer.toTagArray(ALT_DESCRIPTION),
                    size?.let { arrayOf(FILE_SIZE, it) },
                    dimensions?.let { arrayOf(DIMENSION, it.toString()) },
                    blurhash?.let { arrayOf(BLUR_HASH, it) },
                    magnetURI?.let { arrayOf(MAGNET_URI, it) },
                    torrentInfoHash?.let { arrayOf(TORRENT_INFOHASH, it) },
                    sensitiveContent?.let {
                        if (it) {
                            ContentWarningSerializer.toTagArray()
                        } else {
                            null
                        }
                    },
                )

            val content = alt ?: ""
            signer.sign(createdAt, KIND, tags.toTypedArray(), content, onReady)
        }
    }
}
