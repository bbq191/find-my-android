package me.ikate.findmy.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/**
 * 指南针工具类
 * 用于获取设备当前的磁极方位角
 */
object CompassHelper {

    /**
     * 在 Compose 中监听设备方向变化
     * @return 当前方位角 (0-360度，0为北)，如果传感器不可用则返回 null
     */
    @Composable
    fun rememberCompassHeading(): Float? {
        val context = LocalContext.current
        var heading by remember { mutableFloatStateOf(0f) }
        var isSensorAvailable by remember { androidx.compose.runtime.mutableStateOf(false) }

        DisposableEffect(context) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            if (accelerometer == null || magnetometer == null) {
                isSensorAvailable = false
                return@DisposableEffect onDispose { }
            }
            isSensorAvailable = true

            val listener = object : SensorEventListener {
                private var gravity: FloatArray? = null
                private var geomagnetic: FloatArray? = null
                private val r = FloatArray(9)
                private val i = FloatArray(9)
                private var lastHeading = 0f

                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        gravity = event.values.clone()
                    }
                    if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        geomagnetic = event.values.clone()
                    }

                    if (gravity != null && geomagnetic != null) {
                        if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                            val orientation = FloatArray(3)
                            SensorManager.getOrientation(r, orientation)
                            // orientation[0] 是方位角 (弧度)，范围 -π 到 π
                            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()

                            if (!azimuth.isNaN()) {
                                // 转换为 0-360 度
                                azimuth = (azimuth + 360) % 360

                                // 使用指数移动平均 (EMA) 算法进行平滑处理，减少抖动
                                // alpha 越小越平滑，但响应越慢；alpha 越大响应越快，但抖动越大
                                val alpha = 0.05f

                                // 处理 359 -> 0 度跨越边界的问题
                                var diff = azimuth - lastHeading
                                if (diff > 180) diff -= 360
                                if (diff < -180) diff += 360

                                val newHeading = (lastHeading + diff * alpha + 360) % 360

                                // 仅当变化超过一定阈值时才更新状态，避免微小浮动触发重绘
                                if (abs(newHeading - heading) > 0.5f) {
                                    heading = newHeading
                                    lastHeading = newHeading
                                } else {
                                    // 仅更新内部计算值，不触发 Compose 状态更新
                                    lastHeading = newHeading
                                }
                            }
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    // 不需要处理
                }
            }

            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }

        return if (isSensorAvailable) heading else null
    }
}
