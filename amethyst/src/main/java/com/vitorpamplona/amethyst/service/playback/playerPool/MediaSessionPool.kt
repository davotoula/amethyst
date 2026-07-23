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
@file:OptIn(UnstableApi::class)

package com.vitorpamplona.amethyst.service.playback.playerPool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemCache
import com.vitorpamplona.amethyst.ui.MainActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SessionListener(
    val session: MediaSession,
    val playerListener: Player.Listener,
) {
    // Set once, by the retire funnel. Guards the re-entrant path session.release() ->
    // onDisconnected -> releaseSession -> registry.drop() landing on this same entry.
    val retired = AtomicBoolean(false)

    fun removeListeners() {
        session.player.removeListener(playerListener)
    }
}

/**
 * The goal for this class is to make sure all sessions and exoplayers are closed correctly.
 */
class MediaSessionPool(
    val exoPlayerPool: ExoPlayerPool,
    val dataSourceFactory: DataSource.Factory,
    val appContext: Context,
    // Ceiling on cached sessions. Each one holds a checked-out ExoPlayer, so on a device whose
    // decoder ceiling is lower than [MAX_CACHED_SESSIONS] this is what keeps the session cache
    // from pinning more MediaCodec instances than the hardware will grant.
    maxSessions: Int = MAX_CACHED_SESSIONS,
    val reset: (MediaSession, Boolean) -> Unit,
) {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            com.vitorpamplona.quartz.utils.Log
                .e("MediaSessionPool", "Caught exception: ${throwable.message}", throwable)
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    val globalCallback = MediaSessionCallback(this, appContext)

    // Last cleanup timestamp in nanos, guarded by CAS so concurrent releaseSession() calls
    // can't all win the time check and each launch a redundant scope.launch sweep.
    private val lastCleanupNs = AtomicLong(System.nanoTime())

    // The bitmap loader is stateless w.r.t. the session; a fresh allocation per session was
    // pure noise. ExoPlayer's DEFAULT_EXECUTOR_SERVICE is a process-wide singleton, the
    // dataSourceFactory is owned by the pool, and the appContext is already retained.
    // The init is in a separate function so the @OptIn lands on a real declaration —
    // applying it to a `by lazy` property doesn't propagate into the lambda body.
    private val sharedBitmapLoader by lazy { buildSharedBitmapLoader() }

    @OptIn(UnstableApi::class)
    private fun buildSharedBitmapLoader(): DataSourceBitmapLoader =
        DataSourceBitmapLoader
            .Builder(appContext)
            .setExecutorService(DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get())
            .setDataSourceFactory(dataSourceFactory)
            // Cap decoded artwork to the platform's own metadata bitmap limit so the legacy
            // MediaSession path never has to re-scale it. media3 would otherwise size-limit the
            // bitmap using Resources.getSystem()'s config_mediaMetadataBitmapMaxSize, which on
            // several OEM ROMs (e.g. LineageOS/peridot) is unresolvable and falls back to the full
            // screen width. That over-sized bitmap then gets re-scaled by the framework inside
            // android.media.session.MediaSession.setMetadata(), and some of those ROMs recycle the
            // *source* bitmap during MediaMetadata.Builder.scaleBitmap(). media3's CacheBitmapLoader
            // caches that now-recycled bitmap and hands it back on the next metadata update, which
            // crashes with "cannot use a recycled source in createBitmap". Pre-sizing to the same
            // limit the framework uses keeps build() from ever calling scaleBitmap().
            .setMaximumOutputDimension(mediaMetadataBitmapMaxSize())
            .build()

    /**
     * Mirrors how android.media.session.MediaSession derives its metadata bitmap ceiling: the
     * framework dimension config_mediaMetadataBitmapMaxSize, resolved from the app context so it
     * matches the value the platform compares against. Falls back to AOSP's 320dp default when the
     * (hidden, framework-internal) resource can't be resolved by name.
     */
    private fun mediaMetadataBitmapMaxSize(): Int {
        val resources = appContext.resources
        val id = resources.getIdentifier("config_mediaMetadataBitmapMaxSize", "dimen", "android")
        val resolved = if (id != 0) resources.getDimensionPixelSize(id) else 0
        return if (resolved > 0) resolved else (DEFAULT_METADATA_BITMAP_DP * resources.displayMetrics.density).toInt()
    }

    private val registry =
        SessionRegistry<SessionListener>(maxSessions.coerceIn(1, MAX_CACHED_SESSIONS)) { entry ->
            retireSession(entry)
        }

    /**
     * The one place a session's player goes back to the pool.
     *
     * The CAS is not redundant even though [SessionRegistry] signals each drop exactly once:
     * session.release() below can reach onDisconnected -> releaseSession -> registry.drop() on
     * the same entry, and that re-entrant path is what the guard stops. Do not remove it.
     *
     * Both orderings below are load-bearing, not tidiness:
     * - removeListeners() before release(), because releasing a session can fire
     *   onIsPlayingChanged, which would re-enter setPlaying while we are still inside
     *   the registry's entryRemoved callback.
     * - releasePlayerAsync() before release(), so a throw from session teardown cannot
     *   strand the player.
     */
    private fun retireSession(entry: SessionListener) {
        if (!entry.retired.compareAndSet(false, true)) return
        entry.removeListeners()
        exoPlayerPool.releasePlayerAsync(entry.session.player as ExoPlayer)
        entry.session.release()
    }

    internal fun setPlaying(
        id: String,
        isPlaying: Boolean,
    ) {
        registry.setPlaying(id, isPlaying)
    }

    @OptIn(UnstableApi::class)
    fun newSession(
        id: String,
        keepPlaying: Boolean,
        context: Context,
        // Best-effort affinity hint: when the pool still has a paused player carrying this
        // exact mediaId (matches MediaItem.mediaId, which is the videoUri), the warm player
        // is reused so the populated buffer survives. Null falls back to a cold acquire.
        preferredMediaId: String?,
    ): MediaSession {
        val player = exoPlayerPool.acquirePlayer(context, preferredMediaId)

        // newSession owns the player until registry.register() hands ownership over. Anything that
        // throws in between — MediaSession.Builder.build(), reset(), or the PendingIntent in
        // bindSessionActivity() — would otherwise strand a counted player with no owner.
        //
        // The throw sites named above are all *after* the session exists, so returning the player
        // alone is not enough: a live MediaSession bound to it, and a listener attached to it,
        // must come off first, or the pool re-issues a player that something else still holds.
        var session: MediaSession? = null
        var connector: MediaSessionExoPlayerConnector? = null

        try {
            val mediaSession =
                MediaSession
                    .Builder(context, player)
                    .apply {
                        setBitmapLoader(sharedBitmapLoader)
                        setId(id)
                        setCallback(globalCallback)
                    }.build()
            session = mediaSession

            val listener = MediaSessionExoPlayerConnector(mediaSession, this)
            connector = listener

            mediaSession.player.addListener(listener)

            reset(mediaSession, keepPlaying)

            // Warm-pool fast path acquires a player that still holds its MediaItem, so the
            // client side skips setMediaItem (see GetVideoController) and onAddMediaItems
            // never fires for this fresh session — leaving the notification's tap target
            // unset. Re-bind it from the player's current item so tapping the playback
            // notification opens the originating nostr URI.
            bindSessionActivity(mediaSession, mediaSession.player.currentMediaItem)

            registry.register(mediaSession.id, SessionListener(mediaSession, listener))

            return mediaSession
        } catch (e: Throwable) {
            // Unwind in reverse. releasePlayerAsync only *queues* the return, so the session and
            // listener are detached synchronously before the queued release ever runs — same
            // rationale as the ordering inside retireSession.
            exoPlayerPool.releasePlayerAsync(player)
            connector?.let { player.removeListener(it) }
            session?.release()
            throw e
        }
    }

    fun bindSessionActivity(
        session: MediaSession,
        mediaItem: MediaItem?,
    ) {
        val callbackUri = mediaItem?.mediaMetadata?.extras?.getString(MediaItemCache.EXTRA_CALLBACK_URI) ?: return
        session.setSessionActivity(
            PendingIntent.getActivity(
                appContext,
                0,
                Intent(Intent.ACTION_VIEW, callbackUri.toUri(), appContext, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    fun releaseSession(session: MediaSession) {
        // The registry removes the entry and then signals; retireSession does the teardown.
        // Nothing here depends on a cache removal firing a callback — that dependency is what
        // stranded players whose entry had already been evicted while playing.
        //
        // Unlike the old code this does NOT release the session when the id is unknown. Not in the
        // registry means already retired (retireSession released it) or never owned, so releasing
        // again would be exactly the double-release being removed here. newSession's failure path
        // produces such a session.
        registry.drop(session.id)
        cleanupUnused()
    }

    fun cleanupUnused() {
        val now = System.nanoTime()
        val previous = lastCleanupNs.get()
        if (now - previous < CLEANUP_INTERVAL_NS) return
        // CAS so only one caller actually launches the sweep when many releases fire at once.
        if (!lastCleanupNs.compareAndSet(previous, now)) return
        scope.launch {
            registry.idleSnapshot().forEach {
                if (it.session.connectedControllers.isEmpty()) {
                    releaseSession(it.session)
                }
            }
        }
    }

    fun destroy() {
        // Synchronous: retireSession uses the non-suspending releasePlayerAsync, so no coroutine
        // is needed here. Ordering still holds — releasePlayerAsync and ExoPlayerPool.destroy()
        // both launch on ExoPlayerPool's single main-thread scope and serialize on its one Mutex,
        // so queued returns run first. The old version launched its teardown and then cancelled
        // the scope on the same frame, so the teardown never ran at all.
        registry.dropAll()
        exoPlayerPool.destroy()
        scope.cancel()
    }

    fun getSession(
        id: String,
        keepPlaying: Boolean,
        context: Context,
        preferredMediaId: String? = null,
    ): MediaSession {
        registry.get(id)?.let { return it.session }

        return newSession(id, keepPlaying, context, preferredMediaId)
    }

    fun playingContent() = registry.playingEntries()

    // Widened from cache-only to cache-or-playing. Inert at its only caller
    // (PlaybackService.onUpdateNotification), which is inside a `playing.isEmpty()` branch
    // covering both pools, so no session is playing when it runs.
    fun getSession(id: String) = registry.get(id)?.session

    class MediaSessionCallback(
        val pool: MediaSessionPool,
        val appContext: Context,
    ) : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            // set up return call when clicking on the Notification bar
            pool.bindSessionActivity(mediaSession, mediaItems.firstOrNull())

            return Futures.immediateFuture(mediaItems)
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ) {
            pool.releaseSession(session)
        }
    }

    class MediaSessionExoPlayerConnector(
        val mediaSession: MediaSession,
        val pool: MediaSessionPool,
    ) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Moves the existing entry. Allocating a fresh SessionListener here (the old
            // behaviour) meant one session could have two or three live wrappers at once, so a
            // drop of one could hand its player back while another still pointed at that live
            // session.
            pool.setPlaying(mediaSession.id, isPlaying)
        }
    }

    companion object {
        private val CLEANUP_INTERVAL_NS = TimeUnit.MINUTES.toNanos(1)

        // Roughly how many videos can share a screen at once. Acts as the upper bound only —
        // a device that advertises fewer concurrent decoders than this caps lower.
        const val MAX_CACHED_SESSIONS = 10

        // AOSP default for config_mediaMetadataBitmapMaxSize, used when the framework resource
        // can't be resolved by name on a given ROM.
        private const val DEFAULT_METADATA_BITMAP_DP = 320
    }
}
