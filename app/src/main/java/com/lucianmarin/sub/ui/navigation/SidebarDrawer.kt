package com.lucianmarin.sub.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.sub.model.NavigationDestination
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.model.SubNotifications
import com.lucianmarin.sub.state.AppUiState
import com.lucianmarin.sub.ui.theme.LocalSubPalette
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubHeroIcon
import com.lucianmarin.sub.ui.theme.SubTheme

/**
 * Floating bottom navigation bar displaying the main sections:
 * - Authenticated: Feed, Messages (subtabs: Replies, Mentions), Trending (subtabs: Saved, Followers, Following), People, Discover
 * - Unauthenticated: About, Login (subtabs: Register), Trending, People, Discover
 */
@Composable
fun SidebarView(
    uiState: AppUiState,
    onSelectSection: (SidebarSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val isInConversation = uiState.navigationPath.lastOrNull() is NavigationDestination.Conversation
    val activeParentSection = uiState.selectedSection.parentBottomSection

    val isDark = LocalSubPalette.current.isDark
    val barBg = if (isDark) SubTheme.whitesmoke else SubTheme.background
    Row(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(barBg)
            .padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (section in uiState.visibleSidebarSections) {
            val targetSection = uiState.targetSectionForBottomNav(section)
            val isSelected = when {
                section == SidebarSection.MESSAGES && isInConversation -> true
                isInConversation -> false
                else -> activeParentSection == section
            }

            val iconName = when {
                section == SidebarSection.MESSAGES && isInConversation -> {
                    when (uiState.activeConversationHasMessages) {
                        true -> "chat"
                        false -> "sms"
                        null -> targetSection.iconName
                    }
                }
                section == SidebarSection.LOGIN &&
                    uiState.selectedSection == SidebarSection.REGISTER -> {
                    uiState.selectedSection.iconName
                }
                else -> targetSection.iconName
            }

            SidebarIconButton(
                title = targetSection.title,
                iconName = iconName,
                isSelected = isSelected,
                badgeCount = uiState.bottomNavBadgeCount(section),
                onClick = { onSelectSection(targetSection) }
            )
        }
    }
}

@Composable
private fun SidebarIconButton(
    title: String,
    iconName: String,
    isSelected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    val hasBadge = badgeCount > 0
    val iconColor = when {
        isSelected -> SubTheme.background
        hasBadge -> SubTheme.alert
        else -> SubTheme.gray
    }
    val badgeColor = if (isSelected) SubTheme.background else SubTheme.alert
    val bgColor = if (isSelected) SubTheme.accent else Color.Transparent
    val badgeText = SubNotifications.capBadge(badgeCount)

    Box(
        modifier = Modifier
            .width(45.dp)
            .height(35.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .semantics { contentDescription = title }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        SubHeroIcon(
            name = iconName,
            tint = iconColor,
            size = 19.dp
        )

        if (badgeText != null) {
            Text(
                text = badgeText,
                fontFamily = Route159FontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = badgeColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 2.dp)
            )
        }
    }
}

