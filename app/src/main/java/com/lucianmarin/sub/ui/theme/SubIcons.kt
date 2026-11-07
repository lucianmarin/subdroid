package com.lucianmarin.sub.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SubHeroIcon(
    name: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    fontWeight: FontWeight = FontWeight.Medium
) {
    val fontSizeSp = with(LocalDensity.current) { size.toSp() }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = tint,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.wrapContentSize(unbounded = true),
            style = TextStyle(
                fontFamily = MaterialSymbolsFontFamily,
                fontWeight = fontWeight,
                color = tint,
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
