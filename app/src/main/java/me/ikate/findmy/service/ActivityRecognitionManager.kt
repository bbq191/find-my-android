package me.ikate.findmy.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/**
 * 活动识别管理器
 * 负责注册和取消活动转换监听
 */
class ActivityRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "ActivityRecognition"
        private const val REQUEST_CODE = 1001
    }

    private val activityRecognitionClient = ActivityRecognition.getClient(context)

    /**
     * 需要监听的活动类型
     */
    private val monitoredActivities = listOf(
        DetectedActivity.STILL,
        DetectedActivity.WALKING,
        DetectedActivity.RUNNING,
        DetectedActivity.ON_BICYCLE,
        DetectedActivity.IN_VEHICLE
    )

    /**
     * 创建活动转换请求
     */
    private fun createTransitionRequest(): ActivityTransitionRequest {
        val transitions = mutableListOf<ActivityTransition>()

        for (activityType in monitoredActivities) {
            // 监听进入和退出状态
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build()
            )
        }

        return ActivityTransitionRequest(transitions)
    }

    /**
     * 获取 PendingIntent
     */
    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * 检查活动识别权限
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 开始监听活动转换
     */
    fun startActivityTransitionUpdates(
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (!hasPermission()) {
            Log.w(TAG, "缺少活动识别权限")
            onFailure(SecurityException("Missing ACTIVITY_RECOGNITION permission"))
            return
        }

        val request = createTransitionRequest()
        val pendingIntent = getPendingIntent()

        activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "活动转换监听已启动")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "启动活动转换监听失败: ${e.message}")
                onFailure(e)
            }
    }

    /**
     * 停止监听活动转换
     */
    fun stopActivityTransitionUpdates(
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val pendingIntent = getPendingIntent()

        activityRecognitionClient.removeActivityTransitionUpdates(pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "活动转换监听已停止")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "停止活动转换监听失败: ${e.message}")
                onFailure(e)
            }
    }

    /**
     * 获取当前活动类型（单次检测）
     */
    fun getCurrentActivity(
        onResult: (SmartLocationConfig.ActivityType) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        if (!hasPermission()) {
            onResult(SmartLocationConfig.ActivityType.UNKNOWN)
            return
        }

        // 使用上次保存的活动状态作为当前状态
        // Activity Recognition API 不提供同步获取当前状态的方法
        val lastActivity = SmartLocationConfig.getLastActivity(context)
        onResult(lastActivity)
    }
}
