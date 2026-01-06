# 地图交互逻辑文档

本文档旨在描述 Android 版 "查找" (Find My) 应用的地图交互逻辑。目标是在 Android 平台上，利用 Google Maps SDK 复刻 iOS 原生 "查找" 应用的流畅体验，同时符合 Android (API 36) 的设计规范。

## 1. 地图初始化与配置

### 1.1 地图 SDK 选择
*   **Google Maps SDK for Android**: 采用最新的 Compose 版 (`maps-compose`)。
*   **坐标系处理**:
    *   应用内部逻辑主要使用 WGS-84 (GPS) 坐标。
    *   针对中国大陆用户，通过 `CoordinateConverter` 工具类处理 GCJ-02 (火星坐标) 偏移。
    *   如果设备定位返回 WGS-84，而地图显示有偏移，则需应用 `CoordinateConverter.wgs84ToGcj02` 转换。

### 1.2 初始视图
*   默认位置：北京天安门 (39.9042, 116.4074)。
*   缩放级别：Zoom 12。
*   定位蓝点：启用 (`isMyLocationEnabled = true`)，需要运行时请求权限。

## 2. 交互逻辑

### 2.1 底部面板与地图联动
*   **底部面板 (BottomSheet)**：包含设备列表/详情。
*   **地图 Padding**：当地图底部的面板向上滑动时，地图的“有效显示区域”随之改变。
    *   实现：监听 BottomSheet 的偏移量，动态调用 `googleMap.setPadding(0, 0, 0, offset)`。
    *   效果：保持 Google Logo 和定位蓝点不被面板遮挡，且 `animateCamera` 居中时会自动考虑 Padding，使目标点位于可见区域中心。

### 2.2 设备选中
*   **触发**：点击列表项或地图上的 Marker。
*   **动作**：
    1.  高亮选中状态。
    2.  地图平滑移动 (`animateCamera`) 到设备坐标。
    3.  Zoom 级别调整为 15。
    4.  底部面板展示设备详情。

### 2.3 取消选中
*   **触发**：点击地图空白区域或关闭详情面板。
*   **动作**：
    1.  清除选中状态。
    2.  底部面板恢复为列表模式。

### 2.4 地图图层切换
*   右上角悬浮按钮，支持切换：
    *   标准地图 (Normal)
    *   卫星地图 (Satellite)
    *   混合地图 (Hybrid)

## 3. 性能优化
*   使用 `GoogleMap` Composable，其内部已优化 View 复用。
*   Marker 渲染采用 `MarkerState`，只更新坐标而非频繁重创建。