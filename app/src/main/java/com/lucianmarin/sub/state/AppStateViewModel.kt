package com.lucianmarin.sub.state

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lucianmarin.sub.MainActivity
import com.lucianmarin.sub.R
import com.lucianmarin.sub.model.NavigationDestination
import com.lucianmarin.sub.model.SidebarSection
import com.lucianmarin.sub.model.SubEntry
import com.lucianmarin.sub.model.SubNotifications
import com.lucianmarin.sub.model.SubUser
import com.lucianmarin.sub.network.APIClient
import com.lucianmarin.sub.network.APIException
import com.lucianmarin.sub.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val apiBaseUrl: String = APIClient.DEFAULT_BASE_URL,
    val authToken: String? = null,
    val currentUser: SubUser? = null,
    val selectedSection: SidebarSection = SidebarSection.TRENDING,
    val lastMessagesSubtab: SidebarSection = SidebarSection.MESSAGES,
    val lastTrendingSubtab: SidebarSection = SidebarSection.TRENDING,
    val navigationPath: List<NavigationDestination> = emptyList(),
    val activeConversationHasMessages: Boolean? = null,
    val notifications: SubNotifications = SubNotifications(),
    val searchRequestedQuery: String? = null,
    val reloadTrigger: Int = 0,
    val isInitialized: Boolean = false
) {
    val isAuthenticated: Boolean
        get() = !authToken.isNullOrBlank() && currentUser != null

    val savedEmail: String?
        get() = currentUser?.email?.takeIf { it.isNotEmpty() }


    val visibleSidebarSections: List<SidebarSection>
        get() = if (isAuthenticated) {
            SidebarSection.authenticatedSections
        } else {
            SidebarSection.unauthenticatedSections
        }

    fun targetSectionForBottomNav(section: SidebarSection): SidebarSection = when (section) {
        SidebarSection.MESSAGES -> if (isAuthenticated && lastMessagesSubtab in SidebarSection.messagesSubtabs) {
            lastMessagesSubtab
        } else {
            SidebarSection.MESSAGES
        }
        SidebarSection.TRENDING -> if (isAuthenticated && lastTrendingSubtab in SidebarSection.trendingSubtabs) {
            lastTrendingSubtab
        } else {
            SidebarSection.TRENDING
        }
        else -> section
    }

    fun badgeCount(forSection: SidebarSection): Int = when (forSection) {
        SidebarSection.FOLLOWERS -> notifications.followers
        SidebarSection.MENTIONS -> notifications.mentions
        SidebarSection.MESSAGES -> notifications.messages
        SidebarSection.REPLIES -> notifications.replies
        else -> 0
    }

    fun bottomNavBadgeCount(forSection: SidebarSection): Int = when (forSection) {
        SidebarSection.MESSAGES -> notifications.messages + notifications.replies + notifications.mentions
        SidebarSection.TRENDING -> notifications.followers
        else -> badgeCount(forSection)
    }
}

class AppStateViewModel(application: Application) : AndroidViewModel(application) {

    val storage = SessionStorage(application)
    val api = APIClient(cacheStore = storage)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var lastKnownActivity: Int = 0
    private var lastKnownMessages: Int = 0
    private var hasCompletedInitialPoll: Boolean = false

    init {
        NotificationHelper.createNotificationChannels(application)

        val token = storage.getAuthToken()
        val savedUser = storage.getSavedUser()
        val savedMessagesSubtab = storage.getLastMessagesSubtab()
        val savedTrendingSubtab = storage.getLastTrendingSubtab()
        api.authToken = token
        val cachedNotifs = api.getCachedNotifications() ?: SubNotifications()

        val initialSection = if (!token.isNullOrBlank() && savedUser != null) {
            SidebarSection.FEED
        } else {
            SidebarSection.TRENDING
        }

        _uiState.update {
            it.copy(
                authToken = token,
                currentUser = savedUser,
                notifications = cachedNotifs,
                selectedSection = initialSection,
                lastMessagesSubtab = savedMessagesSubtab,
                lastTrendingSubtab = savedTrendingSubtab
            )
        }

        viewModelScope.launch {
            val savedTheme = storage.getThemeMode()
            val savedBaseUrl = storage.getApiBaseUrl()
            lastKnownActivity = storage.getLastUnreadNotifications()
            lastKnownMessages = storage.getLastUnreadMessages()
            api.baseUrl = savedBaseUrl

            _uiState.update {
                it.copy(
                    themeMode = savedTheme,
                    apiBaseUrl = savedBaseUrl,
                    isInitialized = true
                )
            }

            if (!token.isNullOrBlank() && savedUser != null) {
                refreshNotifications()
                refreshCurrentUserProfile()
                NotificationHelper.schedulePeriodicWork(getApplication())
            }

            startNotificationPolling()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            storage.setThemeMode(mode)
        }
    }

