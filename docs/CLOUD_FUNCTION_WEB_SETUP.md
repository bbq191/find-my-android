# Cloud Function 网页端配置指南 ☁️

**无需命令行！** 本指南教你如何完全通过 Firebase Console 网页端配置按需位置更新功能。

---

## 🌐 方法对比

| 方法         | 优点          | 缺点                          | 推荐场景      |
|------------|-------------|-----------------------------|-----------|
| **网页端编辑**  | 无需安装工具，即开即用 | 功能较简单，不支持本地调试               | 快速测试，简单函数 |
| **CLI 部署** | 功能完整，支持本地调试 | 需要安装 Node.js 和 Firebase CLI | 生产环境，复杂项目 |

**本指南使用：网页端编辑** ✅

---

## 📋 操作步骤

### 步骤 1：打开 Firebase Console

1. 访问：https://console.firebase.google.com/
2. 选择你的项目：`findmy`（或你的项目名称）
3. 点击左侧菜单 **"Functions"**（函数）

![Firebase Console](https://i.imgur.com/firebase-functions.png)

---

### 步骤 2：升级到 Blaze 计划（必需）

⚠️ **Cloud Functions 需要 Blaze（按量付费）计划**，但有免费额度：

- **免费额度**：每月 200 万次调用
- **你的项目**：预计每月 3 万次 ✅ 完全免费
- **账单保护**：可设置每日预算上限

**升级方法**：

1. 点击左下角 **"Upgrade"**（升级）
2. 选择 **"Blaze"** 计划
3. 绑定信用卡（仅用于验证，不会自动扣费）
4. 设置预算警报：**每日 $1 USD**（保护）

---

### 步骤 3：创建第一个 Cloud Function

#### 3.1 在网页端编辑器中创建函数

1. 点击 **"Get Started"**（开始使用）
2. 选择运行环境：
    - **Runtime**: `Node.js 18`（推荐）
    - **Region**: `asia-east1`（台湾）或 `asia-northeast1`（东京）

3. 点击 **"Create function"**（创建函数）

---

#### 3.2 配置函数信息

在弹出的配置窗口中：

| 配置项                  | 填写内容                           |
|----------------------|--------------------------------|
| **Function name**    | `onLocationRequest`            |
| **Region**           | `asia-east1` (Taiwan)          |
| **Trigger type**     | `Cloud Firestore`              |
| **Event type**       | `Document Created`             |
| **Document path**    | `locationRequests/{requestId}` |
| **Memory allocated** | `256 MB`（默认）                   |
| **Timeout**          | `60 seconds`                   |

点击 **"Save"**（保存）

---

#### 3.3 编写函数代码

在代码编辑器中，删除默认代码，粘贴以下内容：

```javascript
const functions = require('firebase-functions');
const admin = require('firebase-admin');

// 初始化 Admin SDK（只需执行一次）
if (admin.apps.length === 0) {
  admin.initializeApp();
}

/**
 * 监听 locationRequests 集合的新文档创建事件
 * 当用户请求位置更新时，发送 FCM Data Message 给目标用户
 */
exports.onLocationRequest = functions
  .region('asia-east1')  // 与上面选择的 Region 保持一致
  .firestore
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
        return null;
      }

      const userData = userDoc.data();
      const fcmTokens = userData.fcmTokens || [];

      if (fcmTokens.length === 0) {
        console.warn(`目标用户没有 FCM Token: ${targetUid}`);
        await snapshot.ref.update({
          status: 'failed',
          error: 'No FCM tokens available'
        });
        return null;
      }

      // 2. 构建 FCM Data Message
      const message = {
        tokens: fcmTokens,
        data: {
          type: 'LOCATION_REQUEST',
          requesterUid: requesterUid,
          timestamp: timestamp.toString()
        },
        android: {
          priority: 'high'
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
        const tokensToRemove = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            tokensToRemove.push(fcmTokens[idx]);
            console.warn(`Token 发送失败: ${fcmTokens[idx]}`);
          }
        });

        if (tokensToRemove.length > 0) {
          await admin.firestore()
            .collection('users')
            .doc(targetUid)
            .update({
              fcmTokens: admin.firestore.FieldValue.arrayRemove(...tokensToRemove)
            });
        }
      }

      return null;
    } catch (error) {
      console.error('发送 FCM 失败:', error);
      await snapshot.ref.update({
        status: 'failed',
        error: error.toString()
      });
      return null;
    }
  });
```

---

#### 3.4 部署函数

1. 点击右上角 **"Deploy"**（部署）
2. 等待部署完成（约 1-2 分钟）
3. 看到绿色勾号 ✅ 表示成功

---

### 步骤 4：创建定期清理函数（可选）

#### 4.1 创建第二个函数

1. 点击 **"Create function"**（创建函数）
2. 配置：

| 配置项               | 填写内容                         |
|-------------------|------------------------------|
| **Function name** | `cleanupOldLocationRequests` |
| **Region**        | `asia-east1`                 |
| **Trigger type**  | `Cloud Pub/Sub`              |
| **Topic**         | 创建新主题：`daily-cleanup`        |
| **Schedule**      | `0 0 * * *`（每天凌晨执行）          |

#### 4.2 代码

```javascript
exports.cleanupOldLocationRequests = functions
  .region('asia-east1')
  .pubsub
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
    return null;
  });
```

---

### 步骤 5：配置 Firestore 安全规则

1. 点击左侧菜单 **"Firestore Database"**
2. 点击顶部 **"Rules"**（规则）标签
3. 复制你的规则文件内容：`docs/firebase-config/firestore.rules`
4. 粘贴到编辑器
5. 点击 **"Publish"**（发布）

---

## ✅ 测试函数

### 方法 1：在 Firebase Console 测试

1. 进入 **Firestore Database**
2. 手动创建一个测试文档：

**集合**: `locationRequests`
**文档 ID**: 自动生成
**字段**:

```json
{
  "requesterUid": "测试用户A的UID",
  "targetUid": "测试用户B的UID",
  "timestamp": 1704700800000,
  "status": "pending"
}
```

3. 点击 **"Add"**（添加）
4. 查看 **Functions > Logs**（日志）标签
5. 应该看到：`收到位置请求: ...`

---

### 方法 2：在应用中测试

1. 打开 Android 应用
2. 登录两个用户（设备 A 和设备 B）
3. 用户 A 点击联系人 B 的"刷新"按钮
4. 观察：
    - Firebase Console > Firestore > `locationRequests` 集合应出现新文档
    - Firebase Console > Functions > Logs 应显示执行日志
    - 设备 B 应收到 FCM 并上报位置
    - 设备 A 应在 5-10 秒内看到更新

---

## 📊 监控和调试

### 查看日志

1. **Functions > Dashboard**（仪表盘）：
    - 调用次数
    - 执行时间
    - 错误率

2. **Functions > Logs**（日志）：
    - 实时日志输出
    - `console.log()` 的内容
    - 错误堆栈

3. **过滤日志**：
   ```
   resource.labels.function_name="onLocationRequest"
   severity="ERROR"
   ```

---

### 常见问题排查

#### 问题 1: 函数没有触发

**检查**：

- Firestore 规则是否允许写入 `locationRequests`
- 文档路径是否正确：`locationRequests/{requestId}`
- 查看 Functions > Logs 是否有错误

#### 问题 2: FCM 发送失败

**检查**：

- 目标用户的 `users/{uid}/fcmTokens` 字段是否存在
- Token 是否有效（在 Logs 中查看错误信息）
- 网络连接是否正常

#### 问题 3: 函数执行超时

**解决**：

- 增加超时时间：Functions > 函数设置 > Timeout: `120s`
- 检查 Firestore 查询是否有索引
- 优化代码，减少不必要的 await

---

## 💰 成本估算

### 免费额度（Blaze 计划）

| 资源    | 免费额度     | 你的项目估算    | 状态   |
|-------|----------|-----------|------|
| 调用次数  | 200 万次/月 | 3 万次/月    | ✅ 免费 |
| GB-秒  | 40 万/月   | ~1.5 万/月  | ✅ 免费 |
| CPU-秒 | 20 万/月   | ~7500/月   | ✅ 免费 |
| 出站流量  | 5 GB/月   | ~0.1 GB/月 | ✅ 免费 |

**结论**: 完全在免费额度内，不会产生费用 🎉

---

## 🔐 安全建议

### 1. 启用应用检查（App Check）

防止滥用 API：

1. Firebase Console > **App Check**
2. 注册应用
3. 启用 SafetyNet/Play Integrity

### 2. 设置预算警报

防止意外高额账单：

1. Google Cloud Console > **Billing**
2. **Budgets & alerts**
3. 设置预算：`$1 USD/day`

### 3. 限流保护

在函数中添加：

```javascript
// 检查用户请求频率
const recentRequests = await admin.firestore()
  .collection('locationRequests')
  .where('requesterUid', '==', requesterUid)
  .where('timestamp', '>', Date.now() - 60000)
  .get();

if (recentRequests.size >= 5) {
  throw new Error('请求过于频繁');
}
```

---

## 📚 与 CLI 方法对比

| 功能         | 网页端            | CLI      |
|------------|----------------|----------|
| 创建函数       | ✅ 支持           | ✅ 支持     |
| 在线编辑       | ✅ 实时编辑         | ❌ 需本地编辑  |
| 本地调试       | ❌ 不支持          | ✅ 完整支持   |
| 版本控制       | ⚠️ 手动复制        | ✅ Git 集成 |
| 多人协作       | ⚠️ 需小心         | ✅ 代码仓库   |
| TypeScript | ❌ 仅 JavaScript | ✅ 支持     |
| 依赖管理       | ⚠️ 自动管理        | ✅ 手动控制   |

**推荐**：

- **学习/测试**：使用网页端 ⚡ 快速上手
- **生产环境**：使用 CLI 🔧 专业稳定

---

## 🎯 下一步

完成网页端配置后：

1. ✅ 测试位置请求功能
2. ✅ 查看日志确认执行成功
3. ✅ 设置预算警报保护账单
4. 📱 在应用中实际测试
5. 📊 监控调用次数和性能

---

## 🆘 需要帮助？

### Firebase 官方文档

- Cloud Functions 入门：https://firebase.google.com/docs/functions/get-started
- FCM 服务端指南：https://firebase.google.com/docs/cloud-messaging/server

### 常见问题

- Firebase 论坛：https://firebase.google.com/support
- Stack Overflow：搜索 `firebase-cloud-functions`

### 本地测试（可选）

如果需要更强大的功能，可以后续切换到 CLI 方法，参考：

- `docs/CLOUD_FUNCTION_SETUP.md`（完整 CLI 指南）

---

## ✨ 总结

**网页端配置优势**：

- ⚡ 无需安装任何工具
- 🌐 浏览器即开即用
- 📝 实时编辑和部署
- 🔍 内置日志查看

**完成本指南后，你的按需位置更新功能将完全可用！** 🎉
