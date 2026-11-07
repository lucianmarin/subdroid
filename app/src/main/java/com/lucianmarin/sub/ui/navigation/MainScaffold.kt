package com.lucianmarin.sub.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lucianmarin.sub.model.NavigationDestination
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.state.AppUiState
import com.lucianmarin.sub.ui.screens.AboutScreen
import com.lucianmarin.sub.ui.screens.ConversationScreen
import com.lucianmarin.sub.ui.screens.EditEntryScreen
import com.lucianmarin.sub.ui.screens.FeedStreamScreen
import com.lucianmarin.sub.ui.screens.LoginScreen
import com.lucianmarin.sub.ui.screens.MemberScreen
import com.lucianmarin.sub.ui.screens.MessagesInboxScreen
import com.lucianmarin.sub.ui.screens.OptionsScreen
import com.lucianmarin.sub.ui.screens.PeopleListScreen
import com.lucianmarin.sub.ui.screens.RegisterScreen
import com.lucianmarin.sub.ui.screens.ReplyDetailScreen
import com.lucianmarin.sub.ui.theme.SubTheme

/**
 * Main application scaffold with a 640dp max-width main content area and a floating bottom navigation bar.
 * Content extends full-height behind the floating bar so the area around the nav bar is completely transparent.
 */
@Composable
fun MainScaffold(
    viewModel: AppStateViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    BackHandler(enabled = uiState.navigationPath.isNotEmpty()) {
        viewModel.popNavigation()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SubTheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
        ) {
            MainContentRouter(
                uiState = uiState,
                viewModel = viewModel
            )
        }

        SidebarView(
            uiState = uiState,
            onSelectSection = { viewModel.selectSection(it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (isKeyboardVisible) Modifier else Modifier.navigationBarsPadding())
                .padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun MainContentRouter(
    uiState: AppUiState,
    viewModel: AppStateViewModel
) {
    val activeDestination = uiState.navigationPath.lastOrNull()
    if (activeDestination != null) {
        when (activeDestination) {
            is NavigationDestination.ReplyDetail -> {
                ReplyDetailScreen(
                    entryId = activeDestination.id,
                    viewModel = viewModel
                )
            }
            is NavigationDestination.Member -> {
                MemberScreen(
                    username = activeDestination.username,
                    viewModel = viewModel
                )
            }
            is NavigationDestination.Conversation -> {
                ConversationScreen(
                    username = activeDestination.username,
                    viewModel = viewModel
                )
            }
            is NavigationDestination.EditEntry -> {
                EditEntryScreen(
                    entry = activeDestination.entry,
                    viewModel = viewModel
                )
            }
            is NavigationDestination.Options -> {
                OptionsScreen(
                    viewModel = viewModel
                )
            }
        }
    } else {
        when (val section = uiState.selectedSection) {
            SidebarSection.FEED,
            SidebarSection.MENTIONS,
            SidebarSection.REPLIES,
            SidebarSection.SAVED,
            SidebarSection.TRENDING,
            SidebarSection.DISCOVER -> {
                FeedStreamScreen(
                    section = section,
                    viewModel = viewModel
                )
            }
            SidebarSection.FOLLOWING,
            SidebarSection.FOLLOWERS,
            SidebarSection.PEOPLE -> {
                PeopleListScreen(
                    section = section,
                    viewModel = viewModel
                )
            }
            SidebarSection.MESSAGES -> {
                MessagesInboxScreen(
                    viewModel = viewModel
                )
            }
            SidebarSection.PROFILE -> {
                val username = uiState.currentUser?.username
                if (!username.isNullOrBlank()) {
                    MemberScreen(
                        username = username,
                        viewModel = viewModel
                    )
                } else {
                    LoginScreen(viewModel = viewModel)
                }
            }
            SidebarSection.OPTIONS -> {
                OptionsScreen(viewModel = viewModel)
            }
            SidebarSection.ABOUT -> {
                AboutScreen(viewModel = viewModel)
            }
            SidebarSection.LOGIN,
            SidebarSection.REGISTER -> {
                LoginScreen(viewModel = viewModel)
            }
        }
    }
}