    fun setApiBaseUrl(url: String) {
        val normalized = APIClient.normalizeBaseUrl(url)
        api.baseUrl = normalized
        _uiState.update { it.copy(apiBaseUrl = normalized) }
        viewModelScope.launch {
            storage.setApiBaseUrl(normalized)
        }
    }


    fun applyAuth(token: String, user: SubUser, email: String? = null) {
        val mergedUser = if (!email.isNullOrBlank() && user.email.isBlank()) {
            user.copy(email = email)
        } else {
            user
        }
        storage.setAuthToken(token)
        storage.setSavedUser(mergedUser)
        api.authToken = token
        val cachedNotifs = api.getCachedNotifications() ?: SubNotifications()
        _uiState.update {
            it.copy(
                authToken = token,
                currentUser = mergedUser,
                notifications = cachedNotifs,
                navigationPath = emptyList(),
                activeConversationHasMessages = null,
                selectedSection = SidebarSection.FEED
            )
        }
        NotificationHelper.schedulePeriodicWork(getApplication())
        refreshNotifications()
    }

    fun updateCurrentUser(user: SubUser, email: String? = null) {
        val existingEmail = email?.takeIf { it.isNotBlank() } ?: _uiState.value.currentUser?.email
        val merged = if (user.email.isBlank() && !existingEmail.isNullOrBlank()) {
            user.copy(email = existingEmail)
        } else if (!email.isNullOrBlank()) {
            user.copy(email = email)
        } else {
            user
        }
        storage.setSavedUser(merged)
        _uiState.update { it.copy(currentUser = merged) }
    }


    fun logout() {
        storage.clearCache()
        storage.setAuthToken(null)
        storage.setSavedUser(null)
        api.authToken = null
        lastKnownActivity = 0
        lastKnownMessages = 0
        hasCompletedInitialPoll = false
        viewModelScope.launch {
            storage.saveLastUnreadCounts(0, 0)
        }
        NotificationHelper.cancelPeriodicWork(getApplication())

        _uiState.update {
            it.copy(
                authToken = null,
                currentUser = null,
                notifications = SubNotifications(),
                navigationPath = emptyList(),
                activeConversationHasMessages = null,
                selectedSection = SidebarSection.TRENDING
            )
        }
    }

    fun selectSection(section: SidebarSection) {
        val current = _uiState.value
        val updatedNotifs = when (section) {
            SidebarSection.FOLLOWERS -> current.notifications.copy(followers = 0)
            SidebarSection.MENTIONS -> current.notifications.copy(mentions = 0)
            SidebarSection.MESSAGES -> current.notifications.copy(messages = 0)
            SidebarSection.REPLIES -> current.notifications.copy(replies = 0)
            else -> current.notifications
        }

        val updatedMessagesSubtab = if (section in SidebarSection.messagesSubtabs) {
            storage.setLastMessagesSubtab(section)
            section
        } else {
            current.lastMessagesSubtab
        }

        val updatedTrendingSubtab = if (section in SidebarSection.trendingSubtabs) {
            storage.setLastTrendingSubtab(section)
            section
        } else {
            current.lastTrendingSubtab
        }

        if (current.selectedSection == section && current.navigationPath.isEmpty()) {
            _uiState.update {
                it.copy(
                    lastMessagesSubtab = updatedMessagesSubtab,
                    lastTrendingSubtab = updatedTrendingSubtab,
                    notifications = updatedNotifs,
                    reloadTrigger = it.reloadTrigger + 1
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    selectedSection = section,
                    lastMessagesSubtab = updatedMessagesSubtab,
                    lastTrendingSubtab = updatedTrendingSubtab,
                    navigationPath = emptyList(),
                    activeConversationHasMessages = null,
                    notifications = updatedNotifs
                )
            }
        }
        refreshNotifications()
    }

