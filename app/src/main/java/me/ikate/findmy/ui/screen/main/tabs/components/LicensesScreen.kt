package me.ikate.findmy.ui.screen.main.tabs.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.ikate.findmy.util.rememberHaptics

/**
 * 开源库许可证数据类
 */
private data class Library(
    val name: String,
    val author: String,
    val licenseType: String,
    val url: String? = null
)

/**
 * 开源协议全屏页面
 *
 * 设计特点：
 * - 全屏页面，带大标题 TopAppBar
 * - LazyColumn 展示开源库列表
 * - 双行布局：库名称（加粗）+ 作者
 * - 协议徽章：右侧 Chip 显示协议类型
 * - 点击跳转 GitHub 页面
 * - 头部感谢语
 *
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // 开源库列表
    val libraries = remember {
        listOf(
            Library(
                name = "Jetpack Compose",
                author = "Google",
                licenseType = "Apache 2.0",
                url = "https://github.com/androidx/androidx"
            ),
            Library(
                name = "Material Design 3",
                author = "Google",
                licenseType = "Apache 2.0",
                url = "https://github.com/material-components/material-components-android"
            ),
            Library(
                name = "腾讯地图 SDK",
                author = "腾讯",
                licenseType = "Commercial",
                url = "https://lbs.qq.com/"
            ),
            Library(
                name = "腾讯定位 SDK",
                author = "腾讯",
                licenseType = "Commercial",
                url = "https://lbs.qq.com/"
            ),
            Library(
                name = "Eclipse Paho MQTT",
                author = "Eclipse Foundation",
                licenseType = "EPL 2.0",
                url = "https://github.com/eclipse/paho.mqtt.android"
            ),
            Library(
                name = "Room Database",
                author = "Google",
                licenseType = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/room"
            ),
            Library(
                name = "Firebase Cloud Messaging",
                author = "Google",
                licenseType = "Firebase ToS",
                url = "https://firebase.google.com/docs/cloud-messaging"
            ),
            Library(
                name = "Firebase Firestore",
                author = "Google",
                licenseType = "Firebase ToS",
                url = "https://firebase.google.com/docs/firestore"
            ),
            Library(
                name = "Coil",
                author = "Coil Contributors",
                licenseType = "Apache 2.0",
                url = "https://github.com/coil-kt/coil"
            ),
            Library(
                name = "Accompanist",
                author = "Google",
                licenseType = "Apache 2.0",
                url = "https://github.com/google/accompanist"
            ),
            Library(
                name = "Koin",
                author = "InsertKoin",
                licenseType = "Apache 2.0",
                url = "https://github.com/InsertKoinIO/koin"
            ),
            Library(
                name = "Kotlin Coroutines",
                author = "JetBrains",
                licenseType = "Apache 2.0",
                url = "https://github.com/Kotlin/kotlinx.coroutines"
            ),
            Library(
                name = "AndroidX Libraries",
                author = "Google",
                licenseType = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx"
            ),
            Library(
                name = "WorkManager",
                author = "Google",
                licenseType = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/work"
            ),
            Library(
                name = "Biometric",
                author = "Google",
                licenseType = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/biometric"
            ),
            Library(
                name = "Security Crypto",
                author = "Google",
                licenseType = "Apache 2.0",
                url = "https://developer.android.com/jetpack/androidx/releases/security"
            ),
            Library(
                name = "Play Services Location",
                author = "Google",
                licenseType = "Play ToS",
                url = "https://developers.google.com/android/guides/setup"
            ),
            Library(
                name = "Gson",
                author = "Google",
                licenseType = "Apache 2.0",
                url = "https://github.com/google/gson"
            ),
            Library(
                name = "ZXing",
                author = "ZXing Authors",
                licenseType = "Apache 2.0",
                url = "https://github.com/zxing/zxing"
            )
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "开源协议",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.click()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 感谢语
            item {
                ThankYouHeader()
            }

            // 开源库列表
            items(libraries) { library ->
                LicenseItem(
                    library = library,
                    onClick = {
                        haptics.click()
                        library.url?.let { openUrl(context, it) }
                    }
                )
            }

            // Apache 2.0 说明
            item {
                ApacheLicenseNote()
            }
        }
    }
}

/**
 * 感谢语头部
 */
@Composable
private fun ThankYouHeader() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "感谢以下开源项目对 Find My 的贡献",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * 开源库列表项
 */
@Composable
private fun LicenseItem(
    library: Library,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = library.name,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = library.author,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 协议徽章
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getLicenseColor(library.licenseType)
                ) {
                    Text(
                        text = library.licenseType,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                // 跳转图标
                if (library.url != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "打开链接",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = Modifier.clickable(
            enabled = library.url != null,
            onClick = onClick
        ),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )

    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

/**
 * 根据协议类型获取徽章颜色
 */
@Composable
private fun getLicenseColor(licenseType: String) = when {
    licenseType.contains("Apache") -> MaterialTheme.colorScheme.secondaryContainer
    licenseType.contains("MIT") -> MaterialTheme.colorScheme.tertiaryContainer
    licenseType.contains("EPL") -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceContainerHigh
}

/**
 * Apache 2.0 许可证说明
 */
@Composable
private fun ApacheLicenseNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Apache License 2.0",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Licensed under the Apache License, Version 2.0.\n" +
                    "You may obtain a copy of the License at:\n" +
                    "http://www.apache.org/licenses/LICENSE-2.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "本应用遵循相关开源协议，在此对所有开源贡献者表示感谢。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 打开 URL
 */
private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
        // 忽略异常
    }
}
