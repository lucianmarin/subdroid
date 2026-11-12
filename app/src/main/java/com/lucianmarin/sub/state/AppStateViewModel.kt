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
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

data class AppUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val apiBaseUrl: String = APIClient.DEFAULT_BASE_URL,
    val notificationPollingMinutes: Int = SessionStorage.DEFAULT_POLLING_MINUTES,
    val authToken: String? = null,
    val currentUser: SubUser? = null,
    val selectedSection: SidebarSection = SidebarSection.TRENDING,
    val lastMessagesSubtab: SidebarSection = SidebarSection.MESSAGES,
    val lastTrendingSubtab: SidebarSection = SidebarSection.TRENDING,
    val navigationPath: List<NavigationDestination> = emptyList(),
    val activeConversationHasMessages: Boolean? = null,
    val activeConversationIsBlocked: Boolean = false,
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
    private var viewChangeNotificationJob: Job? = null

    init {
        NotificationHelper.createNotificationChannels(application)

        val token = storage.getAuthToken()
        val savedUser = storage.getSavedUser()
        val savedPollingMinutes = storage.getNotificationPollingMinutes()
        val savedMessagesSubtab = storage.getLastMessagesSubtab()
        val savedTrendingSubtab = storage.getLastTrendingSubtab()
        api.authToken = token
        val isAuthed = !token.isNullOrBlank() && savedUser != null
        val cachedNotifs = if (isAuthed) {
            storage.getSavedNotifications() ?: api.getCachedNotifications() ?: SubNotifications()
        } else {
            SubNotifications()
        }

        val initialSection = if (isAuthed) {
            SidebarSection.FEED
        } else {
            SidebarSection.TRENDING
        }

        _uiState.update {
            it.copy(
                notificationPollingMinutes = savedPollingMinutes,
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
            api.baseUrl = savedBaseUrl

            _uiState.update {
                it.copy(
                    themeMode = savedTheme,
                    apiBaseUrl = savedBaseUrl,
                    isInitialized = true
                )
            }

            if (isAuthed) {
                refreshCurrentUserProfile()
                startNotificationPolling()
                NotificationHelper.schedulePeriodicWork(getApplication(), savedPollingMinutes)
            }
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
        _uiState.update {
            it.copy(
                apiBaseUrl = normalized,
                reloadTrigger = it.reloadTrigger + 1
            )
        }
        viewModelScope.launch {
            storage.setApiBaseUrl(normalized)
            if (_uiState.value.isAuthenticated) {
                fetchAndApplyNotifications()
            }
        }
    }

    fun updateNotificationPollingMinutes(minutes: Int) {
        val clamped = maxOf(1, minutes)
        storage.setNotificationPollingMinutes(clamped)
        _uiState.update { it.copy(notificationPollingMinutes = clamped) }
        if (_uiState.value.isAuthenticated) {
            startNotificationPolling()
            NotificationHelper.schedulePeriodicWork(getApplication(), clamped)
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
        val cachedNotifs = storage.getSavedNotifications() ?: api.getCachedNotifications() ?: SubNotifications()
        _uiState.update {
            it.copy(
                authToken = token,
                currentUser = mergedUser,
                notifications = cachedNotifs,
                navigationPath = emptyList(),
                activeConversationHasMessages = null,
                activeConversationIsBlocked = false,
                selectedSection = SidebarSection.FEED
            )
        }
        persistNotifications(cachedNotifs)
        startNotificationPolling()
        NotificationHelper.schedulePeriodicWork(
            getApplication(),
            _uiState.value.notificationPollingMinutes
        )
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
        pollingJob?.cancel()
        pollingJob = null
        viewChangeNotificationJob?.cancel()
        viewChangeNotificationJob = null
        storage.clearCache()
        storage.setAuthToken(null)
        storage.setSavedUser(null)
        storage.setSavedNotifications(null)
        api.authToken = null
        NotificationHelper.cancelPeriodicWork(getApplication())

        _uiState.update {
            it.copy(
                authToken = null,
                currentUser = null,
                notifications = SubNotifications(),
                navigationPath = emptyList(),
                activeConversationHasMessages = null,
                activeConversationIsBlocked = false,
                selectedSection = SidebarSection.TRENDING
            )
        }
    }

    fun clearNotificationCount(section: SidebarSection) {
        viewChangeNotificationJob?.cancel()
        val current = _uiState.value.notifications
        val updated = when (section) {
            SidebarSection.FOLLOWERS -> current.copy(followers = 0)
            SidebarSection.MENTIONS -> current.copy(mentions = 0)
            SidebarSection.MESSAGES -> current.copy(messages = 0)
            SidebarSection.REPLIES -> current.copy(replies = 0)
            else -> return
        }
        _uiState.update { it.copy(notifications = updated) }
    }

    fun selectSection(section: SidebarSection) {
        val current = _uiState.value

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
                    activeConversationIsBlocked = false
                )
            }
        }
        refreshNotificationsOnViewChange()
    }

    fun openReply(id: Int) {
        var changed = false
        _uiState.update { state ->
            val currentTop = state.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.ReplyDetail && currentTop.id == id) {
                state
            } else {
                changed = true
                state.copy(
                    activeConversationHasMessages = null,
                    activeConversationIsBlocked = false,
                    navigationPath = state.navigationPath + NavigationDestination.ReplyDetail(id)
                )
            }
        }
        if (changed) refreshNotificationsOnViewChange()
    }

    fun openMember(username: String) {
        val clean = username.trimStart('@').trim().lowercase()
        if (clean.isEmpty()) return
        var changed = false
        _uiState.update { current ->
            val currentTop = current.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.Member && currentTop.username.equals(clean, ignoreCase = true)) {
                current
            } else {
                changed = true
                current.copy(
                    activeConversationHasMessages = null,
                    activeConversationIsBlocked = false,
                    navigationPath = current.navigationPath + NavigationDestination.Member(clean)
                )
            }
        }
        if (changed) refreshNotificationsOnViewChange()
    }

    fun openOptions() {
        var changed = false
        _uiState.update { current ->
            val currentTop = current.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.Options) {
                current
            } else {
                changed = true
                current.copy(
                    activeConversationHasMessages = null,
                    activeConversationIsBlocked = false,
                    navigationPath = current.navigationPath + NavigationDestination.Options
                )
            }
        }
        if (changed) refreshNotificationsOnViewChange()
    }

    fun openConversation(username: String) {
        val clean = username.trimStart('@').trim().lowercase()
        if (clean.isEmpty()) return
        var changed = false
        _uiState.update { current ->
            val currentTop = current.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.Conversation && currentTop.username.equals(clean, ignoreCase = true)) {
                current
            } else {
                changed = true
                current.copy(
                    activeConversationHasMessages = null,
                    activeConversationIsBlocked = false,
                    navigationPath = current.navigationPath + NavigationDestination.Conversation(clean)
                )
            }
        }
        if (changed) refreshNotificationsOnViewChange()
    }

    fun setActiveConversationHasMessages(hasMessages: Boolean?) {
        _uiState.update { it.copy(activeConversationHasMessages = hasMessages) }
    }

    fun setActiveConversationIsBlocked(isBlocked: Boolean) {
        _uiState.update { it.copy(activeConversationIsBlocked = isBlocked) }
    }

    fun openEditEntry(entry: SubEntry) {
        var changed = false
        _uiState.update { current ->
            val currentTop = current.navigationPath.lastOrNull()
            if (currentTop is NavigationDestination.EditEntry && currentTop.entry.id == entry.id) {
                current
            } else {
                changed = true
                current.copy(
                    activeConversationHasMessages = null,
                    activeConversationIsBlocked = false,
                    navigationPath = current.navigationPath + NavigationDestination.EditEntry(entry)
                )
            }
        }
        if (changed) refreshNotificationsOnViewChange()
    }

    fun openDiscoverSearch(query: String) {
        _uiState.update {
            it.copy(
                navigationPath = emptyList(),
                activeConversationHasMessages = null,
                activeConversationIsBlocked = false,
                selectedSection = SidebarSection.DISCOVER,
                searchRequestedQuery = query
            )
        }
        refreshNotificationsOnViewChange()
    }

    fun openPeopleSearch(query: String) {
        _uiState.update {
            it.copy(
                navigationPath = emptyList(),
                activeConversationHasMessages = null,
                activeConversationIsBlocked = false,
                selectedSection = SidebarSection.PEOPLE,
                searchRequestedQuery = query
            )
        }
        refreshNotificationsOnViewChange()
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
                activeConversationHasMessages = if (topIsConv) it.activeConversationHasMessages else null,
                activeConversationIsBlocked = if (topIsConv) it.activeConversationIsBlocked else false
            )
        }
        refreshNotificationsOnViewChange()
        return true
    }

    fun refreshNotificationsOnViewChange() {
        if (!_uiState.value.isAuthenticated) return
        viewChangeNotificationJob?.cancel()
        viewChangeNotificationJob = viewModelScope.launch {
            fetchAndApplyNotifications()
        }
    }

    fun refreshNotifications() {
        if (!_uiState.value.isAuthenticated) return
        viewModelScope.launch {
            fetchAndApplyNotifications()
        }
    }

    private suspend fun fetchAndApplyNotifications() {
        if (!_uiState.value.isAuthenticated) return
        try {
            val latest = NotificationHelper.pollAndDeliverNotifications(
                context = getApplication(),
                api = api,
                storage = storage
            )
            if (_uiState.value.isAuthenticated) {
                _uiState.update { it.copy(notifications = latest) }
            }
        } catch (e: APIException) {
            if (e.statusCode == 401) {
                logout()
            }
        } catch (_: Exception) {
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

    private fun persistNotifications(notifications: SubNotifications) {
        if (_uiState.value.isAuthenticated) {
            storage.setSavedNotifications(notifications)
        } else {
            storage.setSavedNotifications(null)
        }
    }

    /**
     * Background polling loop on `/api/notifications` matching `startNotificationPolling()`
     * in `../macreply/Sources/Macreply/State/AppState.swift`.
     */
    private fun startNotificationPolling() {
        pollingJob?.cancel()
        if (!_uiState.value.isAuthenticated) return
        pollingJob = viewModelScope.launch {
            while (isActive && _uiState.value.isAuthenticated) {
                fetchAndApplyNotifications()
                val intervalMs = maxOf(1, _uiState.value.notificationPollingMinutes) * 60_000L
                delay(intervalMs)
            }
        }
    }

    fun handleDeepLink(intent: Intent?) {
        if (intent == null) return

        val destExtra = intent.getStringExtra(NotificationHelper.EXTRA_DESTINATION)
        if (!destExtra.isNullOrBlank()) {
            intent.removeExtra(NotificationHelper.EXTRA_DESTINATION)
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
    const val NOTIF_ID_REPLIES = 1001
    const val NOTIF_ID_MESSAGES = 1002
    const val NOTIF_ID_FOLLOWERS = 1003
    const val NOTIF_ID_MENTIONS = 1004
    private const val WORK_NAME = "subreply_notification_poll"
    private const val ONE_TIME_WORK_NAME = "subreply_notification_poll_onetime"
    private val pollMutex = Mutex()

    /**
     * Fetches `/api/notifications`, diffs strictly against the previous `/api/notifications`
     * value stored in [SessionStorage.getSavedNotifications], persists the latest response,
     * and delivers notifications only for categories whose server count increased.
     */
    suspend fun pollAndDeliverNotifications(
        context: Context,
        api: APIClient,
        storage: SessionStorage
    ): SubNotifications = pollMutex.withLock {
        val previous = storage.getSavedNotifications() ?: SubNotifications()
        val latest = api.fetchNotifications()
        val alerts = latest.newNotifications(since = previous)
        storage.setSavedNotifications(latest)
        for (alert in alerts) {
            sendSectionNotification(
                context = context,
                section = alert.section,
                message = alert.message
            )
        }
        latest
    }

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

    fun sendSectionNotification(
        context: Context,
        section: SidebarSection,
        message: String
    ) {
        val (channelId, notificationId) = when (section) {
            SidebarSection.MESSAGES -> CHANNEL_MESSAGES to NOTIF_ID_MESSAGES
            SidebarSection.REPLIES -> CHANNEL_ACTIVITY to NOTIF_ID_REPLIES
            SidebarSection.FOLLOWERS -> CHANNEL_ACTIVITY to NOTIF_ID_FOLLOWERS
            SidebarSection.MENTIONS -> CHANNEL_ACTIVITY to NOTIF_ID_MENTIONS
            else -> CHANNEL_ACTIVITY to NOTIF_ID_REPLIES
        }
        sendLocalNotification(
            context = context,
            channelId = channelId,
            notificationId = notificationId,
            title = "Subreply",
            body = message,
            destination = section.key
        )
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

    fun schedulePeriodicWork(
        context: Context,
        intervalMinutes: Int = SessionStorage.DEFAULT_POLLING_MINUTES
    ) {
        val clampedMinutes = maxOf(1, intervalMinutes).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<NotificationPollWorker>(
            maxOf(15L, clampedMinutes),
            TimeUnit.MINUTES
        )
            .setInitialDelay(clampedMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        if (clampedMinutes < 15L) {
            scheduleNextOneTimePoll(context, clampedMinutes)
        } else {
            workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        }
    }

    fun scheduleNextOneTimePoll(context: Context, delayMinutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<NotificationPollWorker>()
            .setInitialDelay(maxOf(1L, delayMinutes), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }

    fun cancelPeriodicWork(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
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
            NotificationHelper.pollAndDeliverNotifications(
                context = applicationContext,
                api = api,
                storage = storage
            )

            val pollingMinutes = storage.getNotificationPollingMinutes().toLong()
            if (pollingMinutes < 15L) {
                NotificationHelper.scheduleNextOneTimePoll(applicationContext, pollingMinutes)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

