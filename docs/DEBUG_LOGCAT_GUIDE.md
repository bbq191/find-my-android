# Logcat 调试指南 - 短时实时追踪

## 问题诊断

如果 `adb logcat -s MyFirebaseMsgService ContinuousLocationWorker` 没有输出日志，按以下步骤排查：

---

## 步骤 1: 验证设备连接

```bash
# 检查设备是否连接
adb devices

# 预期输出：
# List of devices attached
# <设备ID>    device
```

**如果没有设备**：
- 确保 USB 调试已开启
- 重新连接 USB 线
- 授权调试权限

---

## 步骤 2: 清空日志缓冲区

```bash
# 清空旧日志
adb logcat -c

# 然后重新开始监听
adb logcat -s MyFirebaseMsgService ContinuousLocationWorker
```

---

## 步骤 3: 验证应用是否运行

```bash
# 检查应用进程
adb shell "ps | grep me.ikate.findmy"

# 或者使用（Android 8+）
adb shell "ps -A | grep me.ikate.findmy"
```

**如果没有进程**：
- 打开应用
- 确保应用在前台或后台运行

---

## 步骤 4: 使用更宽松的过滤器

```bash
# 方法 1: 使用通配符
adb logcat | grep -E "MyFirebaseMsgService|ContinuousLocationWorker"

# 方法 2: 只过滤这两个 TAG（不限制日志级别）
adb logcat MyFirebaseMsgService:V ContinuousLocationWorker:V *:S

# 方法 3: 查看所有日志（调试用）
adb logcat | grep "实时追踪"
```

**日志级别说明**：
- `V` - Verbose（详细）
- `D` - Debug（调试）
- `I` - Info（信息）
- `W` - Warning（警告）
- `E` - Error（错误）
- `*:S` - 静默其他所有日志

---

## 步骤 5: 触发日志输出

### 5.1 触发 FCM 消息

**在另一台设备或模拟器上**：
1. 登录另一个用户账号
2. 对当前用户点击"实时"按钮
3. 等待 5-10 秒

**预期日志**：
```
D/MyFirebaseMsgService: Message data payload: {type=LOCATION_TRACK_START, ...}
D/MyFirebaseMsgService: 🎯 收到来自: <UID> 的实时追踪请求
D/MyFirebaseMsgService: 已启动连续位置追踪任务，WorkRequest ID: ...
```

### 5.2 手动创建 Firestore 请求

**在 Firebase Console**：
1. 打开 Firestore Database
2. 进入 `locationRequests` 集合
3. 手动添加文档：
   ```json
   {
     "requesterUid": "测试用户UID",
     "targetUid": "你的设备UID",
     "type": "continuous",
     "timestamp": 当前时间戳,
     "status": "pending"
   }
   ```

### 5.3 检查 Worker 日志

```bash
# 专门查看 Worker 相关日志
adb logcat | grep -E "Worker|WorkManager|WM-"
```

**预期输出**：
```
D/ContinuousLocationWorker: 🎯 开始短时实时追踪
D/ContinuousLocationWorker: 📍 第 1 次位置上报成功
```

---

## 步骤 6: 检查日志权限

```bash
# 检查应用的日志输出权限
adb logcat | grep "me.ikate.findmy"

# 如果完全没有输出，检查系统日志设置
adb shell getprop log.tag.MyFirebaseMsgService
```

---

## 常见问题与解决方案

### 问题 1: 日志完全没有输出

**可能原因**：
- 应用没有运行
- 日志级别被过滤
- 代码没有执行到

**解决方案**：
```bash
# 1. 强制停止并重新启动应用
adb shell am force-stop me.ikate.findmy
adb shell am start -n me.ikate.findmy/.MainActivity

# 2. 使用更宽松的过滤器
adb logcat -v time | grep -E "MyFirebase|Continuous|实时"

# 3. 查看所有应用日志
adb logcat | grep "me.ikate.findmy"
```

---

### 问题 2: 只看到部分日志

**可能原因**：
- 日志缓冲区已满
- 日志输出速度过快

**解决方案**：
```bash
# 增大日志缓冲区
adb logcat -G 16M

# 保存日志到文件
adb logcat -s MyFirebaseMsgService ContinuousLocationWorker > logcat.txt

# 实时查看文件
tail -f logcat.txt
```

---

### 问题 3: FCM 消息没有到达

**检查步骤**：

1. **验证 FCM Token**：
   ```bash
   # 查看 Token 注册日志
   adb logcat | grep "FCM Token"
   ```

2. **检查 Cloud Function**：
   ```bash
   # 在项目目录执行
   firebase functions:log --only onLocationRequest
   ```

3. **测试 FCM 连接**：
   ```bash
   # 查看 FCM 相关日志
   adb logcat | grep -E "FCM|firebase"
   ```

