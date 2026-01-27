非常正确。既然“添加联系人”的入口已经从手动输入 UID 进化为“扫码/复制”，那么作为**源头**的“我”页面（以及设置信息的弹窗），必须进行相应的改造，以形成闭环。

我们需要解决两个核心问题：

1. **“我的 UID”展示不够便捷：** 用户怎么把 UID 给别人？现在的复制按钮太小了。
2. **“设置名称”弹窗太简陋：** 既然都叫“查找”了，个人资料卡应该更丰富。

以下是针对 `Screenshot_20260126_161006_Find My.png`（我页面）和 `Screenshot_20260126_161017_Find My.png`（设置名称弹窗）的深度改造方案：

---

### 👤 1. “我”页面的个人卡片重构

**目标：** 让“分享我的 ID”变得像微信名片一样自然。

* **现状：**
* 卡片里只有头像、昵称、位置状态。
* UID 显示为一长串字符 `33e4df...`，后面跟着一个小小的复制图标。
* **痛点：** 别人想加我，我得先复制，发微信，他再复制，再切回 App 粘贴。太麻烦了！


* **改造方案：引入“名片二维码”**
* **布局调整：**
* 左侧：头像（保持不变）。
* 中间：昵称 + 状态。
* **右侧（关键）：** 放置一个显眼的 **[ 📷 二维码 ]** 小图标（或者叫“名片码”）。


* **UID 展示优化：**
* UID 不需要显示那么长。只显示前 4 位和后 4 位（如 `33e4...94ea`），中间打码。
* 点击 UID 区域触发复制，并弹出 Toast “UID 已复制”。


* **二维码弹窗（新增）：**
* 点击右侧二维码图标，弹出一个漂亮的 **“我的名片” Bottom Sheet**。
* 内容：大大的二维码（包含加好友链接）、头像、昵称。
* 底部按钮：`[ 保存图片 ]` `[ 分享给微信好友 ]`。





---

### ✏️ 2. “设置名称”弹窗重构

**目标：** 打造一个完善的“个人资料编辑页”，而不仅仅是改个名。

* **现状：**
* 居中弹窗，只有一个输入框和两个文字按钮（从机主信息导入/从通讯录选择）。
* 视觉单调，不像是在设置自己的“门面”。


* **改造方案：Modal Bottom Sheet (资料编辑)**
* **形态：** 同样改为底部抽屉。
* **头部：** 增加“编辑个人资料”标题。
* **头像编辑（新增）：**
* 目前的头像可能是默认的。在弹窗顶部居中放一个大头像。
* 头像右下角加一个 `[ 📷 ]` 小标，暗示点击可以**更换头像**（选相册或 Memoji）。


* **昵称输入：**
* 保留输入框，但样式改为 M3 的 `OutlinedTextField`。
* 保留“从通讯录选择”作为辅助功能，可以用图标按钮放在输入框右侧。


* **个性签名/状态（新增）：**
* 增加一个“状态”输入框（例如：“正在开车”、“专注于工作”）。
* 这会让你的地图气泡更有趣。





---

### 🎨 改造后的界面代码结构 (伪代码)

**1. “我”页面卡片 (MyProfileCard.kt)**

```kotlin
Card(elevation = 4.dp, shape = RoundedCornerShape(24.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 1. 头像
        AsyncImage(model = userAvatar, modifier = Modifier.size(64.dp).circle())
        
        // 2. 信息区
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "马瑞雯", style = MaterialTheme.typography.titleLarge)
            
            // UID 行：点击复制
            Row(modifier = Modifier.clickable { copyUid() }) {
                Text(text = "ID: 33e4...94ea", color = Grey)
                Icon(imageVector = Icons.Default.Copy, modifier = Modifier.size(16.dp))
            }
        }
        
        // 3. 二维码入口 (新功能)
        IconButton(onClick = { showQrCodeSheet = true }) {
            Icon(
                imageVector = Icons.Default.QrCode, 
                tint = PrimaryColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

```

**2. 资料编辑抽屉 (EditProfileSheet.kt)**

