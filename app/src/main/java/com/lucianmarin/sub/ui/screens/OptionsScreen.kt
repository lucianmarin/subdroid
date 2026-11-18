package com.lucianmarin.sub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.sub.model.OptionsTab
import com.lucianmarin.sub.model.SubUser
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubDivider
import com.lucianmarin.sub.ui.theme.SubMenuHeader
import com.lucianmarin.sub.ui.theme.SubSubmitButton
import com.lucianmarin.sub.ui.theme.SubTheme
import com.lucianmarin.sub.ui.theme.ThemeMode
import com.lucianmarin.sub.ui.theme.subHorizontalSwipe
import kotlinx.coroutines.launch

/**
 * Matches `templates/pages/profile.html`, `templates/pages/account.html`,
 * and `Macreply/Views/OptionsView.swift` 1:1.
 */
@Composable
fun OptionsScreen(
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeTab = uiState.activeOptionsTab

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setActiveOptionsTab(OptionsTab.PROFILE)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SubTheme.background)
            .subHorizontalSwipe(
                onSwipeNext = {
                    val tabs = OptionsTab.entries
                    val idx = tabs.indexOf(activeTab)
                    if (idx < tabs.lastIndex) {
                        viewModel.setActiveOptionsTab(tabs[idx + 1])
                        viewModel.refreshNotificationsOnViewChange()
                        true
                    } else {
                        false
                    }
                },
                onSwipePrevious = {
                    val tabs = OptionsTab.entries
                    val idx = tabs.indexOf(activeTab)
                    if (idx > 0) {
                        viewModel.setActiveOptionsTab(tabs[idx - 1])
                        viewModel.refreshNotificationsOnViewChange()
                        true
                    } else {
                        false
                    }
                }
            )
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        )
        SubMenuHeader(
            items = OptionsTab.entries.map { it.label to it },
            selected = activeTab,
            onSelect = {
                viewModel.setActiveOptionsTab(it)
                viewModel.refreshNotificationsOnViewChange()
            }
        )

        SubDivider()

        when (activeTab) {
            OptionsTab.PROFILE -> ProfileFormView(
                user = uiState.currentUser,
                viewModel = viewModel
            )
            OptionsTab.ACCOUNT -> AccountFormView(
                viewModel = viewModel
            )
        }

        Spacer(modifier = Modifier.height(SubTheme.bottomSpacerHeight))
    }
}

/**
 * Matches `SubFormField` in `Macreply/Views/OptionsView.swift` (`label` + optional `*` + `whitesmoke` field).
 */
