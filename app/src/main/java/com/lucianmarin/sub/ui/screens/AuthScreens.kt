package com.lucianmarin.sub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.state.AppStateViewModel
import com.lucianmarin.sub.ui.theme.Route159FontFamily
import com.lucianmarin.sub.ui.theme.SubDivider
import com.lucianmarin.sub.ui.theme.SubMenuHeader
import com.lucianmarin.sub.ui.theme.SubSubmitButton
import com.lucianmarin.sub.ui.theme.SubTheme
import kotlinx.coroutines.launch

enum class LoginTab(val label: String) {
    LOGIN("Login"),
    REGISTER("Register")
}

/**
 * Matches `templates/pages/login.html`, `templates/pages/register.html`,
 * and `Macreply/Views/AuthViews.swift` (`LoginView` / `RegisterView`) with grouped `Login · Register` subtabs.
 */
@Composable
fun LoginScreen(
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var activeTab by remember {
        mutableStateOf(
            if (uiState.selectedSection == SidebarSection.REGISTER) {
                LoginTab.REGISTER
            } else {
                LoginTab.LOGIN
            }
        )
    }

    LaunchedEffect(uiState.selectedSection) {
        when (uiState.selectedSection) {
            SidebarSection.REGISTER -> activeTab = LoginTab.REGISTER
            SidebarSection.LOGIN -> activeTab = LoginTab.LOGIN
            else -> {}
        }
    }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submitLogin() {
        if (isSubmitting) return
        val u = username.trim()
        val p = password
        if (u.isEmpty() || p.isEmpty()) {
            errorMessage = "Please enter your username and password."
            return
        }
        isSubmitting = true
        errorMessage = null

        scope.launch {
            try {
                val auth = viewModel.api.login(username = u, password = p)
                val token = auth.token
                val user = auth.user
                if (!token.isNullOrEmpty() && user != null) {
                    viewModel.applyAuth(
                        token = token,
                        user = user,
                        email = if (u.contains("@")) u else user.email.takeIf { it.isNotEmpty() }
                    )
                } else {
                    errorMessage = "Invalid credentials."
                }
                isSubmitting = false
            } catch (err: Exception) {
                errorMessage = err.message ?: "Invalid credentials."
                isSubmitting = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SubTheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        )
        SubMenuHeader(
            items = LoginTab.entries.map { it.label to it },
            selected = activeTab,
            onSelect = { tab ->
                activeTab = tab
                errorMessage = null
                when (tab) {
                    LoginTab.LOGIN -> if (uiState.selectedSection != SidebarSection.LOGIN) {
                        viewModel.selectSection(SidebarSection.LOGIN)
                    }
                    LoginTab.REGISTER -> if (uiState.selectedSection != SidebarSection.REGISTER) {
                        viewModel.selectSection(SidebarSection.REGISTER)
                    }
                }
            }
        )

        SubDivider()

        when (activeTab) {
            LoginTab.LOGIN -> {
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
                            label = "Username or email",
                            required = true,
                            value = username,
                            onValueChange = { username = it },
                            onSubmit = { submitLogin() },
                            modifier = Modifier.weight(1f)
                        )
                        SubFormField(
                            label = "Password",
                            required = true,
                            isPassword = true,
                            value = password,
                            onValueChange = { password = it },
                            onSubmit = { submitLogin() },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!errorMessage.isNullOrEmpty()) {
                        Text(
                            text = errorMessage!!,
                            fontFamily = Route159FontFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = SubTheme.alert
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.weight(1f))
                        SubSubmitButton(
                            title = if (isSubmitting) "Signing in..." else "Login",
                            enabled = !isSubmitting,
                            onClick = { submitLogin() }
                        )
                    }
                }
            }
            LoginTab.REGISTER -> {
                RegisterFormContent(viewModel = viewModel)
            }
        }

        Spacer(modifier = Modifier.height(SubTheme.bottomSpacerHeight))
    }
}

