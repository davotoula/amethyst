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
import android.os.Build
import android.util.Log
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.CacheStrategy
import coil3.network.ConnectivityChecker
import coil3.network.NetworkFetcher
import coil3.network.okhttp.asNetworkClient
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import com.vitorpamplona.amethyst.isDebug
import okhttp3.Call

class ImageLoaderSetup {
    companion object {
        val gifFactory =
            if (Build.VERSION.SDK_INT >= 28) {
                AnimatedImageDecoder.Factory()
            } else {
                GifDecoder.Factory()
            }
        val svgFactory = SvgDecoder.Factory()

        val debugLogger = if (isDebug) DebugLogger() else null

        private var currentDiskCache: DiskCache? = null
        private val loadStartTimes = mutableMapOf<String, Long>()

        private val profileImageEventListener =
            object : EventListener() {
                override fun onStart(request: ImageRequest) {
                    val url = request.data.toString()
                    if (isProfileImageUrl(url)) {
                        val startTime = System.currentTimeMillis()
                        loadStartTimes[url] = startTime

                        Log.d("ProfileImageCache", "⏱️ Loading profile image: ${url.take(50)}...")
                        Log.d("ProfileImageCache", "  - Request data: ${request.data}")

                        // Check if URL exists in disk cache before loading
                        ImageCacheFactory.checkDiskCacheForUrl(currentDiskCache, url)
                    }
                }

                override fun onSuccess(
                    request: ImageRequest,
                    result: SuccessResult,
                ) {
                    val url = request.data.toString()
                    if (isProfileImageUrl(url)) {
                        val endTime = System.currentTimeMillis()
                        val startTime = loadStartTimes.remove(url)
                        val loadTimeMs = if (startTime != null) endTime - startTime else -1

                        Log.d("ProfileImageCache", "✓ Profile image loaded from ${result.dataSource.name}: ${url.take(50)}... (${loadTimeMs}ms)")
                        Log.d("ProfileImageCache", "  - Load time: ${loadTimeMs}ms")
                        Log.d("ProfileImageCache", "  - Result drawable: ${result.image}")

                        // If loaded from network, check if it gets written to disk cache
                        if (result.dataSource.name.contains("NETWORK")) {
                            Log.d("ProfileImageCache", "  - Image loaded from network, should be cached to disk now")
                            // Check disk cache again after a brief delay
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                ImageCacheFactory.checkDiskCacheForUrl(currentDiskCache, url)
                                ImageCacheFactory.logCacheStats(currentDiskCache, null)
                            }, 500)
                        }
                    }
                }

                override fun onError(
                    request: ImageRequest,
                    result: ErrorResult,
                ) {
                    val url = request.data.toString()
                    if (isProfileImageUrl(url)) {
                        val endTime = System.currentTimeMillis()
                        val startTime = loadStartTimes.remove(url)
                        val loadTimeMs = if (startTime != null) endTime - startTime else -1

                        val errorMsg = result.throwable.message ?: "Unknown error"
                        Log.w("ProfileImageCache", "✗ Profile image failed to load: ${url.take(50)}... (${loadTimeMs}ms) - $errorMsg")

                        // Check for cache-related errors
                        if (errorMsg.contains("cache", ignoreCase = true) ||
                            errorMsg.contains("disk", ignoreCase = true) ||
                            errorMsg.contains("space", ignoreCase = true) ||
                            errorMsg.contains("full", ignoreCase = true) ||
                            errorMsg.contains("IOException", ignoreCase = true) ||
                            errorMsg.contains("pinning", ignoreCase = true) ||
                            errorMsg.contains("trim", ignoreCase = true)
                        ) {
                            Log.e("ProfileImageCache", "🚨 CACHE-RELATED ERROR DETECTED: $errorMsg")
                            Log.e("ProfileImageCache", "  - This might indicate cache is full or corrupted")
                            Log.e("ProfileImageCache", "  - URL: $url")

                            // Log current cache stats to understand the situation
                            ImageCacheFactory.logCacheStats(currentDiskCache, null)
                        }
                    }
                }

                private fun isProfileImageUrl(url: String): Boolean =
                    url.startsWith("http") &&
                        (
                            url.contains("/profile") ||
                                url.contains("avatar") ||
                                url.contains("picture") ||
                                url.contains(".jpg") ||
                                url.contains(".png") ||
                                url.contains(".webp") ||
                                url.contains(".gif")
                        )
            }

        @OptIn(DelicateCoilApi::class)
        fun setup(
            app: Application,
            diskCache: DiskCache,
            memoryCache: MemoryCache,
            callFactory: (url: String) -> Call.Factory,
        ) {
            // Store disk cache reference for logging
            currentDiskCache = diskCache

            Log.d("ProfileImageCache", "Setting up ImageLoader with disk caching enabled")

            SingletonImageLoader.setUnsafe(
                ImageLoader
                    .Builder(app)
                    .diskCache { diskCache }
                    .memoryCache { memoryCache }
                    .precision(Precision.INEXACT)
                    .logger(debugLogger)
                    .eventListener(profileImageEventListener)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .components {
                        add(gifFactory)
                        add(svgFactory)
                        add(Base64Fetcher.Factory)
                        add(BlurHashFetcher.Factory)
                        add(Base64Fetcher.BKeyer)
                        add(BlurHashFetcher.BKeyer)
                        add(OkHttpFactory(callFactory))
                    }.build(),
            )
        }
    }
}

/**
 * Copied from Coil to allow networkClient to be a function of the url.
 * So that Tor and non Tor clients can be used.
 */
@OptIn(ExperimentalCoilApi::class)
class OkHttpFactory(
    val networkClient: (url: String) -> Call.Factory,
) : Fetcher.Factory<Uri> {
    private val cacheStrategyLazy = lazy { CacheStrategy.DEFAULT }
    private val connectivityCheckerLazy = singleParameterLazy(::ConnectivityChecker)

    override fun create(
        data: Uri,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        if (!isApplicable(data)) return null

        val url = data.toString()

        return NetworkFetcher(
            url = url,
            options = options,
            networkClient = lazy { networkClient(url).asNetworkClient() },
            diskCache = lazy { imageLoader.diskCache },
            cacheStrategy = cacheStrategyLazy,
            connectivityChecker = connectivityCheckerLazy.get(options.context),
        )
    }

    private fun isApplicable(data: Uri): Boolean = data.scheme == "http" || data.scheme == "https"
}

internal fun <P, T> singleParameterLazy(initializer: (P) -> T) = SingleParameterLazy(initializer)

internal class SingleParameterLazy<P, T>(
    initializer: (P) -> T,
) : Any() {
    private var initializer: ((P) -> T)? = initializer
    private var value: Any? = UNINITIALIZED

    @Suppress("UNCHECKED_CAST")
    fun get(parameter: P): T {
        val value1 = value
        if (value1 !== UNINITIALIZED) {
            return value1 as T
        }

        return synchronized(this) {
            val value2 = value
            if (value2 !== UNINITIALIZED) {
                value2 as T
            } else {
                val newValue = initializer!!(parameter)
                value = newValue
                initializer = null
                newValue
            }
        }
    }
}

private object UNINITIALIZED
