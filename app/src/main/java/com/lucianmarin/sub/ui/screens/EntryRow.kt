package com.lucianmarin.sub.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.sub.model.SubEntry
import com.lucianmarin.sub.model.SubUser
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubDivider
import com.lucianmarin.sub.ui.theme.SubTheme
import com.lucianmarin.sub.util.ContentParser
import com.lucianmarin.sub.util.ContentToken
import kotlinx.coroutines.launch

/**
 * Matches `templates/items/entry.html` and `Macreply/Views/EntryRowView.swift` (`RichContentView`) 1:1.
 * Renders a single paragraph beginning with bold `author.fullName + " "` followed by parsed tokens.
 */
@Composable
fun RichContentView(
    author: SubUser?,
    content: String,
    viewModel: AppStateViewModel,
    fontSize: TextUnit = 14.sp,
    singleLine: Boolean = false,
    onContainerClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryColor = SubTheme.primary
    val accentColor = SubTheme.accent

    val normalized = remember(content) {
        content
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
    }
    val tokens = remember(normalized) { ContentParser.parse(normalized) }

    val annotated = buildAnnotatedString {
        if (author != null) {
            val authorStyle = TextLinkStyles(
                style = SpanStyle(
                    fontFamily = Route159FontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    color = primaryColor
                )
            )
            withLink(
                LinkAnnotation.Clickable(
                    tag = "author_${author.username}",
                    styles = authorStyle,
                    linkInteractionListener = {
                        viewModel.openMember(author.username)
                    }
                )
            ) {
                append(author.fullName)
            }
            withStyle(
                SpanStyle(
                    fontFamily = Route159FontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    color = primaryColor
                )
            ) {
                append(" ")
            }
        }

        val tokenLinkStyle = TextLinkStyles(
            style = SpanStyle(
                fontFamily = Route159FontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = fontSize,
                color = accentColor
            )
        )
        val textSpanStyle = SpanStyle(
            fontFamily = Route159FontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = fontSize,
            color = primaryColor
        )

        tokens.forEachIndexed { idx, token ->
            when (token) {
                is ContentToken.Text -> {
                    if (onContainerClick != null) {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "text_$idx",
                                styles = TextLinkStyles(style = textSpanStyle),
                                linkInteractionListener = { onContainerClick() }
                            )
                        ) {
                            append(token.text)
                        }
                    } else {
                        withStyle(textSpanStyle) {
                            append(token.text)
                        }
                    }
                }
                is ContentToken.Mention -> {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "mention_${idx}_${token.username}",
                            styles = tokenLinkStyle,
                            linkInteractionListener = {
                                viewModel.openMember(token.username)
                            }
                        )
                    ) {
                        append("@${token.username}")
                    }
                }
                is ContentToken.ReplyRef -> {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "reply_${idx}_${token.id}",
                            styles = tokenLinkStyle,
                            linkInteractionListener = {
                                viewModel.openReply(token.id)
                            }
                        )
                    ) {
                        append("#${token.id}")
                    }
                }
                is ContentToken.Hashtag -> {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "hashtag_${idx}_${token.tag}",
                            styles = tokenLinkStyle,
                            linkInteractionListener = {
                                viewModel.openDiscoverSearch("#${token.tag}")
                            }
                        )
                    ) {
                        append("#${token.tag}")
                    }
                }
                is ContentToken.Link -> {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "link_${idx}_${token.url}",
                            styles = tokenLinkStyle,
                            linkInteractionListener = {
                                openExternalUrl(context, token.url)
                            }
                        )
                    ) {
                        append(token.display)
                    }
                }
            }
        }
    }

    Text(
        text = annotated,
        fontFamily = Route159FontFamily,
        fontSize = fontSize,
        lineHeight = 20.sp,
        color = primaryColor,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
        modifier = modifier.fillMaxWidth()
    )
}

private enum class SaveButtonState {
    SAVE,
    UNSAVE,
    RETRY_SAVE,
    RETRY_UNSAVE
}

private enum class DeleteButtonState {
    IDLE,
    CONFIRMING,
    RETRY,
    DELETED
}

/**
 * Matches `templates/items/entry.html` and `Macreply/Views/EntryRowView.swift` (`EntryItemView`) 1:1.
 */
