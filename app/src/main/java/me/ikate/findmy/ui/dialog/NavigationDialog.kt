package me.ikate.findmy.ui.dialog

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng

/**
 * 导航模式
 */
enum class NavigationMode(
    val label: String,
    val icon: ImageVector,
    val googleMapsMode: String,
    val amapMode: String
) {
    DRIVING("驾车", Icons.Default.DirectionsCar, "d", "car"),
    WALKING("步行", Icons.AutoMirrored.Filled.DirectionsWalk, "w", "walk"),
    BICYCLING("骑行", Icons.AutoMirrored.Filled.DirectionsBike, "b", "ride"),
    TRANSIT("公交", Icons.Default.DirectionsBus, "r", "bus")
}

/**
 * 导航对话框
 * 提供导航模式选择和地图应用选择
 */
@Composable
fun NavigationDialog(
    contactName: String,
    destination: LatLng,
    currentLocation: LatLng? = null,
    distanceText: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(NavigationMode.DRIVING) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "导航至 $contactName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (distanceText != null) {
                        Text(
                            text = "距离约 $distanceText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "选择出行方式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 导航模式选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NavigationMode.entries.forEach { mode ->
                        NavigationModeButton(
                            mode = mode,
                            isSelected = selectedMode == mode,
                            onClick = { selectedMode = mode }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "选择地图应用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 地图应用选择
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Google Maps
                    if (isAppInstalled(context, "com.google.android.apps.maps")) {
                        MapAppButton(
                            name = "Google Maps",
                            icon = Icons.Default.Map,
                            onClick = {
                                launchGoogleMaps(context, destination, selectedMode)
                                onDismiss()
                            }
                        )
                    }

                    // 高德地图
                    if (isAppInstalled(context, "com.autonavi.minimap")) {
                        MapAppButton(
                            name = "高德地图",
                            icon = Icons.Default.Map,
                            onClick = {
                                launchAmap(context, destination, contactName, selectedMode)
                                onDismiss()
                            }
                        )
                    }

                    // 百度地图
                    if (isAppInstalled(context, "com.baidu.BaiduMap")) {
                        MapAppButton(
                            name = "百度地图",
                            icon = Icons.Default.Map,
                            onClick = {
                                launchBaiduMap(context, destination, contactName, selectedMode)
                                onDismiss()
                            }
                        )
                    }

                    // 系统默认（始终显示）
                    MapAppButton(
                        name = "系统默认地图",
                        icon = Icons.Default.Navigation,
                        onClick = {
                            launchSystemMap(context, destination, contactName)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun NavigationModeButton(
    mode: NavigationMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        tonalElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = mode.icon,
                contentDescription = mode.label,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun MapAppButton(
    name: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 检查应用是否已安装
 */
private fun isAppInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

/**
 * 启动 Google Maps 导航
 */
private fun launchGoogleMaps(context: Context, destination: LatLng, mode: NavigationMode) {
    val uri = Uri.parse(
        "google.navigation:q=${destination.latitude},${destination.longitude}&mode=${mode.googleMapsMode}"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }
    context.startActivity(intent)
}

/**
 * 启动高德地图导航
 */
private fun launchAmap(context: Context, destination: LatLng, name: String, mode: NavigationMode) {
    val uri = Uri.parse(
        "amapuri://route/plan/?" +
                "dlat=${destination.latitude}&dlon=${destination.longitude}" +
                "&dname=${Uri.encode(name)}" +
                "&dev=0&t=${getAmapMode(mode)}"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.autonavi.minimap")
        addCategory(Intent.CATEGORY_DEFAULT)
    }
    context.startActivity(intent)
}

/**
 * 启动百度地图导航
 */
private fun launchBaiduMap(context: Context, destination: LatLng, name: String, mode: NavigationMode) {
    val uri = Uri.parse(
        "baidumap://map/direction?" +
                "destination=latlng:${destination.latitude},${destination.longitude}|name:${Uri.encode(name)}" +
                "&coord_type=wgs84" +
                "&mode=${getBaiduMode(mode)}" +
                "&src=findmy"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.baidu.BaiduMap")
    }
    context.startActivity(intent)
}

/**
 * 启动系统默认地图
 */
private fun launchSystemMap(context: Context, destination: LatLng, name: String) {
    val uri = Uri.parse(
        "geo:${destination.latitude},${destination.longitude}?q=${destination.latitude},${destination.longitude}(${Uri.encode(name)})"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

/**
 * 获取高德地图导航模式
 */
private fun getAmapMode(mode: NavigationMode): Int {
    return when (mode) {
        NavigationMode.DRIVING -> 0
        NavigationMode.TRANSIT -> 1
        NavigationMode.WALKING -> 2
        NavigationMode.BICYCLING -> 3
    }
}

/**
 * 获取百度地图导航模式
 */
private fun getBaiduMode(mode: NavigationMode): String {
    return when (mode) {
        NavigationMode.DRIVING -> "driving"
        NavigationMode.TRANSIT -> "transit"
        NavigationMode.WALKING -> "walking"
        NavigationMode.BICYCLING -> "riding"
    }
}
