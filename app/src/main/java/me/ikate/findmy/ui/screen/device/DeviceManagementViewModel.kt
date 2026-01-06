package me.ikate.findmy.ui.screen.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.DeviceType
import me.ikate.findmy.data.repository.DeviceRepository

/**
 * 设备管理 ViewModel
 * 负责设备的添加、编辑、删除操作
 */
class DeviceManagementViewModel : ViewModel() {

    private val deviceRepository = DeviceRepository()

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

    /**
     * 添加设备
     */
    fun addDevice(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double,
        deviceType: DeviceType
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            try {
                val device = Device(
                    id = id,
                    name = name,
                    location = LatLng(latitude, longitude),
                    battery = 100,
                    lastUpdateTime = System.currentTimeMillis(),
                    isOnline = true,
                    deviceType = deviceType
                )
                deviceRepository.saveDevice(device)
                _operationState.value = OperationState.Success("设备添加成功")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("设备添加失败: ${e.message}")
            }
        }
    }

    /**
     * 更新设备
     */
    fun updateDevice(device: Device) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            try {
                deviceRepository.saveDevice(device)
                _operationState.value = OperationState.Success("设备更新成功")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("设备更新失败: ${e.message}")
            }
        }
    }

    /**
     * 删除设备
     */
    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            try {
                deviceRepository.deleteDevice(deviceId)
                _operationState.value = OperationState.Success("设备删除成功")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("设备删除失败: ${e.message}")
            }
        }
    }

    /**
     * 重置状态
     */
    fun resetState() {
        _operationState.value = OperationState.Idle
    }
}

/**
 * 操作状态
 */
sealed class OperationState {
    data object Idle : OperationState()
    data object Loading : OperationState()
    data class Success(val message: String) : OperationState()
    data class Error(val message: String) : OperationState()
}
