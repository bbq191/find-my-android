package me.ikate.findmy.ui.screen.main.tabs

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import me.ikate.findmy.ui.components.ContactsPermissionDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import me.ikate.findmy.BuildConfig
import me.ikate.findmy.R
import me.ikate.findmy.data.model.User
import me.ikate.findmy.util.ProfileHelper

/**
 * 我的 Tab
 * 个人资料和设置页面
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MeTab(
    currentUser: User?,
    meName: String?,
    meAvatarUrl: String?,
    sharingWithCount: Int = 0,
    onNameChange: (String) -> Unit = {},
    onAvatarChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showAppIconDialog by remember { mutableStateOf(false) }

    // 通讯录权限状态
    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    var showContactsPermissionDialog by remember { mutableStateOf(false) }
    // 记录权限请求后要执行的操作
    var pendingContactsAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // 监听权限状态变化，执行待处理的操作
    androidx.compose.runtime.LaunchedEffect(contactsPermissionState.status.isGranted) {
        if (contactsPermissionState.status.isGranted && pendingContactsAction != null) {
            pendingContactsAction?.invoke()
            pendingContactsAction = null
        }
    }

    var showAboutDialog by remember { mutableStateOf(false) }

    // 用于从通讯录选择联系人的临时状态
    var pendingContactName by remember { mutableStateOf<String?>(null) }
    var pendingContactAvatar by remember { mutableStateOf<String?>(null) }

    // 联系人选择器
    val contactPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            val profile = ProfileHelper.getContactFromUri(context, uri)
            if (profile != null && !profile.displayName.isNullOrBlank()) {
                pendingContactName = profile.displayName
                pendingContactAvatar = profile.photoUri?.toString()
            }
        }
    }

    // 编辑名称对话框
    if (showEditNameDialog) {
        EditNameDialog(
            currentName = meName ?: "",
            pendingName = pendingContactName,
            pendingAvatar = pendingContactAvatar,
            onDismiss = {
                showEditNameDialog = false
                pendingContactName = null
                pendingContactAvatar = null
            },
            onConfirm = { newName, avatarUri ->
                onNameChange(newName)
                avatarUri?.let { onAvatarChange(it) }
                showEditNameDialog = false
                pendingContactName = null
                pendingContactAvatar = null
            },
            onPickContact = {
                contactPickerLauncher.launch(null)
            },
            hasContactsPermission = contactsPermissionState.status.isGranted,
            onRequestContactsPermission = { action ->
                pendingContactsAction = action
                showContactsPermissionDialog = true
            }
        )
    }

    // 应用图标选择对话框
    if (showAppIconDialog) {
        AppIconDialog(
            onDismiss = { showAppIconDialog = false }
        )
    }

    // 关于对话框
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }

    // 通讯录权限引导对话框
    if (showContactsPermissionDialog) {
        val isPermanentlyDenied = !contactsPermissionState.status.isGranted &&
            !contactsPermissionState.status.shouldShowRationale

        ContactsPermissionDialog(
            onRequestPermission = {
                showContactsPermissionDialog = false
                contactsPermissionState.launchPermissionRequest()
            },
            onDismiss = {
                showContactsPermissionDialog = false
                pendingContactsAction = null
            },
            onOpenSettings = if (isPermanentlyDenied) {
                {
                    showContactsPermissionDialog = false
                    // 打开应用设置
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }
            } else null
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // iOS 风格大标题
        MeHeader()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 个人资料卡片
            item {
                ProfileCard(
                    user = currentUser,
                    displayName = meName,
                    avatarUrl = meAvatarUrl,
                    sharingWithCount = sharingWithCount,
                    onEditClick = { showEditNameDialog = true }
                )
            }

            // 设置选项
            item {
                SettingsSection(
                    onAppIconClick = { showAppIconDialog = true },
                    onNotificationsClick = {
                        // 打开系统通知设置
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    onPrivacyClick = {
                        // 打开应用权限设置
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    onAboutClick = { showAboutDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun MeHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            Text(
                text = "我",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ProfileCard(
    user: User?,
    displayName: String?,
    avatarUrl: String?,
    sharingWithCount: Int = 0,
    onEditClick: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onEditClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像 - 统一样式
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "头像",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else if (!displayName.isNullOrBlank()) {
                        Text(
                            text = displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 用户信息
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayName ?: "点击设置名称",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (displayName != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    // 标签 - 统一样式
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = "我",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 位置共享状态
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (sharingWithCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = if (sharingWithCount > 0) {
                            "正在与 $sharingWithCount 人共享位置"
                        } else {
                            "未共享位置"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // UID - 完整显示
                user?.let { u ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = u.uid,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        androidx.compose.ui.platform.ClipEntry(
                                            android.content.ClipData.newPlainText("UID", u.uid)
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制 UID",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSection(
    onAppIconClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    var showLicensesDialog by remember { mutableStateOf(false) }

    // 开源协议对话框
    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            SettingsItem(
                icon = Icons.Default.AppShortcut,
                title = "应用图标",
                subtitle = "自定义应用图标样式",
                onClick = onAppIconClick
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "通知设置",
                subtitle = "管理推送通知偏好",
                onClick = onNotificationsClick
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingsItem(
                icon = Icons.Default.Security,
                title = "隐私设置",
                subtitle = "管理位置共享权限",
                onClick = onPrivacyClick
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingsItem(
                icon = Icons.Default.Info,
                title = "关于",
                subtitle = "版本信息与帮助",
                onClick = onAboutClick
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            SettingsItem(
                icon = Icons.Default.Description,
                title = "开源协议",
                subtitle = "查看第三方开源库许可",
                onClick = { showLicensesDialog = true }
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 编辑名称对话框
 * @param pendingName 从通讯录选择后待确认的名称
 * @param pendingAvatar 从通讯录选择后待确认的头像
 * @param onConfirm (name, avatarUri) -> Unit
 * @param onPickContact 打开通讯录选择器
 * @param hasContactsPermission 是否有通讯录权限
 * @param onRequestContactsPermission 请求通讯录权限的回调
 */
