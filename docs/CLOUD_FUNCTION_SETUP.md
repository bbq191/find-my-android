# Cloud Function 配置指南 - 按需位置更新

本文档提供 Firebase Cloud Functions 配置示例，用于实现"按需唤醒"功能的服务端逻辑。

## 功能说明

当用户 A 点击联系人 B 的刷新按钮时：

1. **客户端**：写入 Firestore `locationRequests` 集合
2. **Cloud Function**：监听该集合，发送 FCM Data Message 给目标用户的所有设备
3. **目标设备**：收到 FCM 后启动加急 Worker，立即上报位置
4. **客户端**：通过 Firestore 实时监听获得最新位置

---

## 前置条件

1. 已安装 Firebase CLI：`npm install -g firebase-tools`
2. 已初始化 Functions：`firebase init functions`
3. 选择 TypeScript 或 JavaScript

---

## Cloud Function 代码示例

### 1. 安装依赖

在 `functions` 目录下执行：

```bash
cd functiy
ons
npm install firebase-admin firebase-functions
```

### 2. 编写 Cloud Function

**文件路径**: `functions/src/index.ts`

```typescript
import * as functions from 'firebase-functions';
import * as admin from 'firebase-admin';

admin.initializeApp();

/**
 * 监听 locationRequests 集合的新文档创建事件
 * 当用户请求位置更新时，发送 FCM Data Message 给目标用户
 */
export const onLocationRequest = functions.firestore
  .document('locationRequests/{requestId}')
  .onCreate(async (snapshot, context) => {
    const requestData = snapshot.data();
    const { requesterUid, targetUid, timestamp } = requestData;

    console.log(`收到位置请求: ${requesterUid} -> ${targetUid}`);

    try {
      // 1. 获取目标用户的 FCM Tokens
      const userDoc = await admin.firestore()
        .collection('users')
        .doc(targetUid)
        .get();

      if (!userDoc.exists) {
        console.error(`目标用户不存在: ${targetUid}`);
        return;
      }

      const userData = userDoc.data();
      const fcmTokens: string[] = userData?.fcmTokens || [];

      if (fcmTokens.length === 0) {
        console.warn(`目标用户没有 FCM Token: ${targetUid}`);
        // 更新请求状态为失败
        await snapshot.ref.update({
          status: 'failed',
          error: 'No FCM tokens available'
        });
        return;
      }

      // 2. 构建 FCM Data Message
      const message: admin.messaging.MulticastMessage = {
        tokens: fcmTokens,
        data: {
          type: 'LOCATION_REQUEST',
          requesterUid: requesterUid,
          timestamp: timestamp.toString()
        },
        android: {
          priority: 'high', // 高优先级，确保即时唤醒
        }
      };

      // 3. 发送 FCM
      const response = await admin.messaging().sendMulticast(message);

      console.log(`FCM 发送结果: ${response.successCount}/${fcmTokens.length} 成功`);

      // 4. 更新请求状态
      await snapshot.ref.update({
        status: 'sent',
        successCount: response.successCount,
        failureCount: response.failureCount,
        sentAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // 5. 清理失败的 Token
      if (response.failureCount > 0) {
        const tokensToRemove: string[] = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            tokensToRemove.push(fcmTokens[idx]);
            console.warn(`Token 发送失败: ${fcmTokens[idx]}, 错误: ${resp.error?.message}`);
          }
        });

        // 从用户文档中移除无效 Token
        if (tokensToRemove.length > 0) {
          await admin.firestore()
            .collection('users')
            .doc(targetUid)
            .update({
              fcmTokens: admin.firestore.FieldValue.arrayRemove(...tokensToRemove)
            });
        }
      }

    } catch (error) {
      console.error('发送 FCM 失败:', error);
      await snapshot.ref.update({
        status: 'failed',
        error: String(error)
      });
    }
  });

/**
 * 定期清理过期的位置请求记录 (每天执行一次)
 */
export const cleanupOldLocationRequests = functions.pubsub
  .schedule('every 24 hours')
  .onRun(async (context) => {
    const oneDayAgo = Date.now() - 24 * 60 * 60 * 1000;

    const snapshot = await admin.firestore()
      .collection('locationRequests')
      .where('timestamp', '<', oneDayAgo)
      .get();

    const batch = admin.firestore().batch();
    snapshot.docs.forEach(doc => {
      batch.delete(doc.ref);
    });

    await batch.commit();
    console.log(`清理了 ${snapshot.size} 条过期位置请求记录`);
  });
```

---

## 3. 部署 Cloud Function

```bash
# 在项目根目录执行
firebase deploy --only functions
```

部署成功后，控制台会显示函数 URL 和状态。

---

## 4. Firestore 安全规则

确保 `locationRequests` 集合的安全规则允许用户创建文档：

