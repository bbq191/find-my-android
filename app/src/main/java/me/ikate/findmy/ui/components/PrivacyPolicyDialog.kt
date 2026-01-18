package me.ikate.findmy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 隐私政策弹窗
 * 用于满足高德等 SDK 的隐私合规要求
 * 首次启动时向用户展示隐私政策说明
 */
@Composable
fun PrivacyPolicyDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        title = {
            Text(
                text = "用户协议与隐私政策",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "欢迎使用 Find My！",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "本应用是一款开源的位置共享应用，采用 MIT 协议开源。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "为了向您提供位置共享服务，我们需要收集和使用以下信息：",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                PrivacySection(
                    title = "位置信息",
                    content = "我们使用高德定位 SDK 获取您的位置信息，用于在地图上显示您的位置并与好友共享。位置数据通过加密通道传输。"
                )

                PrivacySection(
                    title = "设备信息",
                    content = "我们收集设备标识符（Android ID）、设备型号等信息，用于区分不同设备和提供个性化服务。"
                )

                PrivacySection(
                    title = "推送服务",
                    content = "我们使用个推推送服务向您发送位置更新通知和好友请求，即使应用在后台也能及时收到消息。"
                )

                PrivacySection(
                    title = "第三方 SDK",
                    content = "本应用集成了以下第三方 SDK：\n" +
                            "• 高德定位 SDK - 提供精准定位服务\n" +
                            "• Mapbox SDK - 提供地图显示服务\n" +
                            "• 个推推送 SDK - 提供消息推送服务"
                )

                PrivacySection(
                    title = "数据存储",
                    content = "您的位置数据存储在本地设备，并通过 MQTT 协议与您授权的好友实时同步。我们不会将您的数据用于其他商业用途。"
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "点击「同意并继续」即表示您已阅读并同意以上条款。如不同意，将无法使用本应用的核心功能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text("同意并继续")
            }
        },
        dismissButton = {
            TextButton(onClick = onDisagree) {
                Text("不同意")
            }
        }
    )
}

@Composable
private fun PrivacySection(
    title: String,
    content: String
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "• $title",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
        )
    }
}
