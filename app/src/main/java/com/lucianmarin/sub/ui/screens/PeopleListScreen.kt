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
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.model.SubUser
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubDivider
import com.lucianmarin.sub.ui.theme.SubTheme
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

    var users by remember(section) { mutableStateOf<List<SubUser>>(emptyList()) }
    var currentPage by remember(section) { mutableIntStateOf(1) }
    var isLoading by remember(section) { mutableStateOf(false) }
    var isLoadingMore by remember(section) { mutableStateOf(false) }
    var errorMessage by remember(section) { mutableStateOf<String?>(null) }
    var searchQuery by remember(section) { mutableStateOf("") }
    var submittedQuery by remember(section) { mutableStateOf("") }

    fun endpointPath(): String = when (section) {
        SidebarSection.FOLLOWING -> "following"
        SidebarSection.FOLLOWERS -> "followers"
        SidebarSection.PEOPLE -> "people"
        else -> "people"
    }

    suspend fun reloadUsers() {
        isLoading = true
        errorMessage = null
        currentPage = 1
        val queryParam = if (section == SidebarSection.PEOPLE && submittedQuery.isNotBlank()) {
            submittedQuery
        } else {
            null
        }
        try {
            val res = viewModel.api.fetchUsers(
                path = endpointPath(),
                page = 1,
                query = queryParam
            )
            val me = uiState.currentUser
            users = if (section == SidebarSection.PEOPLE && queryParam == null && me != null) {
                val selfEntry = res.entries.firstOrNull { it.username.equals(me.username, ignoreCase = true) } ?: me
                listOf(selfEntry) + res.entries.filterNot { it.username.equals(me.username, ignoreCase = true) }
            } else {
                res.entries
            }
            isLoading = false
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
                isLoadingMore = false
            } catch (err: Exception) {
                errorMessage = err.message
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


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SubTheme.background)
            .verticalScroll(rememberScrollState())
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

            if (users.size >= currentPage * pageSize) {
                LoadMoreButton(
                    isLoading = isLoadingMore,
                    onClick = { loadMore() }
                )
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

