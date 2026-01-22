这是一个非常考验**UI/UX 设计功力**的需求。

要在 Android 上复刻 iOS Find My 的**“神韵”**（因为它的交互逻辑确实极其高效），同时又要遵循 Material Design 3 (Material You) 的规范，避免让应用看起来像一个“劣质的山寨 iOS 应用”，我们需要做**“形似而神不似”**的融合。

以下是针对 **联系人、设备、我** 三个标签页及地图设置的详细 UI/UX 设计方案：

---

### 核心设计语言：Material Find My

* **iOS 的神韵：** 底部抽屉 (Bottom Sheet) 交互、半透明模糊 (Blur)、极其克制的图标、大圆角卡片。
* **Android 的特色：** 动态取色 (Dynamic Color)、Material 3 导航栏、悬浮操作按钮 (FAB)、更符合拇指操作的布局。

---

### 一、 整体框架 (The Framework)

* **导航栏 (Navigation):**
* **iOS:** 底部 Tab Bar。
* **Android 复刻:** 使用 **Material 3 NavigationBar**。
* **特色:** 启用 `labelBehavior = ALWAYS_SHOW`，但选中项不仅加粗，还带有 **Pill Indicator (胶囊指示器)** 背景，颜色跟随系统壁纸（Monet 取色）。
* **Tab:** [联系人] [设备] [我]


* **地图层 (Map Layer):**
* **iOS:** 地图全屏铺满，Tab Bar 和 Bottom Sheet 浮在地图之上。
* **Android 复刻:** 同样全屏。使用 `CoordinatorLayout` + `BottomSheetBehavior`。
* **特色:** 状态栏 (Status Bar) 必须完全透明，让地图延伸到屏幕最顶端（沉浸式）。



---

### 二、 标签页 1：联系人 (People)

这是核心交互区。

#### 1. 列表模式 (BottomSheet 收起时)

* **布局:** 一个悬浮在底部的卡片列表（Peek Height 约 40% 屏幕高度）。
* **列表项:**
* **iOS:** 头像在左，名字加粗，下方是位置信息和时间，右侧无箭头。
* **Android 特色:**
* **头像:** 圆形，但增加 **Material 风格的描边**。如果在线，描边为绿色；离线为灰色。
* **点击波纹 (Ripple):** 点击列表项时，要有扩散的水波纹效果（Android 灵魂）。
* **左滑操作:** 增加 Android 习惯的“左滑收藏/置顶”，而不是 iOS 的左滑删除。




* **顶部把手 (Handle):** 必不可少的小横条，提示用户可以拖拽。

#### 2. 详情模式 (BottomSheet 展开/点击某人时)

* **交互:** 点击某人，BottomSheet 变高，地图中心移动到该人坐标。
* **操作按钮组 (Action Buttons):**
* **iOS:** [联系] [通知] [路线](https://www.google.com/search?q=%E5%8F%A6%E4%B8%80%E4%B8%AA%E6%8C%89%E9%92%AE%E3%80%82) (三个圆角矩形块)。
* **Android 复刻:** 使用 **Material 3 FilledTonalButton** (浅色填充按钮)。
* **特色:** 将 [路线](https://www.google.com/search?q=%E5%8F%A6%E4%B8%80%E4%B8%AA%E6%8C%89%E9%92%AE%E3%80%82) 按钮做成 **FAB (悬浮按钮)** 形态，悬浮在 BottomSheet 的右上角边缘（Anchor），这是最经典的 Android 地图交互。


* **功能卡片:**
* "通知"、"共享位置" 等选项使用 **OutlinedCard**，整齐排列。



---

### 三、 标签页 2：设备 (Devices)

这个页面展示你的 S24U、平板、耳机等。

* **展示内容:**
* 图标：使用 Android 风格的设备图标（Pixel 风格或 Samsung 风格的线框图）。
* 状态：电量百分比、是否在线。


* **操作逻辑 (丢失模式):**
* 点击设备 -> 弹出详情单。
* **核心功能区:**
* [播放声音]: 一个巨大的 **Large FAB** 或者 **Extended FAB**，图标是铃铛。


* **标记为丢失:**
* **iOS:** 一个专门的板块 "Mark As Lost"。
* **Android 特色:** 使用 **Switch (开关控件)** 或者 **Material Card**，点击后弹出一个全屏的 `DialogFragment` 来填写留言，而不是 iOS 的那种 Push 页面切换。





---

### 四、 标签页 3：我 (Me)

* **iOS 逻辑:** 控制全局开关（是否共享位置）、接收好友请求。
* **Android 特色复刻:**
* **开关:** 使用 Material 3 的 **Switch**（带图标的那种宽开关），手感更好。
* **当前位置:** 显示一个小地图卡片，标出“我”的位置。
* **好友请求:** 如果有请求，使用 **Banner** 或 **Card** 顶置显示，按钮是 [接受] (Filled) 和 [拒绝] (Outlined)。
* **关于:** 把“帮助”、“隐私策略”收纳进一个列表组。



---

### 五、 地图设置 (Map Settings)

iOS 的地图设置在右上角的 (i) 按钮里。Android 怎么做？

* **入口:** 地图右上角（避开挖孔）放置两个 **Small FAB** (小圆钮)。
1. **图层按钮:** 点击弹出 **Modal Bottom Sheet** (模态底部抽屉)。
2. **定位按钮:** 点击回到自己位置。


* **抽屉内容 (图层选择):**
* **iOS:** [探索] [驾车] [交通] [卫星] (分段选择器)。
* **Android 特色:** 使用 **Material 3 Choice Chips (选择芯片)**。
* 横向排列：`[标准] [卫星] [混合]`
* 下方开关：`[ ] 实时路况` `[ ] 3D建筑`




* **距离单位:** 自动跟随系统（米/公里），无需设置。

---

### 🎨 总结：UI 复刻 CheckList

为了实现“形似神不似”的效果，请严格遵守以下规则：

1. **字体:** 放弃 iOS 的 San Francisco，使用 **Roboto** 或 **Product Sans** (Google 字体)。
2. **圆角:** iOS 是平滑圆角 (Squircle)，Android Material 3 是 **标准圆角 (Rounded Corner)**。卡片圆角建议 `16dp` 或 `28dp`。
3. **颜色:** 放弃 iOS 的纯白/纯黑。背景色使用 `Surface` 色（略带一点点底色的灰白），文字使用 `OnSurface`。**利用 Monet 取色引擎**，让你的 App 主题色自动变成用户壁纸的颜色（比如壁纸是蓝色，按钮就是浅蓝色），这会让 S24U 用户觉得非常高级。
4. **图标:** 使用 **Google Material Symbols (Filled)** 图标库。

**一句话总结设计哲学：**
逻辑流向和功能布局 **1:1 照搬 iOS**（因为用户被教育得很好了），但组件样式、颜色、点击反馈 **100% 使用 Material Design 3**。这样既好用，又像 Android 原生应用。