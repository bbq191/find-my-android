# FindMy for Android

一款仿照 iOS Find My 应用的 Android 定位追踪应用，支持实时位置共享、设备管理、地理围栏等功能。

## 功能特性

### 地图定位
- **Mapbox 矢量地图**：支持标准、卫星、地形等多种图层
- **高德精准定位**：GPS/WiFi/基站混合定位，精度高达 5 米
- **自动坐标转换**：GCJ-02 与 WGS-84 坐标系自动转换

### 位置共享
- **实时位置共享**：向联系人分享实时位置
- **共享管理**：支持暂停、恢复、移除共享关系
- **定时共享**：设置共享过期时间

### 设备管理
- **多设备管理**：管理手机、平板多种设备
- **远程响铃**：远程播放声音帮助定位设备
- **丢失模式**：设备丢失时显示 SOS 信息

### 地理围栏
- **电子围栏**：设置地理围栏区域
- **进出提醒**：设备进入/离开围栏时收到通知

### 智能定位
- **活动识别**：自动识别静止/行走/跑步/驾车状态
- **自适应上报**：根据活动状态和电量动态调整上报频率
- **省电模式**：低电量时自动延长上报间隔

## 技术栈

| 类别 | 技术 |
|------|------|
| 最低 SDK | Android 15 (API 36) |
| UI 框架 | Jetpack Compose + Material Design 3 |
| 数据库 | Room |
| 网络通信 | MQTT (Eclipse Paho) |
| 后台任务 | WorkManager |
| 图片加载 | Coil |
| 安全存储 | Security Crypto |

## 第三方服务配置

> **重要提示**：本项目依赖多个第三方服务，需要自行注册并配置相应的密钥才能正常使用。

### 1. 个推（GeTui）推送服务

用于设备间的消息推送，如远程响铃、丢失模式通知等。

