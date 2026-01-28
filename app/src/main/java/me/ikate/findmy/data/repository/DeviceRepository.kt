package me.ikate.findmy.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ikate.findmy.data.local.FindMyDatabase
import me.ikate.findmy.data.local.entity.DeviceEntity
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.remote.mqtt.LocationMqttService
import me.ikate.findmy.data.remote.mqtt.MqttConfig
import me.ikate.findmy.data.remote.mqtt.MqttConnectionManager

/**
 * 设备数据仓库
 * 使用 Room 进行本地持久化，MQTT 进行实时同步
 * Firestore 已移除，完全使用 MQTT + Room
 */
class DeviceRepository(private val context: Context? = null) {

    companion object {
        private const val TAG = "DeviceRepository"

        @Volatile
        private var mqttManager: MqttConnectionManager? = null

        @Volatile
        private var mqttService: LocationMqttService? = null

        /**
         * 获取 MQTT 连接管理器（单例）
         */
        @SuppressLint("HardwareIds")
        fun getMqttManager(context: Context): MqttConnectionManager {
            val ctx = context.applicationContext
            return mqttManager ?: synchronized(this) {
                mqttManager ?: run {
                    val userId = AuthRepository.getUserId(ctx)
                    val deviceId = userId // Android ID 同时作为用户 ID 和设备 ID
                    val clientId = MqttConfig.generateClientId(userId, deviceId)
                    MqttConnectionManager(ctx, clientId).also { mqttManager = it }
                }
            }
        }

        /**
         * 获取 MQTT 位置服务（单例）
         */
        fun getMqttService(context: Context): LocationMqttService {
            val ctx = context.applicationContext
            return mqttService ?: synchronized(this) {
                mqttService ?: LocationMqttService(
                    ctx,
                    getMqttManager(ctx)
                ).also { mqttService = it }
            }
        }
    }

    private val database = context?.let { FindMyDatabase.getInstance(it) }
    private val deviceDao = database?.deviceDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 实时监听设备列表变化
     * 从本地 Room 数据库读取，返回 Flow
     */
    fun observeDevices(): Flow<List<Device>> {
        val currentUserId = context?.let { AuthRepository.getUserId(it) }

        if (currentUserId == null || deviceDao == null) {
            Log.w(TAG, "无法获取用户ID或数据库未初始化，返回空流")
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }

        Log.d(TAG, "开始监听设备，当前用户ID: $currentUserId")

        // 从本地数据库观察可见设备
        return deviceDao.observeVisibleDevices(currentUserId).map { entities ->
            entities.map { it.toDomain() }.also { devices ->
                Log.d(TAG, "本地设备数量: ${devices.size}")
            }
        }
    }

    /**
     * 添加或更新设备（保存到本地 + 发布到 MQTT）
     */
    fun saveDevice(device: Device) {
        val ctx = context ?: run {
            Log.e(TAG, "Context 为空，无法保存设备")
            return
        }
        val currentUserId = AuthRepository.getUserId(ctx)

        scope.launch {
            // 1. 保存到本地 Room 数据库
            deviceDao?.insertOrUpdate(DeviceEntity.fromDomain(device))
            Log.d(TAG, "设备已保存到本地: ${device.id}")

            // 2. 发布到 MQTT（如果已配置）
            if (MqttConfig.isConfigured()) {
                val manager = getMqttManager(ctx)
                val service = getMqttService(ctx)

                // 确保 MQTT 已连接
                if (!manager.isConnected()) {
                    Log.d(TAG, "MQTT 未连接，尝试连接...")
                    val connectResult = manager.connect()
                    if (connectResult.isFailure) {
                        Log.w(TAG, "MQTT 连接失败: ${connectResult.exceptionOrNull()?.message}")
                        // 连接失败时，publishLocation 会自动将消息加入离线队列
                    }
                }

                val result = service.publishLocation(device)
                if (result.isSuccess) {
                    Log.d(TAG, "设备位置已发布到 MQTT: ${device.id}")
                } else {
                    Log.w(TAG, "MQTT 发布失败（已加入离线队列）: ${device.id}")
                }
            }
        }
    }

    /**
     * 连接 MQTT
     */
    suspend fun connectMqtt(): Result<Unit> {
        if (context == null) return Result.failure(Exception("Context 为空"))
        if (!MqttConfig.isConfigured()) {
            Log.w(TAG, "MQTT 未配置，跳过连接")
            return Result.success(Unit)
        }

        val manager = getMqttManager(context)
        return manager.connect()
    }

    /**
     * 断开 MQTT
     */
    suspend fun disconnectMqtt(): Result<Unit> {
        if (context == null) return Result.failure(Exception("Context 为空"))
        val manager = getMqttManager(context)
        return manager.disconnect()
    }

    /**
     * 订阅用户的位置更新
     */
    suspend fun subscribeToUser(userId: String): Result<Unit> {
        if (context == null) return Result.failure(Exception("Context 为空"))
        if (!MqttConfig.isConfigured()) return Result.success(Unit)

        val service = getMqttService(context)
        return service.subscribeToUser(userId)
    }

    /**
     * 获取设备（从本地数据库）
     */
    suspend fun getDevice(deviceId: String): Device? {
        return deviceDao?.getById(deviceId)?.toDomain()
    }

    /**
     * 删除设备
     */
    suspend fun deleteDevice(deviceId: String) {
        deviceDao?.deleteById(deviceId)
    }

    /**
     * 清空所有本地数据
     */
    suspend fun clearLocalData() {
        deviceDao?.deleteAll()
    }

    /**
     * 释放资源，取消内部 CoroutineScope
     */
    fun destroy() {
        scope.cancel()
    }
}
