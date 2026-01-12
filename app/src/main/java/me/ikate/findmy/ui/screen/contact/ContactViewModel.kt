package me.ikate.findmy.ui.screen.contact

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.ContactRepository
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.util.AddressFormatter
import me.ikate.findmy.util.NotificationHelper
import me.ikate.findmy.util.PermissionGuideHelper
import java.util.Locale

/**
 * 联系人功能 ViewModel
 * 管理联系人列表、位置共享操作和 UI 状态
 */
class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application.applicationContext)
    private val contactRepository = ContactRepository()
    private val deviceRepository = DeviceRepository()
    private val locationReportService = me.ikate.findmy.service.LocationReportService(application.applicationContext)
    private val context = application
    private val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "ContactViewModel"
        private const val KEY_ME_NAME = "me_name"
        private const val KEY_ME_AVATAR = "me_avatar"
    }

    // ====================================================================
    // 状态管理
    // ====================================================================

    // 联系人列表
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    // 选中的联系人
    private val _selectedContact = MutableStateFlow<Contact?>(null)

    // 选中联系人的地址（反向地理编码结果）
    private val _contactAddress = MutableStateFlow<String?>(null)

    // 当前用户信息 (仅包含 UID)
    private val _currentUser = MutableStateFlow<me.ikate.findmy.data.model.User?>(null)
    val currentUser: StateFlow<me.ikate.findmy.data.model.User?> = _currentUser.asStateFlow()

    // "我"的本地显示信息
    private val _meName = MutableStateFlow<String?>(null)
    val meName: StateFlow<String?> = _meName.asStateFlow()

    private val _meAvatarUrl = MutableStateFlow<String?>(null)
    val meAvatarUrl: StateFlow<String?> = _meAvatarUrl.asStateFlow()

    // 当前设备信息
    private val _myDevice = MutableStateFlow<Device?>(null)
    val myDevice: StateFlow<Device?> = _myDevice.asStateFlow()

    // 当前设备地址 (格式化后)
    private val _myAddress = MutableStateFlow<String?>(null)
    val myAddress: StateFlow<String?> = _myAddress.asStateFlow()

    // 显示添加联系人对话框
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 正在请求位置更新的联系人 UID（用于显示"正在定位..."）
    private val _requestingLocationFor = MutableStateFlow<String?>(null)
    val requestingLocationFor: StateFlow<String?> = _requestingLocationFor.asStateFlow()

    // 正在连续追踪的联系人 UID（用于显示"实时追踪中..."）
    private val _trackingContactUid = MutableStateFlow<String?>(null)
    val trackingContactUid: StateFlow<String?> = _trackingContactUid.asStateFlow()

    // 用于检测新的共享邀请
    private var previousContactIds = setOf<String>()

    // 权限引导对话框状态
    private val _showPermissionGuide = MutableStateFlow(false)
    val showPermissionGuide: StateFlow<Boolean> = _showPermissionGuide.asStateFlow()

    private val _missingPermissions = MutableStateFlow<List<String>>(emptyList())
    val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    // 待执行的操作（等待权限开启后执行）
    private var pendingAction: (() -> Unit)? = null

    init {
        loadLocalProfile()
        observeContacts()
        observeMyDevice()
        // 确保用户已登录，然后监听认证状态
        ensureUserAuthenticatedAndObserve()
        // 启动定期清理过期共享的任务
        startPeriodicCleanup()
    }

    /**
     * 启动定期清理过期共享的任务
     * 每小时检查一次并清理超时的 PENDING 共享
     */
    private fun startPeriodicCleanup() {
        viewModelScope.launch {
            while (true) {
                delay(60 * 60 * 1000L) // 每小时执行一次
                if (authRepository.isSignedIn()) {
                    contactRepository.cleanupExpiredShares().fold(
                        onSuccess = { count ->
                            if (count > 0) {
                                Log.d(TAG, "清理了 $count 个过期共享")
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "清理过期共享失败", error)
                        }
                    )
                }
            }
        }
    }

    /**
     * 确保用户已登录，然后开始监听认证状态
     */
    private fun ensureUserAuthenticatedAndObserve() {
        viewModelScope.launch {
            // 如果未登录，主动触发登录
            if (!authRepository.isSignedIn()) {
                Log.d(TAG, "ContactViewModel: 用户未登录，开始设备登录")
                authRepository.signInWithDeviceId().fold(
                    onSuccess = { user ->
                        Log.d(TAG, "ContactViewModel: 设备登录成功: ${user.uid}")
                        syncCurrentUser()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "ContactViewModel: 设备登录失败", error)
                    }
                )
            } else {
                // 已登录，立即同步
                Log.d(TAG, "ContactViewModel: 用户已登录，立即同步")
                syncCurrentUser()
            }

            // 开始监听后续的认证状态变化
            observeAuthStateChanges()
        }
    }

    /**
     * 监听认证状态变化
     */
    private suspend fun observeAuthStateChanges() {
        authRepository.observeAuthState().collect { user ->
            if (user != null) {
                Log.d(TAG, "检测到用户登录状态变化: ${user.uid}")
                syncCurrentUser()
            } else {
                Log.d(TAG, "用户已登出")
                _currentUser.value = null
            }
        }
    }

    private fun loadLocalProfile() {
        _meName.value = prefs.getString(KEY_ME_NAME, null)
        _meAvatarUrl.value = prefs.getString(KEY_ME_AVATAR, null)
    }

    // ====================================================================
    // 业务方法
    // ====================================================================

    /**
     * 监听当前设备信息
     */
    @SuppressLint("HardwareIds")
    private fun observeMyDevice() {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        viewModelScope.launch {
            deviceRepository.observeDevices().collect { devices ->
                // 查找当前设备 ID 对应的设备
                val device = devices.find { it.id == androidId }

                // 如果位置发生变化，更新地址
                if (device?.location != _myDevice.value?.location) {
                    device?.location?.let { fetchMyAddress(it) }
                }

                _myDevice.value = device
            }
        }
    }

    /**
     * 获取并格式化我的地址 (去除省市)
     */
    private fun fetchMyAddress(latLng: LatLng) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@launch

                // 🌏 强制使用简体中文，确保地址始终显示中文
                val geocoder = Geocoder(context, java.util.Locale.SIMPLIFIED_CHINESE)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(
                        latLng.latitude,
                        latLng.longitude,
                        1
                    ) { addresses: List<Address> ->
                        if (addresses.isNotEmpty()) {
                            val formatted = AddressFormatter.formatAddress(addresses[0])
                            // 如果是Plus Code，说明没有详细地址，显示经纬度
                            _myAddress.value = if (AddressFormatter.isPlusCode(formatted)) {
                                "纬度 ${String.format("%.4f", latLng.latitude)}, " +
                                "经度 ${String.format("%.4f", latLng.longitude)}"
                            } else {
                                formatted
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val formatted = AddressFormatter.formatAddress(addresses[0])
                        // 如果是Plus Code，说明没有详细地址，显示经纬度
                        _myAddress.value = if (AddressFormatter.isPlusCode(formatted)) {
                            "纬度 ${String.format("%.4f", latLng.latitude)}, " +
                            "经度 ${String.format("%.4f", latLng.longitude)}"
                        } else {
                            formatted
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "获取我的地址失败", e)
            }
        }
    }

    /**
     * 绑定联系人（给共享设置别名）
     */
    fun bindContact(shareId: String, contactUri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var name: String? = null
                var photoUrl: String? = null

                // 查询联系人信息
                val cursor = context.contentResolver.query(
                    contactUri,
                    arrayOf(
                        android.provider.ContactsContract.Contacts.DISPLAY_NAME,
                        android.provider.ContactsContract.Contacts.PHOTO_URI
                    ),
                    null, null, null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex =
                            it.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                        val photoIndex =
                            it.getColumnIndex(android.provider.ContactsContract.Contacts.PHOTO_URI)

                        if (nameIndex >= 0) name = it.getString(nameIndex)
                        if (photoIndex >= 0) photoUrl = it.getString(photoIndex)
                    }
                }

                if (name != null) {
                    val result = contactRepository.bindContact(shareId, name, photoUrl)
                    result.onFailure { e ->
                        _errorMessage.value = "绑定失败: ${e.message}"
                    }
                    // 成功后，Repository 的监听会自动更新列表
                } else {
                    _errorMessage.value = "无法从所选联系人获取姓名"
                }
            } catch (e: Exception) {
                Log.e(TAG, "绑定联系人失败", e)
                _errorMessage.value = "绑定失败: ${e.localizedMessage}"
            }
        }
    }

    /**
     * 同步当前用户信息
     */
    private fun syncCurrentUser() {
        viewModelScope.launch {
            // 同步到 Firestore
            val result = contactRepository.syncCurrentUser()
            result.onFailure { error ->
                Log.e(TAG, "同步用户信息失败", error)
            }

            // 获取当前用户信息并更新状态
            val user = contactRepository.getCurrentUser()
            _currentUser.value = user
        }
    }

    /**
     * 清除选中联系人
     */
    fun clearSelectedContact() {
        _selectedContact.value = null
        _contactAddress.value = null
    }

    /**
     * 监听联系人列表
     */
    private fun observeContacts() {
        viewModelScope.launch {
            contactRepository.observeMyContacts().collect { contactList ->
                // 检测新的 PENDING 邀请并显示通知
                detectAndNotifyNewInvitations(contactList)

                _contacts.value = contactList
                Log.d(TAG, "联系人列表更新: ${contactList.size} 个联系人")
            }
        }
    }

    /**
     * 检测新的共享邀请并显示通知
     */
    private fun detectAndNotifyNewInvitations(newContactList: List<Contact>) {
        val currentContactIds = newContactList.map { it.id }.toSet()

        // 查找新增的 PENDING 邀请（别人邀请我的）
        newContactList.forEach { contact ->
            val isNew = !previousContactIds.contains(contact.id)
            val isPendingInvitation = contact.shareStatus == me.ikate.findmy.data.model.ShareStatus.PENDING &&
                    contact.shareDirection == me.ikate.findmy.data.model.ShareDirection.THEY_SHARE_TO_ME

            if (isNew && isPendingInvitation) {
                Log.d(TAG, "检测到新的共享邀请: ${contact.name}")
                NotificationHelper.showShareRequestNotification(
                    context = context,
                    senderName = contact.name,
                    shareId = contact.id
                )
            }
        }

        previousContactIds = currentContactIds
    }

    /**
     * 检查位置共享所需的关键权限
     * @param action 权限满足后要执行的操作
     * @return true 表示权限已满足，false 表示需要引导用户开启
     */
    private fun checkAndGuidePermissions(action: () -> Unit): Boolean {
        val (allGranted, missing) = PermissionGuideHelper.checkLocationSharePermissions(context)

        if (!allGranted) {
            // 保存待执行的操作
            pendingAction = action
            _missingPermissions.value = missing
            _showPermissionGuide.value = true
            Log.d(TAG, "缺少权限: $missing")
            return false
        }

        return true
    }

    /**
     * 用户确认已开启权限
     */
    fun onPermissionGranted() {
        _showPermissionGuide.value = false

        // 重新检查权限
        val (allGranted, _) = PermissionGuideHelper.checkLocationSharePermissions(context)

        if (allGranted) {
            // 执行待执行的操作
            pendingAction?.invoke()
            pendingAction = null
        } else {
            // 权限仍未满足，提示用户
            _errorMessage.value = "权限未完全开启，请确认已按照引导设置"
            Log.w(TAG, "用户点击确认但权限仍未满足")
        }
    }

    /**
     * 关闭权限引导对话框
     */
    fun dismissPermissionGuide() {
        _showPermissionGuide.value = false
        pendingAction = null
    }

    /**
     * 显示添加对话框
     */
    fun showAddDialog() {
        _showAddDialog.value = true
    }

    /**
     * 隐藏添加对话框
     */
    fun hideAddDialog() {
        _showAddDialog.value = false
    }

    /**
     * 发起位置共享
     */
    fun shareLocation(targetInput: String, duration: ShareDuration) {
        if (targetInput.isBlank()) {
            _errorMessage.value = "请输入对方 UID"
            return
        }

        // 检查权限，如果不满足则引导用户
        val hasPermission = checkAndGuidePermissions {
            // 权限满足后执行的操作
            executeShareLocation(targetInput, duration)
        }

        // 如果权限已满足，直接执行
        if (hasPermission) {
            executeShareLocation(targetInput, duration)
        }
    }

    /**
     * 执行位置共享操作（内部方法）
     */
    private fun executeShareLocation(targetInput: String, duration: ShareDuration) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.createLocationShare(targetInput, duration)
            _isLoading.value = false

            result.fold(
                onSuccess = { shareId ->
                    hideAddDialog()
                    Log.d(TAG, "位置共享成功: $shareId")
                    // 成功后联系人列表会自动更新(通过 observeContacts)
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "分享失败，请重试"
                    Log.e(TAG, "位置共享失败", error)
                }
            )
        }
    }

    /**
     * 接受位置共享
     */
    fun acceptShare(shareId: String) {
        // 检查权限，如果不满足则引导用户
        val hasPermission = checkAndGuidePermissions {
            // 权限满足后执行的操作
            executeAcceptShare(shareId)
        }

        // 如果权限已满足，直接执行
        if (hasPermission) {
            executeAcceptShare(shareId)
        }
    }

    /**
     * 执行接受共享操作（内部方法）
     */
    private fun executeAcceptShare(shareId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.acceptLocationShare(shareId)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    Log.d(TAG, "接受共享成功: $shareId")
                    // 成功后联系人列表会自动更新
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "接受失败，请重试"
                    Log.e(TAG, "接受共享失败", error)
                }
            )
        }
    }

    /**
     * 拒绝位置共享
     */
    fun rejectShare(shareId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.rejectLocationShare(shareId)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    Log.d(TAG, "拒绝共享成功: $shareId")
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "拒绝失败，请重试"
                    Log.e(TAG, "拒绝共享失败", error)
                }
            )
        }
    }

    /**
     * 停止共享
     */
    fun stopSharing(shareId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.stopSharing(shareId = shareId)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    Log.d(TAG, "停止共享成功: $shareId")
                    // 如果停止的是当前选中的联系人，清除选中状态
                    if (_selectedContact.value?.id == shareId) {
                        clearSelectedContact()
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "停止共享失败，请重试"
                    Log.e(TAG, "停止共享失败", error)
                }
            )
        }
    }

    /**
     * 移除联系人 (停止共享的别名，用于 UI 语义)
     */
    fun removeContact(contact: Contact) {
        stopSharing(contact.id)
    }

    /**
     * 暂停共享
     */
    fun pauseShare(contact: Contact) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.pauseLocationShare(shareId = contact.id)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    Log.d(TAG, "暂停共享成功: ${contact.id}")
                    // 更新选中联系人的状态 (如果需要)
                    when (_selectedContact.value?.id) {
                        contact.id -> {
                            // 可以在这里局部更新，或者等待 repository 的 flow 更新
                        }
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "暂停共享失败，请重试"
                    Log.e(TAG, "暂停共享失败", error)
                }
            )
        }
    }

    /**
     * 恢复共享
     */
    fun resumeShare(contact: Contact, duration: ShareDuration) {
        viewModelScope.launch {
            _isLoading.value = true
            val result =
                contactRepository.resumeLocationShare(shareId = contact.id, duration = duration)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    Log.d(TAG, "恢复共享成功: ${contact.id}")

                    // 🔔 恢复成功后，通知对端以发起方恢复时间为准继续分享
                    // 通过 FCM 触发对端立即上报最新位置
                    contact.targetUserId?.let { targetUid ->
                        Log.d(TAG, "恢复共享后通知对端: targetUid=$targetUid")
                        requestLocationUpdate(targetUid)
                    } ?: Log.w(TAG, "无法通知对端: targetUserId 为空")
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "恢复共享失败，请重试"
                    Log.e(TAG, "恢复共享失败", error)
                }
            )
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 开始短时实时追踪（60秒高频位置更新）
     *
     * 流程：
     * 1. 先上报自己的最新位置（互惠原则）
     * 2. 创建 trackingRequests 记录，指定 type 为 "continuous"
     * 3. Cloud Function 监听后发送 FCM 消息类型为 LOCATION_TRACK_START
     * 4. 目标设备启动 ContinuousLocationWorker，在60秒内每7秒上报一次
     * 5. 60秒后自动停止，或者调用 stopContinuousTracking() 手动停止
     *
     * @param targetUid 目标联系人的 UID
     */
    fun startContinuousTracking(targetUid: String) {
        viewModelScope.launch {
            try {
                val currentUid = _currentUser.value?.uid
                if (currentUid == null) {
                    Log.w(TAG, "当前用户未登录，无法开始追踪")
                    return@launch
                }

                // 设置追踪状态
                _trackingContactUid.value = targetUid
                Log.d(TAG, "开始连续追踪: targetUid=$targetUid")

                // 先上报自己的位置
                val myLocationReport = locationReportService.reportCurrentLocation()
                myLocationReport.fold(
                    onSuccess = { device ->
                        Log.d(TAG, "我的位置已上报成功，现在启动对方的连续追踪")
                    },
                    onFailure = { e ->
                        Log.w(TAG, "上报我的位置失败: ${e.message}")
                    }
                )

                // 创建连续追踪请求
                val trackingData = hashMapOf(
                    "requesterUid" to currentUid,
                    "targetUid" to targetUid,
                    "type" to "continuous",
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                )

                firestore.collection("locationRequests")
                    .add(trackingData)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "连续追踪请求已创建: ${documentReference.id}")
                        // 60秒后自动清除追踪状态
                        viewModelScope.launch {
                            delay(60000)
                            if (_trackingContactUid.value == targetUid) {
                                _trackingContactUid.value = null
                                Log.d(TAG, "连续追踪已自动结束（60秒超时）")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "创建连续追踪请求失败", e)
                        _trackingContactUid.value = null
                        _errorMessage.value = "启动实时追踪失败: ${e.message}"
                    }
            } catch (e: Exception) {
                Log.e(TAG, "开始连续追踪失败", e)
                _trackingContactUid.value = null
                _errorMessage.value = "启动追踪失败: ${e.localizedMessage}"
            }
        }
    }

    /**
     * 停止连续追踪
     *
     * 流程：
     * 1. 创建停止请求记录
     * 2. Cloud Function 发送 FCM 消息类型为 LOCATION_TRACK_STOP
     * 3. 目标设备取消正在运行的 ContinuousLocationWorker
     *
     * @param targetUid 目标联系人的 UID
     */
    fun stopContinuousTracking(targetUid: String) {
        viewModelScope.launch {
            try {
                val currentUid = _currentUser.value?.uid
                if (currentUid == null) {
                    Log.w(TAG, "当前用户未登录")
                    return@launch
                }

                Log.d(TAG, "停止连续追踪: targetUid=$targetUid")

                // 创建停止请求
                val stopData = hashMapOf(
                    "requesterUid" to currentUid,
                    "targetUid" to targetUid,
                    "type" to "stop_continuous",
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                )

                firestore.collection("locationRequests")
                    .add(stopData)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "停止追踪请求已创建: ${documentReference.id}")
                        _trackingContactUid.value = null
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "创建停止追踪请求失败", e)
                        // 即使失败也清除状态
                        _trackingContactUid.value = null
                        _errorMessage.value = "停止追踪失败: ${e.message}"
                    }
            } catch (e: Exception) {
                Log.e(TAG, "停止连续追踪失败", e)
                _trackingContactUid.value = null
                _errorMessage.value = "停止失败: ${e.localizedMessage}"
            }
        }
    }

    /**
     * 请求联系人的实时位置更新
     * 通过 Firestore 创建位置请求，触发 Cloud Function 发送 FCM 给目标用户
     *
     * @param targetUid 目标用户的 UID
     *
     * 注意：需要配置 Cloud Function 监听 locationRequests 集合，
     * 并发送 FCM Data Message 给目标用户的所有设备
     */
    fun requestLocationUpdate(targetUid: String) {
        viewModelScope.launch {
            try {
                val currentUid = _currentUser.value?.uid
                if (currentUid == null) {
                    Log.w(TAG, "当前用户未登录，无法请求位置更新")
                    return@launch
                }

                // 设置加载状态
                _requestingLocationFor.value = targetUid
                Log.d(TAG, "请求位置更新: targetUid=$targetUid")

                // 🔄 重要：先上报我自己的最新位置，确保对方能看到最新的我的位置
                val myLocationReport = locationReportService.reportCurrentLocation()
                myLocationReport.fold(
                    onSuccess = { device ->
                        Log.d(TAG, "我的位置已上报成功，现在请求对方位置")
                    },
                    onFailure = { e ->
                        Log.w(TAG, "上报我的位置失败: ${e.message}")
                        // 即使失败也继续请求对方位置
                    }
                )

                // 创建位置请求记录
                // Cloud Function 会监听这个集合，并发送 FCM 给目标用户
                val requestData = hashMapOf(
                    "requesterUid" to currentUid,
                    "targetUid" to targetUid,
                    "type" to "single",
                    "timestamp" to System.currentTimeMillis(),
                    "status" to "pending"
                )

                firestore.collection("locationRequests")
                    .add(requestData)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "位置请求已创建: ${documentReference.id}")
                        // 10秒后清除加载状态（给双方设备足够的时间上报位置）
                        viewModelScope.launch {
                            delay(10000)
                            if (_requestingLocationFor.value == targetUid) {
                                _requestingLocationFor.value = null
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "创建位置请求失败", e)
                        _requestingLocationFor.value = null
                        _errorMessage.value = "请求位置更新失败: ${e.message}"
                    }
            } catch (e: Exception) {
                Log.e(TAG, "请求位置更新失败", e)
                _requestingLocationFor.value = null
                _errorMessage.value = "请求失败: ${e.localizedMessage}"
            }
        }
    }
}