**文件路径**: `firestore.rules`

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // 位置请求：仅允许已认证用户创建
    match /locationRequests/{requestId} {
      allow create: if request.auth != null
                    && request.resource.data.requesterUid == request.auth.uid;
      allow read: if request.auth != null
                  && (resource.data.requesterUid == request.auth.uid
                      || resource.data.targetUid == request.auth.uid);
    }

    // 用户文档：允许用户读写自己的 FCM Token
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

部署规则：

```bash
firebase deploy --only firestore:rules
```

---

## 5. 测试流程

### 测试步骤：

1. **用户 A** 打开应用，点击联系人 B 的"刷新"按钮
2. 查看 Firebase Console > Firestore > `locationRequests` 集合，应该看到新文档
3. 查看 Cloud Functions 日志：
   ```bash
   firebase functions:log
   ```
4. **用户 B** 设备收到 FCM Data Message，启动加急 Worker
5. **用户 A** 在 5-10 秒内看到位置从"10分钟前"变为"刚刚"

### 调试技巧：

- **查看 FCM 发送日志**：Firebase Console > Functions > `onLocationRequest` > 日志
- **检查 FCM Token**：Firestore > `users/{uid}` > 查看 `fcmTokens` 字段
- **监控请求状态**：Firestore > `locationRequests/{id}` > 查看 `status` 字段

---

## 6. 性能优化建议

### A. 限流防护

在 Cloud Function 中添加请求频率限制：

```typescript
// 检查用户最近1分钟内的请求次数
const recentRequests = await admin.firestore()
  .collection('locationRequests')
  .where('requesterUid', '==', requesterUid)
  .where('targetUid', '==', targetUid)
  .where('timestamp', '>', Date.now() - 60000)
  .get();

if (recentRequests.size >= 3) {
  console.warn('请求过于频繁，拒绝执行');
  await snapshot.ref.update({
    status: 'rejected',
    error: 'Too many requests'
  });
  return;
}
```

### B. 批量处理

如果多个用户同时请求同一个目标用户，可以合并 FCM 发送：

```typescript
// 使用 Firestore Transaction 确保原子性
await admin.firestore().runTransaction(async (transaction) => {
  // 检查是否有相同目标的待处理请求
  // 如果有，跳过发送
});
```

---

## 7. 成本估算

- **Cloud Functions 调用**：每次请求触发 1 次函数
- **FCM 发送**：免费（Google 提供）
- **Firestore 写入**：
    - 创建请求：1 次写入
    - 更新状态：1 次写入
    - 清理过期记录：取决于数量

**估算**：每 1000 次位置请求 ≈ $0.01（忽略 FCM 成本）

---

## 8. 故障排查

### 问题 1: FCM 没有发送

**可能原因**：

- 目标用户的 `fcmTokens` 数组为空
- Token 过期或无效

**解决方案**：

- 确保 `MyFirebaseMessagingService.onNewToken()` 正确上传 Token
- 检查 Firestore `users/{uid}/fcmTokens` 字段

### 问题 2: 设备没有响应 FCM

**可能原因**：

- FCM Data Message 被系统拦截（国产 ROM）
- App 进程被完全杀死

**解决方案**：

- 指导用户将应用加入"自启动白名单"
- 测试时保持应用在后台运行

### 问题 3: Cloud Function 超时

**可能原因**：

- Firestore 查询慢
- FCM 发送超时

**解决方案**：

- 增加函数超时时间：
  ```typescript
  export const onLocationRequest = functions
    .runWith({ timeoutSeconds: 120 })
    .firestore.document(...)
  ```

---

## 9. 替代方案（无 Cloud Function）

如果不想使用 Cloud Functions，可以使用客户端直接发送方案：

### 方案 A: 使用 HTTP Cloud Function（客户端调用）

```kotlin
// ContactViewModel.kt
fun requestLocationUpdate(targetUid: String) {
    val functions = Firebase.functions
    functions.getHttpsCallable("requestLocationUpdate")
        .call(mapOf("targetUid" to targetUid))
        .addOnSuccessListener { result ->
            Log.d(TAG, "位置请求已发送")
        }
}
```

### 方案 B: 使用 Admin SDK（服务器端）

如果有自己的后端服务器，可以直接集成 Firebase Admin SDK。

---

## 总结

通过配置 Cloud Function，您的应用现在支持：

✅ **即时位置更新**：用户点击刷新后 5-10 秒内获得最新位置
✅ **自动防抖**：FCM 服务端 + 客户端双重防护
✅ **高可用性**：多设备 Token 支持，失败自动清理
✅ **低成本**：按需触发，无固定费用

**下一步**：可选实现"等级 3：实时追踪模式"，参考文档 `LOCATION_UPDATE_STRATEGY.md`。
