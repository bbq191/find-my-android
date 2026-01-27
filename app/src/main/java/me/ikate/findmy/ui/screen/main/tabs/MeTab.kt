package me.ikate.findmy.ui.screen.main.tabs

import me.ikate.findmy.ui.components.PermissionSecurityScreen
import me.ikate.findmy.ui.components.getPermissionStatusSummary
import me.ikate.findmy.util.PermissionStatusChecker
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import me.ikate.findmy.ui.theme.FindMyShapes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import me.ikate.findmy.data.model.User
import me.ikate.findmy.ui.screen.main.tabs.components.AboutSheet
import me.ikate.findmy.ui.screen.main.tabs.components.AppIconSheet
import me.ikate.findmy.ui.screen.main.tabs.components.EditProfileSheet
import me.ikate.findmy.ui.screen.main.tabs.components.LicensesScreen
import me.ikate.findmy.ui.screen.main.tabs.components.MyQrCodeSheet

/**
 * 我的 Tab
 * 个人资料和设置页面
 */
@Composable
fun MeTab(
    currentUser: User?,
    meName: String?,
    meAvatarUrl: String?,
    meStatus: String? = null,
    sharingWithCount: Int = 0,
    onNameChange: (String) -> Unit = {},
    onAvatarChange: (String?) -> Unit = {},
    onStatusChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showQrCodeSheet by remember { mutableStateOf(false) }
    var showAppIconSheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var showLicensesScreen by remember { mutableStateOf(false) }
    var showPermissionSecurityScreen by remember { mutableStateOf(false) }

    // 权限状态
    var permissionStatus by remember {
        mutableStateOf(PermissionStatusChecker.checkAllPermissions(context))
    }

    // 编辑资料 BottomSheet
    if (showEditProfileSheet) {
        EditProfileSheet(
            currentName = meName,
            currentAvatarUrl = meAvatarUrl,
            currentStatus = meStatus,
            onDismiss = { showEditProfileSheet = false },
            onSave = { newName, avatarUrl, status ->
                onNameChange(newName)
                avatarUrl?.let { onAvatarChange(it) }
                onStatusChange(status)
                showEditProfileSheet = false
            }
        )
    }

    // 名片二维码 BottomSheet
    if (showQrCodeSheet && currentUser != null) {
        MyQrCodeSheet(
            uid = currentUser.uid,
            nickname = meName ?: "未设置",
            avatarUrl = meAvatarUrl,
            status = meStatus,
            onDismiss = { showQrCodeSheet = false }
        )
    }

    // 应用图标选择 BottomSheet
    if (showAppIconSheet) {
        AppIconSheet(
            onDismiss = { showAppIconSheet = false }
        )
    }

    // 关于 BottomSheet
    if (showAboutSheet) {
        AboutSheet(
            onDismiss = { showAboutSheet = false }
        )
    }

    // 开源协议全屏页面
    if (showLicensesScreen) {
        LicensesScreen(
            onBack = { showLicensesScreen = false }
        )
        return
    }

    // 权限与安全页面
    if (showPermissionSecurityScreen) {
        PermissionSecurityScreen(
            onBack = {
                showPermissionSecurityScreen = false
                // 返回时刷新权限状态
                permissionStatus = PermissionStatusChecker.checkAllPermissions(context)
            }
        )
        return
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
                    onEditClick = { showEditProfileSheet = true },
                    onQrCodeClick = { showQrCodeSheet = true }
                )
            }

            // 设置选项
            item {
                SettingsSection(
                    onAppIconClick = { showAppIconSheet = true },
                    onPermissionSecurityClick = { showPermissionSecurityScreen = true },
                    onAboutClick = { showAboutSheet = true },
                    onLicensesClick = { showLicensesScreen = true },
                    permissionStatusSummary = getPermissionStatusSummary(permissionStatus)
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
    onEditClick: () -> Unit,
    onQrCodeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // UID 掩码显示
    val maskedUid = remember(user?.uid) {
        user?.uid?.let { uid ->
            if (uid.length > 8) {
                "${uid.take(4)}...${uid.takeLast(4)}"
            } else {
                uid
            }
        }
    }

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

                // UID - 掩码显示，点击复制
                user?.let { u ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        androidx.compose.ui.platform.ClipEntry(
                                            android.content.ClipData.newPlainText("UID", u.uid)
                                        )
                                    )
                                    android.widget.Toast.makeText(
                                        context,
                                        "UID 已复制",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "ID: ${maskedUid ?: u.uid}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制 UID",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 二维码图标
            IconButton(
                onClick = onQrCodeClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = "我的名片",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
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

/**
 * 设置区域 - 按功能分组显示
 * MONET 设计：组与组之间 16dp 间距
 */
@Composable
private fun SettingsSection(
    onAppIconClick: () -> Unit,
    onPermissionSecurityClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLicensesClick: () -> Unit,
    permissionStatusSummary: String
) {
    // 判断权限是否完整
    val hasPermissionWarning = permissionStatusSummary.contains("未") ||
                                permissionStatusSummary.contains("关闭") ||
                                permissionStatusSummary.contains("需要")

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)  // 组间距 16dp
    ) {
        // 第一组：应用设置
        SettingsGroup(title = "应用设置") {
            SettingsItem(
                icon = Icons.Default.AppShortcut,
                title = "应用图标",
                subtitle = "自定义应用图标样式",
                onClick = onAppIconClick
            )
        }

        // 第二组：权限与安全
        SettingsGroup(title = "权限与安全") {
            SettingsItem(
                icon = Icons.Default.Security,
                title = "权限与安全",
                subtitle = permissionStatusSummary,
                onClick = onPermissionSecurityClick,
                showWarning = hasPermissionWarning
            )
        }

        // 第三组：关于
        SettingsGroup(title = "关于") {
            Column {
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
                    onClick = onLicensesClick
                )
            }
        }
    }
}

/**
 * 设置分组卡片
 */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        // 分组标题
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // 分组内容卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FindMyShapes.Medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            content()
        }
    }
}

/**
 * 设置项组件
 * @param showWarning 是否显示警告指示器（橙色感叹号）
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showWarning: Boolean = false
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                // 权限健康检查指示器
                if (showWarning) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "需要注意",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (showWarning) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
