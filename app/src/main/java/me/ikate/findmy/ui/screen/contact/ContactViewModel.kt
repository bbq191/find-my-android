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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.repository.ContactRepository
import me.ikate.findmy.data.repository.DeviceRepository
import java.util.Locale

/**
 * 联系人功能 ViewModel
 * 管理联系人列表、位置共享操作和 UI 状态
 */
class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val contactRepository = ContactRepository()
    private val deviceRepository = DeviceRepository()
    private val context = application
    private val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

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
    val selectedContact: StateFlow<Contact?> = _selectedContact.asStateFlow()

    // 选中联系人的地址（反向地理编码结果）
    private val _contactAddress = MutableStateFlow<String?>(null)
    val contactAddress: StateFlow<String?> = _contactAddress.asStateFlow()

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

    init {
        loadLocalProfile()
        observeContacts()
        syncCurrentUser()
        observeMyDevice()
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

                val geocoder = Geocoder(context, Locale.getDefault())
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses: List<Address> ->
                        if (addresses.isNotEmpty()) {
                            _myAddress.value = formatAddress(addresses[0])
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        _myAddress.value = formatAddress(addresses[0])
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "获取我的地址失败", e)
            }
        }
    }
    
    /**
     * 格式化地址：去除国家、省、市、区及其分割逗号和多余空格
     */
    private fun formatAddress(address: Address): String {
        var fullAddress = address.getAddressLine(0) ?: return "未知位置"
        
        // 1. 动态移除所有已知的行政区划信息
        val components = listOfNotNull(
            address.countryName,
            address.adminArea,
            address.subAdminArea,
            address.locality,
            address.subLocality,
            "中国" // 保底移除
        )
        
        components.forEach { component ->
            fullAddress = fullAddress.replace(component, "")
        }
        
        // 2. 移除所有逗号和标点
        fullAddress = fullAddress.replace(",", "")
            .replace("，", "")
            .replace(" ", " ") // 统一空格
            
        // 3. 正则处理：将多个空格缩减为一个，并去除首尾空格
        fullAddress = fullAddress.replace("\\s+".toRegex(), " ").trim()
        
        // 4. 去除首部可能残留的连接符
        return fullAddress.trimStart('-', '、', ' ')
            .ifBlank { "未知具体位置" }
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
                        val nameIndex = it.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                        val photoIndex = it.getColumnIndex(android.provider.ContactsContract.Contacts.PHOTO_URI)
                        
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
     * 选中联系人并获取地址
     */
    fun selectContact(contact: Contact) {
        _selectedContact.value = contact
        _contactAddress.value = null // 重置地址
        
        contact.location?.let { latLng ->
            fetchAddress(latLng)
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
     * 获取地址（反向地理编码）
     */
    private fun fetchAddress(latLng: LatLng) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    _contactAddress.value = "无法获取地址(Geocoder不可用)"
                    return@launch
                }

                val geocoder = Geocoder(context, Locale.getDefault())
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses: List<Address> ->
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0]
                            val addressText = address.getAddressLine(0) ?: "未知位置"
                            _contactAddress.value = addressText
                        } else {
                            _contactAddress.value = "未找到地址"
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]
                        val addressText = address.getAddressLine(0) ?: "未知位置"
                        _contactAddress.value = addressText
                    } else {
                        _contactAddress.value = "未找到地址"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取地址失败", e)
                _contactAddress.value = "获取地址失败"
            }
        }
    }

    /**
     * 监听联系人列表
     */
    private fun observeContacts() {
        viewModelScope.launch {
            contactRepository.observeMyContacts().collect { contactList ->
                _contacts.value = contactList
                Log.d(TAG, "联系人列表更新: ${contactList.size} 个联系人")
            }
        }
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
    fun shareLocation(targetUid: String, duration: ShareDuration) {
        if (targetUid.isBlank()) {
            _errorMessage.value = "请输入对方 UID"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.createLocationShare(targetUid, duration)
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
            val result = contactRepository.stopSharing(shareId)
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
            val result = contactRepository.pauseLocationShare(contact.id)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    Log.d(TAG, "暂停共享成功: ${contact.id}")
                    // 更新选中联系人的状态 (如果需要)
                    if (_selectedContact.value?.id == contact.id) {
                         // 可以在这里局部更新，或者等待 repository 的 flow 更新
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
            val result = contactRepository.resumeLocationShare(contact.id, duration)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    Log.d(TAG, "恢复共享成功: ${contact.id}")
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
}