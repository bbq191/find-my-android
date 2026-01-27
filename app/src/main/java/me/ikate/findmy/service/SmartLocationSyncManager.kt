package me.ikate.findmy.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.ShareDirection
import me.ikate.findmy.data.model.ShareStatus
import me.ikate.findmy.data.repository.ContactRepository
import me.ikate.findmy.data.repository.DeviceRepository

/**
 * 智能位置同步管理器
 * iOS Find My 风格：持续同步，自动更新
 *
 * 功能：
 * - 自动订阅所有已接受共享联系人的位置主题
 * - 管理选中联系人的高频更新
 * - 响应应用前后台状态切换
 *
 * 更新频率策略：
 * | 场景 | 更新间隔 |
 * |------|----------|
 * | 选中联系人 + 前台 | 5 秒 |
 * | 前台普通联系人 | 1 分钟 |
 * | 后台 | 5 分钟 |
 * | 低电量后台 | 10 分钟 |
 */
class SmartLocationSyncManager(
    private val context: Context,
    private val contactRepository: ContactRepository,
    private val locationReportService: LocationReportService
) {
    companion object {
        private const val TAG = "SmartLocationSyncManager"

        // 更新间隔常量
        private const val INTERVAL_FOCUSED_MS = 5_000L       // 选中联系人：5秒
        private const val INTERVAL_FOREGROUND_MS = 60_000L   // 前台普通：1分钟
        private const val INTERVAL_BACKGROUND_MS = 300_000L  // 后台：5分钟
        private const val INTERVAL_LOW_BATTERY_MS = 600_000L // 低电量后台：10分钟

        // 低电量阈值
        private const val LOW_BATTERY_THRESHOLD = 20

        @Volatile
        private var instance: SmartLocationSyncManager? = null

        fun getInstance(context: Context): SmartLocationSyncManager {
            return instance ?: synchronized(this) {
                instance ?: SmartLocationSyncManager(
                    context = context.applicationContext,
                    contactRepository = ContactRepository(context.applicationContext),
                    locationReportService = LocationReportService(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 当前选中的联系人 UID（用于高频更新）
    private val _focusedContactUid = MutableStateFlow<String?>(null)
    val focusedContactUid: StateFlow<String?> = _focusedContactUid.asStateFlow()

    // 应用是否在前台
    private val _isAppForeground = MutableStateFlow(true)
    val isAppForeground: StateFlow<Boolean> = _isAppForeground.asStateFlow()

    // 同步任务
    private var focusedSyncJob: Job? = null
    private var backgroundSyncJob: Job? = null

    // 已订阅的联系人 UID 集合
    private val subscribedContacts = mutableSetOf<String>()

    /**
     * 初始化同步管理器
     * 应在应用启动时调用
     */
    fun initialize() {
        Log.i(TAG, "初始化智能位置同步管理器")

        // 监听联系人列表变化，自动订阅/取消订阅
        scope.launch {
            contactRepository.observeMyContacts().collect { contacts ->
                updateSubscriptions(contacts)
            }
        }

        // 启动后台同步
        startBackgroundSync()
    }

    /**
     * 设置选中的联系人
     * 选中后会启动高频位置刷新（每5秒）
     *
     * @param uid 联系人 UID，null 表示取消选中
     */
    fun setFocusedContact(uid: String?) {
        val previousUid = _focusedContactUid.value

        if (previousUid == uid) {
            return // 没有变化
        }

        Log.i(TAG, "设置选中联系人: ${previousUid ?: "无"} -> ${uid ?: "无"}")
        _focusedContactUid.value = uid

        // 取消之前的高频刷新任务
        focusedSyncJob?.cancel()
        focusedSyncJob = null

        // 如果有新的选中联系人且在前台，启动高频刷新
        if (uid != null && _isAppForeground.value) {
            startFocusedSync(uid)
        }
    }

    /**
     * 应用进入前台
     */
    fun onAppForeground() {
        if (_isAppForeground.value) return

        Log.i(TAG, "应用进入前台")
        _isAppForeground.value = true

        // 重启后台同步（使用前台间隔）
        restartBackgroundSync()

        // 如果有选中联系人，启动高频刷新
        _focusedContactUid.value?.let { uid ->
            startFocusedSync(uid)
        }

        // 立即刷新所有联系人位置
        refreshAllContacts()
    }

    /**
     * 应用进入后台
     */
    fun onAppBackground() {
        if (!_isAppForeground.value) return

        Log.i(TAG, "应用进入后台")
        _isAppForeground.value = false

        // 停止高频刷新（节省电量）
        focusedSyncJob?.cancel()
        focusedSyncJob = null

        // 重启后台同步（使用后台间隔）
        restartBackgroundSync()
    }

    /**
     * 刷新所有联系人位置
     * 向所有已接受共享的联系人发送位置请求
     */
    fun refreshAllContacts() {
        scope.launch {
            try {
                val contacts = contactRepository.observeMyContacts().first()
                val validContacts = contacts.filter { contact ->
                    contact.shareStatus == ShareStatus.ACCEPTED &&
                    !contact.isPaused &&
                    (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
                     contact.shareDirection == ShareDirection.MUTUAL)
                }

                Log.i(TAG, "刷新所有联系人位置，数量: ${validContacts.size}")

                // 先上报自己的位置
                locationReportService.reportCurrentLocation()

                // 向每个联系人发送位置请求
                validContacts.forEach { contact ->
                    contact.targetUserId?.let { targetUid ->
                        requestLocationUpdate(targetUid)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "刷新所有联系人位置失败", e)
            }
        }
    }

    /**
     * 刷新单个联系人位置
     */
    fun refreshContact(targetUid: String) {
        scope.launch {
            try {
                Log.d(TAG, "刷新联系人位置: $targetUid")

                // 先上报自己的位置
                locationReportService.reportCurrentLocation()

                // 发送位置请求
                requestLocationUpdate(targetUid)
            } catch (e: Exception) {
                Log.e(TAG, "刷新联系人位置失败: $targetUid", e)
            }
        }
    }

    /**
     * 更新订阅状态
     * 根据联系人列表变化自动订阅/取消订阅
     */
    private suspend fun updateSubscriptions(contacts: List<Contact>) {
        val mqttService = DeviceRepository.getMqttService(context)

        // 计算需要订阅的联系人
        val shouldSubscribe = contacts.filter { contact ->
            contact.shareStatus == ShareStatus.ACCEPTED &&
            !contact.isPaused &&
            (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
             contact.shareDirection == ShareDirection.MUTUAL)
        }.mapNotNull { it.targetUserId }.toSet()

        // 新增订阅
        val toSubscribe = shouldSubscribe - subscribedContacts
        toSubscribe.forEach { uid ->
            mqttService.subscribeToUser(uid)
            subscribedContacts.add(uid)
            Log.d(TAG, "订阅联系人位置: $uid")
        }

        // 取消订阅
        val toUnsubscribe = subscribedContacts - shouldSubscribe
        toUnsubscribe.forEach { uid ->
            mqttService.unsubscribeFromUser(uid)
            subscribedContacts.remove(uid)
            Log.d(TAG, "取消订阅联系人位置: $uid")
        }

        if (toSubscribe.isNotEmpty() || toUnsubscribe.isNotEmpty()) {
            Log.i(TAG, "订阅状态更新: +${toSubscribe.size} -${toUnsubscribe.size}, 当前订阅数: ${subscribedContacts.size}")
        }
    }

    /**
     * 启动选中联系人的高频同步
     */
    private fun startFocusedSync(targetUid: String) {
        focusedSyncJob?.cancel()

        focusedSyncJob = scope.launch {
            Log.i(TAG, "启动高频同步: $targetUid (每${INTERVAL_FOCUSED_MS / 1000}秒)")

            // 立即刷新一次
            refreshContact(targetUid)

            // 然后定期刷新
            while (true) {
                delay(INTERVAL_FOCUSED_MS)

                // 检查是否仍然选中且在前台
                if (_focusedContactUid.value != targetUid || !_isAppForeground.value) {
                    Log.d(TAG, "停止高频同步: 联系人已取消选中或应用进入后台")
                    break
                }

                refreshContact(targetUid)
            }
        }
    }

    /**
     * 启动后台同步
     */
    private fun startBackgroundSync() {
        backgroundSyncJob?.cancel()

        backgroundSyncJob = scope.launch {
            while (true) {
                val interval = calculateSyncInterval()
                Log.d(TAG, "后台同步等待: ${interval / 1000}秒")

                delay(interval)

                // 刷新所有非选中的联系人
                refreshNonFocusedContacts()
            }
        }
    }

    /**
     * 重启后台同步
     */
    private fun restartBackgroundSync() {
        backgroundSyncJob?.cancel()
        startBackgroundSync()
    }

    /**
     * 刷新非选中的联系人
     */
    private suspend fun refreshNonFocusedContacts() {
        try {
            val focusedUid = _focusedContactUid.value
            val contacts = contactRepository.observeMyContacts().first()
            val validContacts = contacts.filter { contact ->
                contact.shareStatus == ShareStatus.ACCEPTED &&
                !contact.isPaused &&
                (contact.shareDirection == ShareDirection.THEY_SHARE_TO_ME ||
                 contact.shareDirection == ShareDirection.MUTUAL) &&
                contact.targetUserId != focusedUid // 排除选中的联系人
            }

            if (validContacts.isEmpty()) return

            Log.d(TAG, "后台刷新联系人位置，数量: ${validContacts.size}")

            // 先上报自己的位置
            locationReportService.reportCurrentLocation()

            // 向每个联系人发送位置请求
            validContacts.forEach { contact ->
                contact.targetUserId?.let { targetUid ->
                    requestLocationUpdate(targetUid)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "后台刷新联系人位置失败", e)
        }
    }

    /**
     * 计算同步间隔
     */
    private fun calculateSyncInterval(): Long {
        return if (_isAppForeground.value) {
            INTERVAL_FOREGROUND_MS
        } else {
            if (isLowBattery()) {
                INTERVAL_LOW_BATTERY_MS
            } else {
                INTERVAL_BACKGROUND_MS
            }
        }
    }

    /**
     * 检查是否低电量
     */
    private fun isLowBattery(): Boolean {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                100
            }
            batteryPct <= LOW_BATTERY_THRESHOLD
        } catch (e: Exception) {
            Log.w(TAG, "获取电量失败", e)
            false
        }
    }

    /**
     * 发送位置请求
     */
    private suspend fun requestLocationUpdate(targetUid: String) {
        try {
            val mqttManager = DeviceRepository.getMqttManager(context)
            val currentUid = me.ikate.findmy.data.repository.AuthRepository(context).getCurrentUserId()

            if (currentUid == null) {
                Log.w(TAG, "当前用户未登录，无法请求位置")
                return
            }

            // 确保 MQTT 已连接
            if (!mqttManager.isConnected()) {
                Log.d(TAG, "MQTT 未连接，尝试连接...")
                val connectResult = mqttManager.connect()
                if (connectResult.isFailure) {
                    Log.w(TAG, "MQTT 连接失败: ${connectResult.exceptionOrNull()?.message}")
                    return
                }
            }

            val requestData = mapOf(
                "requesterUid" to currentUid,
                "targetUid" to targetUid,
                "type" to "single",
                "timestamp" to System.currentTimeMillis()
            )

            val topic = "findmy/requests/$targetUid"
            val payload = com.google.gson.Gson().toJson(requestData)

            mqttManager.publish(topic, payload).fold(
                onSuccess = {
                    Log.d(TAG, "位置请求已发送: $targetUid")
                },
                onFailure = { error ->
                    Log.w(TAG, "位置请求发送失败: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "发送位置请求异常", e)
        }
    }

    /**
     * 销毁同步管理器
     */
    fun destroy() {
        Log.i(TAG, "销毁智能位置同步管理器")
        focusedSyncJob?.cancel()
        backgroundSyncJob?.cancel()
        scope.cancel()
        locationReportService.destroy()
        instance = null
    }
}
