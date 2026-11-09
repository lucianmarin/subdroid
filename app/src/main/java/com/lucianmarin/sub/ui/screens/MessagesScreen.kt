package com.lucianmarin.sub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.model.SubChat
import com.lucianmarin.sub.model.SubUser
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubDivider
import com.lucianmarin.sub.ui.theme.SubPullToRefreshColumn
import com.lucianmarin.sub.ui.theme.SubTheme
import kotlinx.coroutines.launch

/**
 * Matches `templates/messages.html`, `templates/items/chat.html`,
 * and `Macreply/Views/MessagesView.swift` (`MessagesInboxView`) 1:1.
 */
@Composable
fun MessagesInboxScreen(
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val pageSize = 16

    val initialCached = remember(uiState.authToken) {
        viewModel.api.getCachedMessagesInbox(page = 1)
    }

    var chats by remember(uiState.authToken) {
        mutableStateOf(initialCached?.entries ?: emptyList())
    }
    var currentPage by remember(uiState.authToken) { mutableIntStateOf(1) }
    var canLoadMore by remember(uiState.authToken) {
        mutableStateOf((initialCached?.entries?.size ?: 0) >= pageSize)
    }
    var isLoading by remember(uiState.authToken) { mutableStateOf(false) }
    var isLoadingMore by remember(uiState.authToken) { mutableStateOf(false) }
    var loadMoreError by remember(uiState.authToken) { mutableStateOf(false) }
    var errorMessage by remember(uiState.authToken) { mutableStateOf<String?>(null) }

    suspend fun reloadChats() {
        if (!uiState.isAuthenticated) return

        if (chats.isEmpty()) {
            viewModel.api.getCachedMessagesInbox(page = 1)?.let { cached ->
                chats = cached.entries
                canLoadMore = cached.entries.size >= pageSize
            }
        }

        isLoading = chats.isEmpty()
        errorMessage = null
        loadMoreError = false
        currentPage = 1
        try {
            val res = viewModel.api.fetchMessagesInbox(page = 1)
            chats = res.entries
            canLoadMore = res.entries.size >= pageSize
            isLoading = false
            viewModel.refreshNotifications()
        } catch (err: Exception) {
            errorMessage = err.message ?: "Unable to load messages"
            isLoading = false
        }
    }

    fun loadMore() {
        if (!uiState.isAuthenticated || isLoadingMore) return
        isLoadingMore = true
        val nextPage = currentPage + 1
        scope.launch {
            try {
                val res = viewModel.api.fetchMessagesInbox(page = nextPage)
                chats = chats + res.entries
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

    LaunchedEffect(uiState.reloadTrigger, uiState.authToken) {
        reloadChats()
    }


    SubPullToRefreshColumn(
        onRefresh = {
            reloadChats()
        },
        modifier = modifier
    ) {
        SectionSubtabsHeader(
            section = SidebarSection.MESSAGES,
            uiState = uiState,
            viewModel = viewModel,
            onReselectCurrent = { scope.launch { reloadChats() } }
        )
        SubDivider()

        if (isLoading && chats.isEmpty()) {
            Text(
                text = "Loading...",
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.gray,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else if (errorMessage != null && chats.isEmpty()) {
            Text(
                text = errorMessage ?: "Unable to load messages",
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.alert,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else {
            for (chat in chats) {
                ChatInboxRowView(
                    chat = chat,
                    currentUser = uiState.currentUser,
                    viewModel = viewModel
                )
                SubDivider()
            }

            if (canLoadMore) {
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

/**
 * Matches `templates/common/messages.html` and `Macreply/Views/MessagesView.swift` (`MessageInboxRow`) 1:1.
 */
@Composable
private fun ChatInboxRowView(
    chat: SubChat,
    currentUser: SubUser?,
    viewModel: AppStateViewModel
) {
    val isIncoming = currentUser != null && currentUser.username.equals(chat.toUser.username, ignoreCase = true)
    val otherUser = if (isIncoming) chat.createdBy else chat.toUser

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RichContentView(
            author = chat.createdBy,
            content = chat.content,
            viewModel = viewModel,
            fontSize = 14.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "message",
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = SubTheme.gray,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.openConversation(otherUser.username)
                }
            )

            Text(
                text = if (chat.seen) "read" else "unread",
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = if (chat.seen) SubTheme.silver else SubTheme.accent
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = chat.timestamp,
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = SubTheme.silver,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.openConversation(otherUser.username)
                }
            )
        }
    }
}


private enum class ClearConversationState {
    IDLE,
    CONFIRMING,
    RETRY,
    CLEARED
}

/**
 * Matches `templates/message.html`, `templates/items/message.html`, `templates/items/clear.html`,
 * and `Macreply/Views/MessagesView.swift` (`ConversationView`) 1:1.
 */
@Composable
fun ConversationScreen(
    username: String,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val pageSize = 16

    val initialCached = remember(username, uiState.authToken) {
        viewModel.api.getCachedConversation(username = username, page = 1)
    }

    var messages by remember(username, uiState.authToken) {
        mutableStateOf(initialCached?.entries ?: emptyList())
    }
    var isBlockedFromServer by remember(username, uiState.authToken) {
        mutableStateOf(initialCached?.blocked)
    }
    var currentPage by remember(username, uiState.authToken) { mutableIntStateOf(1) }
    var canLoadMore by remember(username, uiState.authToken) {
        mutableStateOf((initialCached?.entries?.size ?: 0) >= pageSize)
    }
    var isLoading by remember(username, uiState.authToken) { mutableStateOf(false) }
    var isLoadingMore by remember(username, uiState.authToken) { mutableStateOf(false) }
    var loadMoreError by remember(username, uiState.authToken) { mutableStateOf(false) }
    var errorMessage by remember(username, uiState.authToken) { mutableStateOf<String?>(null) }
    var clearState by remember(username, uiState.authToken) {
        mutableStateOf(ClearConversationState.IDLE)
    }

    val isSelfConversation = uiState.currentUser?.username?.equals(username, ignoreCase = true) == true
    val isBlockedWaitingForResponse = remember(isBlockedFromServer, messages, uiState.currentUser, username) {
        if (isBlockedFromServer != null) {
            isBlockedFromServer!!
        } else {
            val current = uiState.currentUser?.username?.lowercase()
            if (current == null || messages.isEmpty() || current == username.lowercase()) {
                false
            } else {
                val hasForward = messages.any { it.createdBy.username.lowercase() == current }
                val hasBackward = messages.any { it.createdBy.username.lowercase() == username.lowercase() }
                hasForward && !hasBackward
            }
        }
    }

    suspend fun loadConversation() {
        if (!uiState.isAuthenticated) return

        if (messages.isEmpty()) {
            viewModel.api.getCachedConversation(username = username, page = 1)?.let { cached ->
                messages = cached.entries
                canLoadMore = cached.entries.size >= pageSize
                if (cached.blocked != null) {
                    isBlockedFromServer = cached.blocked
                }
                viewModel.setActiveConversationHasMessages(cached.entries.isNotEmpty())
            }
        }

        isLoading = messages.isEmpty()
        errorMessage = null
        loadMoreError = false
        currentPage = 1
        clearState = ClearConversationState.IDLE
        try {
            val result = viewModel.api.fetchConversation(username = username, page = 1)
            messages = result.entries
            canLoadMore = result.entries.size >= pageSize
            if (result.blocked != null) {
                isBlockedFromServer = result.blocked
            }
            viewModel.setActiveConversationHasMessages(result.entries.isNotEmpty())
            isLoading = false
            viewModel.refreshNotifications()
        } catch (err: Exception) {
            errorMessage = err.message ?: "Unable to load conversation"
            isLoading = false
        }
    }

    fun loadMore() {
        if (!uiState.isAuthenticated || isLoadingMore) return
        isLoadingMore = true
        val nextPage = currentPage + 1
        scope.launch {
            try {
                val result = viewModel.api.fetchConversation(username = username, page = nextPage)
                messages = messages + result.entries
                currentPage = nextPage
                canLoadMore = result.entries.size >= pageSize
                loadMoreError = false
                isLoadingMore = false
            } catch (_: Exception) {
                loadMoreError = true
                isLoadingMore = false
            }
        }
    }

    suspend fun sendNewMessage(content: String): Result<Unit> {
        return try {
            val sent = viewModel.api.sendMessage(username = username, content = content)
            messages = listOf(sent) + messages
            viewModel.setActiveConversationHasMessages(true)
            val current = uiState.currentUser?.username?.lowercase()
            if (current != null && current != username.lowercase()) {
                val hasBackward = messages.any { it.createdBy.username.lowercase() == username.lowercase() }
                isBlockedFromServer = !hasBackward
            }
            clearState = ClearConversationState.IDLE
            Result.success(Unit)
        } catch (err: Exception) {
            Result.failure(err)
        }
    }

    fun clearAllMessages() {
        if (!uiState.isAuthenticated) return
        scope.launch {
            try {
                val res = viewModel.api.clearConversation(username = username)
                if (res.status == "cleared") {
                    clearState = ClearConversationState.CLEARED
                    messages = emptyList()
                    viewModel.setActiveConversationHasMessages(false)
                } else {
                    clearState = ClearConversationState.RETRY
                }
            } catch (_: Exception) {
                clearState = ClearConversationState.RETRY
            }
        }
    }

    LaunchedEffect(username, uiState.authToken) {
        if (messages.isNotEmpty()) {
            viewModel.setActiveConversationHasMessages(true)
        }
        loadConversation()
    }

    DisposableEffect(username) {
        onDispose {
            viewModel.setActiveConversationHasMessages(null)
        }
    }

    SubPullToRefreshColumn(
        onRefresh = {
            loadConversation()
        },
        modifier = modifier
    ) {
        if (isBlockedWaitingForResponse) {
            PlaceholderBannerView(
                text = "Please wait for @$username to respond"
            )
        } else {
            ComposerView(
                placeholder = if (isSelfConversation) "Message yourself" else "Message @$username",
                onSubmit = { text -> sendNewMessage(text) }
            )
        }
        SubDivider()


        if (isLoading && messages.isEmpty()) {
            Text(
                text = "Loading...",
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.gray,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else if (errorMessage != null && messages.isEmpty()) {
            Text(
                text = errorMessage ?: "Unable to load conversation",
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.alert,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else {
            for (msg in messages) {
                ConversationMessageRowView(
                    message = msg,
                    currentUser = uiState.currentUser,
                    viewModel = viewModel
                )
                SubDivider()
            }

            if (canLoadMore) {
                LoadMoreButton(
                    isLoading = isLoadingMore,
                    isError = loadMoreError,
                    onClick = { loadMore() }
                )
            } else if (!isBlockedWaitingForResponse && (messages.isNotEmpty() || clearState == ClearConversationState.CLEARED)) {
                ClearOutConversationRow(
                    state = clearState,
                    onStartConfirm = { clearState = ClearConversationState.CONFIRMING },
                    onUndo = { clearState = ClearConversationState.IDLE },
                    onConfirm = { clearAllMessages() }
                )
            }
        }

        Spacer(modifier = Modifier.height(SubTheme.bottomSpacerHeight))
    }
}

private enum class UnsendButtonState {
    IDLE,
    CONFIRMING,
    RETRY,
    UNSENT
}

/**
 * Matches `templates/items/message.html` 1:1.
 * Incoming messages (`createdBy != currentUser`) are indented 30dp (`padding(start = 50.dp)`) with no `.small` metadata row.
 * Outgoing messages are at 0dp with `.small` row (`read`/`unread`, `unsend` -> `undo`/`yes` -> `unsent`, timestamp).
 */
@Composable
private fun ConversationMessageRowView(
    message: SubChat,
    currentUser: SubUser?,
    viewModel: AppStateViewModel
) {
    val scope = rememberCoroutineScope()
    val isOutgoing = currentUser != null && currentUser == message.createdBy
    var unsendState by remember(message.id) { mutableStateOf(UnsendButtonState.IDLE) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isOutgoing) 20.dp else 50.dp,
                end = 20.dp,
                top = 10.dp,
                bottom = 10.dp
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RichContentView(
            author = message.createdBy,
            content = message.content,
            viewModel = viewModel,
            fontSize = 14.sp
        )

        if (isOutgoing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (message.seen) "read" else "unread",
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = if (message.seen) SubTheme.gray else SubTheme.accent
                )

                when (unsendState) {
                    UnsendButtonState.IDLE -> {
                        Text(
                            text = "unsend",
                            fontFamily = Route159FontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = SubTheme.gray,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                unsendState = UnsendButtonState.CONFIRMING
                            }
                        )
                    }
                    UnsendButtonState.CONFIRMING, UnsendButtonState.RETRY -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "undo",
                                fontFamily = Route159FontFamily,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = SubTheme.accent,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    unsendState = UnsendButtonState.IDLE
                                }
                            )
                            Text(
                                text = if (unsendState == UnsendButtonState.RETRY) "retry" else "yes",
                                fontFamily = Route159FontFamily,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = SubTheme.alert,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    scope.launch {
                                        try {
                                            val res = viewModel.api.unsendMessage(id = message.id)
                                            if (res.status == "unsent") {
                                                unsendState = UnsendButtonState.UNSENT
                                            } else {
                                                unsendState = UnsendButtonState.RETRY
                                            }
                                        } catch (_: Exception) {
                                            unsendState = UnsendButtonState.RETRY
                                        }
                                    }
                                }

                            )
                        }
                    }
                    UnsendButtonState.UNSENT -> {
                        Text(
                            text = "unsent",
                            fontFamily = Route159FontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = SubTheme.silver
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = message.timestamp,
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.silver
                )
            }
        }
    }
}

/**
 * Matches `templates/pages/loader.html` (`.load` with `Clear out` -> `Undo — Yes` -> `Cleared`)
 * and `Macreply/Views/MessagesView.swift`.
 */
@Composable
private fun ClearOutConversationRow(
    state: ClearConversationState,
    onStartConfirm: () -> Unit,
    onUndo: () -> Unit,
    onConfirm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            ClearConversationState.IDLE -> {
                Text(
                    text = "Clear out",
                    fontFamily = Route159FontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.gray,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStartConfirm
                    )
                )
            }
            ClearConversationState.CONFIRMING, ClearConversationState.RETRY -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Undo",
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.accent,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onUndo
                        )
                    )
                    Text(
                        text = " — ",
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.silver
                    )
                    Text(
                        text = if (state == ClearConversationState.RETRY) "Retry" else "Yes",
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.alert,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onConfirm
                        )
                    )
                }
            }
            ClearConversationState.CLEARED -> {
                Text(
                    text = "Cleared",
                    fontFamily = Route159FontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.gray
                )
            }
        }
    }
}

