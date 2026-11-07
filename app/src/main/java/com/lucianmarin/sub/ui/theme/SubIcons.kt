package com.lucianmarin.sub.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SubIcons {

    /**
     * Maps Material Symbol names (and SF Symbol / Heroicon aliases from `../macreply` and `../subreply`)
     * to the bundled Google Material Symbols Rounded font ligatures (`res/font/material_symbols_rounded.ttf`).
     */
    fun materialSymbolFor(name: String): String = when (name) {
        "bolt" -> "bolt"
        "playlist_add", "text.insert", "bars-arrow-up" -> "playlist_add"
        "playlist_play", "text.append", "bars-arrow-down" -> "playlist_play"
        "playlist_add_check" -> "playlist_add_check"
        "list_arrow" -> "list_arrow"
        "low_priority" -> "low_priority"
        "alternate_email", "at", "at-symbol" -> "alternate_email"
        "chat_bubble", "bubble.left", "chat-bubble-left" -> "chat_bubble"
        "chat", "text.bubble", "chat-bubble-bottom-center-text" -> "chat"
        "sms", "ellipsis.bubble", "chat-bubble-left-ellipsis" -> "sms"
        "undo", "arrowshape.turn.up.left", "arrow-uturn-left" -> "undo"
        "reply" -> "reply"
        "bookmark" -> "bookmark"
        "account_circle", "person.crop.circle", "user-circle" -> "account_circle"
        "tune", "slider.horizontal.3", "adjustments-horizontal" -> "tune"
        "info", "info.circle", "information-circle" -> "info"
        "lock", "lock-closed" -> "lock"
        "person_add", "person.badge.plus" -> "person_add"
        "assignment_turned_in", "clipboard-document-check" -> "assignment_turned_in"
        "group", "person.2", "users" -> "group"
        "trending_up", "chart.line.uptrend.xyaxis", "arrow-trending-up" -> "trending_up"
        "language", "globe", "globe-alt" -> "language"
        "public" -> "public"
        "search", "magnifyingglass", "magnifying-glass" -> "search"
        "cancel", "xmark.circle.fill" -> "cancel"
        "close", "xmark" -> "close"
        "menu", "bars-3" -> "menu"
        "arrow_back", "arrow-left" -> "arrow_back"
        "arrow_upward", "arrow.up" -> "arrow_upward"
        "arrow_downward", "arrow.down" -> "arrow_downward"
        "receipt_long", "receipt-refund" -> "receipt_long"
        "attach_money", "currency-dollar" -> "attach_money"
        "credit_card", "credit-card" -> "credit_card"
        "payments", "banknotes" -> "payments"
        "play_arrow", "play" -> "play_arrow"
        "smartphone", "device-phone-mobile" -> "smartphone"
        "computer", "computer-desktop" -> "computer"
        "devices" -> "devices"
        "segment" -> "segment"
        "sort" -> "sort"
        else -> name
    }
}

@Composable
fun SubMaterialSymbol(
    name: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    animated: Boolean = false
) {
    val symbolLigature = remember(name) { SubIcons.materialSymbolFor(name) }
    val fontSizeSp = with(LocalDensity.current) { size.toSp() }

    val scaleFactor: Float
    val rotationDeg: Float
    if (animated) {
        val transition = rememberInfiniteTransition(label = "sub_icon_$symbolLigature")
        val animScale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1600
                    1.0f at 0 using FastOutSlowInEasing
                    1.12f at 224 using FastOutSlowInEasing
                    0.95f at 448 using FastOutSlowInEasing
                    1.03f at 640 using FastOutSlowInEasing
                    1.0f at 800
                    1.0f at 1600
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "scale_$symbolLigature"
        )
        val animRot by transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1600
                    0f at 0 using FastOutSlowInEasing
                    -8f at 224 using FastOutSlowInEasing
                    4f at 448 using FastOutSlowInEasing
                    -1.5f at 640 using FastOutSlowInEasing
                    0f at 800
                    0f at 1600
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "rot_$symbolLigature"
        )
        scaleFactor = animScale
        rotationDeg = animRot
    } else {
        scaleFactor = 1f
        rotationDeg = 0f
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (animated) {
                    Modifier.graphicsLayer {
                        scaleX = scaleFactor
                        scaleY = scaleFactor
                        rotationZ = rotationDeg
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbolLigature,
            color = tint,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.wrapContentSize(unbounded = true),
            style = TextStyle(
                fontFamily = MaterialSymbolsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = fontSizeSp,
                lineHeight = fontSizeSp,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            )
        )
    }
}

@Composable
fun SubHeroIcon(
    name: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    @Suppress("UNUSED_PARAMETER") strokeWidthDp: Dp = 2.dp,
    animated: Boolean = false
) {
    SubMaterialSymbol(
        name = name,
        tint = tint,
        modifier = modifier,
        size = size,
        animated = animated
    )
}

@Composable
fun SubBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val upperTile = remember {
        PathParser().parsePathString(
            "M240 249.271C240 243.589 243.21 238.395 248.292 235.854L458.292 130.854C468.265 125.867 480 133.12 480 144.271V230.729C480 236.411 476.79 241.605 471.708 244.146L261.708 349.146C251.735 354.133 240 346.88 240 335.729V249.271Z"
        ).toPath()
    }
    val lowerTile = remember {
        PathParser().parsePathString(
            "M120 369.271C120 363.589 123.21 358.395 128.292 355.854L338.292 250.854C348.265 245.867 360 253.12 360 264.271V350.729C360 356.411 356.79 361.605 351.708 364.146L141.708 469.146C131.735 474.133 120 466.88 120 455.729V369.271Z"
        ).toPath()
    }

    Canvas(modifier = modifier.size(size)) {
        val corner = this.size.width * 0.22f
        drawRoundRect(
            color = Color(0xFF2EB8B8),
            cornerRadius = CornerRadius(corner, corner)
        )
        val scaleFactor = this.size.width / 600f
        withTransform({
            scale(scaleX = scaleFactor, scaleY = scaleFactor, pivot = Offset.Zero)
        }) {
            drawPath(path = upperTile, color = Color(0xFF1F4D7A))
            drawPath(path = lowerTile, color = Color(0xFFFFFFFF))
        }
    }
}
