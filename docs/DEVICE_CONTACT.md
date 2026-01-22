这是一份为您精心设计的 **“Android 版 Find My (Ultimate Edition)”** 架构蓝图。

这个架构融合了 **Google 生态的先进性** (Firestore/FCM/Room) 和 **中国本土的实用性** (Amap)，并引入 **MQTT** 补足实时短板。对于手持 Samsung S24 Ultra 且拥有网络环境的开发者来说，这是学习 Android 现代开发架构（Jetpack, MVVM, Clean Architecture）的最佳练兵场。

---

# 🏗️ 项目代号：NeurOne Locator

**核心理念：** Offline First（离线优先）+ Event Driven（事件驱动）

## 一、 系统架构全景图

我们将系统分为三层，完全遵循 Google 推荐的 **Repository 模式**：

1. **UI Layer (Presentation):**

- **Map:** 高德地图 SDK (TextureMapView)
- **ViewModel:** 管理设备状态、好友列表、实时位置流 (LiveData/Flow)

2. **Domain Layer (Business Logic):**

- **SmartLocator:** 智能定位调度器（核心算法）
- **LocationRepository:** 决定数据是从数据库读，还是从网络拉

3. **Data Layer (Infrastructure):**

- **Local:** Room Database (本地缓存、历史轨迹)
- **Remote:** Firestore (用户资料、低频位置、设备列表)
- **Stream:** MQTT (实时高频坐标流)
- **Signal:** FCM (系统级唤醒指令)
- **Sensor:** Amap Location SDK (高德定位)

---

## 二、 核心模块详细设计

### 1. 数据层：Room + Firestore (双重真理源)

这是实现“离线可用”和“历史回溯”的关键。

- **Firestore (云端):**
- `users/{uid}`: 用户基础信息。
- `users/{uid}/devices/{deviceId}`: 设备信息（FCM Token, 电池电量, **Last_Location**）。
- _设计决策：Firestore 只存“最后一次已知位置”，不存高频轨迹（省钱）。_

- **Room (本地):**
- `LocationEntity`: 存储完整的轨迹点 (timestamp, lat, lng, speed, accuracy, isUploaded)。
- **作用：** 无论有没有网，定位数据先存 Room。有网时由 `WorkManager` 批量同步到 Firestore 的历史记录表（如果有回溯需求）或仅作为本地缓存。

### 2. 通讯层：FCM + MQTT (唤醒与流)

这是复刻 iOS 体验的精髓。

- **FCM (Firebase Cloud Messaging):** **只能用于“控制指令”**。
- 指令集 (Payload):
- `CMD_WAKE_UP`: 唤醒 App，开启 MQTT。
- `CMD_REPORT_ONCE`: 立即上报一次位置到 Firestore（不开启实时流）。
- `CMD_RING`: 播放声音（找设备）。

- _S24U 优势：_ 在三星手机上，FCM 是系统级服务，哪怕 App 被杀，FCM 也能拉起 `WorkManager` 执行逻辑。

- **MQTT (Message Queuing):** **只用于“实时直播”**。
- Topic: `live/{uid}`
- 逻辑：只有当 FCM 唤醒了 App，且 App 成功连接 MQTT 后，才开始向该 Topic 疯狂发送坐标。

### 3. 定位层：Amap + 坐标转换 (The Smart Locator)

- **数据源：** 高德定位 SDK。
- **痛点处理 (坐标系)：**
- **存储标准：** 建议全系统（Firestore/MQTT/Room）统一存储 **WGS-84** (国际标准)。
- **显示转换：** 仅在 UI 层（高德地图渲染时），将 WGS-84 转为 **GCJ-02**。
- _理由：_ 这样如果你以后想换 Mapbox 或 Google Maps，底层数据不用动。

---

## 三、 “智能定位”逻辑复刻 (State Machine)

要像 iOS 一样省电又实时，必须实现一个**状态机**：

#### 状态 A：静默守望 (Background / Idle)

