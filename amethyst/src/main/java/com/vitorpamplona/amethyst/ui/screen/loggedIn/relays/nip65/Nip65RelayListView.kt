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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip65

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.navs.rememberExtendedNav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayUrlEditField
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer

@Composable
fun Nip65RelayList(
    postViewModel: Nip65RelayListViewModel,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    nav: INav,
) {
    val newNav = rememberExtendedNav(nav, onClose)

    val homeFeedState by postViewModel.homeRelays.collectAsStateWithLifecycle()
    val notifFeedState by postViewModel.notificationRelays.collectAsStateWithLifecycle()

    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            contentPadding = FeedPadding,
        ) {
            renderNip65HomeItems(homeFeedState, postViewModel, accountViewModel, newNav)
            renderNip65NotifItems(notifFeedState, postViewModel, accountViewModel, newNav)
        }
    }
}

@Composable
fun Nip65InboxRelayList(
    postViewModel: Nip65RelayListViewModel,
    accountViewModel: AccountViewModel,
    onClose: () -> Unit,
    nav: INav,
) {
    val newNav = rememberExtendedNav(nav, onClose)

    val notifFeedState by postViewModel.notificationRelays.collectAsStateWithLifecycle()

    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            contentPadding = FeedPadding,
        ) {
            renderNip65NotifItems(notifFeedState, postViewModel, accountViewModel, newNav)
        }
    }
}

fun LazyListScope.renderNip65HomeItems(
    feedState: List<BasicRelaySetupInfo>,
    postViewModel: Nip65RelayListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    itemsIndexed(feedState, key = { _, item -> "Nip65Home" + item.relay.url }) { index, item ->
        BasicRelaySetupInfoDialog(
            item,
            onDelete = { postViewModel.deleteHomeRelay(item) },
            accountViewModel = accountViewModel,
            nav,
        )
    }

    item {
        Spacer(modifier = StdVertSpacer)
        RelayUrlEditField { postViewModel.addHomeRelay(relaySetupInfoBuilder(it)) }
    }
}

fun LazyListScope.renderNip65NotifItems(
    feedState: List<BasicRelaySetupInfo>,
    postViewModel: Nip65RelayListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    itemsIndexed(feedState, key = { _, item -> "Nip65Notif" + item.relay.url }) { index, item ->
        BasicRelaySetupInfoDialog(
            item,
            onDelete = { postViewModel.deleteNotifRelay(item) },
            accountViewModel = accountViewModel,
            nav,
        )
    }

    item {
        Spacer(modifier = StdVertSpacer)
        RelayUrlEditField { postViewModel.addNotifRelay(relaySetupInfoBuilder(it)) }
    }
}
