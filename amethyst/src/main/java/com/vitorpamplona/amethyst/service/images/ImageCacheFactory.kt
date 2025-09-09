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
package com.vitorpamplona.amethyst.service.images

import android.app.Application
import android.util.Log
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.vitorpamplona.amethyst.service.safeCacheDir
import okio.Path.Companion.toOkioPath

class ImageCacheFactory {
    companion object {
        fun newDisk(app: Application): DiskCache {
            val diskCache =
                DiskCache
                    .Builder()
                    .directory(app.safeCacheDir().resolve("image_cache").toOkioPath())
                    .maxSizePercent(0.2)
                    .maximumMaxSizeBytes(1024 * 1024 * 1024) // 1GB
                    .build()

            Log.d("ProfileImageCache", "Disk cache initialized - Directory: ${diskCache.directory}")
            return diskCache
        }

        fun newMemory(app: Application): MemoryCache {
            val memoryCache =
                MemoryCache
                    .Builder()
                    .maxSizePercent(app)
                    .build()

            Log.d("ProfileImageCache", "Memory cache initialized")
            return memoryCache
        }

        fun logCacheStats(
            diskCache: DiskCache?,
            memoryCache: MemoryCache?,
        ) {
            diskCache?.let {
                val sizeBytes = it.size
                val sizeMB = sizeBytes / (1024 * 1024)
                Log.d("ProfileImageCache", "Disk cache stats - Used: ${sizeMB}MB")
                Log.d("ProfileImageCache", "Disk cache directory: ${it.directory}")

                // Check if disk cache directory exists and is writable
                val cacheDir = it.directory.toFile()
                Log.d("ProfileImageCache", "Cache dir exists: ${cacheDir.exists()}, writable: ${cacheDir.canWrite()}")
                if (cacheDir.exists()) {
                    val files = cacheDir.listFiles()
                    Log.d("ProfileImageCache", "Cache dir contains ${files?.size ?: 0} files")
                }
            }

            memoryCache?.let {
                val sizeBytes = it.size
                val sizeMB = sizeBytes / (1024 * 1024)
                Log.d("ProfileImageCache", "Memory cache stats - Used: ${sizeMB}MB")
            }
        }

        fun checkDiskCacheForUrl(
            diskCache: DiskCache?,
            url: String,
        ) {
            diskCache?.let { cache ->
                try {
                    Log.d("ProfileImageCache", "Checking disk cache for key: ${url.take(50)}...")
                    // Note: We can't directly check if a key exists in Coil 3 disk cache
                    // This is just a placeholder for now - the main info will come from the EventListener
                } catch (e: Exception) {
                    Log.w("ProfileImageCache", "Error checking disk cache for URL: ${e.message}")
                }
            }
        }
    }
}
