# Firebase Firestore 配置说明

## 问题原因

应用崩溃是因为 Firestore 默认拒绝所有未经授权的访问。我们需要配置安全规则以允许访问。

## 解决方案

### 方法一：使用 Firebase CLI 部署安全规则（推荐）

1. **安装 Firebase CLI**（如果还没有安装）
   ```bash
   npm install -g firebase-tools
   ```

2. **登录 Firebase**
   ```bash
   firebase login
   ```

3. **在项目目录中初始化 Firebase**（如果还没有初始化）
   ```bash
   firebase init firestore
   ```
   - 选择现有项目
   - 使用默认的 `firestore.rules` 文件（已创建）

4. **部署安全规则**
   ```bash
   firebase deploy --only firestore:rules
   ```

### 方法二：通过 Firebase Console 手动配置

1. **打开 Firebase Console**
   - 访问：https://console.firebase.google.com/
   - 选择你的项目

2. **进入 Firestore Database**
   - 左侧菜单 → Firestore Database
   - 点击 "规则" 标签页

3. **复制以下规则并发布**

   **开发/测试环境规则**（临时使用，允许所有访问）：
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /devices/{deviceId} {
         allow read, write: if true;
       }
       match /contacts/{contactId} {
         allow read, write: if true;
       }
     }
   }
   ```

   **⚠️ 警告**：这个规则允许任何人读写你的数据库！仅用于开发测试。

4. **点击 "发布" 按钮**

### 方法三：快速测试（最简单）

如果只是想快速测试，可以使用测试模式：

1. 进入 Firebase Console → Firestore Database → 规则
2. 使用以下规则（30天后自动失效）：
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /{document=**} {
         allow read, write: if request.time < timestamp.date(2026, 2, 6);
       }
     }
   }
   ```

## 生产环境配置

在生产环境中，请启用 Firebase Authentication 并使用更安全的规则。

### 1. 启用 Firebase Authentication

Firebase Console → Authentication → 登录方法 → 启用所需的认证方式（如邮箱/密码、Google 登录等）

### 2. 使用安全规则

在 `firestore.rules` 文件中，取消注释生产环境规则部分：

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /devices/{deviceId} {
      // 只允许设备所有者访问自己的设备
      allow read: if request.auth != null &&
                     resource.data.ownerId == request.auth.uid;
      allow create: if request.auth != null &&
                       request.resource.data.ownerId == request.auth.uid;
      allow update, delete: if request.auth != null &&
                               resource.data.ownerId == request.auth.uid;
    }
  }
}
```

### 3. 在应用中实现用户认证

需要在应用启动时进行用户登录，并在保存设备数据时添加 `ownerId` 字段。

## 验证配置

部署规则后，重新运行应用：

```bash
./gradlew installDebug
```

应用应该能够正常连接 Firestore 而不会崩溃。

## 测试数据

你可以通过 Firebase Console 手动添加一些测试设备数据：

1. Firebase Console → Firestore Database → 数据
2. 创建集合：`devices`
3. 添加文档，字段示例：
   ```
   name: "iPhone 15 Pro"
   location: GeoPoint(39.9042, 116.4074)
   battery: 85
   lastUpdateTime: Timestamp.now()
   isOnline: true
   deviceType: "PHONE"
   ownerId: "test-user-123"  // 生产环境用实际用户ID
   ```

## 常见问题

**Q: 部署后仍然报权限错误？**
- 等待1-2分钟，规则部署需要时间生效
- 清除应用数据重新安装
- 检查规则是否正确发布

**Q: 如何查看当前生效的规则？**
- Firebase Console → Firestore Database → 规则标签页

**Q: 我应该使用哪种方法？**
- 快速测试：方法三
- 开发阶段：方法一或方法二（测试模式规则）
- 生产环境：必须使用认证规则

## 下一步

完成 Firestore 配置后，建议：
1. 实现 Firebase Authentication
2. 创建设备数据上报功能
3. 添加设备管理功能（添加、编辑、删除设备）
