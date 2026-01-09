package me.ikate.findmy.ui.screen.main.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMap

/**
 * 地图图层切换按钮
 * 显示在右上角，用于切换地图图层（标准、卫星、混合）
 *
 * @param map 地图实例
 * @param modifier 修饰符
 */
@Composable
fun MapLayerButton(
    map: GoogleMap?,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    FloatingActionButton(
        onClick = { showMenu = true },
        modifier = modifier.size(48.dp),
        containerColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = "切换地图图层",
            tint = Color.Gray
        )
    }

    // 下拉菜单
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text("标准地图") },
            onClick = {
                map?.mapType = GoogleMap.MAP_TYPE_NORMAL
            }
        )
        DropdownMenuItem(
            text = { Text("卫星地图") },
            onClick = {
                map?.mapType = GoogleMap.MAP_TYPE_SATELLITE
            }
        )
        DropdownMenuItem(
            text = { Text("混合地图") },
            onClick = {
                map?.mapType = GoogleMap.MAP_TYPE_HYBRID
            }
        )
    }
}