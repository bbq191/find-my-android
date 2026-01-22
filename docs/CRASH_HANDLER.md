针对您正在开发的 Android “查找”应用（涉及 Mapbox、高德、MQTT 等复杂组件），崩溃（Crash）是不可避免的。尤其是 S24 Ultra 这种新机型，可能会遇到兼容性问题。

**“未雨绸缪”**（构建优雅的全局捕获机制）部分。

---

### 一：如何构建一个“优雅”的崩溃收集功能？

所谓的“优雅”，是指：

1. **不弹系统丑陋的弹窗**：“xx应用已停止运行”。
2. **兜底保护**：尝试保存现场，甚至自动重启 App。
3. **静默上报**：下次启动时，悄悄把日志传给你的 Firestore 或 MQTT。

#### 方案 A：集成成熟 SDK (推荐生产环境)

如果你为了省事，直接用 **Firebase Crashlytics** 或 **腾讯 Bugly**。

* **优点：** 自动去混淆，后台图表漂亮，能捕获 Native Crash。
* **缺点：** 它是第三方的，不够 Geek，且国内网络下 Firebase 可能上报失败。

#### 方案 B：自研 CrashHandler (推荐学习与自用)

这是 Android 开发者的必修课。我们需要实现 `Thread.UncaughtExceptionHandler` 接口。

以下是为您定制的 **CrashMonitor** 实现方案：

#### 1. 核心代码：全局异常捕获器

新建文件 `utils/CrashHandler.kt`：

```kotlin
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashHandler : Thread.UncaughtExceptionHandler {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var context: Context
    
    // 初始化
    fun init(ctx: Context) {
        context = ctx.applicationContext
        // 1. 获取系统默认的 Handler (用于兜底)
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        // 2. 将自己设为全局默认 Handler
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        if (!handleException(ex) && defaultHandler != null) {
            // 如果我们处理失败，或者是系统级错误，交给系统默认处理 (弹窗并杀进程)
            defaultHandler?.uncaughtException(thread, ex)
        } else {
            // 优雅退出逻辑
            try {
                Thread.sleep(2000) // 给点时间把日志写完
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            
            // 3. 可以在这里重启 App (可选)
            // restartApp() 

            // 4. 杀掉进程
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }

    private fun handleException(ex: Throwable): Boolean {
        if (ex == null) return false
        
        // 1. 收集错误信息
        val logInfo = getCrashInfo(ex)
        
        // 2. 保存到本地文件 (Android/data/包名/files/crash_logs/)
        saveCrashInfo2File(logInfo)
        
        // 3. 可以在这里直接通过 MQTT 发送给备用机 (如果网络还活着)
        // MqttClient.publish("crash/report", logInfo)

        // 4. UI 提示 (Toast 只能在 Looper 线程弹)
        object : Thread() {
            override fun run() {
                Looper.prepare()
                // 这里可以弹一个 Toast，或者启动一个全屏的 "崩溃致歉 Activity"
                android.widget.Toast.makeText(context, "很抱歉，程序出现异常即将退出", android.widget.Toast.LENGTH_LONG).show()
                Looper.loop()
            }
        }.start()

        return true
    }

    private fun getCrashInfo(ex: Throwable): String {
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        ex.printStackTrace(printWriter)
        var cause = ex.cause
        while (cause != null) {
            cause.printStackTrace(printWriter)
            cause = cause.cause
        }
        printWriter.close()
        val result = writer.toString()
        
        val sb = StringBuffer()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        sb.append("Time: $time\n")
        sb.append("Device: Samsung S24 Ultra\n") // 可以动态获取 Build.MODEL
        sb.append("Android: ${android.os.Build.VERSION.RELEASE}\n")
        sb.append("\nStack Trace:\n")
        sb.append(result)
        return sb.toString()
    }

    private fun saveCrashInfo2File(info: String) {
        val fileName = "crash-${System.currentTimeMillis()}.log"
        // 存到私有目录，不需要额外申请权限
        val dir = File(context.getExternalFilesDir(null), "crash_logs")
        if (!dir.exists()) dir.mkdirs()
        
        try {
            val fos = FileOutputStream(File(dir, fileName))
            fos.write(info.toByteArray())
            fos.close()
            Log.e("CrashHandler", "Crash saved to: ${dir.absolutePath}/$fileName")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

```

#### 2. 初始化 (Application)

在你的 `MyApplication.kt` 中尽早初始化：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化崩溃捕获
        CrashHandler.init(this)
        
        // 其他 SDK 初始化...
    }
}

```

#### 3. 进阶：做一个“优雅的”崩溃展示页

当 `handleException` 发生时，直接杀进程体验很差。你可以启动一个独立的 `CrashActivity`。

```kotlin
// 在 handleException 中：
val intent = Intent(context, CrashActivity::class.java)
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
intent.putExtra("LOG", logInfo)
context.startActivity(intent)

```

**CrashActivity 的设计：**

* **UI:** 显示一个大大的 "Oops!" 图标。
* **信息:** 显示简略的错误原因（不要把整个堆栈给用户看）。
* **按钮:**
* [重启应用]: 重新启动 `MainActivity`。
* [上传日志]: 手动触发上传到 Firestore。
* [复制日志]: 方便你直接发给 DeepSeek 或粘贴到电脑分析。



---

### 二： 结合你的架构：如何利用 Firestore/MQTT？

既然你是为了自用，可以做得更 Hardcore 一点：

1. **下次启动上传 (Firestore):**
   在 `MainActivity` 的 `onCreate` 里检查 `crash_logs` 文件夹下有没有文件。如果有，读取内容，上传到 Firestore 的 `crash_reports` 集合，上传成功后删除本地文件。
   *这样你就能在控制台看到昨晚 S24U 在后台到底为什么死了。*

### 总结

1. **找回已消失的日志：** 用 `adb shell dumpsys dropbox`。
2. **构建收集器：** 结合实际情况使用上面的 `CrashHandler` 代码。
3. **优雅处理：** 不要让 App 闪退消失，而是跳到一个专门的 `CrashActivity`，给用户（也就是你自己）一个重启的机会。