# sharedWith 字段自动清空问题分析与修复

## 问题现象

用户反馈：`sharedWith` 字段会被**自动清空**，导致：
- 新增的 `listener3`（监听共享设备位置更新）无法触发
- 用户A点击刷新后，对端设备B上报了位置，但用户A的UI不刷新
- 需要频繁手动修复 Firestore 数据

---

## 🔍 根本原因分析

### 问题链路

```
定时任务/主动刷新 → LocationReportWorker
  ↓
LocationReportService.reportCurrentLocation()
  ↓
创建新的 Device 对象（sharedWith = emptyList()，默认值）
  ↓
DeviceRepository.saveDevice(device)
  ↓
使用 .set() 覆盖整个文档
  ↓
❌ sharedWith 被清空！
```

### 代码定位

#### 1. LocationReportService.kt:139-150

```kotlin
// 创建设备对象时，没有保留现有的 sharedWith
val device = Device(
    id = getDeviceId(),
    name = getDeviceName(),
    ownerId = currentUserId,
    location = LatLng(location.latitude, location.longitude),
    battery = getBatteryLevel(),
    lastUpdateTime = System.currentTimeMillis(),
    isOnline = true,
    deviceType = getDeviceType(),
    customName = getCustomDeviceName(),
    bearing = bearing
    // ❌ 没有 sharedWith 参数！
    // 使用 Device 数据类的默认值：sharedWith = emptyList()
)
```

**问题：**
- 位置上报时只关心位置、电量等信息
- 创建 Device 对象时没有传入 `sharedWith`
- Kotlin 数据类使用默认值：`sharedWith = emptyList()`

#### 2. DeviceRepository.kt:152-156（修复前）

```kotlin
val deviceData = hashMapOf(
    "name" to device.name,
    "location" to GeoPoint(...),
    "battery" to device.battery,
    // ... 其他字段
    "sharedWith" to device.sharedWith  // ⚠️ 这里是 emptyList()
)

devicesCollection.document(device.id)
    .set(deviceData)  // ❌ .set() 会完全覆盖文档！
```

**问题：**
- `.set()` 会**完全覆盖**文档内容
- 即使 Firestore 中 `sharedWith` 原本有值，也会被覆盖为 `emptyList()`

### 触发频率

**极高频率！** 每次位置更新都会触发：

1. **定期上报**：每15分钟（或用户自定义间隔）
2. **主动刷新**：用户A点击刷新时，触发对端设备B上报
3. **应用启动**：应用前台时可能立即上报一次

**结果：** `sharedWith` 字段会被频繁清空，用户体验极差。

---

## ✅ 修复方案

### 核心修改：DeviceRepository.kt:160-161

**修复前：**
```kotlin
devicesCollection.document(device.id)
    .set(deviceData)  // ❌ 覆盖模式
```

**修复后：**
```kotlin
devicesCollection.document(device.id)
    .set(deviceData, SetOptions.merge())  // ✅ 合并模式
```

### 完整修改内容

**修改1：移除 deviceData 中的 sharedWith 字段**
```kotlin
val deviceData = hashMapOf(
    "name" to device.name,
    "location" to GeoPoint(device.location.latitude, device.location.longitude),
    "battery" to device.battery,
    "lastUpdateTime" to com.google.firebase.Timestamp.now(),
    "isOnline" to device.isOnline,
    "deviceType" to device.deviceType.name,
    "ownerId" to currentUserId,
    "customName" to device.customName,
    "bearing" to device.bearing
    // ❌ 移除: "sharedWith" to device.sharedWith
)
```

**修改2：使用 merge 模式**
```kotlin
.set(deviceData, SetOptions.merge())
```

### 设计原则

**关注点分离：**
- `DeviceRepository.saveDevice()` - 只负责位置、电量等设备状态
- `ContactRepository` - 负责 `sharedWith` 字段的管理
  - `acceptLocationShare()` - 使用 `FieldValue.arrayUnion()` 添加
  - `stopSharing()` - 使用 `FieldValue.arrayRemove()` 移除

**安全性：**
- 使用 `SetOptions.merge()` 确保不会意外覆盖其他字段
- 使用 `FieldValue.arrayUnion/arrayRemove` 确保原子操作

---

## 🧪 验证修复

### 验证步骤

#### 第1步：手动修复现有数据

如果 `sharedWith` 已经被清空，先手动修复：

**方法1：使用 MigrationHelper**
```bash
# 重新编译安装（会自动执行修复）
./gradlew installDebug

# 查看日志
adb logcat -s MainActivity:D MigrationHelper:D
```

**方法2：手动修复 Firestore**
- Firebase Console → Firestore → devices/{deviceId}
- 添加字段：`sharedWith: ["user_a_uid"]`

#### 第2步：触发位置上报

**方法A：主动刷新**
```bash
# 设备A点击刷新按钮
# 观察 Firestore 中设备B的 sharedWith 是否保持不变
```

**方法B：手动触发 Worker**
```bash
# 强制执行位置上报
adb shell am startservice \
  -a me.ikate.findmy.ACTION_LOCATION_REPORT
```

#### 第3步：验证 sharedWith 未被清空