- **触发机制：** `WorkManager` (Periodic, 15分钟间隔) 或 手机地理围栏 (Geofence)。
- **行为：**

1. 低功耗获取一次高德定位。
2. 写入 Room。
3. 更新 Firestore 的 `devices/{id}` 文档 (字段: `last_lat`, `last_lng`, `updated_at`, `battery`).
4. **不连接 MQTT。**

- **用户视角：** 家人打开 App，看到你是“15分钟前”的位置，电量 80%。

#### 状态 B：实时追踪 (Live Tracking)

- **触发机制：** 家人点击了你的头像 -> 家人 App 发送 FCM (`CMD_WAKE_UP`) -> 你的 S24U 收到 FCM。
- **行为：**

1. FCM 唤醒 `WakefulBroadcastReceiver` 或 `JobService`。
2. 启动 **前台服务 (Foreground Service)**，挂一个通知“正在共享位置”。
3. 连接 MQTT Broker。
4. 高德开启 `Hight_Accuracy` 模式 (间隔 2秒)。
5. 收到坐标 -> 转 WGS84 -> Publish 到 MQTT Topic。

- **用户视角：** 家人地图上的你的头像开始平滑移动，状态变为“现在”。

#### 状态 C：自动休眠 (Auto Sleep)

- **逻辑：** 为了防止忘记关闭导致 S24U 没电。
- **行为：** 开启实时追踪后，启动一个 60 秒的倒计时。如果在 60 秒内没有收到 MQTT 的 `KEEP_ALIVE` 心跳包（说明家人退出了 App 或锁屏了），自动销毁前台服务，切回状态 A。

---

## 四、 针对 S24 Ultra 的开发 CheckList

由于您的设备是 Samsung S24 Ultra，在开发时拥有得天独厚的优势，但也需要注意：

1. **GMS 依赖：** 确保开发机和测试机都安装了 Google Play Services 且网络通畅（FCM 强依赖）。
2. **Battery Unrestricted:** 虽然 FCM 权限很高，但为了 MQTT 稳定性，建议代码检测并引导用户去“电池 -> 无限制”设置白名单。
3. **One UI 适配:** 三星的 One UI 对前台服务通知的样式有特殊处理，建议设置 `NotificationChannel` 的优先级为 `IMPORTANCE_LOW`，以免一直发出叮咚声干扰用户。

## 五、 代码结构参考 (Kotlin)

这是您用来学习的推荐目录结构：

```text
com.neurone.locator
├── data
│   ├── local (Room DAO, Database)
│   ├── remote (FirestoreService, MqttClient, FcmService)
│   ├── model (User, Device, LocationPoint)
│   └── repository (LocationRepository) // 协调 Room 和 Firestore
├── domain
│   ├── usecase (GetLiveLocationUseCase, SyncHistoryUseCase)
│   └── service (SmartLocationService) // 前台服务，核心逻辑
├── ui
│   ├── map (MapFragment, TextureMapView)
│   └── viewmodel (MainViewModel)
├── utils
│   ├── CoordConverter.kt // GCJ02 <-> WGS84
│   └── NetworkUtils.kt
└── worker
    ├── PeriodicUploadWorker.kt // 15分钟一次的打卡
    └── WakeUpWorker.kt // FCM 唤醒后的加急任务

```

## 六、 总结

这套架构是 **Android 开发的集大成者**。

- **学到了什么？**
- **Room:** 本地持久化与缓存策略。
- **Firestore:** NoSQL 数据库设计与实时快照。
- **MQTT:** 工业级长连接协议。
- **FCM:** Google 生态的核心推送机制。
- **Service & WorkManager:** Android 复杂的后台任务调度体系。
- **Amap SDK:** 地图与 GIS 基础。

对于自用来说，只要网络能连上 Google，这套系统的体验是 **最接近 iOS 原生** 的，比纯国产方案更省电、更稳定（得益于 FCM 的系统级保活）。祝您开发愉快！
