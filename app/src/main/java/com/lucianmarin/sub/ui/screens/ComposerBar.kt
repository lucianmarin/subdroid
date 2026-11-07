package com.lucianmarin.sub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
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

val TopFormOuterHorizontalPadding = 20.dp
val TopFormOuterVerticalPadding = 10.dp
val TopFormInnerHorizontalPadding = 10.dp
val TopFormInnerVerticalPadding = 8.dp
val TopFormCornerShape = RoundedCornerShape(10.dp)

@Composable
fun topFormTextStyle(color: Color): TextStyle = TextStyle(
    fontFamily = Route159FontFamily,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = color
)

/**
 * Shared container for top input forms (`ComposerView`, `SearchQueryBar`, `PlaceholderBannerView`)
 * to guarantee identical padding, corner radius, and single-line height across feeds.
 */
@Composable
private fun TopFormTextFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Box(
        modifier = modifier.padding(
            horizontal = TopFormInnerHorizontalPadding,
            vertical = TopFormInnerVerticalPadding
        )
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = topFormTextStyle(SubTheme.gray),
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = topFormTextStyle(SubTheme.primary),
            cursorBrush = SolidColor(SubTheme.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Matches `templates/input/` composers, `static/script.js`, and `Macreply/Views/ComposerView.swift` 1:1.
 * Auto-expanding single-paragraph field with 720-char clamp, >640 redsmoke warning,
 * live char counter at bottom-right, and Enter-to-submit.
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComposerView(
    placeholder: String,
    initialText: String = "",
    onSubmit: suspend (String) -> Result<Unit>,
    modifier: Modifier = Modifier
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(density)

    LaunchedEffect(initialText) {
        if (initialText.isNotEmpty() && text.isEmpty()) {
            text = initialText
        }
    }

    LaunchedEffect(isFocused, imeBottom, text, boxSize) {
        if (isFocused && imeBottom > 0 && boxSize.height > 0) {
            val extraBottomPx = with(density) { 64.dp.toPx() }
            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = 0f,
                    right = boxSize.width.toFloat(),
                    bottom = boxSize.height.toFloat() + extraBottomPx
                )
            )
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
            .onSizeChanged { boxSize = it }
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { isFocused = it.hasFocus }
            .padding(horizontal = TopFormOuterHorizontalPadding, vertical = TopFormOuterVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TopFormCornerShape)
                .background(if (isOverSoftLimit) SubTheme.redsmoke else SubTheme.whitesmoke)
        ) {
            TopFormTextFieldBox(
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
                placeholder = placeholder,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { submitCurrent() }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (text.isNotEmpty()) {
                Text(
                    text = "${text.length}",
                    style = TextStyle(
                        fontFamily = Route159FontFamily,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        color = SubTheme.silver
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 5.dp)
                )
            }
        }

        if (!errorMessage.isNullOrEmpty()) {
            Text(
                text = errorMessage!!,
                style = TextStyle(
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = SubTheme.alert
                )
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TopFormOuterHorizontalPadding, vertical = TopFormOuterVerticalPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TopFormCornerShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(SubTheme.whitesmoke, SubTheme.background)
                    )
                )
                .padding(horizontal = TopFormInnerHorizontalPadding, vertical = TopFormInnerVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAuthPrompt) {
                val primaryStyle = TextLinkStyles(style = SpanStyle(color = SubTheme.primary))
                val annotated = buildAnnotatedString {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "login",
                            styles = primaryStyle,
                            linkInteractionListener = { onLoginClick?.invoke() }
                        )
                    ) {
                        append("Login")
                    }
                    withStyle(SpanStyle(color = SubTheme.gray)) {
                        append(" or ")
                    }
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "register",
                            styles = primaryStyle,
                            linkInteractionListener = { onRegisterClick?.invoke() }
                        )
                    ) {
                        append("register")
                    }
                    withStyle(SpanStyle(color = SubTheme.gray)) {
                        append(" $text")
                    }
                }
                Text(
                    text = annotated,
                    style = topFormTextStyle(SubTheme.gray),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = text,
                    style = topFormTextStyle(SubTheme.gray),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showReplyIcon) {
                SubHeroIcon(
                    name = "reply",
                    tint = SubTheme.gray,
                    size = 16.dp,
                    fontWeight = FontWeight.Normal,
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
            .padding(horizontal = TopFormOuterHorizontalPadding, vertical = TopFormOuterVerticalPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TopFormCornerShape)
                .background(SubTheme.whitesmoke),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopFormTextFieldBox(
                value = query,
                onValueChange = onQueryChange,
                placeholder = placeholder,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onSubmit()
                    }
                ),
                modifier = Modifier.weight(1f)
            )

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
 * with the same 56.dp total height as the top whitesmoke forms.
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