@Composable
fun SubFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String = "",
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    maxLength: Int? = null,
    isErrorBg: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSubmit: (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                color = SubTheme.primary
            )
            if (required) {
                Text(
                    text = "*",
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    color = SubTheme.gray
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TopFormCornerShape)
                .background(if (isErrorBg) SubTheme.redsmoke else SubTheme.whitesmoke)
                .padding(horizontal = TopFormInnerHorizontalPadding, vertical = TopFormInnerVerticalPadding),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = Route159FontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.silver
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    val cleaned = if (singleLine) next.replace("\n", " ") else next
                    if (maxLength == null || cleaned.length <= maxLength) {
                        onValueChange(cleaned)
                    }
                },
                singleLine = singleLine,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = TextStyle(
                    fontFamily = Route159FontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.primary
                ),
                cursorBrush = SolidColor(SubTheme.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                    imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSubmit?.invoke() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProfileFormView(
    user: SubUser?,
    viewModel: AppStateViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var username by remember(user) { mutableStateOf(user?.username ?: "") }
    var email by remember(user) { mutableStateOf(user?.email ?: "") }
    var firstName by remember(user) { mutableStateOf(user?.firstName ?: "") }
    var lastName by remember(user) { mutableStateOf(user?.lastName ?: "") }
    var emoji by remember(user) { mutableStateOf(user?.emoji ?: "") }
    var birthday by remember(user) { mutableStateOf(user?.birthday ?: "") }
    var location by remember(user) { mutableStateOf(user?.location ?: "") }
    var link by remember(user) { mutableStateOf(user?.link ?: "") }
    var description by remember(user) { mutableStateOf(user?.description ?: "") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(user, uiState.savedEmail) {
        if (user != null) {
            username = user.username
            email = user.email.ifEmpty { uiState.savedEmail ?: "" }
            firstName = user.firstName
            lastName = user.lastName
            emoji = user.emoji
            birthday = user.birthday
            location = user.location
            link = user.link
            description = user.description
        }
    }

    fun submitProfile() {
        if (!uiState.isAuthenticated || isSubmitting) return
        isSubmitting = true
        errorMessage = null
        statusMessage = null

        val payload = mapOf(
            "username" to username.trim(),
            "email" to email.trim(),
            "first_name" to firstName.trim(),
            "last_name" to lastName.trim(),
            "emoji" to emoji.trim(),
            "birthday" to birthday.trim(),
            "location" to location.trim(),
            "link" to link.trim(),
            "description" to description.trim()
        )

        scope.launch {
            try {
                val res = viewModel.api.updateProfile(payload = payload)
                val updated = res.user
                if (updated != null) {
                    viewModel.updateCurrentUser(updated, email = email.trim())
                    statusMessage = "Profile updated."
                } else {
                    errorMessage = "Failed to update profile."
                }
                isSubmitting = false
            } catch (err: Exception) {
                errorMessage = err.message ?: "Failed to update profile."
                isSubmitting = false
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TopFormOuterHorizontalPadding, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SubFormField(
                label = "Username",
                required = true,
                value = username,
                onValueChange = { username = it },
                maxLength = 15,
                modifier = Modifier.weight(1f)
            )
            SubFormField(
                label = "Email address",
                required = true,
                value = email,
                onValueChange = { email = it },
                keyboardType = KeyboardType.Email,
                maxLength = 120,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SubFormField(
                label = "First name",
                required = true,
                value = firstName,
                onValueChange = { firstName = it },
                maxLength = 15,
                modifier = Modifier.weight(1f)
            )
            SubFormField(
                label = "Last name",
                required = false,
                value = lastName,
                onValueChange = { lastName = it },
                maxLength = 15,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SubFormField(
                label = "Emoji",
                required = false,
                placeholder = ":)",
                value = emoji,
                onValueChange = { emoji = it },
                maxLength = 10,
                modifier = Modifier.width(64.dp)
            )
            SubFormField(
                label = "Birthday",
                required = false,
                placeholder = "Y-M-D",
                value = birthday,
                onValueChange = { birthday = it },
                maxLength = 10,
                modifier = Modifier.width(108.dp)
            )
            SubFormField(
                label = "Location",
                required = false,
                placeholder = "City, Country",
                value = location,
                onValueChange = { location = it },
                maxLength = 60,
                modifier = Modifier.weight(1f)
            )
        }

        SubFormField(
            label = "Link",
            required = false,
            placeholder = "https://",
            value = link,
            onValueChange = { link = it },
            keyboardType = KeyboardType.Uri,
            maxLength = 240,
            modifier = Modifier.fillMaxWidth()
        )

        SubFormField(
            label = "Description",
            required = false,
            placeholder = "Bio or short description",
            value = description,
            onValueChange = { description = it },
            singleLine = false,
            maxLength = 720,
            isErrorBg = description.length > 640,
            modifier = Modifier.fillMaxWidth()
        )

        if (!errorMessage.isNullOrEmpty()) {
            Text(
                text = errorMessage!!,
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = SubTheme.alert
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!statusMessage.isNullOrEmpty()) {
                Text(
                    text = statusMessage!!,
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.gray
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SubSubmitButton(
                title = if (isSubmitting) "Updating..." else "Update",
                enabled = !isSubmitting,
                onClick = { submitProfile() }
            )
        }
    }
}

@Composable
private fun AccountFormView(
    viewModel: AppStateViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var serverURL by remember(uiState.apiBaseUrl) { mutableStateOf(uiState.apiBaseUrl) }
    var serverSavedNotice by remember { mutableStateOf(false) }
    var pollingMinutesText by remember(uiState.notificationPollingMinutes) {
        mutableStateOf(uiState.notificationPollingMinutes.toString())
    }
    var pollingSavedNotice by remember { mutableStateOf(false) }

    fun savePollingMinutes() {
        val trimmed = pollingMinutesText.trim()
        val parsed = trimmed.toIntOrNull() ?: 5
        viewModel.updateNotificationPollingMinutes(parsed)
        pollingMinutesText = maxOf(1, parsed).toString()
        pollingSavedNotice = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TopFormOuterHorizontalPadding, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        SubFormField(
            label = "API Base URL",
            placeholder = "https://subreply.com/api",
            value = serverURL,
            onValueChange = {
                serverURL = it
                serverSavedNotice = false
            },
            keyboardType = KeyboardType.Uri,
            onSubmit = {
                viewModel.setApiBaseUrl(serverURL)
                serverSavedNotice = true
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (serverSavedNotice) {
                Text(
                    text = "Saved API endpoint.",
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.gray
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SubSubmitButton(
                title = "Save Endpoint",
                onClick = {
                    viewModel.setApiBaseUrl(serverURL)
                    serverSavedNotice = true
                }
            )
        }

        SubDivider()

        SubFormField(
            label = "Notifications polling (minutes)",
            placeholder = "5",
            value = pollingMinutesText,
            onValueChange = {
                pollingMinutesText = it
                pollingSavedNotice = false
            },
            keyboardType = KeyboardType.Number,
            onSubmit = { savePollingMinutes() }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pollingSavedNotice) {
                Text(
                    text = "Saved polling interval.",
                    fontFamily = Route159FontFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = SubTheme.gray
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            SubSubmitButton(
                title = "Save Interval",
                onClick = { savePollingMinutes() }
            )
        }

        SubDivider()

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "Theme",
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = SubTheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                for (mode in ThemeMode.entries) {
                    val isSelected = uiState.themeMode == mode
                    Text(
                        text = mode.label,
                        fontFamily = Route159FontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = if (isSelected) SubTheme.accent else SubTheme.gray,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.setThemeMode(mode)
                        }
                    )
                }
            }
        }

        if (uiState.isAuthenticated) {
            SubDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Signed in as @${uiState.currentUser?.username ?: ""}",
                        fontFamily = Route159FontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.primary
                    )
                    Text(
                        text = "Sign out and clear stored token.",
                        fontFamily = Route159FontFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.gray
                    )
                }
                SubSubmitButton(
                    title = "Logout",
                    onClick = { viewModel.logout() }
                )
            }
        }
    }
}


