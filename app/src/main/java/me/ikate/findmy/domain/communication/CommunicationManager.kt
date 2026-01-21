package me.ikate.findmy.domain.communication

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ikate.findmy.data.local.FindMyDatabase
import me.ikate.findmy.data.remote.mqtt.MqttConnectionManager
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.service.MqttForegroundService
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.pow

/**
 * 通讯管理器
 *
 * 统一管理 MQTT 和 FCM 通讯，提供：
 * - 智能重连（指数退避 + 网络感知）
 * - FCM 唤醒后确保 MQTT 连接
 * - 离线消息队列管理
 * - 消息去重
 * - 连接状态监控
 *
 * 重连策略：
 * ```
 * 重连间隔 = min(MAX_DELAY, BASE_DELAY * 2^attempt)
 * 抖动 = random(0, interval * 0.1)
 * 实际间隔 = interval + 抖动
 * ```
 */
class CommunicationManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CommunicationManager"

        // 重连配置
        private const val BASE_RECONNECT_DELAY_MS = 1000L      // 基础延迟 1秒
        private const val MAX_RECONNECT_DELAY_MS = 60000L      // 最大延迟 60秒
        private const val MAX_RECONNECT_ATTEMPTS = 20          // 最大重试次数
        private const val JITTER_FACTOR = 0.1                  // 抖动因子 10%

        // 离线队列配置
        private const val QUEUE_FLUSH_INTERVAL_MS = 30000L     // 队列刷新间隔 30秒
        private const val MAX_QUEUE_RETRY_COUNT = 5            // 单条消息最大重试次数
        private const val QUEUE_RETRY_DELAY_MS = 5000L         // 队列重试延迟 5秒

        // 消息去重配置
        private const val MESSAGE_DEDUP_WINDOW_MS = 60000L     // 去重窗口 60秒
        private const val MAX_DEDUP_CACHE_SIZE = 1000          // 最大缓存消息数

        @Volatile
        private var instance: CommunicationManager? = null

        fun getInstance(context: Context): CommunicationManager {
            return instance ?: synchronized(this) {
                instance ?: CommunicationManager(context.applicationContext).also {
                    instance = it
                    it.initialize() // 自动初始化
                }
            }
        }
    }

    /**
     * 连接状态
     */
    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        FAILED
    }

    /**
     * 网络类型
     */
    enum class NetworkType {
        NONE,
        WIFI,
        CELLULAR,
        OTHER
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 连接状态
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    // 网络状态
    private val _networkType = MutableStateFlow(NetworkType.NONE)
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    // 离线消息数量
    private val _pendingMessageCount = MutableStateFlow(0)
    val pendingMessageCount: StateFlow<Int> = _pendingMessageCount.asStateFlow()

    // 重连统计
    private val _reconnectStats = MutableStateFlow(ReconnectStats())
    val reconnectStats: StateFlow<ReconnectStats> = _reconnectStats.asStateFlow()

    // 网络回调
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // 重连任务
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var isReconnecting = false

    // 队列刷新任务
    private var queueFlushJob: Job? = null

    // 消息去重缓存: messageId -> timestamp
    private val processedMessages = ConcurrentHashMap<String, Long>()

    // 数据库
    private val database by lazy { FindMyDatabase.getInstance(context) }
    private val pendingMessageDao by lazy { database.pendingMessageDao() }

    /**
     * 重连统计
     */
    data class ReconnectStats(
        val totalAttempts: Int = 0,
        val successfulReconnects: Int = 0,
        val lastReconnectTime: Long = 0,
        val currentAttempt: Int = 0,
        val nextRetryDelayMs: Long = 0
    )

    // 是否已初始化
    @Volatile
    private var isInitialized = false

    /**
     * 初始化通讯管理器
     */
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "通讯管理器已初始化，跳过")
            return
        }
        isInitialized = true
        Log.i(TAG, "初始化通讯管理器")

        // 注册网络状态监听
        registerNetworkCallback()

        // 监听 MQTT 连接状态
        observeMqttConnectionState()

        // 启动队列刷新任务
        startQueueFlushTask()

        // 启动去重缓存清理
        startDedupCacheCleanup()

        // 更新离线消息数量
        updatePendingMessageCount()
    }

    /**
     * 监听 MQTT 连接状态变化
     */
    private fun observeMqttConnectionState() {
        scope.launch {
            try {
                val mqttManager = DeviceRepository.getMqttManager(context)
                mqttManager.connectionState.collect { state ->
                    _connectionStatus.value = when (state) {
                        is MqttConnectionManager.ConnectionState.Connected -> ConnectionStatus.CONNECTED
                        is MqttConnectionManager.ConnectionState.Connecting -> ConnectionStatus.CONNECTING
                        is MqttConnectionManager.ConnectionState.Disconnected -> ConnectionStatus.DISCONNECTED
                        is MqttConnectionManager.ConnectionState.Error -> ConnectionStatus.FAILED
                    }
                    Log.d(TAG, "MQTT 连接状态更新: ${_connectionStatus.value}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "监听 MQTT 连接状态失败", e)
            }
        }
    }

    /**
     * 注册网络状态监听
     */
    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "网络可用")
                updateNetworkType(connectivityManager)
                onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "网络丢失")
                _networkType.value = NetworkType.NONE
                onNetworkLost()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                updateNetworkType(connectivityManager)
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)

        // 初始化网络状态
        updateNetworkType(connectivityManager)
    }

    /**
     * 更新网络类型
     */
    private fun updateNetworkType(connectivityManager: ConnectivityManager) {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        _networkType.value = when {
            capabilities == null -> NetworkType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.OTHER
        }
    }

    /**
     * 网络恢复时的处理
     */
    private fun onNetworkAvailable() {
        scope.launch {
            // 如果之前断开连接，尝试重连
            if (_connectionStatus.value == ConnectionStatus.DISCONNECTED ||
                _connectionStatus.value == ConnectionStatus.FAILED
            ) {
                Log.d(TAG, "网络恢复，尝试重连 MQTT")
                reconnectAttempt = 0  // 重置重试计数
                attemptReconnect()
            }

            // 刷新离线队列
            delay(2000)  // 等待连接稳定
            flushPendingMessages()
        }
    }

    /**
     * 网络丢失时的处理
     */
    private fun onNetworkLost() {
        // 停止重连任务（等待网络恢复）
        stopReconnect()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    /**
     * FCM 唤醒后确保 MQTT 连接
     * 由 FCMMessageHandler 调用
     */
    fun ensureMqttConnection() {
        scope.launch {
            Log.d(TAG, "FCM 唤醒，确保 MQTT 连接")

            // 检查当前连接状态
            try {
                val mqttManager = DeviceRepository.getMqttManager(context)

                if (mqttManager.isConnected()) {
                    Log.d(TAG, "MQTT 已连接")
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    return@launch
                }

                // 确保前台服务运行
                MqttForegroundService.start(context)

                // 等待一段时间让服务启动
                delay(1000)

                // 如果还未连接，尝试连接
                if (!mqttManager.isConnected()) {
                    Log.d(TAG, "MQTT 未连接，尝试连接...")
                    _connectionStatus.value = ConnectionStatus.CONNECTING

                    val result = mqttManager.connect()
                    if (result.isSuccess) {
                        Log.d(TAG, "MQTT 连接成功")
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        reconnectAttempt = 0

                        // 刷新离线队列
                        flushPendingMessages()
                    } else {
                        Log.w(TAG, "MQTT 连接失败，启动重连")
                        attemptReconnect()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "确保 MQTT 连接失败", e)
                attemptReconnect()
            }
        }
    }

    /**
     * 尝试重连（指数退避 + 抖动）
     */
    private fun attemptReconnect() {
        if (isReconnecting) {
            Log.d(TAG, "重连任务已在运行")
            return
        }

        if (_networkType.value == NetworkType.NONE) {
            Log.d(TAG, "无网络，等待网络恢复")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            return
        }

        isReconnecting = true
        _connectionStatus.value = ConnectionStatus.RECONNECTING

        reconnectJob = scope.launch {
            while (isReconnecting && reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempt++

                // 计算延迟（指数退避 + 抖动）
                val baseDelay = min(
                    MAX_RECONNECT_DELAY_MS,
                    (BASE_RECONNECT_DELAY_MS * 2.0.pow(reconnectAttempt - 1)).toLong()
                )
                val jitter = (baseDelay * JITTER_FACTOR * Math.random()).toLong()
                val actualDelay = baseDelay + jitter

                // 更新统计
                _reconnectStats.value = _reconnectStats.value.copy(
                    totalAttempts = _reconnectStats.value.totalAttempts + 1,
                    currentAttempt = reconnectAttempt,
                    nextRetryDelayMs = actualDelay
                )

                Log.d(TAG, "重连尝试 $reconnectAttempt/$MAX_RECONNECT_ATTEMPTS，延迟 ${actualDelay}ms")

                delay(actualDelay)

                if (!isReconnecting) break

                // 检查网络
                if (_networkType.value == NetworkType.NONE) {
                    Log.d(TAG, "网络不可用，暂停重连")
                    break
                }

                try {
                    val mqttManager = DeviceRepository.getMqttManager(context)
                    val result = mqttManager.connect()

                    if (result.isSuccess) {
                        Log.i(TAG, "重连成功（第 $reconnectAttempt 次尝试）")
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        _reconnectStats.value = _reconnectStats.value.copy(
                            successfulReconnects = _reconnectStats.value.successfulReconnects + 1,
                            lastReconnectTime = System.currentTimeMillis(),
                            currentAttempt = 0,
                            nextRetryDelayMs = 0
                        )
                        isReconnecting = false
                        reconnectAttempt = 0

                        // 刷新离线队列
                        flushPendingMessages()
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "重连失败: ${e.message}")
                }
            }

            if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
                Log.w(TAG, "达到最大重试次数，停止重连")
                _connectionStatus.value = ConnectionStatus.FAILED
                isReconnecting = false
            }
        }
    }

    /**
     * 停止重连
     */
    fun stopReconnect() {
        isReconnecting = false
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /**
     * 手动触发重连
     */
    fun manualReconnect() {
        Log.d(TAG, "手动触发重连")
        stopReconnect()
        reconnectAttempt = 0
        attemptReconnect()
    }

    /**
     * 启动队列刷新任务
     */
    private fun startQueueFlushTask() {
        queueFlushJob = scope.launch {
            while (true) {
                delay(QUEUE_FLUSH_INTERVAL_MS)

                if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                    flushPendingMessages()
                }
            }
        }
    }

    /**
     * 刷新离线消息队列
     */
    suspend fun flushPendingMessages() {
        try {
            val mqttService = DeviceRepository.getMqttService(context)
            val sentCount = mqttService.flushPendingMessages()

            if (sentCount > 0) {
                Log.d(TAG, "已发送 $sentCount 条离线消息")
            }

            // 更新计数
            updatePendingMessageCount()

            // 清理超过重试次数的消息
            cleanupFailedMessages()

        } catch (e: Exception) {
            Log.e(TAG, "刷新离线队列失败", e)
        }
    }

    /**
     * 更新离线消息数量
     */
    private fun updatePendingMessageCount() {
        scope.launch {
            try {
                val count = pendingMessageDao.getPendingCount()
                _pendingMessageCount.value = count
            } catch (e: Exception) {
                Log.w(TAG, "获取离线消息数量失败", e)
            }
        }
    }

    /**
     * 清理失败次数过多的消息
     */
    private suspend fun cleanupFailedMessages() {
        try {
            pendingMessageDao.deleteFailedMessages(MAX_QUEUE_RETRY_COUNT)
        } catch (e: Exception) {
            Log.w(TAG, "清理失败消息异常", e)
        }
    }

    /**
     * 检查消息是否已处理（去重）
     *
     * @param messageId 消息ID
     * @return true 如果已处理过，false 如果是新消息
     */
    fun isMessageProcessed(messageId: String): Boolean {
        val now = System.currentTimeMillis()
        val processedTime = processedMessages[messageId]

        if (processedTime != null && (now - processedTime) < MESSAGE_DEDUP_WINDOW_MS) {
            Log.d(TAG, "消息已处理过: $messageId")
            return true
        }

        // 标记为已处理
        processedMessages[messageId] = now
        return false
    }

    /**
     * 标记消息为已处理
     */
    fun markMessageProcessed(messageId: String) {
        processedMessages[messageId] = System.currentTimeMillis()
    }

    /**
     * 启动去重缓存清理
     */
    private fun startDedupCacheCleanup() {
        scope.launch {
            while (true) {
                delay(MESSAGE_DEDUP_WINDOW_MS)
                cleanupDedupCache()
            }
        }
    }

    /**
     * 清理过期的去重缓存
     */
    private fun cleanupDedupCache() {
        val now = System.currentTimeMillis()
        val expiredKeys = processedMessages.entries
            .filter { (now - it.value) > MESSAGE_DEDUP_WINDOW_MS }
            .map { it.key }

        expiredKeys.forEach { processedMessages.remove(it) }

        // 如果缓存过大，删除最老的条目
        if (processedMessages.size > MAX_DEDUP_CACHE_SIZE) {
            val sortedEntries = processedMessages.entries.sortedBy { it.value }
            val toRemove = sortedEntries.take(processedMessages.size - MAX_DEDUP_CACHE_SIZE / 2)
            toRemove.forEach { processedMessages.remove(it.key) }
        }

        if (expiredKeys.isNotEmpty()) {
            Log.d(TAG, "清理了 ${expiredKeys.size} 条过期去重缓存")
        }
    }

    /**
     * 生成消息ID（用于去重）
     */
    fun generateMessageId(topic: String, payload: String): String {
        return "$topic:${payload.hashCode()}:${System.currentTimeMillis() / 1000}"
    }

    /**
     * 获取通讯状态摘要
     */
    fun getStatusSummary(): CommunicationStatus {
        return CommunicationStatus(
            connectionStatus = _connectionStatus.value,
            networkType = _networkType.value,
            pendingMessageCount = _pendingMessageCount.value,
            reconnectStats = _reconnectStats.value,
            dedupCacheSize = processedMessages.size
        )
    }

    /**
     * 通讯状态摘要
     */
    data class CommunicationStatus(
        val connectionStatus: ConnectionStatus,
        val networkType: NetworkType,
        val pendingMessageCount: Int,
        val reconnectStats: ReconnectStats,
        val dedupCacheSize: Int
    )

    /**
     * 释放资源
     */
    fun destroy() {
        Log.d(TAG, "销毁通讯管理器")

        stopReconnect()
        queueFlushJob?.cancel()

        networkCallback?.let {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
        }

        processedMessages.clear()
        scope.cancel()
        instance = null
    }
}
