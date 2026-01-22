### 🧱 一、 核心架构逻辑

在电子围栏场景中，逻辑必须是 **“端侧计算，云端同步”**。

1. **设定者 (Observer - App A):** 在腾讯地图上画圈 -> 存入 Firestore -> 发 FCM 通知对方同步。
2. **被监控者 (Target - App B/S24U):** 收到 FCM -> 从 Firestore 拉取围栏数据 -> 存入 Room -> **注册腾讯地理围栏 SDK**。
3. **触发 (Trigger):** S24U 走进围栏 -> 腾讯 SDK 唤醒 App -> **发送 MQTT 消息** -> App A 收到通知。

---

### 🧬 二、 数据模型设计 (Data Layer)

我们需要定义一个兼容 Room 和 Firestore 的实体类。

**注意：** 腾讯地图和高德一样使用 **GCJ-02** 坐标。为了开发方便，建议**全程使用 GCJ-02**，不要在数据库层转 WGS-84，避免来回转换产生精度误差。

```kotlin
@Entity(tableName = "fences")
data class FenceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val creatorId: String,   // 谁设定的 (User A)
    val targetId: String,    // 谁被监控 (User B)
    val name: String,        // "Home", "Work"
    val address: String,     // "xx路xx号"
    val latitude: Double,    // GCJ-02
    val longitude: Double,   // GCJ-02
    val radius: Float,       // 半径 (米)
    val triggerType: String, // "ENTER" 或 "EXIT"
    val isOneTime: Boolean,  // true=触发后删除 (iOS逻辑)
    val enable: Boolean = true
)

```

---

### ⚙️ 三、 核心实现：腾讯围栏服务 (Target 端)

这是运行在 S24 Ultra 上的核心代码。我们需要封装腾讯的 `TencentGeofenceManager`。

#### 1. 前台服务 (GeofenceService.kt)

为了在 S24U 息屏后保活，必须使用前台服务。

```kotlin
class GeofenceService : Service() {

    private lateinit var fenceManager: TencentGeofenceManager
    private val ACTION_TRIGGER = "com.neurone.findmy.FENCE_TRIGGER"

    override fun onCreate() {
        super.onCreate()
        // 1. 提升为前台服务 (S24U 保活关键)
        startForeground(1001, createNotification())
        
        // 2. 初始化腾讯围栏
        fenceManager = TencentGeofenceManager(this)
    }

    // 当 Room 数据更新时调用此方法
    fun refreshFences(fences: List<FenceEntity>) {
        fenceManager.removeAllFences() // 清除旧的

        fences.filter { it.enable }.forEach { fence ->
            // 3. 构建腾讯围栏对象
            val tencentFence = TencentGeofence.Builder()
                .setTag(fence.id) // 用 ID 做 Tag
                .setCircular(fence.latitude, fence.longitude, fence.radius)
                .setExpirationDuration(TencentGeofence.EXPIRATION_NEVER)
                .build()

            // 4. 注册
            val intent = Intent(ACTION_TRIGGER)
            // Android 12+ 必须加 FLAG_MUTABLE (因为腾讯SDK可能会回填数据)
            val pi = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            
            fenceManager.addFence(tencentFence, pi)
        }
    }

    private fun createNotification(): Notification {
        // 创建一个 "正在监视位置" 的通知
        // ... 代码省略 ...
    }
}

```

#### 2. 触发接收器 (GeofenceReceiver.kt)

当 S24U 进出围栏时，系统会广播这个 Receiver。

