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
package com.vitorpamplona.amethyst.service.uploads.blossom

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.HttpStatusMessages
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.sha256.sha256StreamWithCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.coroutines.executeAsync
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.InputStream
import java.util.Base64

class BlossomUploader {
    data class StreamInfo(
        val hash: HexKey,
        val size: Long,
    )

    /**
     * Calculate SHA256 hash and size of a file by streaming it in chunks
     * to avoid loading the entire file into memory.
     */
    private fun calculateHashAndSize(inputStream: InputStream): StreamInfo {
        val (hash, size) = sha256StreamWithCount(inputStream)
        return StreamInfo(hash.toHexKey(), size)
    }

    fun Context.getFileName(uri: Uri): String? =
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
            else -> uri.path?.let(::File)?.name
        }

    private fun Context.getContentFileName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                return@use cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
            }
        }.getOrNull()

    suspend fun upload(
        uri: Uri,
        contentType: String?,
        size: Long?,
        alt: String?,
        sensitiveContent: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: suspend (hash: HexKey, size: Long, alt: String) -> BlossomAuthorizationEvent?,
        context: Context,
    ): MediaUploadResult {
        checkNotInMainThread()

        val contentResolver = context.contentResolver
        val myContentType = contentType ?: contentResolver.getType(uri)
        val fileName = context.getFileName(uri)

        val imageInputStreamForHash = contentResolver.openInputStream(uri)
        checkNotNull(imageInputStreamForHash) { "Can't open the image input stream" }

        val streamInfo =
            imageInputStreamForHash.use {
                calculateHashAndSize(it)
            }

        val imageInputStream = contentResolver.openInputStream(uri)

        checkNotNull(imageInputStream) { "Can't open the image input stream" }

        return imageInputStream.use { stream ->
            upload(
                stream,
                streamInfo.hash,
                streamInfo.size,
                fileName,
                myContentType,
                alt,
                sensitiveContent,
                serverBaseUrl,
                okHttpClient,
                httpAuth,
                context,
            )
        }
    }

    fun encodeAuth(event: BlossomAuthorizationEvent): String {
        val encodedNIP98Event = Base64.getEncoder().encodeToString(event.toJson().toByteArray())
        return "Nostr $encodedNIP98Event"
    }

    suspend fun upload(
        inputStream: InputStream,
        hash: HexKey,
        length: Long,
        baseFileName: String?,
        contentType: String?,
        alt: String?,
        sensitiveContent: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: suspend (hash: HexKey, size: Long, alt: String) -> BlossomAuthorizationEvent?,
        context: Context,
    ): MediaUploadResult {
        checkNotInMainThread()

        val fileName = baseFileName ?: RandomInstance.randomChars(16)
        val extension =
            contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val apiUrl = serverBaseUrl.removeSuffix("/") + "/upload"

        val client = okHttpClient(apiUrl)
        val requestBuilder = Request.Builder()

        val requestBody: RequestBody =
            object : RequestBody() {
                override fun contentType() = contentType?.toMediaType()

                override fun contentLength() = length

                override fun writeTo(sink: BufferedSink) {
                    inputStream.source().use(sink::writeAll)
                }
            }

        httpAuth(hash, length, alt?.let { "Uploading $it" } ?: "Uploading $fileName")?.let {
            requestBuilder.addHeader("Authorization", encodeAuth(it))
        }

        contentType?.let { requestBuilder.addHeader("Content-Type", it) }

        requestBuilder
            .addHeader("Content-Length", length.toString())
            .url(apiUrl)
            .put(requestBody)

        val request = requestBuilder.build()

        return client.newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    response.body.use { body ->
                        convertToMediaResult(parseResults(body.string()))
                    }
                } else {
                    val errorMessage = response.headers.get("X-Reason")

                    val explanation = HttpStatusMessages.resourceIdFor(response.code)
                    if (errorMessage != null) {
                        throw RuntimeException(stringRes(context, R.string.failed_to_upload_to_server_with_message, serverBaseUrl.displayUrl(), errorMessage))
                    } else if (explanation != null) {
                        throw RuntimeException(stringRes(context, R.string.failed_to_upload_to_server_with_message, serverBaseUrl.displayUrl(), stringRes(context, explanation)))
                    } else {
                        throw RuntimeException(stringRes(context, R.string.failed_to_upload_to_server_with_message, serverBaseUrl.displayUrl(), response.code.toString()))
                    }
                }
            }
        }
    }

    fun convertToMediaResult(result: BlossomUploadResult): MediaUploadResult =
        MediaUploadResult(
            url = result.url,
            type = result.type,
            sha256 = result.sha256,
            size = result.size,
            uploaded = result.uploaded,
            magnet = result.magnet,
            infohash = result.infohash,
            ipfs = result.ipfs,
        )

    fun String.displayUrl() = this.removeSuffix("/").removePrefix("https://")

    suspend fun delete(
        hash: String,
        contentType: String?,
        serverBaseUrl: String,
        okHttpClient: (String) -> OkHttpClient,
        httpAuth: (hash: HexKey, alt: String) -> BlossomAuthorizationEvent?,
        context: Context,
    ): Boolean {
        val extension =
            contentType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: ""

        val apiUrl = serverBaseUrl

        val requestBuilder = Request.Builder()

        httpAuth(hash, "Deleting $hash")?.let {
            requestBuilder.addHeader("Authorization", encodeAuth(it))
        }

        val request =
            requestBuilder
                .url(apiUrl.removeSuffix("/") + "/$hash.$extension")
                .delete()
                .build()

        return okHttpClient(apiUrl).newCall(request).executeAsync().use { response ->
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    true
                } else {
                    val explanation = HttpStatusMessages.resourceIdFor(response.code)
                    if (explanation != null) {
                        throw RuntimeException(stringRes(context, R.string.failed_to_delete_with_message, stringRes(context, explanation)))
                    } else {
                        throw RuntimeException(stringRes(context, R.string.failed_to_delete_with_message, response.code))
                    }
                }
            }
        }
    }

    private fun parseResults(body: String): BlossomUploadResult = JsonMapper.fromJson<BlossomUploadResult>(body)
}