@Composable
fun EntryItemView(
    entry: SubEntry,
    viewModel: AppStateViewModel,
    isRootOverride: Boolean? = null,
    canDeleteAsRecipient: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var saveState by remember(entry.id, entry.saved) {
        mutableStateOf(if (entry.saved) SaveButtonState.UNSAVE else SaveButtonState.SAVE)
    }
    var deleteState by remember(entry.id) {
        mutableStateOf(DeleteButtonState.IDLE)
    }

    val isOwnEntry = uiState.currentUser != null && uiState.currentUser == entry.createdBy
    val canDelete = (isOwnEntry || canDeleteAsRecipient) && entry.replyCount == 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RichContentView(
            author = entry.createdBy,
            content = entry.content,
            viewModel = viewModel,
            fontSize = 14.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Reply button
            Text(
                text = entry.replyButtonLabel(isRootOverride = isRootOverride),
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = SubTheme.gray,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.openReply(entry.id)
                }
            )

            if (uiState.isAuthenticated) {
                // 2. Edit button (own entry with 0 replies and not deleted)
                if (isOwnEntry) {
                    if (entry.replyCount == 0 && deleteState != DeleteButtonState.DELETED) {
                        Text(
                            text = "edit",
                            fontFamily = Route159FontFamily,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            color = SubTheme.gray,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                viewModel.openEditEntry(entry)
                            }
                        )
                    }
                } else {
                    // 3. Save / Unsave / Retry button (others' entries)
                    val saveLabel = when (saveState) {
                        SaveButtonState.SAVE -> "save"
                        SaveButtonState.UNSAVE -> "unsave"
                        SaveButtonState.RETRY_SAVE, SaveButtonState.RETRY_UNSAVE -> "retry"
                    }
                    val saveColor = when (saveState) {
                        SaveButtonState.SAVE -> SubTheme.gray
                        SaveButtonState.UNSAVE -> SubTheme.accent
                        SaveButtonState.RETRY_SAVE, SaveButtonState.RETRY_UNSAVE -> SubTheme.alert
                    }
                    Text(
                        text = saveLabel,
                        fontFamily = Route159FontFamily,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        color = saveColor,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val saving = saveState == SaveButtonState.SAVE || saveState == SaveButtonState.RETRY_SAVE
                            scope.launch {
                                try {
                                    if (saving) {
                                        viewModel.api.saveEntry(entry.id)
                                    } else {
                                        viewModel.api.unsaveEntry(entry.id)
                                    }
                                    saveState = if (saving) SaveButtonState.UNSAVE else SaveButtonState.SAVE
                                } catch (_: Exception) {
                                    saveState = if (saving) SaveButtonState.RETRY_SAVE else SaveButtonState.RETRY_UNSAVE
                                }
                            }
                        }
                    )
                }

                // 4. Delete -> Undo / Yes -> Deleted flow
                if (canDelete) {
                    when (deleteState) {
                        DeleteButtonState.IDLE -> {
                            Text(
                                text = "delete",
                                fontFamily = Route159FontFamily,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = SubTheme.gray,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    deleteState = DeleteButtonState.CONFIRMING
                                }
                            )
                        }
                        DeleteButtonState.CONFIRMING, DeleteButtonState.RETRY -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "undo",
                                    fontFamily = Route159FontFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    color = SubTheme.accent,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        deleteState = DeleteButtonState.IDLE
                                    }
                                )
                                Text(
                                    text = if (deleteState == DeleteButtonState.RETRY) "retry" else "yes",
                                    fontFamily = Route159FontFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    color = SubTheme.alert,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        scope.launch {
                                            try {
                                                viewModel.api.deleteEntry(entry.id)
                                                deleteState = DeleteButtonState.DELETED
                                            } catch (_: Exception) {
                                                deleteState = DeleteButtonState.RETRY
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        DeleteButtonState.DELETED -> {
                            Text(
                                text = "deleted",
                                fontFamily = Route159FontFamily,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                color = SubTheme.silver
                            )
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.weight(1f))

            // 5. Right-aligned timestamp (with edited prefix)
            Text(
                text = if (entry.edited) "··· ${entry.timestamp}" else entry.timestamp,
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = SubTheme.silver,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    viewModel.openReply(entry.id)
                }
            )
        }
    }
}

/**
 * Matches `templates/items/parent.html` (`.entry.inline.fade` with 50% opacity and single-line truncation).
 */
@Composable
fun InlineParentRowView(
    parent: SubEntry,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                viewModel.openReply(parent.id)
            }
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .alpha(0.5f)
    ) {
        RichContentView(
            author = parent.createdBy,
            content = parent.content,
            viewModel = viewModel,
            fontSize = 14.sp,
            singleLine = true,
            onContainerClick = { viewModel.openReply(parent.id) }
        )
    }
}

/**
 * Matches `templates/stream.html` and `Macreply/Views/EntryRowView.swift` (`StreamEntryView`) 1:1.
 */
@Composable
fun StreamEntryView(
    entry: SubEntry,
    previousParentID: Int?,
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        val parent = entry.parent
        if (parent != null) {
            if (previousParentID != parent.id) {
                InlineParentRowView(
                    parent = parent,
                    viewModel = viewModel
                )
                SubDivider()
            }
            val canDeleteAsRecipient = uiState.currentUser != null && uiState.currentUser == parent.createdBy
            EntryItemView(
                entry = entry,
                viewModel = viewModel,
                isRootOverride = false,
                canDeleteAsRecipient = canDeleteAsRecipient,
                modifier = Modifier.padding(start = 30.dp)
            )
            SubDivider()
        } else {
            EntryItemView(
                entry = entry,
                viewModel = viewModel,
                isRootOverride = true
            )
            SubDivider()

            val canDeleteKidAsRecipient = uiState.currentUser != null && uiState.currentUser == entry.createdBy
            for (kid in entry.streamKids) {
                EntryItemView(
                    entry = kid,
                    viewModel = viewModel,
                    isRootOverride = false,
                    canDeleteAsRecipient = canDeleteKidAsRecipient,
                    modifier = Modifier.padding(start = 30.dp)
                )
                SubDivider()
            }
        }
    }
}

/**
 * Matches `templates/pages/loader.html` (`.load`) and `Macreply` (`Load more` / `Retry` centered text).
 */
@Composable
fun LoadMoreButton(
    isLoading: Boolean,
    isError: Boolean = false,
    label: String = "Load more",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isError) "Retry" else label,
            fontFamily = Route159FontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = if (isError) SubTheme.alert else SubTheme.gray,
            modifier = Modifier.clickable(
                enabled = !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
        )
    }
}

fun openExternalUrl(context: Context, url: String) {
    try {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("tel:") || url.startsWith("mailto:")) {
            url
        } else {
            "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

