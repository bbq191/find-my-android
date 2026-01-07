     概述

     根据 ADD_CONTACT_LOGIC.md 文档,实现 iOS Find My 风格的联系人位置共享功能,包括发起共享、接受共享、查看联系人位置等核心功能。

     用户需求确认

     - ✅ 匿名用户:显示联系人标签页但禁用,提示需要注册
     - ✅ 地址显示:本期暂不实现反向解析,只显示距离/时间
     - ✅ 邮箱搜索:允许向未注册邮箱发送邀请
     - ✅ 地图集成:本期实现联系人位置在地图上的显示

     ---
     Phase 1: 数据模型层 (优先级:高)

     1.1 创建新的数据模型

     创建 User.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\User.kt

     data class User(
         val uid: String,
         val email: String,
         val displayName: String? = null,
         val photoUrl: String? = null,
         val createdAt: Long = System.currentTimeMillis()
     )

     创建 ShareDuration.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\ShareDuration.kt

     enum class ShareDuration(val displayName: String, val durationMillis: Long?) {
         ONE_HOUR("分享一小时", 3600_000L),
         END_OF_DAY("分享到今天结束", null),
         INDEFINITELY("始终分享", null);

         companion object {
             fun calculateEndOfDay(): Long {
                 // 计算到今天23:59:59的毫秒数
             }
         }
     }

     创建 LocationShare.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\LocationShare.kt

     data class LocationShare(
         val id: String = "",
         val fromUid: String,
         val toEmail: String,
         val toUid: String? = null,
         val status: ShareStatus = ShareStatus.PENDING,
         val expireTime: Long? = null,
         val createdAt: Long = System.currentTimeMillis(),
         val acceptedAt: Long? = null
     )

     enum class ShareStatus {
         PENDING, ACCEPTED, EXPIRED, REJECTED
     }

     1.2 修改现有数据模型

     修改 Contact.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\Contact.kt

     当前结构:
     data class Contact(
         val id: String,
         val name: String,
         val location: LatLng? = null,
         val avatarUrl: String? = null,
         val lastUpdateTime: Long = System.currentTimeMillis(),
         val isSharingLocation: Boolean = false
     )

     扩展为:
     data class Contact(
         val id: String,
         val email: String,
         val name: String,
         val avatarUrl: String? = null,

         // 共享关系信息
         val shareStatus: ShareStatus = ShareStatus.PENDING,
         val shareDirection: ShareDirection,
         val expireTime: Long? = null,

         // 位置信息(仅当对方分享给我且已接受时有效)
         val location: LatLng? = null,
         val lastUpdateTime: Long? = null,
         val isLocationAvailable: Boolean = false
     )

     enum class ShareDirection {
         I_SHARE_TO_THEM,   // 我分享给他们
         THEY_SHARE_TO_ME,  // 他们分享给我
         MUTUAL             // 双向共享
     }

     修改 Device.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\Device.kt

     添加字段:
     data class Device(
         // ... 现有字段保持不变 ...
         val sharedWith: List<String> = emptyList() // 新增:共享给哪些用户UID
     )

     ---
     Phase 2: Repository 层 (优先级:高)

     2.1 创建 ContactRepository.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\repository\ContactRepository.kt

     核心方法:
     1. syncCurrentUser(): 同步当前用户信息到 Firestore users 集合
     2. searchUserByEmail(email): 根据邮箱搜索用户
     3. createLocationShare(toEmail, duration): 发起位置共享
     4. acceptLocationShare(shareId): 接受位置共享,更新设备的 sharedWith
     5. rejectLocationShare(shareId): 拒绝位置共享
     6. stopSharing(shareId): 停止共享,移除设备的 sharedWith
     7. observeMyContacts(): 实时监听联系人列表(Flow)

     Firestore 查询策略:
     - 由于 Firestore 不支持 OR 查询,需要两次查询后合并:
       - 查询1: whereEqualTo("fromUid", currentUid) - 我分享给别人的
       - 查询2: whereEqualTo("toUid", currentUid) - 别人分享给我的
     - 使用两个 addSnapshotListener 监听,任一变化时触发合并

     关键实现细节:
     - acceptLocationShare 时需要批量更新分享者的所有设备,添加当前用户到 sharedWith 数组
     - 使用 FieldValue.arrayUnion() 和 FieldValue.arrayRemove() 操作数组字段

     2.2 修改 DeviceRepository.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\repository\DeviceRepository.kt

     修改 observeDevices() 方法:

     当前实现:
     fun observeDevices(): Flow<List<Device>> = callbackFlow {
         val query = devicesCollection.whereEqualTo("ownerId", currentUserId)
         // ...
     }

     扩展为查询两类设备:
     fun observeDevices(): Flow<List<Device>> = callbackFlow {
         // 监听1: 我的设备 (ownerId == currentUserId)
         // 监听2: 共享给我的设备 (sharedWith array-contains currentUserId)
         // 合并结果并去重
     }

     添加方法:
     - updateDeviceSharedWith(deviceId, sharedWith): 更新设备的共享列表

     修改 saveDevice() 方法:
     - 保存时包含 sharedWith 字段

     ---
     Phase 3: ViewModel 层 (优先级:中)

     3.1 创建 ContactViewModel.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\contact\ContactViewModel.kt

     状态管理:
     class ContactViewModel : ViewModel() {
         private val contactRepository = ContactRepository()

         // 联系人列表
         private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
         val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

         // 显示添加对话框
         private val _showAddDialog = MutableStateFlow(false)
         val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

         // 错误消息
         private val _errorMessage = MutableStateFlow<String?>(null)
         val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

         // 加载状态
         private val _isLoading = MutableStateFlow(false)
         val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

         init {
             observeContacts()
             syncCurrentUser()
         }
     }

     核心方法:
     - showAddDialog() / hideAddDialog()
     - shareLocation(email, duration)
     - acceptShare(shareId)
     - stopSharing(shareId)
     - clearError()

     3.2 修改 MainViewModel.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\MainViewModel.kt

     集成 ContactViewModel:
     - 在 MainScreen 中实例化 ContactViewModel
     - 或在 MainViewModel 中添加联系人相关状态(根据项目架构选择)

     ---
     Phase 4: UI 层 (优先级:中)

     4.1 创建 ShareLocationDialog.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\components\ShareLocationDialog.kt

     UI 组件:
     - OutlinedTextField - 邮箱输入
     - RadioButton 组 - 时长选择(一小时/到今天结束/始终分享)
     - AlertDialog - 对话框容器
     - 表单验证:邮箱格式验证

     4.2 创建 ContactListPanel.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\components\ContactListPanel.kt

     参考现有的 DeviceListPanel 设计:
     - 顶部:标题"联系人" + FloatingActionButton(添加按钮)
     - 中间:LazyColumn 联系人列表
       - ContactListItem 组件:头像 + 名称 + 状态 + 共享方向指示
       - 状态显示:"正在等待接受..." / "位置可用" / "已过期"
     - 空状态:提示"暂无联系人,点击右上角+添加位置共享"

     匿名用户处理:
     - 检测 FirebaseAuth.getInstance().currentUser?.isAnonymous
     - 如果是匿名用户,显示提示:"位置共享功能需要注册账号"
     - 提供"去注册"按钮引导到登录页

     4.3 修改 DeviceListPanel.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\components\DeviceListPanel.kt

     当前 Tab 0 代码:
     0 -> "联系人功能开发中"

     修改为:
     0 -> {
         // 检查是否匿名用户
         val isAnonymous = FirebaseAuth.getInstance().currentUser?.isAnonymous ?: true

         if (isAnonymous) {
             // 显示匿名用户提示
             AnonymousUserPrompt(onSignUpClick = { /* 跳转登录页 */ })
         } else {
             // 显示联系人列表
             ContactListPanel(
                 contacts = contacts,
                 onContactClick = onContactClick,
                 onAddContactClick = onAddContactClick
             )
         }
     }

     添加参数:
     @Composable
     fun DeviceListPanel(
         devices: List<Device>,
         contacts: List<Contact>, // 新增
         onDeviceClick: (Device) -> Unit,
         onContactClick: (Contact) -> Unit, // 新增
         onAddContactClick: () -> Unit, // 新增
         onDeviceDelete: (Device) -> Unit = {},
         modifier: Modifier = Modifier
     )

     4.4 修改 MainScreen.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\MainScreen.kt

     集成联系人功能:
     1. 实例化 ContactViewModel
     2. 收集 contacts 和 showAddDialog 状态
     3. 传递给 DeviceListPanel
     4. 处理 ShareLocationDialog 显示/隐藏
     5. 处理联系人点击事件:在地图上定位到联系人位置

     4.5 创建地图联系人标记 (MapViewWrapper.kt)

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\components\MapViewWrapper.kt

     添加联系人 Marker:
     - 遍历 contacts 列表,为有位置的联系人添加 Marker
     - 区分设备和联系人:
       - 设备 Marker:蓝色/红色圆点
       - 联系人 Marker:绿色圆点或人形图标
     - 点击联系人 Marker 时显示联系人详情(可选)

     ---
     Phase 5: Firestore 安全规则 (优先级:高)

     5.1 更新 firestore.rules

     路径: E:\my-projects\findmy\firestore.rules (或在 Firebase Console 中更新)

     添加 users 集合规则:
     match /users/{userId} {
       // 所有已登录用户可以读取(用于搜索)
       allow read: if request.auth != null;

       // 只能创建/更新自己的用户信息
       allow create, update: if request.auth != null &&
                                request.auth.uid == userId;

       // 不允许删除
       allow delete: if false;
     }

     添加 location_shares 集合规则:
     match /location_shares/{shareId} {
       // 读取:分享者或接收者
       allow read: if request.auth != null && (
         resource.data.fromUid == request.auth.uid ||
         resource.data.toUid == request.auth.uid ||
         resource.data.toEmail == request.auth.token.email
       );

       // 创建:fromUid 必须是自己
       allow create: if request.auth != null &&
                        request.resource.data.fromUid == request.auth.uid;

       // 更新:分享者或接收者
       allow update: if request.auth != null && (
         resource.data.fromUid == request.auth.uid ||
         resource.data.toUid == request.auth.uid ||
         resource.data.toEmail == request.auth.token.email
       );

       // 删除:只有分享者
       allow delete: if request.auth != null &&
                        resource.data.fromUid == request.auth.uid;
     }

     更新 devices 集合规则:
     match /devices/{deviceId} {
       // 读取:设备拥有者 OR 被分享的用户
       allow read: if request.auth != null && (
         resource.data.ownerId == request.auth.uid ||
         resource.data.sharedWith.hasAny([request.auth.uid])
       );

       // 创建/更新/删除:只能操作自己的设备
       allow create: if request.auth != null &&
                        request.resource.data.ownerId == request.auth.uid;

       allow update: if request.auth != null &&
                        resource.data.ownerId == request.auth.uid;

       allow delete: if request.auth != null &&
                        resource.data.ownerId == request.auth.uid;
     }

     ---
     Phase 6: 用户登录集成 (优先级:中)

     6.1 修改 LoginScreen.kt 或 AuthViewModel.kt

     路径: E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\auth\LoginScreen.kt

     在用户登录/注册成功后调用:
     // 邮箱登录成功后
     viewModelScope.launch {
         contactRepository.syncCurrentUser()
     }

     注意:匿名用户不需要同步(因为没有邮箱)

     ---
     Phase 7: 测试与验证 (优先级:高)

     7.1 单元测试(可选)

     - Repository 层方法测试
     - ViewModel 状态流转测试

     7.2 集成测试

     双用户场景:
     1. 用户 A(a@test.com) 登录
     2. 用户 A 分享位置给 b@test.com(一小时)
     3. 验证 Firestore 中创建了 location_shares 记录
     4. 用户 B(b@test.com) 登录
     5. 用户 B 在联系人列表看到来自 A 的共享邀请
     6. 用户 B 接受共享
     7. 验证:
       - location_shares 状态变为 ACCEPTED
       - 用户 A 的设备 sharedWith 包含 B 的 UID
       - 用户 B 能在地图上看到 A 的设备
     8. 用户 A 停止共享
     9. 验证 B 无法再看到 A 的设备

     匿名用户场景:
     1. 匿名登录
     2. 点击"联系人"标签页
     3. 验证显示"需要注册账号"提示

     ---
     实施顺序建议

     第1步:数据基础 (2-3小时)

     - 创建所有数据模型(User, ShareDuration, LocationShare)
     - 修改 Contact.kt 和 Device.kt

     第2步:Repository 层 (4-5小时)

     - 创建 ContactRepository 完整实现
     - 修改 DeviceRepository 支持共享设备查询

     第3步:ViewModel (2小时)

     - 创建 ContactViewModel

     第4步:UI 基础组件 (3-4小时)

     - 创建 ShareLocationDialog
     - 创建 ContactListPanel
     - 修改 DeviceListPanel 集成联系人 Tab

     第5步:主界面集成 (2小时)

     - 修改 MainScreen 集成联系人功能
     - 处理对话框和事件流

     第6步:地图集成 (2小时)

     - 在 MapViewWrapper 添加联系人 Marker

     第7步:安全规则和登录集成 (1小时)

     - 更新 Firestore 规则
     - 在登录流程中添加用户同步

     第8步:测试 (2-3小时)

     - 双用户测试
     - 边界情况测试

     预计总工时: 18-22小时

     ---
     关键技术挑战与解决方案

     挑战1: Firestore 不支持 OR 查询

     解决方案: 执行两次独立查询后在客户端合并,使用两个 addSnapshotListener 监听变化

     挑战2: 设备 sharedWith 同步

     解决方案: 在 acceptLocationShare 中批量查询分享者的所有设备并更新 sharedWith 字段,使用 FieldValue.arrayUnion()

     挑战3: 实时更新性能

     解决方案:
     - 使用 Firestore 的实时监听机制
     - 客户端只监听必要的数据
     - 使用 distinctBy 去重避免重复渲染

     挑战4: 匿名用户边界处理

     解决方案: 在 UI 层检测 isAnonymous,显示不同的内容而非隐藏 Tab

     ---
     关键文件清单

     新建文件 (7个)

     1. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\User.kt
     2. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\ShareDuration.kt
     3. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\LocationShare.kt
     4. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\repository\ContactRepository.kt
     5. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\contact\ContactViewModel.kt
     6. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\components\ContactListPanel.kt
     7. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\components\ShareLocationDialog.kt

     修改文件 (6个)

     1. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\Contact.kt
     2. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\model\Device.kt
     3. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\data\repository\DeviceRepository.kt
     4. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\components\DeviceListPanel.kt
     5. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\components\MapViewWrapper.kt
     6. E:\my-projects\findmy\app\src\main\java\me\ikate\findmy\ui\screen\main\MainScreen.kt

     配置文件

     - firestore.rules (通过 Firebase Console 更新)

     ---
     后续优化方向

     1. 推送通知: 使用 Firebase Cloud Messaging,接收共享邀请时推送通知
     2. 反向地理编码: 集成地图 API,显示详细地址信息
     3. 过期自动清理: 使用 Cloud Functions 定期清理过期共享
     4. 共享历史记录: 保存已删除的共享记录供用户查看
     5. 批量操作: 支持同时向多个联系人发起共享
     6. 地理围栏：联系人离开/到达某地发通知
     7. 导航: 点击联系人可导航到联系人相应位置，可选交通工具

     ---
     备注

     - 遵循现有架构模式:Repository + ViewModel + Compose UI
     - 使用 StateFlow 进行状态管理
     - 使用 callbackFlow 处理 Firestore 实时监听
     - 直接实例化 Repository,无需 DI 框架
     - 所有文本使用中文,符合项目规范
     - UI 设计参考现有的 DeviceListPanel 风格