这张“编辑地理围栏”界面（`Screenshot_20260123_105055_Find My.png`）功能已经很完整了，且应用了 Material You 的配色。

为了让它在 **S24 Ultra** 这样的大屏设备上更好用，并且视觉上更接近 iOS Find My 的那种“轻盈感”，我建议从 **交互逻辑** 和 **控件样式** 两个维度进行优化：

---

### 🗺️ 1. 地图交互层 (Map Interaction)

目前的逻辑似乎是“点击地图设置中心”，这在移动端其实不够精准。

#### **A. 交互模式：改为“定针不动，地图动”**

* **现状：** 地图上插了一个 Marker，用户点击别处 Marker 移动。
* **优化建议：** 采用主流打车/外卖软件的逻辑。
* **中心锚点：** 在屏幕（或 MapView 可视区域）的正中心固定一个 **Pin（大头针）** 图标。
* **操作：** 用户拖动地图，Pin 永远悬浮在中心。
* **反馈：** 拖动结束后，自动获取 Pin 下方的地址并更新到底部面板。
* **优势：** 比手指点击更精准，且单手操作更舒服。



#### **B. 围栏视觉：更轻盈的圆**

* **现状：** 蓝色圆圈颜色较深，遮挡了地图细节。
* **优化建议：**
* **填充 (Fill):** 透明度调至 **10% - 15%** (`PrimaryContainer` 色)。
* **描边 (Stroke):** 保持 **2dp** 实线 (`Primary` 色)。
* **动态缩放：** 当用户拖动 Slider 调整半径时，圆圈应实时平滑缩放（不要有跳变）。



---

### 🎛️ 2. 底部控制面板 (Control Panel)

目前的面板显得有点“拥挤”，且控件样式有点像旧版 Android。

#### **A. 半径滑块：M3 标准 Slider**

* **现状：** 一个带有许多圆点的自定义 Slider，视觉杂讯太多，看起来像虚线。
* **优化建议：** 使用标准的 **Material 3 Slider**。
* **样式：** 一条干净的实线轨道。
* **刻度 (Tick Marks):** 仅在 **100m, 500m, 1km** 这种关键节点显示吸附点，不要密密麻麻全是点。
* **数值显示：** 拖动时，在手指上方显示气泡 (Tooltip) 实时显示米数。



#### **B. 触发条件：Segmented Button (分段按钮)**

* **现状：** 两个独立的 Checkbox 样式的按钮（到达时通知 / 离开时通知）。
* **优化建议：** 使用 **Multi-select Segmented Button**。
* **样式：** 两个按钮紧贴在一起，两端圆角。
* **布局：**
  `[ (icon) 到达 ]` `[ (icon) 离开 ]`
* **状态：** 选中项背景变深，图标变实心。这种控件比两个分开的按钮更节省空间，逻辑关联性更强。



#### **C. 地址展示：去除巨大的输入框感**

* **现状：** “西山区鱼翅路”被放在一个巨大的圆角矩形框里，看起来像个可以打字的输入框。
* **优化建议：** **纯文本展示**。
* **样式：**
* 小标题：`位置` (Caption 字体, 灰色)
* 内容：`西山区鱼翅路` (Body Large 字体, 加粗, 黑色/深色)。


* **位置：** 放在面板的最顶端，不需要边框背景，显得更通透。



---

### 🖱️ 3. 操作按钮 (Action Buttons)

**这是 S24 Ultra 大屏体验的关键。**

#### **A. 移除顶部按钮**

* **现状：** “移除”和“更新”在屏幕最右上角。
* **问题：** 在 S24U 上，手指很难够到右上角。
* **优化建议：** **全部移到底部**。

#### **B. 底部按钮布局**

在控制面板的最下方，放置两个按钮：

1. **主按钮 (Primary Action):** **[ 保存配置 ]**
* **样式：** `FilledButton` (宽大的胶囊形)，主色填充。
* **位置：** 占据宽度的大部分。


2. **破坏性按钮 (Destructive Action):** **[ 删除围栏 ]**
* **样式：** `TextButton` (红色文字) 或者 `TonalButton` (浅红色背景)。
* **位置：** 放在主按钮下方，或者作为一个单独的红色文字链接。



---

### 🎨 4. 视觉微调 (Visual Polish)

* **Marker 图标：** 截图里还是那个卡通猪 🐷。建议换成 **用户头像 (Avatar)** 或者通用的 **定位图钉 (Pin)** 图标。如果是用户头像，记得加上那个“状态环”。
* **提示文案：** 底部的黄色小字提示（“联系人当前在围栏内...”）非常棒！建议给它加一个淡黄色的背景容器 (`SurfaceVariant`) 和一个小图标 (`Info`), 做成一个 **Banner** 样式，视觉上更突出。

### 🛠️ 改造后布局代码结构 (伪代码)

```xml
<LinearLayout orientation="vertical" padding="24dp">

    <TextView text="选定位置" style="@style/TextAppearance.Material3.LabelMedium"/>
    <TextView text="西山区鱼翅路" style="@style/TextAppearance.Material3.TitleMedium" marginTop="4dp"/>

    <Spacer height="24dp"/>

    <Row>
        <TextView text="半径" />
        <TextView text="200 米" weight="bold"/>
    </Row>
    <com.google.android.material.slider.Slider
        android:valueFrom="100"
        android:valueTo="1000"
        android:stepSize="100" />

    <Spacer height="24dp"/>

    <com.google.android.material.button.MaterialButtonToggleGroup
        app:singleSelection="false"> <Button text="到达时" icon="@drawable/ic_arrive"/>
        <Button text="离开时" icon="@drawable/ic_leave"/>
    </com.google.android.material.button.MaterialButtonToggleGroup>

    <Spacer height="16dp"/>

    <Card backgroundTint="LightYellow">
        <TextView text="💡 当前状态：联系人已在围栏内" textColor="DarkOrange"/>
    </Card>

    <Spacer weight="1"/> <Button text="保存更改" style="@style/Widget.Material3.Button"/>
    <Button text="删除围栏" style="@style/Widget.Material3.Button.TextButton" textColor="Red"/>

</LinearLayout>

```

按照这个方案调整，操作流会变成：**拖动地图选点 -> 滑动滑块调大小 -> 底部大拇指保存**。这在 S24 Ultra 上是一气呵成的体验。