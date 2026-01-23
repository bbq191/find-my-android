package me.ikate.findmy.ui.dialog

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.tencentmap.mapsdk.maps.model.LatLng
import me.ikate.findmy.ui.theme.FindMyShapes

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
 * 地图应用信息
 */
private data class MapAppInfo(
    val name: String,
    val packageName: String?,
    val icon: ImageVector,
    val isSystemDefault: Boolean = false
)

/**
 * 导航发起页 (Navigation Launch Sheet)
 *
 * 设计遵循 NAVIGATION_UI.md 规范：
 * - Modal Bottom Sheet 替代居中弹窗，单手操作更便捷
 * - 紧凑型出行方式选择，显示预估时间
 * - 水平滚动的地图应用选择
 * - 明确的"开始导航"主按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDialog(
    contactName: String,
    destination: LatLng,
    currentLocation: LatLng? = null,
    distanceText: String? = null,
    addressText: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMode by remember { mutableStateOf(NavigationMode.DRIVING) }

    // 检测已安装的地图应用
    val installedMapApps = remember {
        buildList {
            if (isAppInstalled(context, "com.autonavi.minimap")) {
                add(MapAppInfo("高德", "com.autonavi.minimap", Icons.Default.Map))
            }
            if (isAppInstalled(context, "com.baidu.BaiduMap")) {
                add(MapAppInfo("百度", "com.baidu.BaiduMap", Icons.Default.Map))
            }
            if (isAppInstalled(context, "com.google.android.apps.maps")) {
                add(MapAppInfo("Google", "com.google.android.apps.maps", Icons.Default.Map))
            }
            // 系统默认始终可用
            add(MapAppInfo("系统", null, Icons.Default.Navigation, isSystemDefault = true))
        }
    }

    var selectedMapApp by remember { mutableStateOf(installedMapApps.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = FindMyShapes.BottomSheetTop,
        dragHandle = {
            // 标准拖拽手柄
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
                .padding(bottom = 32.dp)
        ) {
            // 头部信息
            NavigationHeader(
                contactName = contactName,
                distanceText = distanceText,
                addressText = addressText
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 出行方式选择（紧凑型分段按钮）
            TransportModeSelector(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
                distanceKm = distanceText?.replace("公里", "")?.replace("米", "")?.toDoubleOrNull()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 地图应用选择（水平滚动）
            if (installedMapApps.size > 1) {
                MapAppSelector(
                    apps = installedMapApps,
                    selectedApp = selectedMapApp,
                    onAppSelected = { selectedMapApp = it }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // 开始导航按钮
            StartNavigationButton(
                mapAppName = selectedMapApp.name,
                onClick = {
                    launchNavigation(
                        context = context,
                        destination = destination,
                        name = contactName,
                        mode = selectedMode,
                        mapApp = selectedMapApp
                    )
                    onDismiss()
                }
            )
        }
    }
}

/**
 * 导航头部信息
 */
@Composable
private fun NavigationHeader(
    contactName: String,
    distanceText: String?,
    addressText: String?
) {
    Column {
        Text(
            text = "导航至 $contactName",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 距离信息
            if (distanceText != null) {
                Icon(
                    imageVector = Icons.Default.NorthEast,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 地址信息
            if (addressText != null) {
                if (distanceText != null) {
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = addressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

/**
 * 出行方式选择器 - 紧凑型分段按钮
 */
@Composable
private fun TransportModeSelector(
    selectedMode: NavigationMode,
    onModeSelected: (NavigationMode) -> Unit,
    distanceKm: Double?
) {
    Column {
        Text(
            text = "出行方式",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavigationMode.entries.forEach { mode ->
                TransportModeChip(
                    mode = mode,
                    isSelected = selectedMode == mode,
                    estimatedTime = estimateTime(distanceKm, mode),
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 出行方式选项卡
 */
@Composable
private fun TransportModeChip(
    mode: NavigationMode,
    isSelected: Boolean,
    estimatedTime: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = mode.icon,
                contentDescription = mode.label,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = estimatedTime ?: mode.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

/**
 * 地图应用选择器 - 水平滚动
 */
@Composable
private fun MapAppSelector(
    apps: List<MapAppInfo>,
    selectedApp: MapAppInfo,
    onAppSelected: (MapAppInfo) -> Unit
) {
    Column {
        Text(
            text = "使用以下地图打开",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            apps.forEach { app ->
                MapAppChip(
                    app = app,
                    isSelected = selectedApp == app,
                    onClick = { onAppSelected(app) }
                )
            }
        }
    }
}

/**
 * 地图应用选项卡
 */
@Composable
private fun MapAppChip(
    app: MapAppInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选中指示点
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Icon(
                imageVector = app.icon,
                contentDescription = app.name,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = app.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * 开始导航按钮
 */
@Composable
private fun StartNavigationButton(
    mapAppName: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "打开${mapAppName}地图",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 估算出行时间
 */
private fun estimateTime(distanceKm: Double?, mode: NavigationMode): String? {
    if (distanceKm == null || distanceKm <= 0) return null

    // 估算速度 (km/h)
    val speedKmh = when (mode) {
        NavigationMode.DRIVING -> 40.0  // 城市平均车速
        NavigationMode.WALKING -> 5.0   // 步行速度
        NavigationMode.BICYCLING -> 15.0 // 骑行速度
        NavigationMode.TRANSIT -> 25.0   // 公交平均速度
    }

    val timeMinutes = (distanceKm / speedKmh * 60).toInt()

    return when {
        timeMinutes < 60 -> "${timeMinutes}分钟"
        else -> {
            val hours = timeMinutes / 60
            val mins = timeMinutes % 60
            if (mins > 0) "${hours}小时${mins}分" else "${hours}小时"
        }
    }
}

/**
 * 启动导航
 */
private fun launchNavigation(
    context: Context,
    destination: LatLng,
    name: String,
    mode: NavigationMode,
    mapApp: MapAppInfo
) {
    when {
        mapApp.packageName == "com.google.android.apps.maps" -> {
            launchGoogleMaps(context, destination, mode)
        }
        mapApp.packageName == "com.autonavi.minimap" -> {
            launchAmap(context, destination, name, mode)
        }
        mapApp.packageName == "com.baidu.BaiduMap" -> {
            launchBaiduMap(context, destination, name, mode)
        }
        else -> {
            launchSystemMap(context, destination, name)
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
                "&coord_type=gcj02" +
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