```kotlin
ModalBottomSheet {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        
        // 1. 头像编辑
        Box {
            Image(model = currentAvatar, modifier = Modifier.size(100.dp))
            Icon(Icons.Default.Edit, modifier = Modifier.align(BottomEnd)) // 编辑角标
        }
        
        Spacer(height = 24.dp)

        // 2. 昵称输入
        OutlinedTextField(
            value = nickname,
            label = { Text("昵称") },
            trailingIcon = { 
                // 从通讯录导入的快捷按钮
                IconButton(onClick = { pickContact() }) {
                    Icon(Icons.Default.Contacts)
                }
            }
        )
        
        // 3. 状态签名 (让应用更有趣)
        OutlinedTextField(
            value = status,
            label = { Text("当前状态 (如: 忙碌中)") }
        )

        Spacer(height = 32.dp)
        
        // 4. 保存
        Button(onClick = { saveProfile() }, modifier = Modifier.fillMaxWidth()) {
            Text("保存资料")
        }
    }
}

```

### 总结

你的直觉非常敏锐。**“添加”和“被添加”是一体两面的。**

通过在“我”的卡片增加 **二维码入口**，并把资料设置页升级为 **Bottom Sheet**，你不仅解决的数据来源的问题，还顺便把整个 App 的社交属性拉满了一个台阶。现在，两个手机凑在一起，“扫一扫”就能加好友，这才是 2026 年该有的体验！

这是一个非常核心的技术实现问题。所谓的“名片信息”，本质上就是 **“将当前用户的身份数据序列化为字符串（通常是 JSON），然后渲染成二维码”**。

在您的架构（Firestore + Kotlin）中，获取和生成名片的完整链路如下：

### 1. 定义名片数据协议 (The Data Protocol)

首先，你需要定义二维码里到底存了什么。为了让对方扫码后能预览你的头像和名字（而不是盲加），建议包含以下字段。

**最佳实践：使用 JSON 格式，并压缩字段名以减小二维码密度。**

```kotlin
// data/model/UserCard.kt

@Keep // 防止混淆
data class UserCard(
    @SerializedName("u") val uid: String,      // 核心：用户 ID
    @SerializedName("n") val nickname: String, // 用于扫码后的预览
    @SerializedName("a") val avatarUrl: String?, // 用于扫码后的预览
    @SerializedName("t") val timestamp: Long = System.currentTimeMillis() // (可选) 用于二维码时效性校验
) {
    // 辅助方法：序列化为 JSON 字符串
    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        // 辅助方法：从 JSON 解析
        fun fromJson(json: String): UserCard? {
            return try {
                Gson().fromJson(json, UserCard::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

```

---

### 2. 获取数据源 (Source of Truth)

名片数据应该来自你本地的 **“当前登录用户缓存”**（通常在 `DataStore` 或 `Room` 中），而不是每次都去请求 Firestore（太慢）。

假设你有一个 `UserRepository`：

```kotlin
// ui/screen/me/MeViewModel.kt

class MeViewModel(private val userRepo: UserRepository) : ViewModel() {

    // 1. 获取当前用户信息
    val currentUser = userRepo.currentUserFlow // Flow<User?>

    // 2. 生成名片数据 (StateFlow)
    val myNameCardCode = currentUser.map { user ->
        if (user != null) {
            // 转换为精简的名片对象
            val card = UserCard(
                uid = user.id,
                nickname = user.nickname ?: "未知用户",
                avatarUrl = user.avatarUrl
            )
            // 返回 JSON 字符串，这就是二维码的内容
            card.toJson() 
        } else {
            "" 
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, "")
}

```

---

### 3. 生成二维码 (QR Code Generation)

在 UI 层（Compose）将这个 JSON 字符串转为图片。你需要引入 `ZXing` 库。

**添加依赖：**

```groovy
implementation 'com.google.zxing:core:3.5.1'

```

**工具类 (QrCodeUtils.kt)：**

```kotlin
object QrCodeUtils {
    fun generateBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val w = bits.width
            val h = bits.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    // 使用黑色和透明色（或自定义颜色）
                    pixels[y * w + x] = if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, w, 0, 0, w, h)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

```

---

