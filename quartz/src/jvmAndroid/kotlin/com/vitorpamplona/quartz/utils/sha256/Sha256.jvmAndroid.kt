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
package com.vitorpamplona.quartz.utils.sha256

import java.io.InputStream

val pool = Sha256Pool(5) // max parallel operations

actual fun sha256(data: ByteArray) = pool.hash(data)

/**
 * Calculate SHA256 hash while counting bytes read from the stream.
 * Returns both the hash and the number of bytes processed.
 * This is more efficient than reading the stream twice.
 *
 * @param inputStream The input stream to hash
 * @param bufferSize Size of chunks to read (default 8KB)
 * @return Pair of (hash bytes, bytes read count)
 */
fun sha256StreamWithCount(
    inputStream: InputStream,
    bufferSize: Int = 8192,
): Pair<ByteArray, Long> {
    val countingStream = CountingInputStream(inputStream)
    val hash = pool.hashStream(countingStream, bufferSize)
    return Pair(hash, countingStream.bytesRead)
}
