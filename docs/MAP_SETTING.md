这是一个非常典型的“开发者调试视角”界面。目前的地图设置页（截图 8）虽然功能全（甚至把光照效果的黄昏/黎明都列出来了），但**信息密度过大，视觉干扰强，缺乏层级感**。

作为一款面向普通用户的“查找”应用，地图设置页应该是**直观、轻量且高效**的。我们需要把这个“控制台风格”的界面改造成符合 **Material You** 规范的现代设置面板。

以下是针对截图 8 的具体优化建议：

### 🎨 一、 核心重构：由“文字”转为“图像”

目前的“标准”、“卫星”只是文字按钮，用户不够直观。现代地图应用（Google Maps, Apple Maps）都采用**缩略图卡片**。

* **优化方案：**
* **组件：** 使用一行两个（或三个）**Selectable Image Cards**。
* **内容：**
* 左卡片：一张矢量地图的截图，下注“标准”。
* 右卡片：一张真实卫星地图的截图，下注“卫星”。


* **交互：** 选中时，卡片外圈显示 **Primary Color（动态取色）** 的粗描边，并打上一个勾选标记（Check Icon）。
* **个性化样式整合：** 将“白浅”、“墨渊”整合进这层逻辑，或者作为“标准”模式下的子选项。但通常来说，**跟随系统深色模式**自动切换“白浅/墨渊”是最好的体验，不需要让用户手动选。



### 🧩 二、 功能区重构：由“九宫格”转为“Chip 胶囊”

目前的“地图功能”区域（路况、3D建筑、地标...）使用了巨大的彩色方块图标，非常占地且像“老人机”界面。

* **优化方案：**
* **筛选机制：** 这里的功能其实是**“图层叠加 (Overlays)”**。
* **组件：** 使用 **Material 3 Filter Chips** (过滤胶囊)。
* **布局：** 采用 **FlowLayout** (自动换行) 或者 **横向滚动条**。
* **样式：**
* **未选中：** 灰色边框，透明背景，灰色图标。
* **选中：** 浅色背景 (SecondaryContainer)，深色文字，前面加一个 ✔️。


* **精简选项：** 现在的选项太多了（地点、道路、兴趣点通常是底图自带的，用户不需要开关）。建议只保留核心高频功能：
* `[🚦 实时路况]`
* `[🏢 3D 建筑]`
* `[🛰️ 卫星图]` (如果不想做上面的大卡片，也可以放这里)





### ☀️ 三、 光照效果：极度简化

目前的“光照效果”区域占据了屏幕三分之一，且包含“黄昏”、“黎明”这种极低频的调试选项。

* **优化方案：**
* **逻辑：** 普通用户只需要“浅色”、“深色”和“跟随系统”。
* **组件：** 使用 **Material 3 Segmented Button** (分段按钮)。
* **布局：** 一个长条，分三段：
* `[ 🌓 自动 ]` `[ ☀️ 浅色 ]` `[ 🌙 深色 ]`


* **位置：** 将其移到最底部，或者直接砍掉——**最佳实践是默认跟随系统**，在“我-通用设置”里留个开关即可，不要占用寸土寸金的地图设置页。



### 🗑️ 四、 降噪与清理

* **移除调试信息：** “需在腾讯地图控制台配置...”这行灰色小字是写给你自己看的，正式版请务必隐藏，或者移到 Logcat 里。
* **统一圆角：** 截图里的蓝色大按钮圆角和其他按钮不一致。请统一使用 **24dp** 或 **Full Stadium (半圆)** 圆角。

---

### 📏 推荐布局代码结构 (Compose 伪代码)

如果让我重写这个 BottomSheet，结构会是这样：

```kotlin
Column(modifier = Modifier.padding(16.dp)) {
    // 1. 标题与把手
    DragHandle()
    Text("地图模式", style = MaterialTheme.typography.titleMedium)
    
    // 2. 地图类型选择 (大图卡片)
    Row(horizontalArrangement = Arrangement.SpaceEvenly) {
        MapTypeCard(image = R.drawable.map_standard, text = "标准", selected = true)
        MapTypeCard(image = R.drawable.map_satellite, text = "卫星", selected = false)
    }

    Spacer(height = 24.dp)

    // 3. 地图细节 (Filter Chips)
    Text("地图细节", style = MaterialTheme.typography.titleMedium)
    FlowRow {
        FilterChip(selected = trafficEnabled, label = "实时路况", icon = Icons.Traffic)
        FilterChip(selected = building3dEnabled, label = "3D 建筑", icon = Icons.Apartment)
        FilterChip(selected = showLabels, label = "地名标签", icon = Icons.Label)
    }
    
    // 4. (可选) 主题设置 - 建议跟随系统，此处可省略
}

```

### ✨ 最终效果预期

优化后的界面应该只有现在的 **1/2 高度**，用户单手拇指即可轻松操作所有选项。

* **视觉重点** 会集中在两张精美的地图预览图上。
* **操作效率** 会因为 Chip 的紧凑排列而大幅提升。
* **专业感** 会因为去掉了“调试选项”和“多余图标”而显著增强。

你的 App 已经非常有模有样了，把这个设置页做“轻”，整体质感会立刻提升一个档次！