```kotlin
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.neurone.findmy.FENCE_TRIGGER") {
            // 1. 解析腾讯 SDK 返回的 Tag (即 FenceID)
            // 注意：腾讯的 extra key 比较特殊，建议查阅最新文档
            val fenceId = intent.getStringExtra(TencentGeofenceManager.EXTRA_TAG)
            
            // 2. 数据库查详情
            val db = RoomDB.get(context)
            val fence = db.fenceDao().getFence(fenceId) ?: return

            // 3. 核心：发送通知给对方 (MQTT)
            // 此时 App 可能在后台，网络可能断开，需要重连机制
            CoroutineScope(Dispatchers.IO).launch {
                notifyObserver(fence)
                
                // 4. 如果是一次性的，删除围栏
                if (fence.isOneTime) {
                    db.fenceDao().delete(fence)
                    Firestore.delete(fence.id)
                    // 重新刷新 Service
                }
            }
        }
    }

    private suspend fun notifyObserver(fence: FenceEntity) {
        val msg = "User B 已${if(fence.triggerType == "ENTER") "到达" else "离开"} ${fence.name}"
        // 发布 MQTT
        MqttClient.publish("notify/${fence.creatorId}", msg)
        // 双保险：写入 Firestore 消息列表
        Firestore.addNotification(fence.creatorId, msg)
    }
}

```

---

### 🖥️ 四、 UI 复刻 (User A 端)

使用腾讯地图 SDK 复刻 iOS 设定界面。

1. **地图底图：**
* 使用腾讯地图的默认样式（自带一种类似 iOS 的清爽感）。
* `mapView.map.uiSettings.isZoomControlsEnabled = false` (隐藏缩放按钮，保持极简)。


2. **绘制围栏 (UI):**
* 在地图中心添加一个 `Marker` (目标人物头像)。
* 添加一个 `Circle` (围栏范围)。
* **交互复刻：** 底部放置一个 `Slider` (SeekBar)。监听 Slider 变化 -> 实时更新 `circle.radius` -> 视觉上圈圈变大变小。


3. **逻辑复刻：**
* 当 Slider 拖动时，实时计算目标当前位置与圆心的距离。
* 如果 `距离 < 半径`，UI 上的 SegmentButton 自动高亮 [离开时通知]。
* 如果 `距离 > 半径`，自动高亮 [到达时通知]。
* *(这是 iOS 非常人性化的细节，Android 必须复刻)*。



---

### 🚀 五、 S24 Ultra 专属优化 (The "One UI" Factor)

在 S24U 上，即使有前台服务，腾讯 SDK 依然可能因为深度睡眠而延迟触发。结合你的 **FCM**，我们可以做一个 **“幽灵同步”** 机制。

#### 1. FCM 辅助同步

当 User A 设定好围栏后，发送一条 FCM 给 User B。

* **Payload:** `{"cmd": "SYNC_FENCE"}`
* **S24U 行为:** 收到 FCM -> 启动 `WorkManager` (加急) -> 拉取 Firestore 最新围栏 -> 重启 `GeofenceService`。
* **目的:** 确保 User B 的围栏配置是绝对实时的，不需要等 User B 打开 App。

#### 2. 腾讯 SDK 的省电模式

腾讯 `TencentGeofenceManager` 相比高德，稍微“懒”一点。

* **优化:** 在 S24U 上，建议围栏半径**不要小于 200米**。
* **原因:** 太小（比如 50米）的话，S24U 在车速较快时，可能穿过了围栏，腾讯 SDK 还没来得及唤醒 GPS，导致漏报。200米是安全距离。

---

### 📝 六、 总结：这套方案的可行性

| 维度 | 评价 | 备注 |
| --- | --- | --- |
| **地图体验** | ⭐⭐⭐⭐⭐ | 腾讯地图的 UI 风格非常适合做类 iOS 应用，且 SDK 轻量。 |
| **围栏精度** | ⭐⭐⭐⭐ | 城市内非常准，GCJ-02 无需转换。建议半径 >= 200m。 |
| **实时性** | ⭐⭐⭐⭐⭐ | 触发后通过 MQTT 秒级推送，体验极佳。 |
| **保活能力** | ⭐⭐⭐⭐ | S24U 配合前台服务 + FCM 唤醒，基本不死。 |
| **学习价值** | ⭐⭐⭐⭐⭐ | 涵盖了 Service、Broadcast、Room、MQTT、FCM、Map SDK，全栈移动开发知识点。 |
