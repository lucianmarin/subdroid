package com.lucianmarin.sub.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.state.AppUiState
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubHeroIcon
import com.lucianmarin.sub.ui.theme.SubMenuHeader
import com.lucianmarin.sub.ui.theme.SubTheme
import kotlinx.coroutines.launch

/**
 * Matches `templates/input/` composers, `static/script.js`, and `Macreply/Views/ComposerView.swift` 1:1.
 * Auto-expanding single-paragraph field with 720-char clamp, >640 redsmoke warning,
 * live char counter at bottom-right, and Enter-to-submit.
 */

@Composable
fun ComposerView(
    placeholder: String,
    initialText: String = "",
    onSubmit: suspend (String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(initialText) {
        if (initialText.isNotEmpty() && text.isEmpty()) {
            text = initialText
        }
    }

    val isOverSoftLimit = text.length > 640

    fun submitCurrent() {
        val cleaned = text
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
        if (cleaned.isEmpty() || isSubmitting) return

        isSubmitting = true
        errorMessage = null
        scope.launch {
            val result = onSubmit(cleaned)
            result.onSuccess {
                text = ""
                isSubmitting = false
                focusManager.clearFocus()
            }.onFailure { err ->
                errorMessage = err.message ?: "Failed to submit"
                isSubmitting = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (isOverSoftLimit) SubTheme.redsmoke else SubTheme.whitesmoke)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        top = 10.dp,
                        end = if (text.isEmpty()) 8.dp else 34.dp,
                        bottom = 10.dp
                    )
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.gray
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = { newValue ->
                        if (newValue.contains('\n') || newValue.contains('\r')) {
                            val stripped = newValue
                                .replace("\r\n", " ")
                                .replace("\n", " ")
                                .replace("\r", " ")
                            text = if (stripped.length > 720) stripped.take(720) else stripped
                            submitCurrent()
                        } else {
                            text = if (newValue.length > 720) newValue.take(720) else newValue
                        }
                    },
                    enabled = !isSubmitting,
                    textStyle = TextStyle(
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.primary
                    ),
                    cursorBrush = SolidColor(SubTheme.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { submitCurrent() }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (text.isNotEmpty()) {
                Text(
                    text = "${text.length}",
                    fontFamily = Route159FontFamily,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    color = SubTheme.silver,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 5.dp)
                )
            }
        }

        if (!errorMessage.isNullOrEmpty()) {
            Text(
                text = errorMessage!!,
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = SubTheme.alert
            )
        }
    }
}

/**
 * Matches `.placeholder` banner in `templates/input/reply.html`, `templates/mentions.html`,
 * `templates/replies.html`, `templates/saved.html`, `templates/trending.html`, etc.
 */
@Composable
fun PlaceholderBannerView(
    text: String,
    showReplyIcon: Boolean = false,
    isAuthPrompt: Boolean = false,
    onLoginClick: (() -> Unit)? = null,
    onRegisterClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SubTheme.whitesmoke)
                .then(
                    if (onClick != null && !isAuthPrompt) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAuthPrompt && text == "Login or register to post and reply") {
                val annotated = buildAnnotatedString {
                    pushStringAnnotation(tag = "AUTH", annotation = "login")
                    withStyle(SpanStyle(color = SubTheme.primary)) {
                        append("Login")
                    }
                    pop()
                    withStyle(SpanStyle(color = SubTheme.gray)) {
                        append(" or ")
                    }
                    pushStringAnnotation(tag = "AUTH", annotation = "register")
                    withStyle(SpanStyle(color = SubTheme.primary)) {
                        append("register")
                    }
                    pop()
                    withStyle(SpanStyle(color = SubTheme.gray)) {
                        append(" to post and reply")
                    }
                }
                @Suppress("DEPRECATION")
                ClickableText(
                    text = annotated,
                    style = TextStyle(
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onClick = { offset ->
                        annotated.getStringAnnotations("AUTH", offset, offset)
                            .firstOrNull()?.let { ann ->
                                when (ann.item) {
                                    "login" -> onLoginClick?.invoke()
                                    "register" -> onRegisterClick?.invoke()
                                }
                            }
                    }
                )
            } else {
                Text(
                    text = text,
                    fontFamily = Route159FontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showReplyIcon) {
                SubHeroIcon(
                    name = SidebarSection.REPLIES.iconName,
                    tint = SubTheme.silver,
                    size = 16.dp,
                    modifier = Modifier.scale(scaleX = 1f, scaleY = -1f)
                )
            }
        }
    }
}

/**
 * Matches `templates/input/query.html` and `templates/input/people.html` (`.query` input + button).
 */
@Composable
fun SearchQueryBar(
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(SubTheme.whitesmoke),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.gray
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.primary
                    ),
                    cursorBrush = SolidColor(SubTheme.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onSubmit()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 29.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClear
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    SubHeroIcon(
                        name = "cancel",
                        tint = SubTheme.gray,
                        size = 14.dp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(29.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                SubHeroIcon(
                    name = "search",
                    tint = SubTheme.silver,
                    size = 16.dp
                )
            }
        }
    }
}

/**
 * Top subtab navigation bar for Messages (Messages, Replies, Mentions) and
 * Trending (Trending, Saved, Followers, Following), rendered in `SubMenuHeader` style
 * with the same 60.dp total height as the top whitesmoke forms.
 */
@Composable
fun SectionSubtabsHeader(
    section: SidebarSection,
    uiState: AppUiState,
    viewModel: AppStateViewModel,
    onReselectCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtabs = when (section) {
        SidebarSection.MESSAGES,
        SidebarSection.REPLIES,
        SidebarSection.MENTIONS -> SidebarSection.messagesSubtabs

        SidebarSection.TRENDING,
        SidebarSection.SAVED,
        SidebarSection.FOLLOWERS,
        SidebarSection.FOLLOWING -> if (uiState.isAuthenticated) {
            SidebarSection.trendingSubtabs
        } else {
            listOf(SidebarSection.TRENDING)
        }

        else -> emptyList()
    }

    if (subtabs.isEmpty()) return

    SubMenuHeader(
        items = subtabs.map { it.title to it },
        selected = section,
        onSelect = { target ->
            if (target == section) {
                onReselectCurrent()
            } else {
                viewModel.selectSection(target)
            }
        },
        badgeCount = { target -> uiState.badgeCount(target) },
        modifier = modifier
    )
}