@Composable
private fun EditNameDialog(
    currentName: String,
    pendingName: String? = null,
    pendingAvatar: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
    onPickContact: () -> Unit = {},
    hasContactsPermission: Boolean = false,
    onRequestContactsPermission: (action: () -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentName) }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var availableSources by remember { mutableStateOf<List<Pair<ProfileHelper.ProfileSource, ProfileHelper.OwnerProfile>>>(emptyList()) }

    // 当从通讯录选择返回时，更新状态
    androidx.compose.runtime.LaunchedEffect(pendingName, pendingAvatar) {
        if (pendingName != null) {
            name = pendingName
            avatarUri = pendingAvatar
            importMessage = "已选择: $pendingName" +
                    if (pendingAvatar != null) " (含头像)" else ""
            isError = false
        }
    }

    // 来源选择对话框
    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            title = {
                Text(
                    text = "选择导入来源",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (availableSources.isEmpty()) {
                        Text(
                            text = "未找到可用的导入来源",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        availableSources.forEach { (source, profile) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        name = profile.displayName ?: ""
                                        avatarUri = profile.photoUri?.toString()
                                        importMessage = "已导入: ${profile.displayName}" +
                                                if (profile.photoUri != null) " (含头像)" else ""
                                        isError = false
                                        showSourcePicker = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = source.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = profile.displayName ?: "",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcePicker = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "设置名称",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "设置一个名称，方便联系人识别你",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        importMessage = null
                    },
                    label = { Text("名称") },
                    placeholder = { Text("请输入名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 从机主信息导入按钮
                TextButton(
                    onClick = {
                        // 检查权限
                        if (!hasContactsPermission) {
                            // 请求权限，并设置授权后的操作
                            onRequestContactsPermission {
                                availableSources = ProfileHelper.getAvailableSources(context)
                                if (availableSources.isEmpty()) {
                                    importMessage = "未找到可用的导入来源，请从通讯录选择"
                                    isError = true
                                } else {
                                    showSourcePicker = true
                                }
                            }
                            return@TextButton
                        }

                        // 获取可用来源
                        availableSources = ProfileHelper.getAvailableSources(context)
                        if (availableSources.isEmpty()) {
                            importMessage = "未找到可用的导入来源，请从通讯录选择"
                            isError = true
                        } else {
                            showSourcePicker = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("从机主信息导入")
                }

                // 从通讯录选择按钮
                TextButton(
                    onClick = {
                        if (!hasContactsPermission) {
                            onRequestContactsPermission { onPickContact() }
                        } else {
                            onPickContact()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("从通讯录选择")
                }

                // 提示信息
                importMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), avatarUri) },
                enabled = name.trim().isNotEmpty()
            ) {
                Text(
                    text = "确定",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * 应用图标选择对话框
 */
@Composable
private fun AppIconDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // 图标选项
    data class IconOption(
        val name: String,
        val componentName: String,
        val iconRes: Int
    )

    val iconOptions = listOf(
        IconOption("男孩", ".MainActivityBoy", R.mipmap.ic_launcher_boy),
        IconOption("女孩", ".MainActivityGirl", R.mipmap.ic_launcher_girl)
    )

    // 获取当前启用的图标
    var currentIcon by remember {
        mutableStateOf(
            iconOptions.firstOrNull { option ->
                try {
                    val componentName = ComponentName(context.packageName, context.packageName + option.componentName)
                    packageManager.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } catch (_: Exception) {
                    false
                }
            }?.componentName ?: ".MainActivityBoy"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择应用图标",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "选择你喜欢的图标样式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    iconOptions.forEach { option ->
                        val isSelected = currentIcon == option.componentName

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    // 切换图标
                                    iconOptions.forEach { opt ->
                                        val componentName = ComponentName(
                                            context.packageName,
                                            context.packageName + opt.componentName
                                        )
                                        val newState = if (opt.componentName == option.componentName) {
                                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                        } else {
                                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                        }
                                        packageManager.setComponentEnabledSetting(
                                            componentName,
                                            newState,
                                            PackageManager.DONT_KILL_APP
                                        )
                                    }
                                    currentIcon = option.componentName
                                }
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = option.iconRes,
                                    contentDescription = option.name,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "切换图标后，桌面图标可能需要几秒钟才能更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "完成",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * 获取当前启用的应用图标资源
 */
private fun getCurrentAppIconRes(context: Context): Int {
    val packageManager = context.packageManager
    val packageName = context.packageName

    // 检查女孩图标是否启用
    return try {
        val girlComponent = ComponentName(packageName, "$packageName.MainActivityGirl")
        if (packageManager.getComponentEnabledSetting(girlComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            R.mipmap.ic_launcher_girl
        } else {
            R.mipmap.ic_launcher_boy
        }
    } catch (_: Exception) {
        R.mipmap.ic_launcher_boy // 默认男孩图标
    }
}

/**
 * 关于对话框
 */
@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentIconRes = remember { getCurrentAppIconRes(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "关于",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 应用图标（跟随当前设置）
                AsyncImage(
                    model = currentIconRes,
                    contentDescription = "应用图标",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                // 应用名称
                Text(
                    text = context.getString(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // 版本信息
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 描述
                Text(
                    text = "一款位置共享与设备查找应用，帮助您与家人朋友保持联系",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 功能列表
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "实时位置共享" to "与家人朋友共享位置",
                        "设备查找" to "远程响铃、丢失模式",
                        "地理围栏" to "到达/离开指定区域提醒",
                        "智能定位" to "根据活动状态智能上报"
                    ).forEach { (title, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "确定",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * 开源协议对话框
 */
@Composable
private fun LicensesDialog(
    onDismiss: () -> Unit
) {
    // 开源库列表
    data class License(
        val name: String,
        val author: String,
        val license: String
    )

    val licenses = listOf(
        License(
            name = "Jetpack Compose",
            author = "Google",
            license = "Apache License 2.0"
        ),
        License(
            name = "Material Design 3",
            author = "Google",
            license = "Apache License 2.0"
        ),
        License(
            name = "Mapbox Maps SDK",
            author = "Mapbox",
            license = "Mapbox Terms of Service"
        ),
        License(
            name = "高德定位 SDK",
            author = "高德",
            license = "高德开放平台服务协议"
        ),
        License(
            name = "高德地理围栏 SDK",
            author = "高德",
            license = "高德开放平台服务协议"
        ),
        License(
            name = "Eclipse Paho MQTT",
            author = "Eclipse Foundation",
            license = "Eclipse Public License 2.0"
        ),
        License(
            name = "Room Database",
            author = "Google",
            license = "Apache License 2.0"
        ),
        License(
            name = "个推推送 SDK",
            author = "个推",
            license = "个推服务协议"
        ),
        License(
            name = "Coil",
            author = "Coil Contributors",
            license = "Apache License 2.0"
        ),
        License(
            name = "Accompanist Permissions",
            author = "Google",
            license = "Apache License 2.0"
        ),
        License(
            name = "Kotlin Coroutines",
            author = "JetBrains",
            license = "Apache License 2.0"
        ),
        License(
            name = "AndroidX Libraries",
            author = "Google",
            license = "Apache License 2.0"
        ),
        License(
            name = "WorkManager",
            author = "Google",
            license = "Apache License 2.0"
        ),
        License(
            name = "Biometric",
            author = "Google",
            license = "Apache License 2.0"
        ),
        License(
            name = "Gson",
            author = "Google",
            license = "Apache License 2.0"
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "开源协议",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "本应用使用了以下开源库，感谢开源社区的贡献：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                licenses.forEach { license ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = license.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${license.author} · ${license.license}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Apache License 2.0 说明
                Text(
                    text = "Apache License 2.0",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Licensed under the Apache License, Version 2.0. " +
                            "You may obtain a copy of the License at:\n" +
                            "http://www.apache.org/licenses/LICENSE-2.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "确定",
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