@Composable
private fun RegisterFormContent(
    viewModel: AppStateViewModel
) {
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password1 by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submitRegistration() {
        if (isSubmitting) return
        if (username.isBlank() || email.isBlank() || password1.isBlank() || firstName.isBlank()) {
            errorMessage = "Please fill in all required fields."
            return
        }
        if (password1 != password2) {
            errorMessage = "Passwords do not match."
            return
        }
        isSubmitting = true
        errorMessage = null

        val payload = mapOf(
            "username" to username.trim(),
            "email" to email.trim(),
            "password1" to password1,
            "password2" to password2,
            "first_name" to firstName.trim(),
            "last_name" to lastName.trim(),
            "emoji" to emoji.trim(),
            "birthday" to birthday.trim(),
            "location" to location.trim()
        )

        scope.launch {
            try {
                val auth = viewModel.api.register(payload = payload)
                val token = auth.token
                val user = auth.user
                if (!token.isNullOrEmpty() && user != null) {
                    viewModel.applyAuth(
                        token = token,
                        user = user,
                        email = email.trim()
                    )
                } else {
                    errorMessage = "Registration failed."
                }
                isSubmitting = false
            } catch (err: Exception) {
                errorMessage = err.message ?: "Registration failed."
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
                label = "Password",
                required = true,
                isPassword = true,
                value = password1,
                onValueChange = { password1 = it },
                maxLength = 120,
                modifier = Modifier.weight(1f)
            )
            SubFormField(
                label = "Password confirmation",
                required = true,
                isPassword = true,
                value = password2,
                onValueChange = { password2 = it },
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
            Text(
                text = "You agree with Privacy, Safety and Terms.",
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = SubTheme.gray,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.selectSection(SidebarSection.ABOUT)
                    }
            )
            SubSubmitButton(
                title = if (isSubmitting) "Registering..." else "Register",
                enabled = !isSubmitting,
                onClick = { submitRegistration() }
            )
        }
    }
}

private enum class AboutTab(val label: String) {
    ABOUT("About"),
    PRIVACY("Privacy"),
    SAFETY("Safety"),
    TERMS("Terms")
}

/**
 * Matches `templates/pages/about.html`, `privacy.html`, `safety.html`, `terms.html`,
 * and `Macreply/Views/AuthViews.swift` (`AboutView`) 1:1.
 */
