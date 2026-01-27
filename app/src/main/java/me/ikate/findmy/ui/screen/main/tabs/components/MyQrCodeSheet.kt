package me.ikate.findmy.ui.screen.main.tabs.components

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import me.ikate.findmy.data.model.UserCard
import me.ikate.findmy.ui.theme.FindMyShapes
import me.ikate.findmy.util.QrCodeUtils
import me.ikate.findmy.util.rememberHaptics
import java.io.OutputStream

/**
 * 我的名片二维码 BottomSheet
 *
 * 特性：
 * - DisposableEffect 自动调亮屏幕（方便扫描）
 * - 白底 Card 包含头像、昵称、UID (掩码)、二维码
 * - 底部按钮：保存图片、分享链接
 *
 * @param uid 用户 UID
 * @param nickname 昵称
 * @param avatarUrl 头像 URL
 * @param status 状态签名
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQrCodeSheet(
    uid: String,
    nickname: String,
    avatarUrl: String?,
    status: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 生成名片数据
    val userCard = remember(uid, nickname, avatarUrl, status) {
        UserCard(
            uid = uid,
            nickname = nickname,
            avatarUrl = avatarUrl,
            status = status
        )
    }

    // 生成二维码内容
    val qrContent = remember(userCard) { userCard.toJson() }

    // 动态生成二维码 Bitmap
    val qrBitmap = remember(qrContent) {
        QrCodeUtils.generateBitmap(qrContent, size = 800)
    }

    // S24U 专属优化：展示二维码时自动调亮屏幕
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f

        if (window != null) {
            val params = window.attributes
            params.screenBrightness = 1.0f // 最亮
            window.attributes = params
        }

        onDispose {
            if (window != null) {
                val params = window.attributes
                params.screenBrightness = originalBrightness // 恢复亮度
                window.attributes = params
            }
        }
    }

    // UID 掩码显示
    val maskedUid = remember(uid) {
        if (uid.length > 8) {
            "${uid.take(4)}****${uid.takeLast(4)}"
        } else {
            uid
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = FindMyShapes.BottomSheetTop,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "我的名片",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 名片卡片容器
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    // 头像
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = Color(0xFFE8E8E8)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "头像",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (nickname.isNotBlank()) {
                                Text(
                                    text = nickname.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color(0xFF666666),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = Color(0xFF666666)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 名字
                    Text(
                        text = nickname.ifBlank { "未设置" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    // UID (掩码)
                    Text(
                        text = "UID: $maskedUid",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    // 状态签名
                    if (!status.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 二维码图片
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "名片二维码",
                            modifier = Modifier.size(200.dp)
                        )
                    } else {
                        // 二维码生成失败的占位
                        Surface(
                            modifier = Modifier.size(200.dp),
                            color = Color(0xFFF5F5F5),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "二维码生成失败",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 底部操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 保存图片按钮
                OutlinedButton(
                    onClick = {
                        haptics.click()
                        qrBitmap?.let { bitmap ->
                            saveQrCodeToGallery(context, bitmap, nickname)
                        } ?: Toast.makeText(context, "二维码未生成", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "保存图片",
                        fontWeight = FontWeight.Medium
                    )
                }

                // 分享按钮
                Button(
                    onClick = {
                        haptics.click()
                        shareUserCard(context, uid, nickname)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "分享链接",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 保存二维码到相册
 */
private fun saveQrCodeToGallery(context: android.content.Context, bitmap: Bitmap, nickname: String) {
    try {
        val filename = "FindMy_${nickname}_${System.currentTimeMillis()}.png"

        val outputStream: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FindMy")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            uri?.let { context.contentResolver.openOutputStream(it) }
        } else {
            @Suppress("DEPRECATION")
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val findMyDir = java.io.File(imagesDir, "FindMy")
            if (!findMyDir.exists()) findMyDir.mkdirs()
            val imageFile = java.io.File(findMyDir, filename)
            java.io.FileOutputStream(imageFile)
        }

        outputStream?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 分享用户 UID
 */
private fun shareUserCard(context: android.content.Context, uid: String, nickname: String) {
    val shareText = buildString {
        appendLine("我是 $nickname，快来添加我为联系人吧！")
        appendLine()
        appendLine("我的 UID: $uid")
        appendLine()
        appendLine("打开「查找」App，添加联系人时粘贴此 UID 即可。")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "添加我为联系人")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }

    context.startActivity(Intent.createChooser(intent, "分享给好友"))
}
