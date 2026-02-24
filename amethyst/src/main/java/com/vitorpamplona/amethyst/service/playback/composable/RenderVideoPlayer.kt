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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderAnimatedBottomInfo
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderCenterButtons
import com.vitorpamplona.amethyst.service.playback.composable.controls.RenderTopButtons
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.LoadedMediaItem
import com.vitorpamplona.amethyst.service.playback.composable.wavefront.Waveform
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

private fun getVideoSizeDp(player: Player): Size? {
    var videoSize = Size(player.videoSize.width.toFloat(), player.videoSize.height.toFloat())

    if (videoSize.width == 0f || videoSize.height == 0f) return null

    val par = player.videoSize.pixelWidthHeightRatio
    if (par < 1.0) {
        videoSize = videoSize.copy(width = videoSize.width * par)
    } else if (par > 1.0) {
        videoSize = videoSize.copy(height = videoSize.height / par)
    }
    return videoSize
}

@Composable
@OptIn(UnstableApi::class)
fun RenderVideoPlayer(
    mediaItem: LoadedMediaItem,
    controllerState: MediaControllerState,
    thumbData: VideoThumb?,
    showControls: Boolean = true,
    contentScale: ContentScale,
    borderModifier: Modifier,
    videoModifier: Modifier,
    onDialog: (() -> Unit)? = null,
    controllerVisible: MutableState<Boolean> = remember { mutableStateOf(false) },
    accountViewModel: AccountViewModel,
) {
    Box(modifier = borderModifier) {
        ContentFrame(
            player = controllerState.controller,
            modifier =
                videoModifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // to prevent the ripple from the tap
                ) { controllerVisible.value = !controllerVisible.value },
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW, // texture view is better inside lazy layouts.
            contentScale = contentScale,
        )

        mediaItem.src.waveformData?.let { Waveform(it, controllerState, Modifier.align(Alignment.Center)) }

        if (showControls) {
            RenderTopButtons(
                mediaData = mediaItem.src,
                controllerState = controllerState,
                controllerVisible = controllerVisible,
                onZoomClick = onDialog,
                modifier = Modifier.align(Alignment.TopEnd),
                accountViewModel = accountViewModel,
            )

            RenderCenterButtons(controllerState, controllerVisible, Modifier.align(Alignment.Center))

            RenderAnimatedBottomInfo(controllerState, controllerVisible, Modifier.align(Alignment.BottomCenter))
        }
    }
}
