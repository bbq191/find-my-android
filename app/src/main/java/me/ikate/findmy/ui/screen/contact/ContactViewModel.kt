package me.ikate.findmy.ui.screen.contact

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.model.User
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.ContactRepository
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.service.LocationReportService
import me.ikate.findmy.service.LocationTrackingManager
import me.ikate.findmy.util.NotificationHelper
import me.ikate.findmy.util.PermissionGuideHelper
import me.ikate.findmy.util.ReverseGeocodeHelper

/**
 * 联系人功能 ViewModel
 * 管理联系人列表、位置共享操作和 UI 状态
 */
class ContactViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ContactViewModel"
        private const val KEY_ME_NAME = "me_name"
        private const val KEY_ME_AVATAR = "me_avatar"
    }

    // ====================================================================
    // 依赖
    // ====================================================================
    private val context = application
    private val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    private val authRepository = AuthRepository(context)
    private val contactRepository = ContactRepository()
    private val deviceRepository = DeviceRepository()
    private val locationReportService = LocationReportService(context)

    // 追踪管理器
    private val trackingManager = LocationTrackingManager(
        firestore = FirebaseFirestore.getInstance(),
        locationReportService = locationReportService,
        scope = viewModelScope
    )

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

    // 追踪状态（委托给 TrackingManager）
    val requestingLocationFor: StateFlow<String?> = trackingManager.requestingLocationFor
    val trackingContactUid: StateFlow<String?> = trackingManager.trackingContactUid

    // 权限引导
    private val _showPermissionGuide = MutableStateFlow(false)
    val showPermissionGuide: StateFlow<Boolean> = _showPermissionGuide.asStateFlow()

    private val _missingPermissions = MutableStateFlow<List<String>>(emptyList())
    val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

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
    }

    // ====================================================================
    // 认证相关
    // ====================================================================

    private fun ensureUserAuthenticatedAndObserve() {
        viewModelScope.launch {
            if (!authRepository.isSignedIn()) {
                Log.d(TAG, "用户未登录，开始设备登录")
                authRepository.signInWithDeviceId().fold(
                    onSuccess = { user ->
                        Log.d(TAG, "设备登录成功: ${user.uid}")
                        syncCurrentUser()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "设备登录失败", error)
                    }
                )
            } else {
                syncCurrentUser()
            }
            observeAuthStateChanges()
        }
    }

    private suspend fun observeAuthStateChanges() {
        authRepository.observeAuthState().collect { user ->
            if (user != null) {
                syncCurrentUser()
            } else {
                _currentUser.value = null
            }
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

    @SuppressLint("HardwareIds")
    private fun observeMyDevice() {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

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
            contactRepository.observeMyContacts().collect { contactList ->
                detectAndNotifyNewInvitations(contactList)
                _contacts.value = contactList
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
        viewModelScope.launch {
            while (true) {
                delay(60 * 60 * 1000L)
                if (authRepository.isSignedIn()) {
                    contactRepository.cleanupExpiredShares()
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

            result.onFailure { error ->
                _errorMessage.value = error.message ?: "接受失败，请重试"
            }
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
                        requestLocationUpdate(targetUid)
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
                val (name, photoUrl) = queryContactInfo(contactUri)

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
    // 位置追踪（委托给 TrackingManager）
    // ====================================================================

    fun requestLocationUpdate(targetUid: String) {
        trackingManager.requestLocationUpdate(_currentUser.value?.uid, targetUid)
    }

    fun startContinuousTracking(targetUid: String) {
        trackingManager.startContinuousTracking(_currentUser.value?.uid, targetUid)
    }

    fun stopContinuousTracking(targetUid: String) {
        trackingManager.stopContinuousTracking(_currentUser.value?.uid, targetUid)
    }

    // ====================================================================
    // 播放声音（委托给 TrackingManager）
    // ====================================================================

    fun requestPlaySound(targetUid: String) {
        trackingManager.requestPlaySound(_currentUser.value?.uid, targetUid)
    }

    fun requestStopSound(targetUid: String) {
        trackingManager.requestStopSound(_currentUser.value?.uid, targetUid)
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
}
