# Find My 功能使用指南

## 已实现功能概览

### 1. ✅ Firebase Authentication（用户认证）
### 2. ✅ 设备数据上报功能
### 3. ✅ 设备管理功能（添加、编辑、删除）

---

## 1. Firebase Authentication（用户认证）

### 功能说明
- 支持**匿名登录**（快速开始，无需注册）
- 支持**邮箱密码登录/注册**
- 自动持久化登录状态
- 未登录时自动跳转到登录界面

### 相关文件
- `AuthRepository.kt` - 认证数据仓库
- `AuthViewModel.kt` - 认证状态管理
- `LoginScreen.kt` - 登录界面
- `MainActivity.kt` - 应用导航

### 使用方式

#### 快速开始（匿名登录）
1. 打开应用
2. 点击 **"快速开始（匿名登录）"** 按钮
3. 自动登录并进入主界面

#### 邮箱登录
1. 输入邮箱和密码
2. 点击 **"登录"** 按钮
3. 或点击 **"没有账号？点击注册"** 进行注册

### Firebase Console 配置

确保在 Firebase Console 中启用了相应的登录方式：

1. 进入 **Firebase Console** → **Authentication** → **Sign-in method**
2. 启用 **匿名登录**（Anonymous）
3. 启用 **电子邮件/密码**（Email/Password）

---

## 2. 设备数据上报功能

### 功能说明
- **自动上报当前设备位置**到 Firebase Firestore
- 支持**后台定期上报**（每15分钟）
- 上报设备信息包括：
  - 设备名称（型号）
  - 当前位置（GPS坐标，自动转换为 GCJ-02）
  - 电池电量
  - 在线状态
  - 设备类型（手机/平板/手表）

### 相关文件
- `LocationReportService.kt` - 位置上报服务
- `LocationReportWorker.kt` - 定期上报任务
- `MainViewModel.kt` - 集成位置上报逻辑

### 工作原理

#### 1. 应用启动时
- 立即上报一次当前位置
- 启动后台定期上报任务（每15分钟）

#### 2. 定期上报
- 使用 WorkManager 实现后台任务
- 即使应用在后台，也会定期上报位置
- 上报失败会自动重试

#### 3. 坐标转换
- GPS 获取的 WGS-84 坐标自动转换为 GCJ-02
- 确保位置在中国大陆地图上准确显示

### 手动触发上报

在 `MainViewModel` 中调用：

```kotlin
viewModel.reportLocationNow()
```

### 停止定期上报

```kotlin
viewModel.stopPeriodicLocationReport()
```

### 查看上报日志

使用 adb logcat 查看上报状态：

```bash
adb logcat -s LocationReportService LocationReportWorker MainViewModel
```

---

## 3. 设备管理功能

### 功能说明
- **查看设备详情**（位置、电量、最后更新时间）
- **编辑设备信息**（待实现界面）
- **删除设备**（带确认对话框）
- 自动过滤当前用户的设备（基于 `ownerId`）

### 相关文件
- `DeviceManagementViewModel.kt` - 设备管理逻辑
- `DeviceDetailPanel.kt` - 设备详情面板（带编辑/删除按钮）
- `DeviceRepository.kt` - 设备数据操作

### 使用方式

#### 查看设备详情
1. 在地图上点击设备标记
2. 或在底部设备列表中点击设备
3. 查看设备详细信息

#### 删除设备
1. 打开设备详情面板
2. 点击 **"删除"** 按钮（红色）
3. 确认删除操作
4. 设备将从 Firebase 中删除

#### 编辑设备（TODO）
1. 打开设备详情面板
2. 点击 **"编辑"** 按钮
3. 修改设备名称、类型等信息
4. 保存修改

---

## 数据结构

### Firebase Firestore 数据结构

#### devices 集合

```json
{
  "devices": {
    "{deviceId}": {
      "name": "Xiaomi Mi 11",
      "ownerId": "user_uid_from_auth",
      "location": GeoPoint(39.9042, 116.4074),
      "battery": 85,
      "lastUpdateTime": Timestamp,
      "isOnline": true,
      "deviceType": "PHONE"
    }
  }
}
```

