package me.ikate.findmy

import android.app.Application
import android.util.Log
import me.ikate.findmy.push.GeTuiManager
import me.ikate.findmy.service.MqttForegroundService

/**
 * FindMy 应用入口
 * 负责全局初始化
 */
class FindMyApplication : Application() {

    companion object {
        private const val TAG = "FindMyApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FindMyApplication 启动")

        // 初始化个推推送
        GeTuiManager.init(this)

        // 启动 MQTT 前台服务（保持后台连接）
        MqttForegroundService.start(this)
    }
}