@Composable
fun AboutScreen(
    viewModel: AppStateViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var activeTab by remember { mutableStateOf(AboutTab.ABOUT) }
    var serverURL by remember(uiState.apiBaseUrl) { mutableStateOf(uiState.apiBaseUrl) }
    var savedNotice by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SubTheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        )
        SubMenuHeader(
            items = AboutTab.entries.map { it.label to it },
            selected = activeTab,
            onSelect = { activeTab = it }
        )

        SubDivider()

        when (activeTab) {
            AboutTab.ABOUT -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TopFormOuterHorizontalPadding, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Text(
                        text = "Welcome to Subdroid, the native Android client for Subreply. It eliminates distractions and clutter, allowing you to focus on meaningful conversations and content sharing directly from a native sidebar and native feeds.",
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.primary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Features and limitations",
                            fontFamily = Route159FontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = SubTheme.primary
                        )
                        AboutRuleRow(1, "Reverse-chronological order for all feeds")
                        AboutRuleRow(2, "Unique name handles with emoji avatars")
                        AboutRuleRow(3, "Unique conversation view similar to a chat interface")
                        AboutRuleRow(4, "Light and dark themes based on system preference")
                        AboutRuleRow(5, "Supports links, @mentions, #id hashrefs and #hashtags")
                        AboutRuleRow(6, "Limited to ASCII characters and emojis")
                        AboutRuleRow(7, "Limited to 640 characters per reply")
                        AboutRuleRow(8, "Limited to one link, mention, hashref or hashtag per reply")
                        AboutRuleRow(9, "No support for paragraphs, keeps conversation tidy")
                        AboutRuleRow(10, "No popularity counters or instant gratification")
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Keeping things private",
                            fontFamily = Route159FontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = SubTheme.primary
                        )
                        AboutRuleRow(1, "Edit or delete your replies before they are replied to")
                        AboutRuleRow(2, "Delete received replies before they are replied to")
                        AboutRuleRow(3, "No redistribution of any data, private or public based on GDPR")
                    }

                    SubDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubFormField(
                            label = "API Server URL",
                            placeholder = "https://subreply.com/api",
                            value = serverURL,
                            onValueChange = {
                                serverURL = it
                                savedNotice = false
                            },
                            keyboardType = KeyboardType.Uri,
                            onSubmit = {
                                viewModel.setApiBaseUrl(serverURL)
                                savedNotice = true
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (savedNotice) {
                                Text(
                                    text = "Updated API Server URL.",
                                    fontFamily = Route159FontFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = SubTheme.gray
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            SubSubmitButton(
                                title = "Save Server",
                                onClick = {
                                    viewModel.setApiBaseUrl(serverURL)
                                    savedNotice = true
                                }
                            )
                        }
                    }
                }
            }
            AboutTab.PRIVACY -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TopFormOuterHorizontalPadding, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Read this page to learn about personal information that Subreply collects and how it may be used.",
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.primary
                    )
                    AboutSectionHeading("Links to third party web sites")
                    Text(
                        text = "Our service may contain links to other websites and software. We are not responsible for the privacy practices or the content of these websites or software.",
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.primary
                    )
                    AboutSectionHeading("Your privacy")
                    AboutRuleRow(1, "Obscene or inflammatory content is subject to removal.")
                    AboutRuleRow(2, "No soapboxing, or attempts to promote or defame a person or entity.")
                    AboutRuleRow(3, "Posting or seeking Personal Info or Private Info will result in a ban without prior warning.")
                    AboutRuleRow(4, "Activities and content are subject to local laws and authorities.")
                    AboutRuleRow(5, "Minimum age for people using the service is 16 years old.")
                }
            }
            AboutTab.SAFETY -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TopFormOuterHorizontalPadding, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Subreply is designed to be safe by stripping away media uploads, file sharing, algorithmic amplification and dark patterns.",
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.primary
                    )
                    AboutSectionHeading("Safe by design")
                    AboutRuleRow(1, "Text-only architecture — no images, videos, audio or file attachments.")
                    AboutRuleRow(2, "Strict character limits — ASCII characters, standard emojis and a maximum of 640 characters.")
                    AboutRuleRow(3, "Intentional linking — at most one external link, mention or hashtag per post.")
                    AboutRuleRow(4, "No algorithmic rabbit holes — feeds are strictly reverse-chronological.")
                    AboutRuleRow(5, "Zero tolerance for CSAE and CSAM.")
                }
            }
            AboutTab.TERMS -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TopFormOuterHorizontalPadding, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "By accessing Subreply or using the services offered by Subreply you agree and acknowledge to be bound by these Terms of Service.",
                        fontFamily = Route159FontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = SubTheme.primary
                    )
                    AboutSectionHeading("Prohibited Uses")
                    AboutRuleRow(1, "Do not harass, threaten, embarrass or cause distress upon any other person.")
                    AboutRuleRow(2, "Do not transmit unlawful, harmful, threatening, abusive, or defamatory material.")
                    AboutRuleRow(3, "Do not impersonate any person or utilize a false identity.")
                    AboutRuleRow(4, "Do not post unsolicited advertising, promotional materials, or spam.")
                    AboutRuleRow(5, "Subreply provides the site and services \"AS IS\" without warranty of any kind.")
                }
            }
        }

        Spacer(modifier = Modifier.height(SubTheme.bottomSpacerHeight))
    }
}

@Composable
private fun AboutSectionHeading(title: String) {
    Text(
        text = title,
        fontFamily = Route159FontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = SubTheme.primary,
        modifier = Modifier.padding(top = 5.dp)
    )
}

@Composable
private fun AboutRuleRow(number: Int, text: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "$number.",
                fontFamily = Route159FontFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = SubTheme.gray,
                modifier = Modifier.width(22.dp)
            )
            Text(
                text = text,
                fontFamily = Route159FontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = SubTheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        SubDivider()
    }
}