---

### 问题 4: Worker 没有启动

**检查 WorkManager 状态**：

```bash
# 查看 WorkManager 日志
adb logcat -s WM-WorkerWrapper WM-WorkSpec

# 或者查看所有 Worker 相关日志
adb logcat | grep -E "Worker|enqueueUniqueWork"
```

**预期日志**：
```
I/WM-WorkerWrapper: Worker result SUCCESS for Work [ id=..., tags={ continuous_location_tracking } ]
```

---

## 完整调试命令集

### 推荐方案 A: 多终端监听

**终端 1 - 应用主日志**：
```bash
adb logcat -v time MyFirebaseMsgService:V ContinuousLocationWorker:V LocationReportWorker:V *:S
```

**终端 2 - WorkManager 日志**：
```bash
adb logcat -v time | grep -E "WM-|Worker"
```

**终端 3 - 通用搜索**：
```bash
adb logcat -v time | grep -E "实时|追踪|位置"
```

---

### 推荐方案 B: 单终端综合日志

```bash
adb logcat -v time \
  MyFirebaseMsgService:V \
  ContinuousLocationWorker:V \
  LocationReportWorker:V \
  ContactViewModel:V \
  WM-WorkerWrapper:V \
  *:S
```

---

### 推荐方案 C: 保存到文件后分析

```bash
# 清空日志
adb logcat -c

# 开始记录
adb logcat -v time > debug.log &

# 触发测试...

# 停止记录（Ctrl+C）

# 分析日志
grep -E "MyFirebase|Continuous|Worker" debug.log
```

---

## 验证清单

完整测试流程：

- [ ] 1. 清空日志：`adb logcat -c`
- [ ] 2. 启动监听：`adb logcat -s MyFirebaseMsgService ContinuousLocationWorker`
- [ ] 3. 打开应用
- [ ] 4. 触发实时追踪
- [ ] 5. 等待 5 秒
- [ ] 6. 检查日志输出

**预期完整日志流程**：

```
# FCM 接收
D/MyFirebaseMsgService: Message data payload: {type=LOCATION_TRACK_START, requesterUid=...}
D/MyFirebaseMsgService: 🎯 收到来自: xxx 的实时追踪请求
D/MyFirebaseMsgService: 已启动连续位置追踪任务，WorkRequest ID: ...

# Worker 启动
D/ContinuousLocationWorker: 🎯 开始短时实时追踪，请求者: xxx

# 位置上报
D/ContinuousLocationWorker: 📍 第 1 次位置上报成功 (耗时: 2341ms, 位置: ...)
D/ContinuousLocationWorker: 📍 第 2 次位置上报成功 (耗时: 1823ms, 位置: ...)
...
D/ContinuousLocationWorker: 📍 第 8 次位置上报成功 (耗时: 1654ms, 位置: ...)

# 追踪结束
D/ContinuousLocationWorker: ⏱️ 追踪时间到，自动停止
D/ContinuousLocationWorker: ✅ 短时实时追踪完成，共上报 8 次位置
D/ContinuousLocationWorker: 🧹 执行清理工作（共上报了 8 次位置）
```

---

## 高级调试技巧

### 1. 实时过滤关键字

```bash
# 只看关键操作
adb logcat -v time | grep --color=auto -E "🎯|📍|✅|❌|⏱️|⏹️"
```

### 2. 按时间戳分析

```bash
# 带时间戳的日志
adb logcat -v threadtime MyFirebaseMsgService:V ContinuousLocationWorker:V *:S
```

### 3. 远程调试

```bash
# 通过 WiFi 连接设备
adb tcpip 5555
adb connect <设备IP>:5555

# 然后正常使用 logcat
adb logcat -s MyFirebaseMsgService ContinuousLocationWorker
```

---

## 快速测试脚本

创建 `debug_tracking.sh`：

```bash
#!/bin/bash

echo "=== 清空日志 ==="
adb logcat -c

echo "=== 开始监听 ==="
adb logcat -v time \
  MyFirebaseMsgService:V \
  ContinuousLocationWorker:V \
  LocationReportWorker:V \
  *:S | tee tracking_debug.log
```

使用方法：
```bash
chmod +x debug_tracking.sh
./debug_tracking.sh
```

---

## 总结

如果以上步骤都无法看到日志，可能的原因：

1. **应用包名不匹配** - 确认是 `me.ikate.findmy`
2. **代码未编译** - 重新 Build 并安装
3. **设备不支持** - 使用真机而非模拟器测试 GPS
4. **权限问题** - 确保位置权限已授予

**最简单的验证方法**：
```bash
# 查看所有应用日志
adb logcat | grep "me.ikate"
```

如果连这个都没有输出，说明应用根本没有运行或日志系统有问题。
