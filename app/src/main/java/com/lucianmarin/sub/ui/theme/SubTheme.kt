package com.lucianmarin.sub.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.sub.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        fun fromId(id: String): ThemeMode = entries.find { it.id == id } ?: SYSTEM
    }
}

val Route159FontFamily = FontFamily(
    Font(R.font.route159_regular, FontWeight.Normal),
    Font(R.font.route159_bold, FontWeight.Bold)
)

val HackFontFamily = FontFamily(
    Font(R.font.hack_regular, FontWeight.Normal)
)

val MaterialSymbolsFontFamily = FontFamily(
    Font(R.font.material_symbols_rounded, FontWeight.Normal),
    Font(R.font.material_symbols_rounded, FontWeight.Medium)
)

/**
 * Exact Subreply color palette matching `../subreply/static/style.sass` and `../macreply/Sources/Macreply/Views/MainSplitView.swift`.
 */
@Immutable
data class SubPalette(
    val isDark: Boolean,
    val background: Color,
    val whitesmoke: Color,
    val redsmoke: Color,
    val gainsboro: Color,
    val silver: Color,
    val gray: Color = Color(0xFF808080),
    val primary: Color,
    val accent: Color = Color(0xFF2EB8B8),
    val alert: Color = Color(0xFFFF4500)
)

val LightSubPalette = SubPalette(
    isDark = false,
    background = Color(0xFFFFFFFF),
    whitesmoke = Color(0xFFF0F0F0),
    redsmoke = Color(0xFFFFF0F0),
    gainsboro = Color(0xFFE0E0E0),
    silver = Color(0xFFC0C0C0),
    gray = Color(0xFF808080),
    primary = Color(0xFF000000),
    accent = Color(0xFF2EB8B8),
    alert = Color(0xFFFF4500)
)

val DarkSubPalette = SubPalette(
    isDark = true,
    background = Color(0xFF000000),
    whitesmoke = Color(0xFF181818),
    redsmoke = Color(0xFF281818),
    gainsboro = Color(0xFF282828),
    silver = Color(0xFF404040),
    gray = Color(0xFF808080),
    primary = Color(0xFFFFFFFF),
    accent = Color(0xFF2EB8B8),
    alert = Color(0xFFFF4500)
)

val LocalSubPalette = staticCompositionLocalOf { LightSubPalette }

object SubTheme {
    val colors: SubPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalSubPalette.current

    val background: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSubPalette.current.background

    val whitesmoke: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSubPalette.current.whitesmoke

    val redsmoke: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSubPalette.current.redsmoke

    val gainsboro: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSubPalette.current.gainsboro

    val silver: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSubPalette.current.silver

    val gray: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSubPalette.current.gray

    val primary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSubPalette.current.primary

    val accent: Color = Color(0xFF2EB8B8)
    val alert: Color = Color(0xFFFF4500)
}

private val SubTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 15.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 10.sp
    )
)

@Composable
fun SubreplyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val palette = if (isDark) DarkSubPalette else LightSubPalette

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.background,
            secondary = palette.accent,
            background = palette.background,
            surface = palette.background,
            surfaceVariant = palette.whitesmoke,
            onBackground = palette.primary,
            onSurface = palette.primary,
            onSurfaceVariant = palette.gray,
            outline = palette.whitesmoke,
            error = palette.alert
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.background,
            secondary = palette.accent,
            background = palette.background,
            surface = palette.background,
            surfaceVariant = palette.whitesmoke,
            onBackground = palette.primary,
            onSurface = palette.primary,
            onSurfaceVariant = palette.gray,
            outline = palette.whitesmoke,
            error = palette.alert
        )
    }

    CompositionLocalProvider(LocalSubPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SubTypography,
            content = content
        )
    }
}

@Composable
fun SubDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = SubTheme.whitesmoke
    )
}

@Composable
fun SubVerticalDivider(modifier: Modifier = Modifier) {
    VerticalDivider(
        modifier = modifier.fillMaxHeight(),
        thickness = 1.dp,
        color = SubTheme.whitesmoke
    )
}

