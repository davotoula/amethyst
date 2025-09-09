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
            val cacheSize = 1024 * 1024 * 1024 // 1GB
            val diskCache =
                DiskCache
                    .Builder()
                    .directory(app.safeCacheDir().resolve("image_cache").toOkioPath())
                    .maxSizePercent(0.2)
                    .maximumMaxSizeBytes(cacheSize.toLong())
                    .build()

            Log.d("ProfileImageCache", "Disk cache initialized - Directory: ${diskCache.directory}, Size: ${cacheSize / (1024 * 1024)}MB (Android ${android.os.Build.VERSION.SDK_INT})")
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
                    val fileCount = files?.size ?: 0
                    Log.d("ProfileImageCache", "Cache dir contains $fileCount files")

                    // Check available disk space
                    val freeSpaceBytes = cacheDir.freeSpace
                    val freeSpaceMB = freeSpaceBytes / (1024 * 1024)
                    Log.d("ProfileImageCache", "Available disk space: ${freeSpaceMB}MB")

                    // Warn if cache is getting large (80% of limit)
                    val warningThreshold =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            80 // 80% of 100MB for Android Q+
                        } else {
                            160 // 80% of 200MB for older Android
                        }

                    if (sizeMB > warningThreshold) {
                        val maxSize = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) "100MB" else "200MB"
                        Log.w("ProfileImageCache", "⚠️  CACHE SIZE WARNING: ${sizeMB}MB is approaching $maxSize limit")
                    }

                    // Warn if very low disk space
                    if (freeSpaceMB < 100) {
                        Log.e("ProfileImageCache", "🚨 LOW DISK SPACE: Only ${freeSpaceMB}MB available")
                        Log.e("ProfileImageCache", "  - This may cause image loading failures")
                    }

                    // Warn if too many files (could slow down filesystem operations)
                    if (fileCount > 5000) {
                        Log.w("ProfileImageCache", "⚠️  HIGH FILE COUNT: $fileCount files may slow cache operations")
                    }
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