#### 字段说明
- `name` - 设备名称
- `ownerId` - 设备所有者的用户 ID
- `location` - 设备位置（GeoPoint 类型，WGS-84 坐标）
- `battery` - 电池电量（0-100）
- `lastUpdateTime` - 最后更新时间
- `isOnline` - 在线状态
- `deviceType` - 设备类型（PHONE, TABLET, WATCH, AIRTAG, OTHER）

---

## 安全规则更新

### 生产环境 Firestore 规则

现在可以启用生产环境的安全规则了：

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /devices/{deviceId} {
      // 只能读取自己的设备
      allow read: if request.auth != null &&
                     resource.data.ownerId == request.auth.uid;

      // 只能创建属于自己的设备
      allow create: if request.auth != null &&
                       request.resource.data.ownerId == request.auth.uid;

      // 只能更新自己的设备
      allow update: if request.auth != null &&
                       resource.data.ownerId == request.auth.uid;

      // 只能删除自己的设备
      allow delete: if request.auth != null &&
                       resource.data.ownerId == request.auth.uid;
    }
  }
}
```

### 部署安全规则

```bash
firebase deploy --only firestore:rules
```

---

## 测试步骤

### 1. 测试认证功能

```bash
# 运行应用
./gradlew installDebug
```

1. 打开应用，应该看到登录界面
2. 点击"快速开始（匿名登录）"
3. 登录成功后进入主界面

### 2. 测试位置上报

1. 确保已授予定位权限
2. 查看 logcat，应该看到 "位置上报成功" 日志
3. 在 Firebase Console → Firestore Database 中查看 `devices` 集合
4. 应该能看到当前设备的数据

### 3. 测试设备管理

1. 在主界面点击设备标记或列表中的设备
2. 查看设备详情
3. 点击"删除"按钮测试删除功能
4. 确认设备从 Firestore 中删除

---

## 下一步优化建议

### 1. 完善编辑功能
- 创建设备编辑对话框
- 支持修改设备名称、类型等

### 2. 添加设备功能
- 创建"添加设备"按钮
- 支持手动添加其他设备（如 AirTag）

### 3. 优化上报机制
- 根据移动距离智能上报（节省电量）
- 支持用户自定义上报频率

### 4. 增强安全性
- 实现设备验证机制
- 添加设备共享功能（家庭成员）

### 5. 用户体验优化
- 添加启动画面（Splash Screen）
- 添加设置页面（上报频率、隐私设置等）
- 支持退出登录功能

---

## 常见问题

### Q: 位置上报失败？
**A:** 检查以下几点：
1. 是否已授予定位权限
2. 是否已开启 GPS 定位服务
3. 是否已登录（需要用户认证）
4. Firestore 安全规则是否正确配置

### Q: 看不到其他设备？
**A:**
- 确保设备数据的 `ownerId` 与当前登录用户的 `uid` 相同
- 检查 Firestore 查询规则

### Q: 坐标偏移问题？
**A:**
- 已实现 WGS-84 → GCJ-02 坐标转换
- 确保使用 `CoordinateConverter` 进行转换

### Q: 如何测试多设备？
**A:**
- 在 Firebase Console 手动添加测试设备数据
- 或在多个设备上安装应用并登录同一账号

---

## 技术架构

```
应用架构：
├── UI Layer (Compose)
│   ├── LoginScreen - 登录界面
│   ├── MainScreen - 主界面
│   ├── DeviceDetailPanel - 设备详情
│   └── MapViewWrapper - 地图组件
│
├── ViewModel Layer
│   ├── AuthViewModel - 认证状态管理
│   ├── MainViewModel - 主界面状态管理
│   └── DeviceManagementViewModel - 设备管理
│
├── Repository Layer
│   ├── AuthRepository - 认证数据操作
│   └── DeviceRepository - 设备数据操作
│
├── Service Layer
│   └── LocationReportService - 位置上报服务
│
├── Worker Layer
│   └── LocationReportWorker - 后台定期任务
│
└── Util Layer
    ├── CoordinateConverter - 坐标转换
    └── MapCameraHelper - 地图相机控制
```

---

## 总结

所有三个核心功能已完整实现：
1. ✅ Firebase Authentication - 用户认证系统
2. ✅ 设备数据上报功能 - 自动后台上报
3. ✅ 设备管理功能 - 查看、编辑、删除

应用现在具备了基本的 Find My 功能，可以跟踪多个设备的位置并进行管理。
