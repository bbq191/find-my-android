这张“导航至”界面（`Screenshot_20260123_160210_Find My.png`）目前是一个 **居中弹窗 (Dialog)**。

虽然看起来很规整，但在 S24 Ultra 这样的大屏设备上，**居中弹窗**并不是最佳选择（手指够不到，且遮挡了地图上下文）。此外，操作逻辑上缺少一个明确的“开始”按钮。

以下是将其重构为 **“导航发起页 (Navigation Launch Sheet)”** 的详细优化建议：

---

### 🏗️ 1. 容器重构：Dialog ➔ Bottom Sheet

* **现状：** 屏幕中央的一个圆角矩形弹窗。
* **问题：** 割裂了与地图的联系，大拇指操作不便，且与应用其他部分的交互模式（底抽屉）不一致。
* **最佳实践：** 改为 **Modal Bottom Sheet (模态底部抽屉)**。
* **交互：** 点击“导航”后，从底部升起一个面板。
* **优势：** 手指天然处于屏幕下方，操作极其顺手；上半部分依然能看到目标在地图上的位置。



---

### 🚗 2. 出行方式模块 (Transport Mode)

* **现状：** 四个独立的方形卡片（驾车、步行、骑行、公交）。
* **问题：** 占据了过多垂直空间，且选中态（黄色背景）虽然清晰，但排版略显松散。
* **改动建议：** 使用 **Segmented Button (分段按钮)** 或 **紧凑型卡片行**。
* **布局：** 一行排列，去掉卡片的大边距。
* **样式：**
* **选中态：** `SecondaryContainer` (深色) 背景 + `OnSecondaryContainer` (浅色) 图标。
* **未选中：** 透明背景 + 灰色图标。


* **细节：** 在图标下方直接显示 **预估时间**（例如“驾车 15分钟”），而不仅仅是模式名称。这才是用户决策的核心依据。



---

### 🗺️ 3. 地图选择模块 (Map App Selector)

* **现状：** 一个巨大的长条形卡片，写着“系统默认地图”，左边有个箭头图标。
* **问题：**
1. **信息量低：** 占了很大地方却没提供什么选项。
2. **歧义：** 用户不知道点这个是“去设置默认地图”还是“开始导航”。


* **改动建议：** **检测并列出已安装的地图 App**。
* **逻辑：** 扫描手机里是否安装了高德、百度、腾讯、Google Maps。
* **样式：**
* **如果只装了一个地图：** 隐藏此模块，直接点“开始”就跳过去。
* **如果装了多个：** 显示一个 **水平滚动的图标列表**。
* `[ (icon)高德 ]` `[ (icon)百度 ]` `[ (icon)系统默认 ]`
* 选中一个作为本次导航的目标。







---

### 🚀 4. 行动区域 (Call to Action)

* **现状：** 只有右下角一个小小的“取消”文字按钮。**严重缺失“开始导航”的主按钮**（目前可能是点击地图选项直接开始？这容易误触）。
* **改动建议：** 增加 **主行动按钮**。
* **组件：** `ExtendedFloatingActionButton` 或 宽大的 `FilledButton`。
* **文案：** **[ 🚀 开始导航 ]** (或者显示 `[ 打开高德地图 ]`)。
* **位置：** 固定在 Bottom Sheet 的最底部。
* **取消操作：** 不需要专门的“取消”按钮，下滑关闭 Sheet 或点击遮罩层即可（这是 Bottom Sheet 的天然优势）。



---

### 🎨 5. 视觉细节优化 (Visual Polish)

* **头部信息 (Header):**
* **增加地址：** 目前只有“距离约 8.1公里”。建议在下方补充具体地址（如“五华区海源北路...”），防止导错地儿。
* **方向指示：** 在距离旁边加一个小箭头图标 `↗`，增加动感。


* **动态取色 (Monet):**
* 确保选中态的颜色（目前是黄色）与系统壁纸主题色一致。



---

### 🛠️ 改造后布局代码结构 (伪代码)

```xml
<LinearLayout orientation="vertical" padding="24dp">

    <DragHandle gravity="center" />

    <TextView text="导航至 马瑞雯" style="@style/HeadLine5" />
    <Row marginTop="8dp">
        <Icon src="@drawable/ic_distance" tint="Grey"/>
        <TextView text="8.1 公里" textColor="Grey"/>
        <TextView text=" • 五华区海源北路" textColor="Grey" maxLines="1"/>
    </Row>

    <Spacer height="24dp"/>

    <LinearLayout orientation="horizontal" weightSum="4">
        <TransportCard icon="car" text="18分钟" selected="true"/>
        <TransportCard icon="walk" text="2小时" selected="false"/>
        <TransportCard icon="bike" text="45分钟" selected="false"/>
        <TransportCard icon="bus" text="30分钟" selected="false"/>
    </LinearLayout>

    <Spacer height="24dp"/>

    <TextView text="使用以下地图打开" style="@style/LabelMedium"/>
    <HorizontalScrollView>
        <Row>
            <AppIcon icon="@drawable/logo_amap" text="高德" selected="true"/>
            <AppIcon icon="@drawable/logo_baidu" text="百度"/>
            <AppIcon icon="@drawable/logo_tencent" text="腾讯"/>
        </Row>
    </HorizontalScrollView>

    <Spacer height="32dp"/>

    <Button 
        text="开始导航" 
        icon="@drawable/ic_navigation"
        style="@style/Widget.Material3.Button.Primary"
        width="match_parent"
        height="56dp"/>

</LinearLayout>

```

### 🌟 最终效果预期

改造后，用户点击“导航”：

1. 底部升起面板，不会遮挡住朋友的头像。
2. 一眼看到“驾车 18分钟”，并默认选中高德地图（因为你上次选过）。
3. 大拇指直接按底部的 **[ 开始导航 ]** 大按钮。
4. 直接跳转高德 App 开始导航。

这套流程比现在的“居中弹窗 -> 选模式 -> 选地图 -> 还要再点一下？”要流畅得多。