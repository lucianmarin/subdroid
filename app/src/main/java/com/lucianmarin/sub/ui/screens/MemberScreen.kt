package com.lucianmarin.sub.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.model.SubEntry
import com.lucianmarin.sub.model.SubUser
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubDivider
import com.lucianmarin.sub.ui.theme.SubPullToRefreshColumn
import com.lucianmarin.sub.ui.theme.SubTheme
import com.lucianmarin.sub.util.ContentParser
import kotlinx.coroutines.launch

/**
 * Matches `templates/member.html`, `templates/items/user.html`, `templates/items/links.html`,
 * `templates/items/status.html`, and `Macreply/Views/MemberView.swift` 1:1.
 */
@Composable
fun MemberScreen(
    username: String,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val pageSize = 16

    val initialCachedMember = remember(username, uiState.authToken) {
        viewModel.api.getCachedMemberDetail(username = username)
    }
    val initialCachedPosts = remember(username, uiState.authToken) {
        viewModel.api.getCachedMemberPosts(username = username, page = 1)
    }

    var member by remember(username, uiState.authToken) {
        mutableStateOf(initialCachedMember)
    }
    var entries by remember(username, uiState.authToken) {
        mutableStateOf(initialCachedPosts?.entries ?: emptyList())
    }
    var isFollowing by remember(username, uiState.authToken) {
        mutableStateOf(initialCachedMember?.followed ?: false)
    }
    var followsViewer by remember(username, uiState.authToken) {
        mutableStateOf(initialCachedMember?.follows ?: false)
    }
    var currentPage by remember(username, uiState.authToken) {
        mutableIntStateOf(initialCachedPosts?.page ?: 1)
    }
    var canLoadMore by remember(username, uiState.authToken) {
        mutableStateOf((initialCachedPosts?.entries?.size ?: 0) >= pageSize)
    }
    var isLoadingDetails by remember(username, uiState.authToken) { mutableStateOf(false) }
    var isLoadingPosts by remember(username, uiState.authToken) { mutableStateOf(false) }
    var isLoadingMore by remember(username, uiState.authToken) { mutableStateOf(false) }
    var loadMoreError by remember(username, uiState.authToken) { mutableStateOf(false) }
    var errorMessage by remember(username, uiState.authToken) { mutableStateOf<String?>(null) }

    suspend fun loadMember() {
        if (member == null) {
            viewModel.api.getCachedMemberDetail(username = username)?.let { cached ->
                member = cached
                isFollowing = cached.followed
                followsViewer = cached.follows
            }
        }
        if (entries.isEmpty()) {
            viewModel.api.getCachedMemberPosts(username = username, page = 1)?.let { cachedPosts ->
                entries = cachedPosts.entries
                currentPage = cachedPosts.page
                canLoadMore = cachedPosts.entries.size >= pageSize
            }
        }
        isLoadingDetails = member == null
        isLoadingPosts = entries.isEmpty()
        errorMessage = null
        loadMoreError = false
        currentPage = 1
        try {
            val fetchedMember = viewModel.api.fetchMemberDetail(username = username)
            member = fetchedMember
            isFollowing = fetchedMember.followed
            followsViewer = fetchedMember.follows
            isLoadingDetails = false

            if (uiState.currentUser?.username?.equals(fetchedMember.username, ignoreCase = true) == true) {
                viewModel.updateCurrentUser(
                    fetchedMember,
                    email = fetchedMember.email.takeIf { it.isNotEmpty() }
                )
            }

            val postsResponse = viewModel.api.fetchMemberPosts(
                username = username,
                page = 1
            )
            entries = postsResponse.entries
            currentPage = postsResponse.page
            canLoadMore = postsResponse.entries.size >= pageSize
            isLoadingPosts = false
        } catch (err: Exception) {
            errorMessage = err.message ?: "Unable to load @$username"
            isLoadingDetails = false
            isLoadingPosts = false
        }
    }

    fun loadMore() {
        if (isLoadingMore) return
        isLoadingMore = true
        val nextPage = currentPage + 1
        scope.launch {
            try {
                val result = viewModel.api.fetchMemberPosts(
                    username = username,
                    page = nextPage
                )
                entries = entries + result.entries
                currentPage = result.page
                canLoadMore = result.entries.size >= pageSize
                loadMoreError = false
                isLoadingMore = false
            } catch (_: Exception) {
                loadMoreError = true
                isLoadingMore = false
            }
        }
    }

    fun toggleFollow() {
        if (!uiState.isAuthenticated) {
            viewModel.selectSection(SidebarSection.LOGIN)
            return
        }
        val currentlyFollowing = isFollowing
        scope.launch {
            try {
                if (currentlyFollowing) {
                    viewModel.api.unfollowUser(username = username)
                } else {
                    viewModel.api.followUser(username = username)
                }
                isFollowing = !currentlyFollowing
                member = member?.copy(followed = isFollowing)
            } catch (_: Exception) {
                // Keep previous state
            }
        }
    }

    LaunchedEffect(username, uiState.authToken, uiState.reloadTrigger) {
        loadMember()
    }

    SubPullToRefreshColumn(
        onRefresh = {
            loadMember()
            viewModel.refreshNotifications()
        },
        modifier = modifier
    ) {
        val user = member
        if (user != null) {
            // 1. User header (`templates/items/user.html`)
            UserRowView(
                user = user,
                isMemberHeader = true,
                viewModel = viewModel
            )
            SubDivider()

            // 2. Social & donation links (`templates/items/links.html`)
            val links = user.allLinks
            val donation = user.donated
            if (links.isNotEmpty() || donation > 0.0) {
                MemberLinksView(user = user)
                SubDivider()
            }


            // 3. Status row (`templates/items/status.html`)
            val isCurrentUser = uiState.currentUser != null && uiState.currentUser == user
            MemberStatusRowView(
                user = user,
                isCurrentUser = isCurrentUser,
                isFollowing = isFollowing,
                followsViewer = followsViewer,
                onToggleFollow = { toggleFollow() },
                viewModel = viewModel
            )
            SubDivider()

            // 4. Member entries stream (`templates/stream.html`)
            if (isLoadingPosts && entries.isEmpty()) {
                PlaceholderBannerView(text = "Loading posts...")
                SubDivider()
            } else if (!errorMessage.isNullOrEmpty() && entries.isEmpty()) {
                Text(
                    text = errorMessage!!,
                    fontFamily = Route159FontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.alert,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                SubDivider()
            } else if (entries.isEmpty() && !isLoadingPosts) {
                PlaceholderBannerView(text = "Uh, nothing here yet.")
                SubDivider()
            } else {
                entries.forEachIndexed { index, entry ->
                    val previousParentID = if (index > 0) entries[index - 1].parent?.id else null
                    StreamEntryView(
                        entry = entry,
                        previousParentID = previousParentID,
                        viewModel = viewModel
                    )
                }

                if (canLoadMore) {
                    LoadMoreButton(
                        isLoading = isLoadingMore,
                        isError = loadMoreError,
                        onClick = { loadMore() }
                    )
                }
            }
        } else if (isLoadingDetails) {
            PlaceholderBannerView(text = "Loading @$username...")
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

        Spacer(modifier = Modifier.height(SubTheme.bottomSpacerHeight))
    }
}


/**
 * Matches `templates/items/user.html` (`.entry` or `.entry.member`) and `Macreply/Views/MemberView.swift` (`UserRowView`).
 * Used on `MemberScreen` (`isMemberHeader = true`) and `PeopleListScreen` (`isMemberHeader = false`).
 */
@Composable
fun UserRowView(
    user: SubUser,
    isMemberHeader: Boolean,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val isSelf = uiState.currentUser?.username?.equals(user.username, ignoreCase = true) == true
    var isFollowed by remember(user.username, user.followed) { mutableStateOf(user.followed) }
    var followError by remember(user.username) { mutableStateOf(false) }
    var isFollowPending by remember(user.username) { mutableStateOf(false) }

    val rightDateLabel = remember(user, isMemberHeader, isSelf) {
        if (isMemberHeader) {
            if (isSelf) {
                if (user.createdAt > 0) ContentParser.timeAgo(user.createdAt) else user.timestamp
            } else {
                if (user.seenAt > 0) ContentParser.timeAgo(user.seenAt) else user.timestamp
            }
        } else {
            if (user.timestamp.isNotEmpty()) {
                user.timestamp
            } else if (user.createdAt > 0) {
                ContentParser.timeAgo(user.createdAt)
            } else {
                ""
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: FullName (+ @username if member header) + Follow/Edit action (if people list) + right date
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.openMember(user.username)
                }
            ) {
                Text(
                    text = user.fullName,
                    fontFamily = Route159FontFamily,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.primary
                )
                if (isMemberHeader) {
                    Text(
                        text = " @${user.username}",
                        fontFamily = Route159FontFamily,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.gray
                    )
                }
            }

            if (!isMemberHeader && uiState.isAuthenticated) {
                Text(
                    text = " • ",
                    fontFamily = Route159FontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.gray
                )
                if (isSelf) {
                    Text(
                        text = "Edit",
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.gray,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.openOptions()
                        }
                    )
                } else {
                    val followTitle = when {
                        followError -> "Retry"
                        isFollowed -> "Unfollow"
                        else -> "Follow"
                    }
                    val followColor = when {
                        followError -> SubTheme.alert
                        isFollowed -> SubTheme.gray
                        else -> SubTheme.accent
                    }
                    Text(
                        text = followTitle,
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = followColor,
                        modifier = Modifier.clickable(
                            enabled = !isFollowPending,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isFollowPending = true
                            val nextState = !isFollowed
                            scope.launch {
                                try {
                                    if (nextState) {
                                        viewModel.api.followUser(user.username)
                                    } else {
                                        viewModel.api.unfollowUser(user.username)
                                    }
                                    isFollowed = nextState
                                    followError = false
                                } catch (_: Exception) {
                                    followError = true
                                }
                                isFollowPending = false
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (rightDateLabel.isNotEmpty()) {
                Text(
                    text = rightDateLabel,
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.silver,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.openMember(user.username)
                    }
                )
            }
        }

        // Row 2: Rich bio description if non-empty
        if (user.description.isNotBlank()) {
            RichContentView(
                author = null,
                content = user.description,
                viewModel = viewModel,
                fontSize = 14.sp
            )
        }

        // Row 3: Link hostname, ~ age, and "from City"
        val rawLink = user.link.takeIf { it.isNotBlank() }
        val host = rawLink?.let { ContentParser.hostname(it) }?.takeIf { it.isNotBlank() }
        val userAge = ContentParser.age(user.birthday)
        val city = user.cityLabel.takeIf { it.isNotBlank() }

        if (host != null || userAge != null || city != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (host != null) {
                    Text(
                        text = host,
                        fontFamily = Route159FontFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.gray,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            openExternalUrl(context, rawLink)
                        }
                    )
                }


                if (userAge != null) {
                    Text(
                        text = "~ ${userAge}y old",
                        fontFamily = Route159FontFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.gray
                    )
                }

                if (city != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "from",
                            fontFamily = Route159FontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = SubTheme.gray
                        )
                        Text(
                            text = city,
                            fontFamily = Route159FontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = SubTheme.gray,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.openPeopleSearch(city)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Matches `templates/items/links.html` (`Also on ... Donated $X.`).
 */
@Composable
fun MemberLinksView(
    user: SubUser,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val links = remember(user) { user.allLinks }
    val donation = user.donated.toInt()

    val grayColor = SubTheme.gray

    val annotated = buildAnnotatedString {
        if (links.isNotEmpty()) {
            withStyle(
                SpanStyle(
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    color = grayColor
                )
            ) {
                append("Also on ")
            }
            val linkStyle = TextLinkStyles(
                style = SpanStyle(
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    color = grayColor
                )
            )
            links.forEachIndexed { idx, social ->
                if (idx > 0) {
                    val sep = if (idx == links.size - 1) " and " else ", "
                    withStyle(
                        SpanStyle(
                            fontFamily = Route159FontFamily,
                            fontSize = 13.sp,
                            color = grayColor
                        )
                    ) {
                        append(sep)
                    }
                }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "social_${social.label}",
                        styles = linkStyle,
                        linkInteractionListener = {
                            openExternalUrl(context, social.url)
                        }
                    )
                ) {
                    append(social.label)
                }
            }
            withStyle(
                SpanStyle(
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    color = grayColor
                )
            ) {
                append(".")
            }
        }

        if (donation > 0) {
            withStyle(
                SpanStyle(
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    color = grayColor
                )
            ) {
                append(if (links.isEmpty()) "Donated $$donation." else " Donated $$donation.")
            }
        }
    }

    Text(
        text = annotated,
        fontFamily = Route159FontFamily,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = grayColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

/**
 * Matches `templates/common/status.html` (`.entry .small.caps`) and `Macreply/Views/MemberView.swift` (`MemberStatusRowView`).
 */
@Composable
fun MemberStatusRowView(
    user: SubUser,
    isCurrentUser: Boolean,
    isFollowing: Boolean,
    followsViewer: Boolean,
    onToggleFollow: () -> Unit,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isCurrentUser) {
            Text(
                text = "NOTE",
                fontFamily = Route159FontFamily,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = SubTheme.accent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.openConversation(user.username)
                }
            )
            Text(
                text = "EDIT",
                fontFamily = Route159FontFamily,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = SubTheme.gray,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.openOptions()
                }
            )
            Text(
                text = "LOGOUT",
                fontFamily = Route159FontFamily,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = SubTheme.alert,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.logout()
                }
            )
        } else {
            Text(
                text = "MESSAGE",
                fontFamily = Route159FontFamily,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = SubTheme.accent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (uiState.isAuthenticated) {
                        viewModel.openConversation(user.username)
                    } else {
                        viewModel.selectSection(SidebarSection.LOGIN)
                    }
                }
            )

            Text(
                text = if (isFollowing) "UNFOLLOW" else "FOLLOW",
                fontFamily = Route159FontFamily,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = if (isFollowing) SubTheme.gray else SubTheme.accent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onToggleFollow()
                }
            )

            if (uiState.isAuthenticated && followsViewer) {
                Text(
                    text = "FOLLOWS YOU",
                    fontFamily = Route159FontFamily,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.silver
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}