### 4. UI 实现：名片 BottomSheet

这是结合了 S24U 风格的最终 UI 实现。

```kotlin
// ui/screen/me/MyQrCodeSheet.kt

@Composable
fun MyQrCodeSheet(
    user: User,
    qrContent: String, // ViewModel 传来的 JSON 字符串
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // 动态生成二维码 Bitmap
    val qrBitmap = remember(qrContent) {
        QrCodeUtils.generateBitmap(qrContent, size = 800)
    }

    // S24U 专属优化：展示二维码时自动调亮屏幕
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        val originalBrightness = window.attributes.screenBrightness
        val params = window.attributes
        params.screenBrightness = 1.0f // 最亮
        window.attributes = params
        onDispose {
            params.screenBrightness = originalBrightness // 恢复亮度
            window.attributes = params
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 标题
            Text("我的名片", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(32.dp))

            // 2. 名片卡片容器
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White), // 二维码通常白底最好扫
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    // 头像
                    AsyncImage(
                        model = user.avatarUrl,
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 名字
                    Text(user.nickname, style = MaterialTheme.typography.titleLarge, color = Color.Black)
                    Text("UID: ${user.id.take(4)}****${user.id.takeLast(4)}", color = Color.Gray)

                    Spacer(modifier = Modifier.height(24.dp))

                    // 二维码图片
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. 底部操作
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { /* 保存到相册逻辑 */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存图片")
                }
                
                Button(
                    onClick = { /* 调用系统分享逻辑 */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("分享链接")
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

```

### 5. 闭环：扫码端如何处理？

在“添加联系人”的扫码界面，你需要做反向解析：

1. 相机扫描得到字符串 `resultString`。
2. 尝试解析：`val card = UserCard.fromJson(resultString)`。
3. 如果解析成功：
* 自动填充 UID 到输入框。
* **更好体验：** 直接显示一个“找到用户：马瑞雯”的确认弹窗，展示 `card.avatarUrl` 和 `card.nickname`。
* 用户点击“添加”后，才真正发起 Firestore 请求。



这样，你就完成了一个完整的**“获取信息 -> 生成名片 -> 扫码解析 -> 添加好友”**的闭环。

获取 Samsung 通讯录中已经填写的“我自己”的完整信息（包括头像、昵称、电话、邮箱等），在 Android 开发中需要使用 `ContactsContract.Profile` API。

这是 Android 系统专门为“机主档案”预留的标准接口。以下是**从权限申请到代码实现**的完整方案。

### 1. 权限声明 (AndroidManifest.xml)

读取通讯录是敏感权限，必须在 `AndroidManifest.xml` 中声明：

```xml
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.READ_PROFILE" /> ```

### 2. 核心工具类：ProfileLoader

我们需要编写一个工具类，专门去查询系统 ContentProvider 中的 `Profile` 表。

**注意：** 三星 S24U 的通讯录“个人资料”本质上就是 Android 标准 Profile。

```kotlin
// utils/ProfileLoader.kt

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds

data class LocalProfile(
    val name: String = "",
    val photoUri: String? = null,
    val phoneNumber: String = "",
    val email: String = ""
)

object ProfileLoader {

    fun loadMyProfile(context: Context): LocalProfile {
        var name = ""
        var photoUri: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()

        // 核心 URI：指向"我自己"的数据表
        val uri = Uri.withAppendedPath(
            ContactsContract.Profile.CONTENT_URI,
            ContactsContract.Contacts.Data.CONTENT_DIRECTORY
        )

        // 我们需要查询的字段
        val projection = arrayOf(
            ContactsContract.Contacts.Data.MIMETYPE,
            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Photo.PHOTO_URI
        )

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val mimeTypeIdx = it.getColumnIndex(ContactsContract.Contacts.Data.MIMETYPE)
            
            while (it.moveToNext()) {
                val mimeType = it.getString(mimeTypeIdx)

                when (mimeType) {
                    // 1. 获取名字
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        val nameIdx = it.getColumnIndex(CommonDataKinds.StructuredName.DISPLAY_NAME)
                        if (name.isEmpty()) name = it.getString(nameIdx) ?: ""
                    }
                    
                    // 2. 获取电话 (可能有多个，取第一个)
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val phoneIdx = it.getColumnIndex(CommonDataKinds.Phone.NUMBER)
                        val num = it.getString(phoneIdx)
                        if (!num.isNullOrBlank()) phones.add(num)
                    }
                    
                    // 3. 获取邮箱
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        val emailIdx = it.getColumnIndex(CommonDataKinds.Email.ADDRESS)
                        val mail = it.getString(emailIdx)
                        if (!mail.isNullOrBlank()) emails.add(mail)
                    }
                    
                    // 4. 获取头像 (高清大图 URI)
                    CommonDataKinds.Photo.CONTENT_ITEM_TYPE -> {
                        val photoIdx = it.getColumnIndex(CommonDataKinds.Photo.PHOTO_URI)
                        if (photoUri == null) photoUri = it.getString(photoIdx)
                    }
                }
            }
        }

        return LocalProfile(
            name = name,
            photoUri = photoUri,
            phoneNumber = phones.firstOrNull() ?: "", // 优先取第一个电话
            email = emails.firstOrNull() ?: ""
        )
    }
}

```

