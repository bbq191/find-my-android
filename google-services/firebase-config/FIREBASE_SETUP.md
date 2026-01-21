# Firebase Cloud Functions 配置说明

> 更新时间: 2026-01-19
> 架构: MQTT 主通道 + FCM 唤醒 + HTTP API

---

## 一、架构概述

```
┌─────────────────────────────────────────────────────────────────┐
│                        通讯架构                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Android App                                                   │
│       │                                                         │
│       ├──► MQTT (主通道)                                        │
│       │    └── 实时位置、消息、命令                              │
│       │                                                         │
│       └──► Cloud Functions HTTP API                             │
│            ├── POST /registerToken   ── FCM Token 注册          │
│            ├── POST /sendPush        ── 发送推送通知            │
│            ├── POST /sendPushBatch   ── 批量发送推送            │
│            ├── GET  /tokenStatus     ── 查询 Token 状态         │
│            └── GET  /health          ── 健康检查                │
│                     │                                           │
│                     ▼                                           │
│               FCM Admin SDK                                     │
│                     │                                           │
│                     ▼                                           │
│               目标设备 (FCM 唤醒)                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 二、部署步骤

### 1. 安装 Firebase CLI

```bash
npm install -g firebase-tools
firebase login
```

### 2. 初始化项目

```bash
cd google-services
firebase init

# 选择:
# - Functions: Configure and deploy Cloud Functions
# - 选择现有项目或创建新项目
# - TypeScript
# - 安装依赖
```

### 3. 部署 Cloud Functions

```bash
cd functions
npm install
npm run build
firebase deploy --only functions
```

### 4. 获取 Functions URL

部署成功后，控制台会显示函数 URL：

```
✔ functions[registerToken(asia-northeast1)]: Successful
✔ functions[sendPush(asia-northeast1)]: Successful
...

Function URL (registerToken): https://asia-northeast1-YOUR_PROJECT.cloudfunctions.net/registerToken
Function URL (sendPush): https://asia-northeast1-YOUR_PROJECT.cloudfunctions.net/sendPush
```

### 5. 配置 Android 应用

在 `local.properties` 中添加：

```properties
# Firebase Cloud Functions URL
FIREBASE_FUNCTIONS_URL=https://asia-northeast1-YOUR_PROJECT.cloudfunctions.net
```

---

## 三、API 文档

### POST /registerToken

注册设备的 FCM Token。

**请求体:**
```json
{
  "deviceId": "设备唯一标识",
  "fcmToken": "FCM Token",
  "platform": "android",
  "appVersion": "1.0.0"
}
```

**响应:**
```json
{
  "success": true,
  "message": "Token registered successfully"
}
```

---

### POST /sendPush

发送推送通知到目标设备。

**请求体:**
```json
{
  "targetToken": "FCM Token 或设备 ID",
  "type": "请求类型",
  "requesterId": "请求者 ID",
  "message": "丢失模式消息（可选）",
  "phoneNumber": "联系电话（可选）",
  "playSound": true
}
```

**支持的请求类型:**

| type | 说明 |
|------|------|
| `single` | 单次位置请求 |
| `continuous` | 开始实时追踪 |
| `stop_continuous` | 停止实时追踪 |
| `heartbeat` | 追踪心跳 |
| `play_sound` | 播放查找提示音 |
| `stop_sound` | 停止播放提示音 |
| `enable_lost_mode` | 启用丢失模式 |
| `disable_lost_mode` | 关闭丢失模式 |

**响应:**
```json
{
  "success": true,
  "messageId": "projects/xxx/messages/xxx"
}
```

**错误响应:**
```json
{
  "error": "Rate limit exceeded",
  "code": "RATE_LIMITED"
}
```

---

### POST /sendPushBatch

批量发送推送通知。

**请求体:**
```json
{
  "targets": [
    {
      "targetToken": "Token1",
      "type": "single",
      "requesterId": "user1"
    },
    {
      "targetToken": "Token2",
      "type": "single",
      "requesterId": "user1"
    }
  ]
}
```

**响应:**
```json
{
  "success": true,
  "successCount": 2,
  "failureCount": 0
}
```

---

### GET /tokenStatus

查询设备 Token 是否已注册。

**请求:**
```
GET /tokenStatus?deviceId=xxx
```

**响应:**
```json
{
  "registered": true,
  "updatedAt": 1737312000000,
  "platform": "android"
}
```

---

### GET /health

健康检查端点。

**响应:**
```json
{
  "status": "healthy",
  "timestamp": 1737312000000,
  "tokenCount": 10
}
```

---

## 四、频率限制

| 请求类型 | 限制 |
|----------|------|
| `single` | 30 次/分钟 |
| `continuous` | 10 次/分钟 |
| `play_sound` | 5 次/分钟 |
| `enable_lost_mode` | 3 次/分钟 |
| 其他 | 30 次/分钟 |

超过限制会返回 `429 Too Many Requests`。

---

## 五、定时任务

| 任务 | 周期 | 说明 |
|------|------|------|
| `cleanupExpiredTokens` | 每 24 小时 | 清理 7 天未更新的 Token |
| `cleanupRateLimitRecords` | 每 1 小时 | 清理过期的频率限制记录 |

---

## 六、本地开发

### 使用模拟器

```bash
cd functions
npm run serve
```

模拟器会在 `http://localhost:5001` 启动。

### 查看日志

```bash
firebase functions:log
```

---

## 七、生产环境注意事项

### Token 存储

当前实现使用内存存储 Token（`Map`），适合轻量级使用。

生产环境建议:
- 使用 Firestore 或 Redis 持久化存储
- 添加 Token 过期机制
- 实现 Token 刷新逻辑

### 安全增强

1. **添加 API 密钥验证**
   ```typescript
   const apiKey = req.headers['x-api-key'];
   if (apiKey !== process.env.API_KEY) {
     res.status(401).json({error: 'Unauthorized'});
     return;
   }
   ```

2. **使用 Firebase App Check**
   - 防止未经授权的 API 调用
   - 验证请求来自真实的应用

3. **IP 白名单**
   - 限制只允许特定 IP 调用
   - 使用 Cloud Armor 防护

---

## 八、故障排除

### Q: 推送发送失败？

1. 检查 FCM Token 是否有效
2. 查看 Cloud Functions 日志
3. 确认目标设备网络正常

### Q: Token 注册后查不到？

1. Cloud Functions 实例可能已重启（内存存储丢失）
2. 检查 Token 是否过期被清理
3. 确认请求成功（检查响应）

### Q: 频率限制触发？

1. 减少请求频率
2. 使用批量 API `/sendPushBatch`
3. 实现客户端请求合并

---

## 九、成本估算

Firebase Cloud Functions 按调用次数计费：

| 项目 | 免费额度 | 超出后价格 |
|------|----------|------------|
| 调用次数 | 200 万次/月 | $0.40/100 万次 |
| 计算时间 | 40 万 GB-秒/月 | $0.0000025/GB-秒 |
| 出站流量 | 5 GB/月 | $0.12/GB |

对于个人使用，免费额度通常足够。