    fun openReply(id: Int) {
        _uiState.update { state ->
            val currentTop = state.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.ReplyDetail && currentTop.id == id) {
                state
            } else {
                state.copy(
                    activeConversationHasMessages = null,
                    navigationPath = state.navigationPath + NavigationDestination.ReplyDetail(id)
                )
            }
        }
    }

    fun openMember(username: String) {
        val clean = username.trimStart('@').trim().lowercase()
        if (clean.isEmpty()) return
        _uiState.update { current ->
            val currentTop = current.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.Member && currentTop.username.equals(clean, ignoreCase = true)) {
                current
            } else {
                current.copy(
                    activeConversationHasMessages = null,
                    navigationPath = current.navigationPath + NavigationDestination.Member(clean)
                )
            }
        }
    }

    fun openOptions() {
        _uiState.update { current ->
            val currentTop = current.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.Options) {
                current
            } else {
                current.copy(
                    activeConversationHasMessages = null,
                    navigationPath = current.navigationPath + NavigationDestination.Options
                )
            }
        }
    }

    fun openConversation(username: String) {
        val clean = username.trimStart('@').trim().lowercase()
        if (clean.isEmpty()) return
        _uiState.update { current ->
            val currentTop = current.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.Conversation && currentTop.username.equals(clean, ignoreCase = true)) {
                current
            } else {
                current.copy(
                    activeConversationHasMessages = null,
                    navigationPath = current.navigationPath + NavigationDestination.Conversation(clean)
                )
            }
        }
    }

    fun setActiveConversationHasMessages(hasMessages: Boolean?) {
        _uiState.update { it.copy(activeConversationHasMessages = hasMessages) }
    }

    fun openEditEntry(entry: SubEntry) {
        _uiState.update { current ->
            val currentTop = current.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.EditEntry && currentTop.entry.id == entry.id) {
                current
            } else {
                current.copy(
                    activeConversationHasMessages = null,
                    navigationPath = current.navigationPath + NavigationDestination.EditEntry(entry)
                )
            }
        }
    }

    fun openDiscoverSearch(query: String) {
        _uiState.update {
            it.copy(
                navigationPath = emptyList(),
                activeConversationHasMessages = null,
                selectedSection = SidebarSection.DISCOVER,
                searchRequestedQuery = query
            )
        }
    }

    fun openPeopleSearch(query: String) {
        _uiState.update {
            it.copy(
                navigationPath = emptyList(),
                activeConversationHasMessages = null,
                selectedSection = SidebarSection.PEOPLE,
                searchRequestedQuery = query
            )
        }
    }

    fun consumeSearchRequestedQuery() {
        _uiState.update { it.copy(searchRequestedQuery = null) }
    }

    fun popNavigation(): Boolean {
        val path = _uiState.value.navigationPath
        if (path.isEmpty()) return false
        val newPath = path.dropLast(1)
        val topIsConv = newPath.lastOrNull() is NavigationDestination.Conversation
        _uiState.update {
            it.copy(
                navigationPath = newPath,
                activeConversationHasMessages = if (topIsConv) it.activeConversationHasMessages else null
            )
        }
        return true
    }

    fun refreshNotifications() {
        if (!_uiState.value.isAuthenticated) return
        viewModelScope.launch {
            try {
                val notifs = api.fetchNotifications()
                handleFetchedNotifications(notifs)
            } catch (e: APIException) {
                if (e.statusCode == 401) {
                    logout()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun refreshCurrentUserProfile() {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
                val member = api.fetchMemberDetail(username = user.username)
                val updated = member.copy(
                    email = if (member.email.isEmpty()) user.email else member.email,
                    phone = if (member.phone.isEmpty()) user.phone else member.phone,
                    social = if (member.social.isEmpty()) user.social else member.social
                )
                updateCurrentUser(updated)
            } catch (e: APIException) {
                if (e.statusCode == 401) {
                    logout()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun handleFetchedNotifications(notifs: SubNotifications) {
        val newActivity = notifs.activityCount
        val newMessages = notifs.messages

        if (hasCompletedInitialPoll && _uiState.value.isAuthenticated) {
            if (newActivity > lastKnownActivity) {
                val delta = newActivity - lastKnownActivity
                NotificationHelper.sendLocalNotification(
                    context = getApplication(),
                    channelId = NotificationHelper.CHANNEL_ACTIVITY,
                    notificationId = NotificationHelper.NOTIF_ID_ACTIVITY,
                    title = "Subreply Activity",
                    body = if (delta == 1) {
                        "You have 1 new notification."
                    } else {
                        "You have $delta new notifications."
                    },
                    destination = "replies"
                )
            }
            if (newMessages > lastKnownMessages) {
                val delta = newMessages - lastKnownMessages
                NotificationHelper.sendLocalNotification(
                    context = getApplication(),
                    channelId = NotificationHelper.CHANNEL_MESSAGES,
                    notificationId = NotificationHelper.NOTIF_ID_MESSAGES,
                    title = "Subreply Messages",
                    body = if (delta == 1) {
                        "You received a new direct message."
                    } else {
                        "You have $delta unread direct messages."
                    },
                    destination = "messages"
                )
            }
        }

        lastKnownActivity = newActivity
        lastKnownMessages = newMessages
        hasCompletedInitialPoll = true
        viewModelScope.launch {
            storage.saveLastUnreadCounts(newActivity, newMessages)
        }

        _uiState.update { it.copy(notifications = notifs) }
    }

    private fun startNotificationPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                if (_uiState.value.isAuthenticated) {
                    try {
                        val notifs = api.fetchNotifications()
                        handleFetchedNotifications(notifs)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun handleDeepLink(intent: Intent?) {
        if (intent == null) return

        val destExtra = intent.getStringExtra(NotificationHelper.EXTRA_DESTINATION)
        if (!destExtra.isNullOrBlank()) {
            SidebarSection.fromKey(destExtra)?.let { selectSection(it) }
            return
        }

        val data: Uri = intent.data ?: return
        val path = data.path?.trim('/') ?: return
        if (path.isEmpty()) return

        val segments = path.split('/')
        val first = segments.first()

        if (first.equals("reply", ignoreCase = true) && segments.size >= 2) {
            segments[1].toIntOrNull()?.let { replyId ->
                openReply(replyId)
                return
            }
        }
        first.toIntOrNull()?.let { replyId ->
            openReply(replyId)
            return
        }

        SidebarSection.fromKey(first)?.let { section ->
            if (!section.requiresAuth || _uiState.value.isAuthenticated) {
                selectSection(section)
                return
            }
        }

        val candidateUser = first.trimStart('@')
        if (candidateUser.isNotEmpty()) {
            openMember(candidateUser)
        }
    }
}

object NotificationHelper {
    const val CHANNEL_ACTIVITY = "subreply_activity"
    const val CHANNEL_MESSAGES = "subreply_messages"
    const val EXTRA_DESTINATION = "subreply_destination"
    const val NOTIF_ID_ACTIVITY = 1001
    const val NOTIF_ID_MESSAGES = 1002
    private const val WORK_NAME = "subreply_notification_poll"

    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val activityChannel = NotificationChannel(
            CHANNEL_ACTIVITY,
            "Mentions & Replies",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for new followers, mentions, and replies on Subreply"
        }

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Direct Messages",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for new direct messages on Subreply"
        }

        manager.createNotificationChannel(activityChannel)
        manager.createNotificationChannel(messagesChannel)
    }

    fun sendLocalNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        destination: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DESTINATION, destination)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    fun schedulePeriodicWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelPeriodicWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class NotificationPollWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val storage = SessionStorage(applicationContext)
        val token = storage.getAuthToken() ?: return Result.success()
        val baseUrl = storage.getApiBaseUrl()
        val api = APIClient(
            baseUrl = baseUrl,
            authToken = token,
            cacheStore = storage
        )

        return try {
            val notifs = api.fetchNotifications()
            val prevNotifs = storage.getLastUnreadNotifications()
            val prevMsgs = storage.getLastUnreadMessages()
            val newNotifs = notifs.activityCount
            val newMsgs = notifs.messages

            if (newNotifs > prevNotifs) {
                val delta = newNotifs - prevNotifs
                NotificationHelper.sendLocalNotification(
                    context = applicationContext,
                    channelId = NotificationHelper.CHANNEL_ACTIVITY,
                    notificationId = NotificationHelper.NOTIF_ID_ACTIVITY,
                    title = "Subreply Activity",
                    body = if (delta == 1) {
                        "You have 1 new notification."
                    } else {
                        "You have $delta new notifications."
                    },
                    destination = "replies"
                )
            }
            if (newMsgs > prevMsgs) {
                val delta = newMsgs - prevMsgs
                NotificationHelper.sendLocalNotification(
                    context = applicationContext,
                    channelId = NotificationHelper.CHANNEL_MESSAGES,
                    notificationId = NotificationHelper.NOTIF_ID_MESSAGES,
                    title = "Subreply Messages",
                    body = if (delta == 1) {
                        "You received a new direct message."
                    } else {
                        "You have $delta unread direct messages."
                    },
                    destination = "messages"
                )
            }

            storage.saveLastUnreadCounts(newNotifs, newMsgs)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