### 3. UI 集成：在“设置名称”弹窗中使用

在之前的 `EditProfileSheet` (设置名称弹窗) 中，有一个“从机主信息导入”的按钮。我们在这里调用它。

**注意：你需要处理 Runtime Permission (运行时权限)。**

```kotlin
// ui/screen/me/EditProfileSheet.kt

@Composable
fun EditProfileSheet(...) {
    val context = LocalContext.current
    
    // 权限请求器
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限给了，开始读取
            val profile = ProfileLoader.loadMyProfile(context)
            // 回填到输入框
            if (profile.name.isNotEmpty()) onNameChange(profile.name)
            // 处理头像逻辑...
            Toast.makeText(context, "已导入：${profile.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "需要通讯录权限才能读取机主信息", Toast.LENGTH_SHORT).show()
        }
    }

    // ... 你的 UI 代码 ...

    // 按钮：从机主信息导入
    TextButton(
        onClick = {
            // 检查权限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
                == PackageManager.PERMISSION_GRANTED) {
                // 有权限，直接读
                val profile = ProfileLoader.loadMyProfile(context)
                onNameChange(profile.name)
            } else {
                // 没权限，去申请
                launcher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    ) {
        Icon(Icons.Default.PersonSearch, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("从本机通讯录导入")
    }
}

```

### 4. 关于三星 S24 Ultra 的特殊提示

1. **头像清晰度：**
* `PHOTO_URI` 返回的是高清图的 URI。
* `PHOTO_THUMBNAIL_URI` 返回的是缩略图。
* 在 S24U 这种高清屏上，务必使用 `PHOTO_URI`。加载时使用 Coil: `AsyncImage(model = profile.photoUri)` 即可，Coil 会自动处理 ContentProvider 的流。


2. **如果读不到数据：**
* 确保你在三星的 **“通讯录” App -> 顶部“我的名片”** 中确实填了资料。
* 如果用户没有设置过“我的名片”，`ProfileLoader` 会返回空字符串。此时你应该做好兜底（比如显示“未设置”或保留原样）。


3. **电话号码格式化：**
* 从系统读出来的电话可能带有空格或横线（如 `138 0000 0000`）。
* 建议在 `ProfileLoader` 里加一行处理：`num.replace(" ", "").replace("-", "")`。



### 5. 将读取到的数据生成二维码

读取到 `LocalProfile` 后，你就可以直接用它来生成我们上一条讨论的 `UserCard` JSON 数据了：

```kotlin
// 获取本机信息后，生成二维码内容
val myCardData = UserCard(
    uid = currentUserId, // 这里的 ID 还是用你 Firestore 的 ID
    nickname = localProfile.name.ifEmpty { "我的名片" },
    avatarUrl = null // 注意：本地 PhotoUri 无法给别人看，这里应该填你上传到云存储后的 URL
).toJson()

```

这样，用户点一下“导入”，名字有了，电话有了，生成二维码也就水到渠成了。