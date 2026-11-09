package com.lucianmarin.sub.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.model.SubEntry
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubDivider
import com.lucianmarin.sub.ui.theme.SubPullToRefreshColumn
import com.lucianmarin.sub.ui.theme.SubTheme
import kotlinx.coroutines.launch

/**
 * Matches `templates/feed.html`, `templates/mentions.html`, `templates/replies.html`,
 * `templates/saved.html`, `templates/trending.html`, `templates/discover.html`,
 * and `Macreply/Views/FeedStreamView.swift` 1:1.
 */
@Composable
fun FeedStreamScreen(
    section: SidebarSection,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val pageSize = 16

    fun endpointPath(): String = when (section) {
        SidebarSection.FEED -> "feed"
        SidebarSection.MENTIONS -> "mentions"
        SidebarSection.REPLIES -> "replies"
        SidebarSection.SAVED -> "saved"
        SidebarSection.TRENDING -> "trending"
        SidebarSection.DISCOVER -> "discover"
        else -> "feed"
    }

    val initialQuery = remember(section, uiState.searchRequestedQuery) {
        if (section == SidebarSection.DISCOVER && !uiState.searchRequestedQuery.isNullOrEmpty()) {
            uiState.searchRequestedQuery
        } else {
            null
        }
    }
    val initialCached = remember(section, uiState.authToken) {
        if (initialQuery == null) {
            viewModel.api.getCachedEntries(path = endpointPath(), page = 1, query = null)
        } else {
            null
        }
    }

    var entries by remember(section, uiState.authToken) {
        mutableStateOf(initialCached?.entries ?: emptyList())
    }
    var currentPage by remember(section, uiState.authToken) { mutableIntStateOf(1) }
    var canLoadMore by remember(section, uiState.authToken) {
        mutableStateOf((initialCached?.entries?.size ?: 0) >= pageSize)
    }
    var isLoading by remember(section, uiState.authToken) { mutableStateOf(false) }
    var isLoadingMore by remember(section, uiState.authToken) { mutableStateOf(false) }
    var loadMoreError by remember(section, uiState.authToken) { mutableStateOf(false) }
    var errorMessage by remember(section, uiState.authToken) { mutableStateOf<String?>(null) }
    var searchQuery by remember(section, uiState.authToken) {
        mutableStateOf(initialQuery ?: "")
    }
    var submittedQuery by remember(section, uiState.authToken) {
        mutableStateOf(initialQuery ?: "")
    }

    suspend fun reloadEntries() {
        val queryParam = if (section == SidebarSection.DISCOVER && submittedQuery.isNotBlank()) {
            submittedQuery
        } else {
            null
        }

        if (queryParam == null && entries.isEmpty()) {
            val cached = viewModel.api.getCachedEntries(path = endpointPath(), page = 1, query = null)
            if (cached != null) {
                entries = cached.entries
                canLoadMore = cached.entries.size >= pageSize
            }
        } else if (queryParam != null) {
            entries = emptyList()
            canLoadMore = false
        }

        isLoading = entries.isEmpty()
        errorMessage = null
        loadMoreError = false
        currentPage = 1
        try {
            val res = viewModel.api.fetchEntries(
                path = endpointPath(),
                page = 1,
                query = queryParam
            )
            entries = res.entries
            canLoadMore = res.entries.size >= pageSize
            isLoading = false
            if (section == SidebarSection.MENTIONS || section == SidebarSection.REPLIES) {
                viewModel.refreshNotifications()
            }
        } catch (err: Exception) {
            errorMessage = err.message ?: "Unable to load stream"
            isLoading = false
        }
    }

    fun loadMore() {
        if (isLoadingMore) return
        isLoadingMore = true
        val nextPage = currentPage + 1
        val queryParam = if (section == SidebarSection.DISCOVER && submittedQuery.isNotBlank()) {
            submittedQuery
        } else {
            null
        }
        scope.launch {
            try {
                val res = viewModel.api.fetchEntries(
                    path = endpointPath(),
                    page = nextPage,
                    query = queryParam
                )
                entries = entries + res.entries
                currentPage = nextPage
                canLoadMore = res.entries.size >= pageSize
                loadMoreError = false
                isLoadingMore = false
            } catch (_: Exception) {
                loadMoreError = true
                isLoadingMore = false
            }
        }
    }

    fun onClearSearch() {
        searchQuery = ""
        submittedQuery = ""
        val cached = viewModel.api.getCachedEntries(path = endpointPath(), page = 1, query = null)
        if (cached != null) {
            entries = cached.entries
            canLoadMore = cached.entries.size >= pageSize
        } else {
            entries = emptyList()
            canLoadMore = false
        }
        scope.launch { reloadEntries() }
    }

    suspend fun postNewThread(content: String): Result<Unit> {
        return try {
            val newEntry = viewModel.api.postThread(content = content)
            entries = listOf(newEntry) + entries
            Result.success(Unit)
        } catch (err: Exception) {
            Result.failure(err)
        }
    }

    LaunchedEffect(section, uiState.reloadTrigger, uiState.authToken) {
        if (section == SidebarSection.DISCOVER && !uiState.searchRequestedQuery.isNullOrEmpty()) {
            val q = uiState.searchRequestedQuery!!
            searchQuery = q
            submittedQuery = q
            viewModel.consumeSearchRequestedQuery()
        }
        reloadEntries()
    }

    LaunchedEffect(uiState.searchRequestedQuery) {
        val q = uiState.searchRequestedQuery
        if (section == SidebarSection.DISCOVER && !q.isNullOrEmpty()) {
            searchQuery = q
            submittedQuery = q
            viewModel.consumeSearchRequestedQuery()
            reloadEntries()
        }
    }


    SubPullToRefreshColumn(
        onRefresh = {
            reloadEntries()
            viewModel.refreshNotifications()
        },
        modifier = modifier
    ) {
        // Section Top Header
        when (section) {
            SidebarSection.FEED -> {
                if (uiState.isAuthenticated) {
                    ComposerView(
                        placeholder = "Start a thread",
                        onSubmit = { text -> postNewThread(text) }
                    )
                } else {
                    PlaceholderBannerView(
                        text = "your account to share",
                        isAuthPrompt = true,
                        onLoginClick = { viewModel.selectSection(SidebarSection.LOGIN) },
                        onRegisterClick = { viewModel.selectSection(SidebarSection.REGISTER) }
                    )
                }
            }
            SidebarSection.DISCOVER -> {
                SearchQueryBar(
                    placeholder = "Search topics",
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSubmit = {
                        submittedQuery = searchQuery
                        scope.launch { reloadEntries() }
                    },
                    onClear = { onClearSearch() }
                )
            }
            SidebarSection.MENTIONS,
            SidebarSection.REPLIES,
            SidebarSection.SAVED,
            SidebarSection.TRENDING -> {
                SectionSubtabsHeader(
                    section = section,
                    uiState = uiState,
                    viewModel = viewModel,
                    onReselectCurrent = { scope.launch { reloadEntries() } }
                )
            }
            else -> Unit
        }

        SubDivider()

        if (isLoading && entries.isEmpty()) {
            Text(
                text = "Loading...",
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.gray,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else if (errorMessage != null && entries.isEmpty()) {
            Text(
                text = errorMessage ?: "Unable to load stream",
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.alert,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else {
            entries.forEachIndexed { index, entry ->
                val previousParentID = if (index > 0) entries[index - 1].parent?.id else null
                StreamEntryView(
                    entry = entry,
                    previousParentID = previousParentID,
                    viewModel = viewModel
                )
            }

            if (canLoadMore && submittedQuery.isBlank()) {
                LoadMoreButton(
                    isLoading = isLoadingMore,
                    isError = loadMoreError,
                    onClick = { loadMore() }
                )
            }
        }

        Spacer(modifier = Modifier.height(SubTheme.bottomSpacerHeight))
    }
}