/**
 * Native Android pull-to-refresh ScrollView matching `RefreshableScrollView` in `../macreply`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPullToRefreshColumn(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                scope.launch {
                    val startMs = System.currentTimeMillis()
                    try {
                        onRefresh()
                    } finally {
                        val elapsedMs = System.currentTimeMillis() - startMs
                        if (elapsedMs < 450L) {
                            delay(450L - elapsedMs)
                        }
                        isRefreshing = false
                    }
                }
            }
        },
        state = pullState,
        modifier = modifier
            .fillMaxSize()
            .background(SubTheme.background),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = isRefreshing,
                containerColor = SubTheme.whitesmoke,
                color = SubTheme.accent,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

/**
 * Diffs a freshly fetched list against the currently displayed list by both key and content (`equals`),
 * updating existing items in-place immediately and inserting brand-new items one by one with a 200ms delay.
 */
suspend fun <T, K> applyDiffedFeedUpdate(
    oldList: List<T>,
    newList: List<T>,
    keySelector: (T) -> K,
    onUpdate: (List<T>) -> Unit,
    delayMillis: Long = 200L
) {
    if (oldList.isEmpty()) {
        onUpdate(newList)
        return
    }
    val oldByKey = oldList.associateBy(keySelector)
    val newIndices = newList.indices.filter { idx ->
        keySelector(newList[idx]) !in oldByKey
    }
    val hasModifiedOrRemoved = oldList.size != newList.size - newIndices.size ||
        newList.any { item ->
            val oldItem = oldByKey[keySelector(item)]
            oldItem != null && oldItem != item
        }

    if (newIndices.isEmpty()) {
        if (hasModifiedOrRemoved || oldList != newList) {
            onUpdate(newList)
        }
        return
    }

    val newIndexSet = newIndices.toSet()
    if (hasModifiedOrRemoved) {
        onUpdate(newList.filterIndexed { idx, _ -> idx !in newIndexSet })
    }

    val revealedIndices = mutableSetOf<Int>()
    for (idx in newIndices.reversed()) {
        delay(delayMillis)
        revealedIndices.add(idx)
        onUpdate(newList.filterIndexed { i, _ -> i !in newIndexSet || i in revealedIndices })
    }
}

/**
 * Matches `.menu` in `../subreply/static/style.sass` and `MenuHeaderView` in `../macreply`,
 * sized to match the exact 60.dp total height (10.dp outer + 10.dp inner vertical padding + 20.sp text)
 * of the top whitesmoke forms (`PlaceholderBannerView` and `SearchQueryBar`).
 */
@Composable
fun <T> SubMenuHeader(
    items: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: ((T) -> Int)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (label, value) ->
                val isActive = value == selected
                val count = badgeCount?.invoke(value) ?: 0
                val textColor = if (isActive) SubTheme.primary else SubTheme.silver
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(value) }
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                fontFamily = Route159FontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = textColor
                            )
                        )
                        if (count > 0) {
                            Text(
                                text = if (count > 99) "99+" else count.toString(),
                                style = TextStyle(
                                    fontFamily = Route159FontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    color = SubTheme.alert
                                )
                            )
                        }
                    }
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(y = 3.dp)
                                .width(10.dp)
                                .height(2.dp)
                                .background(SubTheme.accent)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Matches `SubTextField` in `../macreply/Sources/Macreply/Views/OptionsView.swift` and `style.sass` `input`.
 */
@Composable
fun SubTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontFamily = Route159FontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = SubTheme.gray
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Route159FontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.primary
            ),
            cursorBrush = SolidColor(SubTheme.accent),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction?.invoke() },
                onGo = { onImeAction?.invoke() },
                onSend = { onImeAction?.invoke() },
                onNext = { onImeAction?.invoke() }
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SubTheme.whitesmoke)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = Route159FontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = SubTheme.gray
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                fontFamily = Route159FontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                color = SubTheme.alert
            )
        }
    }
}

/**
 * Matches `SubSubmitButton` in `../macreply/Sources/Macreply/Views/OptionsView.swift` and `button` in `style.sass`.
 */
@Composable
fun SubSubmitButton(
    title: String,
    isLoading: Boolean = false,
    enabled: Boolean = !isLoading,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SubTheme.accent)
            .alpha(if (!enabled || isLoading) 0.6f else 1f)
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 15.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontFamily = Route159FontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = SubTheme.background
        )
    }
}