**注册步骤**：
1. 访问 [个推开发者平台](https://dev.getui.com/) 注册账号
2. 创建应用，获取以下配置信息：
   - `APP_ID`
   - `APP_KEY`
   - `APP_SECRET`
3. 在 `app/build.gradle.kts` 中配置：

```kotlin
android {
    defaultConfig {
        manifestPlaceholders["GETUI_APPID"] = "你的APP_ID"
    }
}
```

4. 在 `AndroidManifest.xml` 中补充配置（如需要）

### 2. EMQX Cloud（MQTT 服务）

用于实时位置同步和设备间通信。

**注册步骤**：
1. 访问 [EMQX Cloud](https://www.emqx.com/zh/cloud) 注册账号
2. 创建 Serverless 部署（免费额度足够个人使用）
3. 获取以下配置信息：
   - 连接地址（Broker URL）
   - 端口号（通常为 8883/TLS 或 1883）
   - 用户名和密码
4. 在项目中配置 MQTT 连接参数：

```kotlin
// 在 data/remote/MqttConfig.kt 或相应配置文件中修改
object MqttConfig {
    const val BROKER_URL = "ssl://你的地址.emqxsl.cn:8883"
    const val USERNAME = "你的用户名"
    const val PASSWORD = "你的密码"
}
```

### 3. Mapbox 地图服务

用于地图显示和导航。

**注册步骤**：
1. 访问 [Mapbox 官网](https://www.mapbox.com/) 注册账号
2. 在 Account 页面获取 Access Token
3. 创建 `app/src/main/res/values/mapbox_access_token.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="mapbox_access_token">你的_ACCESS_TOKEN</string>
</resources>
```

4. 或者在 `gradle.properties` 中配置：

```properties
MAPBOX_DOWNLOADS_TOKEN=你的_SECRET_TOKEN
```

**注意**：Mapbox 需要两种 Token：
- **Public Token**：用于 SDK 调用
- **Secret Token**：用于下载 SDK（需添加到 `gradle.properties`）

### 4. 高德地图（AMap）定位服务

用于获取设备位置和地理围栏功能。

**注册步骤**：
1. 访问 [高德开放平台](https://lbs.amap.com/) 注册账号
2. 创建应用，选择 Android 平台
3. 获取 API Key（注意需要绑定应用的 SHA1 和包名）
4. 在 `AndroidManifest.xml` 中配置：

```xml
<application>
    <meta-data
        android:name="com.amap.api.v2.apikey"
        android:value="你的_API_KEY"/>
</application>
```

**获取 SHA1 指纹**：
```bash
# Debug 版本
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android

# Release 版本
keytool -list -v -keystore your-release-key.keystore -alias your-alias
```

## 配置文件清单

请确保以下配置文件正确配置：

| 文件 | 用途 | 必需 |
|------|------|:----:|
| `gradle.properties` | Mapbox Secret Token | Yes |
| `app/src/main/res/values/mapbox_access_token.xml` | Mapbox Public Token | Yes |
| `AndroidManifest.xml` | 高德 API Key、个推配置 | Yes |
| `data/remote/MqttConfig.kt` | MQTT 连接参数 | Yes |

## 构建与运行

### 环境要求
- Android Studio Ladybug 或更高版本
- JDK 17
- Android SDK 36
- Gradle 8.x

### 构建步骤

1. **克隆项目**
```bash
git clone https://github.com/your-username/findmy.git
cd findmy
```

2. **配置第三方服务**（参考上方配置说明）

3. **同步 Gradle**
```bash
./gradlew --refresh-dependencies
```

4. **构建 Debug 版本**
```bash
./gradlew assembleDebug
```

5. **安装到设备**
```bash
./gradlew installDebug
```

### 常见问题

**Q: 编译时提示 Mapbox SDK 下载失败**

A: 请确保在 `gradle.properties` 中正确配置了 `MAPBOX_DOWNLOADS_TOKEN`。

**Q: 定位功能无法使用**

A: 检查以下几点：
1. 高德 API Key 是否正确配置
2. API Key 绑定的 SHA1 是否与当前签名一致
3. 是否已授予应用位置权限

**Q: 推送消息收不到**

A: 检查以下几点：
1. 个推配置是否正确
2. 是否已授予应用通知权限
3. 检查设备是否开启了省电模式

**Q: MQTT 连接失败**

A: 检查以下几点：
1. EMQX Cloud 服务是否正常
2. 用户名密码是否正确
3. 是否使用了正确的端口（TLS: 8883, TCP: 1883）

## 项目结构

```
app/src/main/java/me/ikate/findmy/
├── MainActivity.kt          # 主入口
├── FindMyApplication.kt     # Application 类
├── data/
│   ├── model/               # 数据模型
│   ├── local/               # Room 数据库
│   ├── remote/              # MQTT 服务
│   └── repository/          # 数据仓库
├── service/                 # 业务服务（定位、围栏等）
├── ui/                      # UI 界面
│   ├── screen/              # 页面
│   ├── components/          # 公共组件
│   ├── dialog/              # 对话框
│   └── theme/               # 主题
├── worker/                  # WorkManager 任务
├── util/                    # 工具类
└── push/                    # 推送服务
```

## 权限说明

| 权限 | 用途 |
|------|------|
| `ACCESS_FINE_LOCATION` | 精确位置定位 |
| `ACCESS_COARSE_LOCATION` | 粗略位置定位 |
| `ACCESS_BACKGROUND_LOCATION` | 后台持续定位 |
| `POST_NOTIFICATIONS` | 发送通知 |
| `INTERNET` | 网络通信 |
| `READ_CONTACTS` | 读取联系人 |
| `VIBRATE` | 震动提醒 |
| `FOREGROUND_SERVICE` | 前台服务保活 |

## 隐私声明

本应用会收集以下信息：
- 设备位置信息（用于位置共享和定位功能）
- 设备信息（用于设备管理）
- 联系人信息（仅用于添加位置共享联系人，不会上传服务器）

所有位置数据均通过加密通道传输，并且用户可随时停止位置共享。

## 许可证

[Apache 2.0](LICENSE)

## 致谢

- [Mapbox](https://www.mapbox.com/) - 地图服务
- [高德开放平台](https://lbs.amap.com/) - 定位服务
- [个推](https://www.getui.com/) - 推送服务
- [EMQX](https://www.emqx.com/) - MQTT 消息服务
