package me.ikate.findmy.ui.screen.contact

import android.app.Application
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.ShareDuration
import me.ikate.findmy.data.repository.ContactRepository
import java.util.Locale

/**
 * 联系人功能 ViewModel
 * 管理联系人列表、位置共享操作和 UI 状态
 */
class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val contactRepository = ContactRepository()
    private val context = application

    companion object {
        private const val TAG = "ContactViewModel"
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
        observeContacts()
        syncCurrentUser()
    }

    // ====================================================================
    // 业务方法
    // ====================================================================

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
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
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
     * 同步当前用户信息
     */
    private fun syncCurrentUser() {
        viewModelScope.launch {
            val result = contactRepository.syncCurrentUser()
            result.onFailure { error ->
                Log.e(TAG, "同步用户信息失败", error)
                // 不显示错误给用户,静默失败
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
    fun shareLocation(email: String, duration: ShareDuration) {
        if (email.isBlank()) {
            _errorMessage.value = "请输入邮箱地址"
            return
        }

        // 简单的邮箱格式验证
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _errorMessage.value = "邮箱格式不正确"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val result = contactRepository.createLocationShare(email, duration)
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
                    // 成功后联系人列表会自动更新
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "停止共享失败，请重试"
                    Log.e(TAG, "停止共享失败", error)
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
