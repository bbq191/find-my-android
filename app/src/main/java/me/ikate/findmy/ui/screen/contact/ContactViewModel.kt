package me.ikate.findmy.ui.screen.contact

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.model.User
import me.ikate.findmy.data.remote.mqtt.message.ShareResponseType
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.ContactRepository
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.service.GeofenceManager
import me.ikate.findmy.service.LocationReportService
import me.ikate.findmy.service.LocationTrackingManager
import me.ikate.findmy.util.DeviceIdProvider
import me.ikate.findmy.util.NotificationHelper
import me.ikate.findmy.util.OnboardingPreferences
import me.ikate.findmy.util.PermissionGuideHelper
import me.ikate.findmy.util.PermissionStatusChecker
import me.ikate.findmy.util.ReverseGeocodeHelper

/**
 * 联系人 UI 聚合状态
 * 将多个独立的 StateFlow 合并为一个，减少 Compose 重组次数
 *
 * 性能优化：
 * - 原来有 13+ 个独立的 collectAsState()
 * - 现在合并为一个 ContactUiState，只有相关状态变化时才重组
 */
data class ContactUiState(
    val contacts: List<Contact> = emptyList(),
    val currentUser: User? = null,
    val meName: String? = null,
    val meAvatarUrl: String? = null,
    val meStatus: String? = null,
    val myDevice: Device? = null,
    val myAddress: String? = null,
    val showAddDialog: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val refreshingContacts: Set<String> = emptySet(),
    val ringingContactUid: String? = null,
    val showPermissionGuide: Boolean = false,
    val missingPermissions: List<String> = emptyList()
)

/**
 * 联系人功能 ViewModel
 * 管理联系人列表、位置共享操作和 UI 状态
 */
class ContactViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val contactRepository: ContactRepository,
    private val deviceRepository: DeviceRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ContactViewModel"
        private const val KEY_ME_NAME = "me_name"
        private const val KEY_ME_AVATAR = "me_avatar"
        private const val KEY_ME_STATUS = "me_status"
    }

    // ====================================================================
    // 依赖
    // ====================================================================
    private val context = application
    // 使用加密存储保护用户数据
    private val prefs = me.ikate.findmy.util.SecurePreferences.getInstance(context).also {
        // 迁移旧的非加密数据
        me.ikate.findmy.util.SecurePreferences.migrateFromPlainPrefs(context, "user_profile")
    }
    private val locationReportService = LocationReportService(context)
    private val geofenceManager = GeofenceManager.getInstance(context)

    // 追踪管理器
    private val trackingManager = LocationTrackingManager(
        context = context,
        locationReportService = locationReportService,
        scope = viewModelScope
    )

    // 定期清理任务（用于取消）
    private var periodicCleanupJob: Job? = null

    // MQTT 服务（用于接收邀请）
    private val mqttService = DeviceRepository.getMqttService(context)

    // ====================================================================
    // 状态管理
    // ====================================================================

    // 联系人列表
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    // 选中的联系人
    private val _selectedContact = MutableStateFlow<Contact?>(null)

    // 当前用户信息
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // "我"的本地显示信息
    private val _meName = MutableStateFlow<String?>(null)
    val meName: StateFlow<String?> = _meName.asStateFlow()

    private val _meAvatarUrl = MutableStateFlow<String?>(null)
    val meAvatarUrl: StateFlow<String?> = _meAvatarUrl.asStateFlow()

    // "我"的状态签名
    private val _meStatus = MutableStateFlow<String?>(null)
    val meStatus: StateFlow<String?> = _meStatus.asStateFlow()

    // 当前设备信息
    private val _myDevice = MutableStateFlow<Device?>(null)
    val myDevice: StateFlow<Device?> = _myDevice.asStateFlow()

    // 当前设备地址
    private val _myAddress = MutableStateFlow<String?>(null)
    val myAddress: StateFlow<String?> = _myAddress.asStateFlow()

    // UI 状态
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 刷新状态（委托给 TrackingManager）
    val refreshingContacts: StateFlow<Set<String>> = trackingManager.refreshingContacts

    // 响铃状态 - 跟踪正在请求响铃的联系人
    private val _ringingContactUid = MutableStateFlow<String?>(null)
    val ringingContactUid: StateFlow<String?> = _ringingContactUid.asStateFlow()

    // 权限引导
    private val _showPermissionGuide = MutableStateFlow(false)
    val showPermissionGuide: StateFlow<Boolean> = _showPermissionGuide.asStateFlow()

    private val _missingPermissions = MutableStateFlow<List<String>>(emptyList())
    val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    /**
     * 聚合 UI 状态
     * 将多个 StateFlow 合并为一个，供 MainScreen 使用
     * 减少 collectAsState() 调用次数，提升性能
     *
     * 使用 combine 最多支持 5 个参数，因此分两层组合
     */
    val uiState: StateFlow<ContactUiState> = combine(
        combine(_contacts, _currentUser, _meName, _meAvatarUrl, _meStatus) { contacts, currentUser, meName, meAvatarUrl, meStatus ->
            ContactUiState(
                contacts = contacts,
                currentUser = currentUser,
                meName = meName,
                meAvatarUrl = meAvatarUrl,
                meStatus = meStatus
            )
        },
        combine(_myDevice, _myAddress, _showAddDialog, _isLoading, _errorMessage) { myDevice, myAddress, showAddDialog, isLoading, errorMessage ->
            Triple(Triple(myDevice, myAddress, showAddDialog), isLoading, errorMessage)
        },
        combine(trackingManager.refreshingContacts, _ringingContactUid, _showPermissionGuide, _missingPermissions) { refreshingContacts, ringingContactUid, showPermissionGuide, missingPermissions ->
            Triple(Triple(refreshingContacts, ringingContactUid, showPermissionGuide), missingPermissions, Unit)
        }
    ) { baseState, deviceState, permissionState ->
        baseState.copy(
            myDevice = deviceState.first.first,
            myAddress = deviceState.first.second,
            showAddDialog = deviceState.first.third,
            isLoading = deviceState.second,
            errorMessage = deviceState.third,
            refreshingContacts = permissionState.first.first,
            ringingContactUid = permissionState.first.second,
            showPermissionGuide = permissionState.first.third,
            missingPermissions = permissionState.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContactUiState()
    )

    private var pendingAction: (() -> Unit)? = null
    private var previousContactIds = setOf<String>()

    // ====================================================================
    // 初始化
    // ====================================================================

    init {
        loadLocalProfile()
        observeContacts()
        observeMyDevice()
        ensureUserAuthenticatedAndObserve()
        startPeriodicCleanup()
        observeTrackingErrors()
        observeLocationUpdates()  // 监听位置更新，更新追踪状态
        // 监听 MQTT 共享消息（订阅在 MainViewModel 中完成）
        observeShareRequests()
        observeShareResponses()
        observeSharePauseStatus()
    }

    /**
     * 监听位置更新
     * 收到位置更新时清除对应联系人的刷新状态
     */
    private fun observeLocationUpdates() {
        viewModelScope.launch {
            try {
                mqttService.locationUpdates.collect { device ->
                    // 收到位置更新，清除刷新状态
                    device.ownerId?.let { ownerId ->
                        if (trackingManager.isRefreshing(ownerId)) {
                            Log.d(TAG, "[位置更新] 收到位置更新，清除刷新状态: $ownerId")
                            trackingManager.onLocationReceived(ownerId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[位置更新] 监听位置更新失败", e)
            }
        }
    }

    private fun observeTrackingErrors() {
        viewModelScope.launch {
            trackingManager.errorMessage.collect { error ->
                if (error != null) {
                    _errorMessage.value = error
                    trackingManager.clearError()
                }
            }
        }
    }

    private fun loadLocalProfile() {
        _meName.value = prefs.getString(KEY_ME_NAME, null)
        _meAvatarUrl.value = prefs.getString(KEY_ME_AVATAR, null)
        _meStatus.value = prefs.getString(KEY_ME_STATUS, null)
    }

    /**
     * 更新"我"的显示名称
     * 注意：SharedPreferences 写入在 IO 线程执行，避免主线程阻塞
     */
    fun updateMeName(name: String) {
        _meName.value = name
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString(KEY_ME_NAME, name).apply()
        }
    }

    /**
     * 更新"我"的头像 URL
     * 注意：SharedPreferences 写入在 IO 线程执行，避免主线程阻塞
     */
    fun updateMeAvatar(avatarUrl: String?) {
        _meAvatarUrl.value = avatarUrl
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString(KEY_ME_AVATAR, avatarUrl).apply()
        }
    }

    /**
     * 更新"我"的状态签名
     * 注意：SharedPreferences 写入在 IO 线程执行，避免主线程阻塞
     */
    fun updateMeStatus(status: String?) {
        _meStatus.value = status
        viewModelScope.launch(Dispatchers.IO) {
            if (status.isNullOrBlank()) {
                prefs.edit().remove(KEY_ME_STATUS).apply()
            } else {
                prefs.edit().putString(KEY_ME_STATUS, status).apply()
            }
        }
    }

    // ====================================================================
    // 认证相关
    // ====================================================================

    private fun ensureUserAuthenticatedAndObserve() {
        viewModelScope.launch {
            authRepository.signIn().fold(
                onSuccess = { userId ->
                    Log.d(TAG, "用户已就绪: $userId")
                    syncCurrentUser()
                },
                onFailure = { error ->
                    Log.e(TAG, "无法获取用户ID", error)
                }
            )
        }
    }

    private fun syncCurrentUser() {
        viewModelScope.launch {
            contactRepository.syncCurrentUser()
            _currentUser.value = contactRepository.getCurrentUser()
        }
    }

    // ====================================================================
    // 设备监听
    // ====================================================================

    private fun observeMyDevice() {
        val androidId = DeviceIdProvider.getDeviceId(context)

        viewModelScope.launch {
            deviceRepository.observeDevices().collect { devices ->
                val device = devices.find { it.id == androidId }

                if (device?.location != _myDevice.value?.location) {
                    device?.location?.let { fetchMyAddress(it) }
                }

                _myDevice.value = device
            }
        }
    }

    private fun fetchMyAddress(latLng: LatLng) {
        viewModelScope.launch {
            ReverseGeocodeHelper.getAddressFromLocation(
                context = context,
                latitude = latLng.latitude,
                longitude = latLng.longitude
            ) { address ->
                _myAddress.value = address
            }
        }
    }

    // ====================================================================
    // 联系人监听
    // ====================================================================

    private fun observeContacts() {
        viewModelScope.launch {
            Log.i(TAG, "[联系人监听] 开始监听联系人列表变化...")
            contactRepository.observeMyContacts().collect { contactList ->
                Log.i(TAG, "[联系人监听] 收到联系人列表更新，数量: ${contactList.size}")
                contactList.forEach { contact ->
                    Log.d(TAG, "[联系人监听] - ${contact.name}: 状态=${contact.shareStatus}, 位置=${contact.location != null}, 更新时间=${contact.lastUpdateTime}")
                }
                detectAndNotifyNewInvitations(contactList)
                // 订阅已接受共享的联系人的位置主题（应用启动时恢复订阅）
                subscribeToAcceptedContacts(contactList)
                _contacts.value = contactList
            }
        }
    }

    /**
     * 订阅已接受共享的联系人的位置主题
     * 应用启动时或联系人列表变化时调用，确保能收到位置更新
     */
    private fun subscribeToAcceptedContacts(contactList: List<Contact>) {
        viewModelScope.launch {
            contactList.forEach { contact ->
                // 只订阅已接受共享且对方共享给我的联系人（包括双向共享）
                if (contact.shareStatus == ShareStatus.ACCEPTED &&
                    (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
                     contact.shareDirection == ShareDirection.MUTUAL) &&
                    !contact.isPaused) {
                    contact.targetUserId?.let { targetUserId ->
                        mqttService.subscribeToUser(targetUserId)
                        Log.d(TAG, "订阅联系人位置主题: $targetUserId (${contact.name})")
                    }
                }
            }
        }
    }

    private fun detectAndNotifyNewInvitations(newContactList: List<Contact>) {
        val currentContactIds = newContactList.map { it.id }.toSet()

        newContactList.forEach { contact ->
            val isNew = !previousContactIds.contains(contact.id)
            val isPendingInvitation = contact.shareStatus == ShareStatus.PENDING &&
                    contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME

            if (isNew && isPendingInvitation) {
                NotificationHelper.showShareRequestNotification(
                    context = context,
                    senderName = contact.name,
                    shareId = contact.id
                )
            }
        }

        previousContactIds = currentContactIds
    }

    private fun startPeriodicCleanup() {
        periodicCleanupJob?.cancel()
        periodicCleanupJob = viewModelScope.launch {
            while (isActive) {
                delay(60 * 60 * 1000L) // 每小时清理一次
                if (authRepository.isSignedIn()) {
                    contactRepository.cleanupExpiredShares()
                }
            }
        }
    }

    /**
     * 监听 MQTT 邀请请求
     */
    private fun observeShareRequests() {
        viewModelScope.launch {
            mqttService.shareRequestUpdates.collect { request ->
                Log.d(TAG, "收到共享邀请: ${request.senderName} (${request.senderId})")

                // 添加到本地数据库
                contactRepository.addContactFromShare(
                    shareId = request.shareId,
                    fromUserId = request.senderId,
                    fromUserName = request.senderName,
                    expireTime = request.expireTime
                )

                // 显示通知
                NotificationHelper.showShareRequestNotification(
                    context = context,
                    senderName = request.senderName,
                    shareId = request.shareId
                )
            }
        }
    }

    /**
     * 监听 MQTT 邀请响应（对方接受/拒绝/移除了我的邀请）
     */
    private fun observeShareResponses() {
        viewModelScope.launch {
            mqttService.shareResponseUpdates.collect { response ->
                Log.d(TAG, "收到共享响应: ${response.responderName} -> ${response.response}")

                when (response.response) {
                    ShareResponseType.ACCEPTED -> {
                        // 仅更新本地状态（不发送 MQTT 响应，避免循环）
                        contactRepository.updateShareStatusLocally(
                            response.shareId,
                            ShareStatus.ACCEPTED
                        )
                        // 订阅对方的位置更新
                        mqttService.subscribeToUser(response.responderId)
                        Log.d(TAG, "${response.responderName} 已接受邀请")
                    }
                    ShareResponseType.REJECTED -> {
                        // 对方拒绝共享，删除本地记录
                        contactRepository.deleteContactLocally(response.shareId)
                        Log.d(TAG, "${response.responderName} 已拒绝共享")
                    }
                    ShareResponseType.REMOVED -> {
                        // 对方将你从联系人列表中移除
                        // 更新状态为 REMOVED，显示"已被移出"而不是直接删除
                        contactRepository.markContactAsRemoved(response.responderId)
                        // 取消订阅对方的位置更新
                        mqttService.unsubscribeFromUser(response.responderId)
                        // 清理该联系人的电子围栏
                        geofenceManager.removeGeofence(response.responderId)
                        // 显示通知
                        NotificationHelper.showShareRemovedNotification(
                            context = context,
                            removerName = response.responderName
                        )
                        Log.d(TAG, "${response.responderName} 已将您移出联系人列表，已清理电子围栏")
                    }
                }
            }
        }
    }

    /**
     * 监听 MQTT 共享暂停/恢复/过期状态
     * 过期是一种特殊的暂停：isExpired=true
     */
    private fun observeSharePauseStatus() {
        viewModelScope.launch {
            mqttService.sharePauseUpdates.collect { pauseMessage ->
                Log.d(TAG, "收到共享暂停状态: ${pauseMessage.senderName} -> isPaused=${pauseMessage.isPaused}, isExpired=${pauseMessage.isExpired}")

                when {
                    pauseMessage.isExpired -> {
                        // 共享已过期（特殊的暂停），显示过期通知
                        NotificationHelper.showShareExpiredNotification(
                            context = context,
                            contactName = pauseMessage.senderName
                        )
                    }
                    pauseMessage.isPaused -> {
                        // 对方暂停了共享，显示通知
                        NotificationHelper.showSharePausedNotification(
                            context = context,
                            contactName = pauseMessage.senderName
                        )
                    }
                    else -> {
                        // 对方恢复了共享，显示通知
                        NotificationHelper.showShareResumedNotification(
                            context = context,
                            contactName = pauseMessage.senderName
                        )
                    }
                }
            }
        }
    }

    // ====================================================================
    // 权限管理
    // ====================================================================

    private fun checkAndGuidePermissions(action: () -> Unit): Boolean {
        val (allGranted, missing) = PermissionGuideHelper.checkLocationSharePermissions(context)

        if (!allGranted) {
            pendingAction = action
            _missingPermissions.value = missing
            _showPermissionGuide.value = true
            return false
        }
        return true
    }

    fun onPermissionGranted() {
        _showPermissionGuide.value = false
        val (allGranted, _) = PermissionGuideHelper.checkLocationSharePermissions(context)

        if (allGranted) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
            _errorMessage.value = "权限未完全开启，请确认已按照引导设置"
        }
    }

    fun dismissPermissionGuide() {
        _showPermissionGuide.value = false
        pendingAction = null
        // 记录提示时间，避免频繁打扰
        PermissionStatusChecker.markPermissionPromptShown(context)
    }

    /**
     * 检查权限状态并在必要时显示恢复提示
     * 应在应用进入前台时调用
     * 使用温和提示机制：每天最多提示一次
     */
    fun checkPermissionStatusOnResume() {
        if (PermissionStatusChecker.shouldShowPermissionRecoveryPrompt(context)) {
            val status = PermissionStatusChecker.checkAllPermissions(context)
            if (!status.hasCriticalPermissions) {
                _missingPermissions.value = status.missingCriticalPermissions + status.missingOptionalPermissions
                _showPermissionGuide.value = true
            }
        }
    }

    /**
     * 获取当前权限状态
     */
    fun getPermissionStatus(): PermissionStatusChecker.PermissionStatus {
        return PermissionStatusChecker.checkAllPermissions(context)
    }

    // ====================================================================
    // 联系人操作
    // ====================================================================

    fun showAddDialog() { _showAddDialog.value = true }
    fun hideAddDialog() { _showAddDialog.value = false }
    fun clearError() { _errorMessage.value = null }

    fun clearSelectedContact() {
        _selectedContact.value = null
    }

    fun shareLocation(targetInput: String, duration: ShareDuration) {
        if (targetInput.isBlank()) {
            _errorMessage.value = "请输入对方 UID"
            return
        }

        val hasPermission = checkAndGuidePermissions {
            executeShareLocation(targetInput, duration)
        }

        if (hasPermission) {
            executeShareLocation(targetInput, duration)
        }
    }

    private fun executeShareLocation(targetInput: String, duration: ShareDuration) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.createLocationShare(targetInput, duration)
            _isLoading.value = false

            result.fold(
                onSuccess = { hideAddDialog() },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "分享失败，请重试"
                }
            )
        }
    }

    fun acceptShare(shareId: String) {
        val hasPermission = checkAndGuidePermissions {
            executeAcceptShare(shareId)
        }

        if (hasPermission) {
            executeAcceptShare(shareId)
        }
    }

    private fun executeAcceptShare(shareId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.acceptLocationShare(shareId)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    // 接受成功后订阅对方的位置更新
                    val contact = _contacts.value.find { it.id == shareId }
                    contact?.targetUserId?.let { targetUserId ->
                        mqttService.subscribeToUser(targetUserId)
                        Log.d(TAG, "已订阅联系人位置: $targetUserId")
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "接受失败，请重试"
                }
            )
        }
    }

    fun rejectShare(shareId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.rejectLocationShare(shareId)
            _isLoading.value = false

            result.onFailure { error ->
                _errorMessage.value = error.message ?: "拒绝失败，请重试"
            }
        }
    }

    fun stopSharing(shareId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            // 在删除联系人前，先清理该联系人的电子围栏
            val contact = _contacts.value.find { it.id == shareId }
            contact?.targetUserId?.let { targetUserId ->
                geofenceManager.removeGeofence(targetUserId)
                Log.d(TAG, "已清理联系人 $targetUserId 的电子围栏")
            }

            val result = contactRepository.stopSharing(shareId = shareId)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    if (_selectedContact.value?.id == shareId) {
                        clearSelectedContact()
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "停止共享失败，请重试"
                }
            )
        }
    }

    fun removeContact(contact: Contact) {
        stopSharing(contact.id)
    }

    fun pauseShare(contact: Contact) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.pauseLocationShare(shareId = contact.id)
            _isLoading.value = false

            result.onFailure { error ->
                _errorMessage.value = error.message ?: "暂停共享失败，请重试"
            }
        }
    }

    fun resumeShare(contact: Contact, duration: ShareDuration) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.resumeLocationShare(
                shareId = contact.id,
                duration = duration
            )
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    contact.targetUserId?.let { targetUid ->
                        refreshLocation(targetUid)
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "恢复共享失败，请重试"
                }
            )
        }
    }

    // ====================================================================
    // 联系人绑定
    // ====================================================================

    fun bindContact(shareId: String, contactUri: android.net.Uri) {
        viewModelScope.launch {
            try {
                // 在 IO 线程执行 ContentResolver 查询，避免阻塞主线程
                val (name, photoUrl) = withContext(Dispatchers.IO) {
                    queryContactInfo(contactUri)
                }

                if (name != null) {
                    val result = contactRepository.bindContact(shareId, name, photoUrl)
                    result.onFailure { e ->
                        _errorMessage.value = "绑定失败: ${e.message}"
                    }
                } else {
                    _errorMessage.value = "无法从所选联系人获取姓名"
                }
            } catch (e: Exception) {
                Log.e(TAG, "绑定联系人失败", e)
                _errorMessage.value = "绑定失败: ${e.localizedMessage}"
            }
        }
    }

    private fun queryContactInfo(contactUri: android.net.Uri): Pair<String?, String?> {
        var name: String? = null
        var photoUrl: String? = null

        context.contentResolver.query(
            contactUri,
            arrayOf(
                android.provider.ContactsContract.Contacts.DISPLAY_NAME,
                android.provider.ContactsContract.Contacts.PHOTO_URI
            ),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(
                    android.provider.ContactsContract.Contacts.DISPLAY_NAME
                )
                val photoIndex = cursor.getColumnIndex(
                    android.provider.ContactsContract.Contacts.PHOTO_URI
                )

                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (photoIndex >= 0) photoUrl = cursor.getString(photoIndex)
            }
        }

        return Pair(name, photoUrl)
    }

    // ====================================================================
    // 位置刷新（委托给 TrackingManager）
    // ====================================================================

    /**
     * 检查指定联系人是否正在刷新位置
     */
    fun isRefreshing(targetUid: String): Boolean {
        return trackingManager.isRefreshing(targetUid)
    }

    /**
     * 刷新联系人位置（点击头像触发）
     * iOS Find My 风格：单击即刷新
     *
     * 注意：执行前会检查定位权限和电池优化权限
     */
    fun refreshLocation(targetUid: String) {
        Log.i(TAG, "========================================")
        Log.i(TAG, "[ContactViewModel] 刷新位置被调用")
        Log.i(TAG, "[ContactViewModel] targetUid: $targetUid")
        Log.i(TAG, "[ContactViewModel] currentUser.uid: ${_currentUser.value?.uid}")
        Log.i(TAG, "========================================")

        val hasPermission = checkAndGuidePermissions {
            Log.i(TAG, "[ContactViewModel] 权限检查通过（回调），执行 trackingManager.requestRefresh")
            trackingManager.requestRefresh(_currentUser.value?.uid, targetUid)
        }

        if (hasPermission) {
            Log.i(TAG, "[ContactViewModel] 权限检查通过（立即），执行 trackingManager.requestRefresh")
            trackingManager.requestRefresh(_currentUser.value?.uid, targetUid)
        } else {
            Log.w(TAG, "[ContactViewModel] 权限检查未通过，等待用户授权")
        }
    }

    // ====================================================================
    // 播放声音（委托给 TrackingManager）
    // ====================================================================

    fun requestPlaySound(targetUid: String) {
        trackingManager.requestPlaySound(_currentUser.value?.uid, targetUid)
        _ringingContactUid.value = targetUid
    }

    fun requestStopSound(targetUid: String) {
        trackingManager.requestStopSound(_currentUser.value?.uid, targetUid)
        _ringingContactUid.value = null
    }

    fun stopRinging() {
        _ringingContactUid.value?.let { targetUid ->
            requestStopSound(targetUid)
        }
    }

    // ====================================================================
    // 丢失模式
    // ====================================================================

    fun enableLostMode(
        targetUid: String,
        message: String,
        phoneNumber: String,
        playSound: Boolean
    ) {
        trackingManager.enableLostMode(
            _currentUser.value?.uid,
            targetUid,
            message,
            phoneNumber,
            playSound
        )
    }

    fun disableLostMode(targetUid: String) {
        trackingManager.disableLostMode(_currentUser.value?.uid, targetUid)
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ContactViewModel 销毁")

        // 取消定期清理任务
        periodicCleanupJob?.cancel()
        periodicCleanupJob = null

        // 释放定位服务资源
        locationReportService.destroy()

        // 释放围栏管理器资源
        geofenceManager.destroy()
    }
}
