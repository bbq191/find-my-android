package me.ikate.findmy.data.remote.firestore

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.ikate.findmy.domain.statemachine.LocationStateMachine
import me.ikate.findmy.push.FCMMessageHandler
import me.ikate.findmy.service.TencentLocationService
import me.ikate.findmy.service.LostModeService
import me.ikate.findmy.service.MqttForegroundService
import me.ikate.findmy.service.SoundPlaybackService

/**
 * 指令执行器
 *
 * 负责执行从 Firestore 获取的远程指令，并更新执行状态
 *
 * 执行流程:
 * 1. 从 CommandRepository 获取待执行指令
 * 2. 标记指令为 EXECUTING
 * 3. 根据指令类型执行对应操作
 * 4. 标记指令为 EXECUTED 或 FAILED
 * 5. 上报执行结果到 Firestore
 */
class CommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "CommandExecutor"

        @Volatile
        private var instance: CommandExecutor? = null

        fun getInstance(context: Context): CommandExecutor {
            return instance ?: synchronized(this) {
                instance ?: CommandExecutor(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandRepository by lazy { CommandRepository.getInstance(context) }
    private val locationService by lazy { TencentLocationService(context) }

    /**
     * 同步并执行所有待执行指令
     * 通常在 FCM 唤醒后调用
     */
    fun syncAndExecuteCommands() {
        scope.launch {
            try {
                Log.d(TAG, "开始同步指令...")

                // 获取所有待执行指令
                val pendingCommands = commandRepository.getPendingCommands()
                if (pendingCommands.isEmpty()) {
                    Log.d(TAG, "没有待执行的指令")
                    return@launch
                }

                Log.d(TAG, "获取到 ${pendingCommands.size} 条待执行指令")

                // 依次执行指令
                for (command in pendingCommands) {
                    executeCommand(command)
                }

                Log.d(TAG, "所有指令执行完成")
            } catch (e: Exception) {
                Log.e(TAG, "同步指令失败", e)
            }
        }
    }

    /**
     * 执行单条指令
     */
    suspend fun executeCommand(command: CommandEntity) {
        val cmdId = command.cmdId
        val cmdType = command.getCommandType()

        if (cmdType == null) {
            Log.w(TAG, "未知指令类型: ${command.type}")
            commandRepository.markAsFailed(cmdId, "Unknown command type: ${command.type}")
            return
        }

        Log.d(TAG, "开始执行指令: $cmdType (id=$cmdId)")

        // 标记为执行中
        commandRepository.markAsExecuting(cmdId)

        try {
            val result = when (cmdType) {
                CommandType.RING -> executeRing(command)
                CommandType.STOP_RING -> executeStopRing(command)
                CommandType.LOCATE -> executeLocate(command)
                CommandType.LOCK -> executeLock(command)
                CommandType.UNLOCK -> executeUnlock(command)
                CommandType.START_TRACKING -> executeStartTracking(command)
                CommandType.STOP_TRACKING -> executeStopTracking(command)
            }

            if (result.success) {
                commandRepository.markAsExecuted(cmdId, result.data)
                Log.d(TAG, "指令执行成功: $cmdType")
            } else {
                commandRepository.markAsFailed(cmdId, result.error ?: "Unknown error")
                Log.w(TAG, "指令执行失败: $cmdType - ${result.error}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行指令异常: $cmdType", e)
            commandRepository.markAsFailed(cmdId, e.message ?: "Exception occurred")
        }
    }

    /**
     * 执行响铃指令
     */
    private suspend fun executeRing(command: CommandEntity): ExecutionResult {
        return try {
            // 使用 SoundPlaybackService 播放声音
            SoundPlaybackService.startPlaying(context, command.requesterUid)

            // 上报响铃状态
            commandRepository.reportRingStatus(triggered = true)

            ExecutionResult(
                success = true,
                data = mapOf("action" to "ring_started")
            )
        } catch (e: Exception) {
            ExecutionResult(success = false, error = e.message)
        }
    }

    /**
     * 执行停止响铃指令
     */
    private suspend fun executeStopRing(command: CommandEntity): ExecutionResult {
        return try {
            // 停止 SoundPlaybackService（统一管理所有响铃）
            // FCMMessageHandler.stopFindSound 现在也委托给 SoundPlaybackService，无需重复调用
            SoundPlaybackService.stopPlaying(context)

            // 上报响铃状态
            commandRepository.reportRingStatus(triggered = false)

            ExecutionResult(
                success = true,
                data = mapOf("action" to "ring_stopped")
            )
        } catch (e: Exception) {
            ExecutionResult(success = false, error = e.message)
        }
    }

    /**
     * 执行定位指令
     */
    private suspend fun executeLocate(command: CommandEntity): ExecutionResult {
        return try {
            // 获取高精度定位
            val location = locationService.getLocation(timeout = 30000L)

            if (location.isSuccess) {
                // 上报位置到 Firestore
                commandRepository.reportLocation(
                    latitude = location.latLng.latitude,
                    longitude = location.latLng.longitude,
                    accuracy = location.accuracy,
                    address = location.address
                )

                ExecutionResult(
                    success = true,
                    data = mapOf(
                        "latitude" to location.latLng.latitude,
                        "longitude" to location.latLng.longitude,
                        "accuracy" to location.accuracy,
                        "address" to (location.address ?: "")
                    )
                )
            } else {
                ExecutionResult(
                    success = false,
                    error = "Location failed: ${location.errorInfo}"
                )
            }
        } catch (e: Exception) {
            ExecutionResult(success = false, error = e.message)
        }
    }

    /**
     * 执行锁定(丢失模式)指令
     */
    private suspend fun executeLock(command: CommandEntity): ExecutionResult {
        return try {
            val params = command.params
            val message = params?.message ?: "此设备已丢失"
            val phoneNumber = params?.phoneNumber ?: ""
            val playSound = params?.playSound ?: true

            // 启用丢失模式服务
            LostModeService.enable(
                context = context,
                message = message,
                phoneNumber = phoneNumber,
                playSound = playSound,
                requesterUid = command.requesterUid
            )

            // 上报丢失模式状态
            commandRepository.reportLostModeStatus(
                enabled = true,
                message = message,
                phoneNumber = phoneNumber
            )

            ExecutionResult(
                success = true,
                data = mapOf("action" to "lost_mode_enabled")
            )
        } catch (e: Exception) {
            ExecutionResult(success = false, error = e.message)
        }
    }

    /**
     * 执行解锁(关闭丢失模式)指令
     */
    private suspend fun executeUnlock(command: CommandEntity): ExecutionResult {
        return try {
            // 关闭丢失模式
            LostModeService.disable(context)

            // 上报丢失模式状态
            commandRepository.reportLostModeStatus(enabled = false)

            ExecutionResult(
                success = true,
                data = mapOf("action" to "lost_mode_disabled")
            )
        } catch (e: Exception) {
            ExecutionResult(success = false, error = e.message)
        }
    }

    /**
     * 执行开始追踪指令
     */
    private suspend fun executeStartTracking(command: CommandEntity): ExecutionResult {
        return try {
            val requesterId = command.requesterUid ?: ""

            // 启动状态机进入实时追踪
            val stateMachine = LocationStateMachine.getInstance(context)
            stateMachine.handleEvent(
                LocationStateMachine.StateEvent.TrackingRequested(
                    requesterId = requesterId,
                    reason = "firestore_command"
                )
            )

            // 确保 MQTT 前台服务运行
            MqttForegroundService.start(context)

            ExecutionResult(
                success = true,
                data = mapOf("action" to "tracking_started")
            )
        } catch (e: Exception) {
            ExecutionResult(success = false, error = e.message)
        }
    }

    /**
     * 执行停止追踪指令
     */
    private suspend fun executeStopTracking(command: CommandEntity): ExecutionResult {
        return try {
            // 停止实时追踪
            val stateMachine = LocationStateMachine.getInstance(context)
            stateMachine.handleEvent(LocationStateMachine.StateEvent.StopTracking)

            ExecutionResult(
                success = true,
                data = mapOf("action" to "tracking_stopped")
            )
        } catch (e: Exception) {
            ExecutionResult(success = false, error = e.message)
        }
    }

    /**
     * 指令执行结果
     */
    data class ExecutionResult(
        val success: Boolean,
        val data: Map<String, Any>? = null,
        val error: String? = null
    )
}
