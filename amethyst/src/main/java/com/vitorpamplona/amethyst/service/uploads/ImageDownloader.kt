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
package com.vitorpamplona.amethyst.service.uploads

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256StreamWithCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.URL

class ImageDownloader {
    class Blob(
        val bytes: ByteArray,
        val contentType: String?,
    )

    /**
     * Result of streaming verification - hash and metadata without storing full file
     */
    class StreamVerification(
        val hash: HexKey,
        val size: Long,
        val contentType: String?,
    )

    /**
     * Stream download and calculate hash for verification without loading entire file into memory.
     * This is memory-efficient for large files (videos, high-res images, etc.)
     */
    suspend fun waitAndVerifyStream(
        imageUrl: String,
        okHttpClient: (url: String) -> OkHttpClient,
    ): StreamVerification? =
        withContext(Dispatchers.IO) {
            var verification: StreamVerification? = null
            var tentatives = 0

            // Servers are usually not ready, so tries to download it for 15 times/seconds.
            while (verification == null && tentatives < 15) {
                verification =
                    try {
                        tryStreamAndVerify(imageUrl, okHttpClient)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }

                if (verification == null) {
                    tentatives++
                    delay(1000)
                }
            }

            return@withContext verification
        }

    suspend fun waitAndGetImage(
        imageUrl: String,
        okHttpClient: (url: String) -> OkHttpClient,
    ): Blob? =
        withContext(Dispatchers.IO) {
            var imageData: Blob? = null
            var tentatives = 0

            // Servers are usually not ready, so tries to download it for 15 times/seconds.
            while (imageData == null && tentatives < 15) {
                imageData =
                    try {
                        tryGetTheImage(imageUrl, okHttpClient)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }

                if (imageData == null) {
                    tentatives++
                    delay(1000)
                }
            }

            return@withContext imageData
        }

    private suspend fun tryStreamAndVerify(
        imageUrl: String,
        okHttpClient: (url: String) -> OkHttpClient,
    ): StreamVerification? =
        withContext(Dispatchers.IO) {
            // TODO: Migrate to OkHttp
            HttpURLConnection.setFollowRedirects(true)
            var url = URL(imageUrl)
            var clientProxy = okHttpClient(imageUrl).proxy
            var huc =
                if (clientProxy != null) {
                    url.openConnection(clientProxy) as HttpURLConnection
                } else {
                    url.openConnection() as HttpURLConnection
                }
            huc.instanceFollowRedirects = true
            var responseCode = huc.responseCode

            if (responseCode in 300..400) {
                val newUrl: String = huc.getHeaderField("Location")

                // open the new connection again
                url = URL(newUrl)
                clientProxy = okHttpClient(newUrl).proxy
                huc =
                    if (clientProxy != null) {
                        url.openConnection(clientProxy) as HttpURLConnection
                    } else {
                        url.openConnection() as HttpURLConnection
                    }
                responseCode = huc.responseCode
            }

            return@withContext try {
                if (responseCode in 200..300) {
                    val (hash, totalBytes) =
                        huc.inputStream.use {
                            sha256StreamWithCount(it)
                        }

                    StreamVerification(
                        hash = hash.toHexKey(),
                        size = totalBytes,
                        contentType = huc.headerFields.get("Content-Type")?.firstOrNull(),
                    )
                } else {
                    null
                }
            } finally {
                // Always disconnect to release connection resources
                huc.disconnect()
            }
        }

    private suspend fun tryGetTheImage(
        imageUrl: String,
        okHttpClient: (url: String) -> OkHttpClient,
    ): Blob? =
        withContext(Dispatchers.IO) {
            // TODO: Migrate to OkHttp
            HttpURLConnection.setFollowRedirects(true)
            var url = URL(imageUrl)
            var clientProxy = okHttpClient(imageUrl).proxy
            var huc =
                if (clientProxy != null) {
                    url.openConnection(clientProxy) as HttpURLConnection
                } else {
                    url.openConnection() as HttpURLConnection
                }
            huc.instanceFollowRedirects = true
            var responseCode = huc.responseCode

            if (responseCode in 300..400) {
                val newUrl: String = huc.getHeaderField("Location")

                // open the new connnection again
                url = URL(newUrl)
                clientProxy = okHttpClient(newUrl).proxy
                huc =
                    if (clientProxy != null) {
                        url.openConnection(clientProxy) as HttpURLConnection
                    } else {
                        url.openConnection() as HttpURLConnection
                    }
                responseCode = huc.responseCode
            }

            return@withContext if (responseCode in 200..300) {
                Blob(
                    huc.inputStream.use { it.readBytes() },
                    huc.headerFields.get("Content-Type")?.firstOrNull(),
                )
            } else {
                null
            }
        }
}