**通过 Firestore Console 检查：**
1. 打开 Firebase Console → Firestore
2. 导航到 `devices/{device_b_id}`
3. 触发位置上报前后，检查 `sharedWith` 字段
4. **预期：** `sharedWith` 保持不变，不会被清空

**通过日志检查：**
```bash
adb logcat -s DeviceRepository:D

# 预期日志：
DeviceRepository: 设备保存成功: android_id_xxx
# 不会有 sharedWith 相关的错误
```

#### 第4步：压力测试

连续触发10次位置上报，验证 `sharedWith` 始终保持不变：

```bash
# 脚本测试
for i in {1..10}; do
  echo "第 $i 次测试"
  # 触发位置上报（通过应用操作或命令）
  sleep 5
  # 检查 Firestore 中 sharedWith 字段
done
```

---

## 📊 影响范围

### 修复前

**受影响的功能：**
- ✅ 位置上报功能正常（位置、电量等可以更新）
- ❌ **共享位置实时同步异常**（listener3 无法监听到更新）
- ❌ 主动刷新功能失效（对端上报了但用户A看不到）
- ❌ 需要频繁手动修复 Firestore 数据

**用户体验：**
- 点击刷新后，需要等很久或手动退出重进才能看到新位置
- 极差的用户体验

### 修复后

**预期效果：**
- ✅ 位置上报功能正常
- ✅ **共享位置实时同步正常**（listener3 正常触发）
- ✅ 主动刷新功能正常（3-5秒内自动刷新）
- ✅ sharedWith 字段永远不会被清空

**用户体验：**
- 点击刷新 → 3-5秒后自动显示最新位置
- 完美的用户体验

---

## 🔒 防止再次发生

### 代码规范

**规则1：禁止在位置上报时操作 sharedWith**
```kotlin
// ❌ 错误
val deviceData = hashMapOf(
    // ...
    "sharedWith" to someValue  // 不应该在这里修改
)

// ✅ 正确
val deviceData = hashMapOf(
    "name" to device.name,
    "location" to geoPoint,
    "battery" to battery
    // sharedWith 应该由 ContactRepository 管理
)
```

**规则2：使用 merge 模式而不是覆盖**
```kotlin
// ❌ 错误
.set(data)  // 覆盖模式

// ✅ 正确
.set(data, SetOptions.merge())  // 合并模式

// ✅ 或使用 update（仅更新指定字段）
.update(mapOf("location" to geoPoint, "battery" to battery))
```

**规则3：sharedWith 只能通过原子操作修改**
```kotlin
// ✅ 正确：添加
.update("sharedWith", FieldValue.arrayUnion(uid))

// ✅ 正确：移除
.update("sharedWith", FieldValue.arrayRemove(uid))

// ❌ 错误：直接赋值
.update("sharedWith", listOf(uid1, uid2))  // 有并发风险
```

### Code Review 检查清单

- [ ] 所有 `.set()` 调用是否使用了 `SetOptions.merge()`？
- [ ] 是否有代码直接修改 `sharedWith` 字段？
- [ ] 是否使用了 `FieldValue.arrayUnion/arrayRemove` 操作数组？
- [ ] 位置上报相关代码是否只更新位置、电量等字段？

---

## 📝 相关修改记录

| 日期 | 文件 | 修改内容 |
|------|------|---------|
| 2026-01-12 | DeviceRepository.kt | 修改 saveDevice() 使用 merge 模式，移除 sharedWith 写入 |
| 2026-01-12 | ContactRepository.kt | 添加 listener3 监听共享设备位置更新 |
| 2026-01-12 | MigrationHelper.kt | 创建自动修复工具，修复历史数据 |

---

## 🎯 总结

### 问题

- 位置上报时使用 `.set()` 覆盖整个文档
- 创建 Device 对象时 `sharedWith` 使用默认值 `emptyList()`
- 导致每次位置上报都会清空 `sharedWith`

### 修复

- 使用 `SetOptions.merge()` 合并模式
- 从 deviceData 中移除 `sharedWith` 字段
- `sharedWith` 只能由 ContactRepository 通过原子操作管理

### 验证

- 手动修复现有数据
- 触发位置上报，检查 sharedWith 是否保持不变
- 压力测试确保稳定性

### 防护

- 代码规范：禁止在位置上报时操作 sharedWith
- Code Review 检查清单
- 使用原子操作管理数组字段

---

## 附录：Firestore 操作对比

| 操作 | 行为 | 适用场景 | 风险 |
|------|------|---------|------|
| `.set(data)` | 完全覆盖文档 | 创建新文档 | ⚠️ 高：会丢失未传入的字段 |
| `.set(data, SetOptions.merge())` | 合并字段 | 更新部分字段 | ✅ 低：保留未传入的字段 |
| `.update(map)` | 只更新指定字段 | 更新已存在的文档 | ✅ 低：文档不存在时会失败 |
| `FieldValue.arrayUnion()` | 添加数组元素（去重） | 管理数组字段 | ✅ 无：原子操作 |
| `FieldValue.arrayRemove()` | 移除数组元素 | 管理数组字段 | ✅ 无：原子操作 |

**推荐：**
- 位置上报：使用 `.set(data, SetOptions.merge())`
- 管理 sharedWith：使用 `FieldValue.arrayUnion/arrayRemove`
