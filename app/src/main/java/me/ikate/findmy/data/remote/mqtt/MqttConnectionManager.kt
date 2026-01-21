package me.ikate.findmy.data.remote.mqtt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSocketFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.math.pow

/**
 * MQTT 连接管理器
 * 基于 Eclipse Paho 客户端，提供协程友好的 API
 */
class MqttConnectionManager(
    private val context: Context,
    private val clientId: String
) {
    companion object {
        private const val TAG = "MqttConnectionManager"
    }

    // 连接状态
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val message: String, val throwable: Throwable? = null) : ConnectionState()
    }

    // 收到的消息
    data class ReceivedMessage(
        val topic: String,
        val payload: String,
        val qos: Int,
        val retained: Boolean
    )

    private var mqttClient: MqttAsyncClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 连接状态
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // 收到的消息流
    private val _messageFlow = MutableSharedFlow<ReceivedMessage>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val messageFlow: SharedFlow<ReceivedMessage> = _messageFlow.asSharedFlow()

    // 订阅的主题
    private val subscribedTopics = ConcurrentHashMap<String, Int>()

    // 重连相关
    private var reconnectAttempt = 0
    private var isReconnecting = false

    // 连接锁，防止重复连接
    @Volatile
    private var isConnecting = false
    private val connectLock = Any()

    // 等待连接完成的回调列表
    private val pendingConnectCallbacks = mutableListOf<(Result<Unit>) -> Unit>()

    /**
     * 连接到 MQTT Broker
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!MqttConfig.isConfigured()) {
            return@withContext Result.failure(Exception("MQTT 配置无效，请检查 local.properties"))
        }

        if (_connectionState.value == ConnectionState.Connected) {
            return@withContext Result.success(Unit)
        }

        // 使用同步块防止并发连接
        val shouldWait = synchronized(connectLock) {
            if (isConnecting) {
                Log.d(TAG, "MQTT 连接已在进行中，等待连接完成...")
                true
            } else {
                isConnecting = true
                false
            }
        }

        // 如果连接正在进行中，等待连接完成
        if (shouldWait) {
            return@withContext suspendCancellableCoroutine { continuation ->
                synchronized(connectLock) {
                    // 再次检查，可能在等待锁期间连接已完成
                    if (_connectionState.value == ConnectionState.Connected) {
                        continuation.resume(Result.success(Unit))
                        return@suspendCancellableCoroutine
                    }
                    if (!isConnecting) {
                        // 连接已结束但未成功，返回当前状态
                        val state = _connectionState.value
                        if (state is ConnectionState.Error) {
                            continuation.resume(Result.failure(state.throwable ?: Exception(state.message)))
                        } else {
                            continuation.resume(Result.failure(Exception("连接状态异常")))
                        }
                        return@suspendCancellableCoroutine
                    }
                    pendingConnectCallbacks.add { result ->
                        continuation.resume(result)
                    }
                }
            }
        }

        _connectionState.value = ConnectionState.Connecting
        reconnectAttempt = 0

        try {
            // 创建客户端
            val persistence = MemoryPersistence()
            mqttClient = MqttAsyncClient(
                MqttConfig.brokerUrl,
                clientId,
                persistence
            )

            // 设置回调
            mqttClient?.setCallback(createCallback())

            // 连接选项
            val options = MqttConnectOptions().apply {
                userName = MqttConfig.username
                password = MqttConfig.password.toCharArray()
                connectionTimeout = MqttConfig.CONNECTION_TIMEOUT
                keepAliveInterval = MqttConfig.KEEP_ALIVE_INTERVAL
                isAutomaticReconnect = false // 我们自己管理重连
                isCleanSession = MqttConfig.CLEAN_SESSION

                // SSL 配置（EMQX Cloud 需要 SSL）
                if (MqttConfig.brokerUrl.startsWith("ssl://")) {
                    socketFactory = SSLSocketFactory.getDefault()
                }
            }

            // 执行连接
            suspendCancellableCoroutine { continuation ->
                mqttClient?.connect(options, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "MQTT 连接成功")
                        _connectionState.value = ConnectionState.Connected
                        reconnectAttempt = 0
                        notifyPendingCallbacks(Result.success(Unit))
                        isConnecting = false
                        continuation.resume(Unit)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "MQTT 连接失败", exception)
                        _connectionState.value = ConnectionState.Error(
                            exception?.message ?: "连接失败",
                            exception
                        )
                        val error = exception ?: Exception("MQTT 连接失败")
                        notifyPendingCallbacks(Result.failure(error))
                        isConnecting = false
                        continuation.resumeWithException(error)
                    }
                })
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "MQTT 连接异常", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "连接异常", e)
            notifyPendingCallbacks(Result.failure(e))
            isConnecting = false
            Result.failure(e)
        }
    }

    /**
     * 通知所有等待连接完成的回调
     */
    private fun notifyPendingCallbacks(result: Result<Unit>) {
        synchronized(connectLock) {
            pendingConnectCallbacks.forEach { callback ->
                try {
                    callback(result)
                } catch (e: Exception) {
                    Log.e(TAG, "通知回调失败", e)
                }
            }
            pendingConnectCallbacks.clear()
        }
    }

    /**
     * 断开连接
     */
    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            isReconnecting = false
            mqttClient?.let { client ->
                if (client.isConnected) {
                    suspendCancellableCoroutine { continuation ->
                        client.disconnect(null, object : IMqttActionListener {
                            override fun onSuccess(asyncActionToken: IMqttToken?) {
                                Log.d(TAG, "MQTT 断开连接成功")
                                continuation.resume(Unit)
                            }

                            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                Log.e(TAG, "MQTT 断开连接失败", exception)
                                continuation.resume(Unit) // 仍然继续
                            }
                        })
                    }
                }
                client.close()
            }
            mqttClient = null
            subscribedTopics.clear()
            _connectionState.value = ConnectionState.Disconnected
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "断开连接异常", e)
            Result.failure(e)
        }
    }

    /**
     * 发布消息
     */
    suspend fun publish(
        topic: String,
        payload: String,
        qos: Int = 1,
        retained: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.i(TAG, "[MQTT发布] 准备发布消息...")
        Log.i(TAG, "[MQTT发布] 主题: $topic")
        Log.i(TAG, "[MQTT发布] QoS: $qos, 保留: $retained")
        Log.d(TAG, "[MQTT发布] 内容: ${payload.take(150)}")

        val client = mqttClient
        if (client == null) {
            Log.e(TAG, "[MQTT发布] ✗ 客户端为空")
            return@withContext Result.failure(Exception("MQTT 客户端为空"))
        }

        if (!client.isConnected) {
            Log.e(TAG, "[MQTT发布] ✗ MQTT 未连接")
            return@withContext Result.failure(Exception("MQTT 未连接"))
        }

        Log.i(TAG, "[MQTT发布] MQTT 连接状态: 已连接")

        try {
            val message = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
                this.qos = qos
                this.isRetained = retained
            }

            suspendCancellableCoroutine { continuation ->
                client.publish(topic, message, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "[MQTT发布] ✓ 消息发布成功: $topic")
                        continuation.resume(Unit)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "[MQTT发布] ✗ 消息发布失败: $topic", exception)
                        continuation.resumeWithException(
                            exception ?: Exception("发布失败")
                        )
                    }
                })
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[MQTT发布] ✗ 发布消息异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 订阅主题
     */
    suspend fun subscribe(topic: String, qos: Int = 1): Result<Unit> = withContext(Dispatchers.IO) {
        val client = mqttClient
        if (client == null || !client.isConnected) {
            return@withContext Result.failure(Exception("MQTT 未连接"))
        }

        try {
            suspendCancellableCoroutine { continuation ->
                client.subscribe(topic, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "订阅成功: $topic")
                        subscribedTopics[topic] = qos
                        continuation.resume(Unit)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "订阅失败: $topic", exception)
                        continuation.resumeWithException(
                            exception ?: Exception("订阅失败")
                        )
                    }
                })
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "订阅异常", e)
            Result.failure(e)
        }
    }

    /**
     * 取消订阅
     */
    suspend fun unsubscribe(topic: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = mqttClient
        if (client == null || !client.isConnected) {
            subscribedTopics.remove(topic)
            return@withContext Result.success(Unit)
        }

        try {
            suspendCancellableCoroutine { continuation ->
                client.unsubscribe(topic, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG, "取消订阅成功: $topic")
                        subscribedTopics.remove(topic)
                        continuation.resume(Unit)
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "取消订阅失败: $topic", exception)
                        subscribedTopics.remove(topic)
                        continuation.resume(Unit) // 仍然继续
                    }
                })
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "取消订阅异常", e)
            Result.failure(e)
        }
    }

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true
    }

    /**
     * 创建 MQTT 回调
     */
    private fun createCallback(): MqttCallbackExtended {
        return object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d(TAG, "连接完成 (重连: $reconnect)")
                _connectionState.value = ConnectionState.Connected

                // 重新订阅之前的主题
                if (reconnect && subscribedTopics.isNotEmpty()) {
                    scope.launch {
                        resubscribeAll()
                    }
                }
            }

            override fun connectionLost(cause: Throwable?) {
                // 检查是否是因为同一个 Client ID 的新连接导致的断开
                // 这种情况下会收到 EOFException，不需要重连
                val isClientIdConflict = cause is java.io.EOFException ||
                    cause?.cause is java.io.EOFException

                if (isClientIdConflict) {
                    Log.d(TAG, "连接被新实例取代（正常行为）")
                    _connectionState.value = ConnectionState.Disconnected
                    // 不重连，因为新实例已经连接了
                    return
                }

                Log.w(TAG, "连接丢失", cause)
                _connectionState.value = ConnectionState.Error(
                    cause?.message ?: "连接丢失",
                    cause
                )

                // 尝试重连
                scope.launch {
                    attemptReconnect()
                }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic != null && message != null) {
                    val payload = String(message.payload, Charsets.UTF_8)
                    Log.i(TAG, "========== [MQTT接收] 收到消息 ==========")
                    Log.i(TAG, "[MQTT接收] 主题: $topic")
                    Log.i(TAG, "[MQTT接收] QoS: ${message.qos}, 保留: ${message.isRetained}")
                    Log.d(TAG, "[MQTT接收] 内容: ${payload.take(150)}")

                    scope.launch {
                        Log.d(TAG, "[MQTT接收] 发送到 messageFlow...")
                        _messageFlow.emit(
                            ReceivedMessage(
                                topic = topic,
                                payload = payload,
                                qos = message.qos,
                                retained = message.isRetained
                            )
                        )
                        Log.d(TAG, "[MQTT接收] ✓ 消息已分发")
                    }
                } else {
                    Log.w(TAG, "[MQTT接收] 收到空消息: topic=$topic, message=$message")
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // 消息投递完成
            }
        }
    }

    /**
     * 重新订阅所有主题
     */
    private suspend fun resubscribeAll() {
        subscribedTopics.forEach { (topic, qos) ->
            try {
                subscribe(topic, qos)
            } catch (e: Exception) {
                Log.e(TAG, "重新订阅失败: $topic", e)
            }
        }
    }

    /**
     * 尝试重连（指数退避，有最大重试次数限制）
     */
    private suspend fun attemptReconnect() {
        if (isReconnecting) return
        isReconnecting = true

        while (isReconnecting && _connectionState.value != ConnectionState.Connected) {
            reconnectAttempt++

            // 检查是否超过最大重试次数
            if (reconnectAttempt > MqttConfig.MAX_RECONNECT_ATTEMPTS) {
                Log.w(TAG, "已达到最大重连次数 (${MqttConfig.MAX_RECONNECT_ATTEMPTS})，停止重连")
                isReconnecting = false
                _connectionState.value = ConnectionState.Error(
                    "已达到最大重连次数，请检查网络后手动重连",
                    null
                )
                break
            }

            val delaySeconds = min(
                MqttConfig.MAX_RECONNECT_DELAY.toLong(),
                2.0.pow(reconnectAttempt.toDouble()).toLong()
            )

            Log.d(TAG, "将在 ${delaySeconds}秒 后尝试重连 (第 $reconnectAttempt/${MqttConfig.MAX_RECONNECT_ATTEMPTS} 次)")
            delay(delaySeconds * 1000)

            if (!isReconnecting) break

            _connectionState.value = ConnectionState.Connecting
            val result = connect()
            if (result.isSuccess) {
                isReconnecting = false
                break
            }
        }
    }

    /**
     * 停止重连
     */
    fun stopReconnect() {
        Log.d(TAG, "停止 MQTT 重连")
        isReconnecting = false
        reconnectAttempt = 0
    }

    /**
     * 释放资源
     */
    fun destroy() {
        stopReconnect()
        scope.launch {
            disconnect()
        }
    }
}
