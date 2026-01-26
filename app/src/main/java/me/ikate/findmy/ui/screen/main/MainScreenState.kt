package me.ikate.findmy.ui.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tencent.tencentmap.mapsdk.maps.TencentMap
import me.ikate.findmy.data.model.Contact
import me.ikate.findmy.data.model.Device
import me.ikate.findmy.data.model.User
import me.ikate.findmy.service.GeofenceManager
import me.ikate.findmy.ui.navigation.FindMyTab
import me.ikate.findmy.ui.screen.contact.ContactViewModel
import me.ikate.findmy.ui.screen.main.components.MapLayerConfig
import me.ikate.findmy.util.MapSettingsManager

/**
 * MainScreen UI 状态容器
 * 封装所有 UI 状态，便于管理和测试
 */
class MainScreenState(
    // ViewModel 状态
    val tencentMap: TencentMap?,
    val isLocationCentered: Boolean,
    val devices: List<Device>,

    // 联系人状态
    val contacts: List<Contact>,
    val currentUser: User?,
    val meName: String?,
    val meAvatarUrl: String?,
    val myDevice: Device?,
    val myAddress: String?,
    val showAddDialog: Boolean,
    val isLoading: Boolean,
    val errorMessage: String?,
    val refreshingContacts: Set<String>,
    val ringingContactUid: String?,

    // 权限引导状态
    val showPermissionGuide: Boolean,
    val missingPermissions: List<String>,

    // 设备 ID
    val currentDeviceId: String,
    val currentUserId: String,

    // 底部面板
    val bottomSheetOffsetDp: Dp,
    val bottomNavHeightDp: Dp = 80.dp
)

/**
 * MainScreen 可变 UI 状态
 * 用于管理用户交互产生的状态变化
 */
class MainScreenMutableState {
    // Tab 状态
    var selectedTab by mutableStateOf(FindMyTab.PEOPLE)

    // 绑定通讯录
    var contactToBind by mutableStateOf<Contact?>(null)

    // 通讯录权限对话框
    var showContactsPermissionDialog by mutableStateOf(false)

    // 导航对话框
    var contactToNavigate by mutableStateOf<Contact?>(null)

    // 丢失模式对话框
    var contactForLostMode by mutableStateOf<Contact?>(null)

    // 地理围栏对话框
    var contactForGeofence by mutableStateOf<Contact?>(null)

    // 设备相关
    var ringingDeviceId by mutableStateOf<String?>(null)
    var deviceToNavigate by mutableStateOf<Device?>(null)
    var deviceForLostMode by mutableStateOf<Device?>(null)

    // 底部面板偏移
    var bottomSheetOffsetPx by mutableFloatStateOf(0f)
}

/**
 * 地图配置状态
 */
class MapConfigState(
    context: android.content.Context
) {
    var isTrafficEnabled by mutableStateOf(MapSettingsManager.loadTrafficEnabled(context))
    var mapLayerConfig by mutableStateOf(MapSettingsManager.loadMapLayerConfig(context))
}

/**
 * 创建并记住 MainScreen 可变状态
 */
@Composable
fun rememberMainScreenMutableState(): MainScreenMutableState {
    return remember { MainScreenMutableState() }
}

/**
 * 创建并记住地图配置状态
 */
@Composable
fun rememberMapConfigState(): MapConfigState {
    val context = LocalContext.current
    return remember { MapConfigState(context) }
}
