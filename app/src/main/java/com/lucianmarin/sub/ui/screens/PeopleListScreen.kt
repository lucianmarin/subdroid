package com.lucianmarin.sub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.lucianmarin.sub.model.PaginatedUsersResponse
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.model.SubUser
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubDivider
import com.lucianmarin.sub.ui.theme.SubPullToRefreshColumn
import com.lucianmarin.sub.ui.theme.SubTheme
import com.lucianmarin.sub.ui.theme.applyDiffedFeedUpdate
import kotlinx.coroutines.launch

/**
 * Matches `templates/following.html`, `templates/followers.html`, `templates/people.html`,
 * and `Macreply/Views/PeopleListView.swift` 1:1.
 * Pagination size = 24 (`api.py`).
 */
@Composable
fun PeopleListScreen(
    section: SidebarSection,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val pageSize = 24

    fun endpointPath(): String = when (section) {
        SidebarSection.FOLLOWING -> "following"
        SidebarSection.FOLLOWERS -> "followers"
        SidebarSection.PEOPLE -> "people"
        else -> "people"
    }

    fun cacheUrlKey(query: String?): String =
        viewModel.api.endpointKey(
            path = endpointPath(),
            queryParams = if (!query.isNullOrEmpty()) mapOf("q" to query) else emptyMap()
        )

    fun arrangeUsers(rawEntries: List<SubUser>, queryParam: String?): List<SubUser> {
        val me = uiState.currentUser
        return if (section == SidebarSection.PEOPLE && queryParam == null && me != null) {
            val selfEntry = rawEntries.firstOrNull { it.username.equals(me.username, ignoreCase = true) } ?: me
            listOf(selfEntry) + rawEntries.filterNot { it.username.equals(me.username, ignoreCase = true) }
        } else {
            rawEntries
        }
    }

    val initialCached = remember(section) {
        viewModel.storage.getCachedFeed(cacheUrlKey(null), PaginatedUsersResponse.serializer())
    }

    var users by remember(section) {
        mutableStateOf(if (initialCached != null) arrangeUsers(initialCached.entries, null) else emptyList())
    }
    var currentPage by remember(section) { mutableIntStateOf(1) }
    var canLoadMore by remember(section) { mutableStateOf((initialCached?.entries?.size ?: 0) >= pageSize) }
    var isLoading by remember(section) { mutableStateOf(initialCached == null) }
    var isLoadingMore by remember(section) { mutableStateOf(false) }
    var loadMoreError by remember(section) { mutableStateOf(false) }
    var errorMessage by remember(section) { mutableStateOf<String?>(null) }
    var searchQuery by remember(section) { mutableStateOf("") }
    var submittedQuery by remember(section) { mutableStateOf("") }

    suspend fun reloadUsers() {
        val queryParam = if (section == SidebarSection.PEOPLE && submittedQuery.isNotBlank()) {
            submittedQuery
        } else {
            null
        }
        val urlKey = cacheUrlKey(queryParam)
        val cached = viewModel.storage.getCachedFeed(urlKey, PaginatedUsersResponse.serializer())
        if (users.isEmpty() && cached != null) {
            users = arrangeUsers(cached.entries, queryParam)
            canLoadMore = cached.entries.size >= pageSize
        }
        val oldList = users

        isLoading = true
        errorMessage = null
        loadMoreError = false
        currentPage = 1
        try {
            val res = viewModel.api.fetchUsers(
                path = endpointPath(),
                page = 1,
                query = queryParam
            )
            val arranged = arrangeUsers(res.entries, queryParam)
            canLoadMore = res.entries.size >= pageSize
            viewModel.storage.setCachedFeed(urlKey, res, PaginatedUsersResponse.serializer())
            isLoading = false
            applyDiffedFeedUpdate(
                oldList = oldList,
                newList = arranged,
                keySelector = { it.username.lowercase() },
                onUpdate = { users = it }
            )
            if (section == SidebarSection.FOLLOWERS) {
                viewModel.refreshNotifications()
            }
        } catch (err: Exception) {
            errorMessage = err.message ?: "Failed to load people"
            isLoading = false
        }
    }

    fun loadMore() {
        if (isLoadingMore) return
        isLoadingMore = true
        val nextPage = currentPage + 1
        val queryParam = if (section == SidebarSection.PEOPLE && submittedQuery.isNotBlank()) {
            submittedQuery
        } else {
            null
        }
        scope.launch {
            try {
                val res = viewModel.api.fetchUsers(
                    path = endpointPath(),
                    page = nextPage,
                    query = queryParam
                )
                users = users + res.entries
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

    LaunchedEffect(section, uiState.reloadTrigger, uiState.authToken) {
        if (section == SidebarSection.PEOPLE && !uiState.searchRequestedQuery.isNullOrEmpty()) {
            val q = uiState.searchRequestedQuery!!
            searchQuery = q
            submittedQuery = q
            viewModel.consumeSearchRequestedQuery()
        }
        reloadUsers()
    }

    LaunchedEffect(uiState.searchRequestedQuery) {
        val q = uiState.searchRequestedQuery
        if (section == SidebarSection.PEOPLE && !q.isNullOrEmpty()) {
            searchQuery = q
            submittedQuery = q
            viewModel.consumeSearchRequestedQuery()
            reloadUsers()
        }
    }


    SubPullToRefreshColumn(
        onRefresh = {
            reloadUsers()
            viewModel.refreshNotifications()
        },
        modifier = modifier
    ) {
        when (section) {
            SidebarSection.PEOPLE -> {
                SearchQueryBar(
                    placeholder = "Find people",
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSubmit = {
                        submittedQuery = searchQuery
                        scope.launch { reloadUsers() }
                    },
                    onClear = {
                        searchQuery = ""
                        submittedQuery = ""
                        scope.launch { reloadUsers() }
                    }
                )
            }
            SidebarSection.FOLLOWING,
            SidebarSection.FOLLOWERS -> {
                SectionSubtabsHeader(
                    section = section,
                    uiState = uiState,
                    viewModel = viewModel,
                    onReselectCurrent = { scope.launch { reloadUsers() } }
                )
            }
            else -> Unit
        }

        SubDivider()

        if (isLoading && users.isEmpty()) {
            Text(
                text = "Loading...",
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.gray,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else if (errorMessage != null && users.isEmpty()) {
            Text(
                text = errorMessage ?: "Failed to load people",
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.alert,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else {
            for (user in users) {
                UserRowView(
                    user = user,
                    isMemberHeader = false,
                    viewModel = viewModel
                )
                SubDivider()
            }

            if (canLoadMore && submittedQuery.isBlank()) {
                LoadMoreButton(
                    isLoading = isLoadingMore,
                    isError = loadMoreError,
                    onClick = { loadMore() }
                )
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

