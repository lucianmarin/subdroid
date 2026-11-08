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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * Matches `templates/pages/reply.html`, `templates/input/reply.html`, and `Macreply/Views/ReplyDetailView.swift` 1:1.
 * Displays full ancestor chain, main entry, reply composer / placeholder banner, and indented descendant threads.
 */
@Composable
fun ReplyDetailScreen(
    entryId: Int,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val initialCached = remember(entryId, uiState.authToken) {
        viewModel.api.getCachedReplyDetail(id = entryId)
    }

    var mainEntry by remember(entryId, uiState.authToken) {
        mutableStateOf(initialCached?.entry)
    }
    var ancestors by remember(entryId, uiState.authToken) {
        mutableStateOf(initialCached?.ancestors ?: emptyList())
    }
    var kids by remember(entryId, uiState.authToken) {
        mutableStateOf(initialCached?.kids ?: emptyList())
    }
    var duplicateFromServer by remember(entryId, uiState.authToken) {
        mutableStateOf<Boolean?>(initialCached?.duplicate)
    }
    var isLoading by remember(entryId, uiState.authToken) { mutableStateOf(false) }
    var errorMessage by remember(entryId, uiState.authToken) { mutableStateOf<String?>(null) }

    suspend fun loadDetail() {
        if (mainEntry == null) {
            viewModel.api.getCachedReplyDetail(id = entryId)?.let { cached ->
                mainEntry = cached.entry
                ancestors = cached.ancestors
                kids = cached.kids
                duplicateFromServer = cached.duplicate
            }
        }
        isLoading = mainEntry == null
        errorMessage = null
        try {
            val result = viewModel.api.fetchReplyDetail(id = entryId)
            mainEntry = result.entry
            ancestors = result.ancestors
            kids = result.kids
            duplicateFromServer = result.duplicate
            isLoading = false
        } catch (err: Exception) {
            errorMessage = err.message ?: "Unable to load thread"
            isLoading = false
        }
    }

    suspend fun postReply(content: String): Result<Unit> {
        return try {
            val newReply = viewModel.api.postReply(id = entryId, content = content)
            kids = listOf(newReply) + kids
            duplicateFromServer = true
            mainEntry = mainEntry?.let { cur ->
                cur.copy(replies = maxOf((cur.replies ?: cur.replyCount) + 1, kids.size))
            }
            Result.success(Unit)
        } catch (err: Exception) {
            Result.failure(err)
        }
    }

    LaunchedEffect(entryId, uiState.authToken) {
        loadDetail()
    }


    SubPullToRefreshColumn(
        onRefresh = {
            loadDetail()
            viewModel.refreshNotifications()
        },
        modifier = modifier
    ) {
        val focal = mainEntry
        if (focal != null) {
            val currentUsername = uiState.currentUser?.username
            val isOwnEntry = !currentUsername.isNullOrEmpty() &&
                currentUsername.equals(focal.createdBy.username, ignoreCase = true)
            val hasDuplicateReply = duplicateFromServer ?: (!currentUsername.isNullOrEmpty() &&
                kids.any { it.createdBy.username.equals(currentUsername, ignoreCase = true) })

            // Ancestor chain from root down to immediate parent
            ancestors.forEachIndexed { idx, ancestor ->
                EntryItemView(
                    entry = ancestor,
                    viewModel = viewModel,
                    isRootOverride = (idx == 0 && ancestor.isRootThread)
                )
                SubDivider()
            }

            // Focal entry
            val canDeleteFocalAsRecipient = ancestors.lastOrNull()?.let { parent ->
                uiState.currentUser != null && uiState.currentUser == parent.createdBy
            } ?: false
            EntryItemView(
                entry = focal,
                viewModel = viewModel,
                isRootOverride = ancestors.isEmpty(),
                canDeleteAsRecipient = canDeleteFocalAsRecipient
            )
            SubDivider()

            // Reply input or placeholder (`templates/input/reply.html`)
            when {
                !uiState.isAuthenticated -> {
                    PlaceholderBannerView(
                        text = "your account to reply",
                        showReplyIcon = true,
                        isAuthPrompt = true,
                        onLoginClick = { viewModel.selectSection(SidebarSection.LOGIN) },
                        onRegisterClick = { viewModel.selectSection(SidebarSection.REGISTER) }
                    )
                }
                isOwnEntry -> {
                    PlaceholderBannerView(
                        text = if (focal.replyCount == 0 && kids.isEmpty()) "No reply yet" else "Reply back to them",
                        showReplyIcon = true
                    )
                }
                hasDuplicateReply -> {
                    PlaceholderBannerView(
                        text = "Share reply #${focal.id} anywhere",
                        showReplyIcon = true
                    )
                }
                else -> {
                    ComposerView(
                        placeholder = "Reply to @${focal.createdBy.username}",
                        onSubmit = { text -> postReply(text) }
                    )
                }
            }
            SubDivider()

            // Descendant threads (`templates/common/threads.html` & `Macreply/Views/ReplyDetailView.swift`)
            val canDeleteDirectKid = uiState.currentUser != null && uiState.currentUser == focal.createdBy
            for (kid in kids) {
                EntryItemView(
                    entry = kid,
                    viewModel = viewModel,
                    isRootOverride = false,
                    canDeleteAsRecipient = canDeleteDirectKid
                )
                SubDivider()

                val canDeleteNestedKid = uiState.currentUser != null && uiState.currentUser == kid.createdBy
                for (nestedKid in kid.streamKids) {
                    EntryItemView(
                        entry = nestedKid,
                        viewModel = viewModel,
                        isRootOverride = false,
                        canDeleteAsRecipient = canDeleteNestedKid,
                        modifier = Modifier.padding(start = 30.dp)
                    )
                    SubDivider()
                }
            }
        } else if (isLoading) {
            PlaceholderBannerView(
                text = "Loading reply #$entryId...",
                showReplyIcon = true
            )
            SubDivider()
        } else if (!errorMessage.isNullOrEmpty()) {
            Text(
                text = errorMessage!!,
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.alert,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

/**
 * Matches `templates/edit.html` and `Macreply/Views/ReplyDetailView.swift` (`EditEntryView`) 1:1.
 */
@Composable
fun EditEntryScreen(
    entry: SubEntry,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var previewEntry by remember(entry) { mutableStateOf(entry) }

    suspend fun saveEdit(content: String): Result<Unit> {
        return try {
            val updated = viewModel.api.editEntry(id = entry.id, content = content)
            previewEntry = updated
            viewModel.popNavigation()
            viewModel.openReply(updated.id)
            Result.success(Unit)
        } catch (err: Exception) {
            Result.failure(err)
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SubTheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        if (uiState.isAuthenticated) {
            ComposerView(
                placeholder = "Edit reply #${entry.id}",
                initialText = entry.content,
                onSubmit = { text -> saveEdit(text) }
            )
        }
        SubDivider()

        EntryItemView(
            entry = previewEntry,
            viewModel = viewModel
        )
        SubDivider()
    }
}

