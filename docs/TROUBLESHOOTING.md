# 常见问题排查 (Troubleshooting)

     0. 清理界面，去除导航栏，去除设备，物品，我的对应功能，仅留下联系人功能

## 1. 注册/登录失败: "supplied auth credential is incorrect, malformed or has expired"

如果添加了 SHA-1 指纹后仍然报错，请按照以下高阶步骤排查。这个问题通常是 **Google Cloud Console 中的
API Key 权限配置** 导致的。

### 第一步：确认正在使用的 API Key

你的项目中目前有两个 API Key，请务必检查 `google-services.json` 中使用的那个：

* **Firebase Auth 使用的 Key**: `AIzaSyCCmrOyw0PbafqN1hjyMV9U0IT9AloZx_g`
    * (存在于 `app/google-services.json` 中)
* Google Maps 使用的 Key: `AIzaSyAbX61sf1bDP3T4Jl61vNDc4Cz1I_c-18A`
    * (存在于 `AndroidManifest.xml` 中)

**你需要检查的是第一个 Key (`...Zx_g`)**。

### 第二步：检查 Google Cloud Console 权限 (关键)

1. 访问 [Google Cloud Console - Credentials](https://console.cloud.google.com/apis/credentials)。
2. 确保左上角选择了项目 **find-my-android-9e4e3**。
3. 在列表中找到 API Key: `AIzaSyCCmrOyw0PbafqN1hjyMV9U0IT9AloZx_g` (通常命名为 "Android key (auto
   created by Firebase)")。
4. 点击编辑该 Key。
5. **检查 "API restrictions" (API 限制)**:
    * 如果你选择了 "Restrict key" (限制密钥)，请确保列表里 **必须勾选** 以下 API：
        * **Identity Toolkit API** (这是 Firebase Auth 必须的！)
        * **Token Service API** (有时需要)
        * Firebase Installations API
        * Firebase Cloud Messaging API (如果有用到)
    * **如果不确定，请暂时选择 "Don't restrict key" (不限制密钥) 保存并重试，以验证是否是这里的问题。**

### 第三步：检查 "Application restrictions" (应用限制)

1. 在同一个 Key 的编辑页面。
2. 如果选择了 "Android apps"，请确保：
    * 包名是 `me.ikate.findmy`
    * SHA-1 指纹是 `92:F7:6D:01:82:09:53:70:BB:88:49:77:23:DA:6B:01:77:55:C6:46`
    * **建议暂时移除所有应用限制 (选 None)**，保存并等待 1-2 分钟重试。如果移除限制后成功，说明之前的限制配置有误。

### 第四步：检查设备环境

1. **网络连接**: 确保模拟器/真机可以访问 Google 服务 (尝试在模拟器浏览器打开 google.com)。
2. **系统时间**: 确保模拟器/真机的时间是准确的。时间偏差会导致 Token 验证失败。
3. **Google Play Services**: 确保模拟器是 "Google Play" 版本，而不是 "AOSP" 版本。

---

## 2. Google Maps 不显示

(保留之前的建议)

* 确保 `AndroidManifest.xml` 中的 Key (`...-18A`) 启用了 **Maps SDK for Android**。