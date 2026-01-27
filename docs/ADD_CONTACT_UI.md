这张“添加联系人”的弹窗（`Screenshot_20260126_155558_Find My.png`）目前功能明确，但交互形式（居中弹窗）在 S24 Ultra 这样的大屏设备上显得略微传统，且“输入 UID”这个操作本身体验较“硬”。

为了延续您应用中“底部抽屉 (Bottom Sheet)”的设计语言，并提升添加好友的便捷度，我建议进行以下优化：

### 核心策略：从“弹窗”转变为“底部面板”

居中弹窗打断了用户的视线，且键盘弹出后容易遮挡内容。改为 **Modal Bottom Sheet** 是最佳选择。

---

### 1. 输入体验优化：引入扫码与剪贴板

输入一长串 UID 是反人类的。我们需要让这个过程变得“无感”。

* **输入框重构：**
* **组件：** 使用 `OutlinedTextField`。
* **尾部图标 (End Icon):** 增加一个 **[ 📷 扫码 ]** 图标。点击直接调起相机扫描对方的二维码（需要在“我”的页面加一个二维码展示功能）。
* **自动填充：** 检测剪贴板。如果剪贴板里正好是一串 UID 格式的字符，输入框下方显示“点击粘贴：33e4df...”。



### 2. 时长选择优化：可视化分段

目前的三个小按钮有点散，且选中状态不够醒目。

* **组件：** 使用 **Material 3 Segmented Button** (分段按钮) 或 **大号 Choice Chips**。
* **样式：**
* `[ ⏳ 1小时 ]` `[ ☀️ 今天 ]` `[ ∞ 始终 ]`
* **图标化：** 为每个选项加上图标，减少阅读成本。
* **默认值：** 默认选中“始终”（对于亲密关系应用，这通常是高频选项），减少一次点击。



### 3. 操作按钮：全宽主按钮

目前的“取消”和“发送邀请”是纯文字按钮，视觉权重太低，不像是一个“确认动作”。

* **主按钮：** 使用全宽度的 **FilledButton**。
* 文案：**[ 发送位置共享邀请 ]**
* 位置：固定在面板最底部。


* **取消操作：** 不需要专门的“取消”按钮。Bottom Sheet 天然支持“下滑关闭”或“点击遮罩关闭”。

---

### 🎨 改造后的界面布局 (伪代码)

```xml
<LinearLayout 
    orientation="vertical" 
    padding="24dp"
    background="@drawable/bg_sheet_rounded_top">

    <DragHandle gravity="center" />
    
    <TextView 
        text="添加联系人" 
        style="@style/Headline6" 
        marginBottom="24dp"/>

    <TextInputLayout
        hint="输入对方 UID 或 邮箱"
        endIconMode="custom"
        endIconDrawable="@drawable/ic_scan_qr"> <TextInputEditText />
    </TextInputLayout>
    
    <Chip 
        text="检测到剪贴板内容，点击粘贴" 
        icon="@drawable/ic_paste"
        visibility="gone"/>

    <Spacer height="24dp"/>

    <TextView text="共享位置时长" style="@style/LabelMedium"/>
    <com.google.android.material.button.MaterialButtonToggleGroup
        marginTop="8dp"
        singleSelection="true">
        
        <Button text="1小时" icon="@drawable/ic_clock"/>
        <Button text="直到今晚" icon="@drawable/ic_sun"/>
        <Button text="始终" icon="@drawable/ic_infinity" checked="true"/>
    </com.google.android.material.button.MaterialButtonToggleGroup>

    <Spacer height="32dp"/>

    <Button 
        text="发送邀请" 
        style="@style/Widget.Material3.Button.Primary"
        width="match_parent"
        height="56dp"/>

</LinearLayout>

```

### ✨ 交互细节补充

1. **自动聚焦：** 面板弹出时，自动聚焦输入框并弹起软键盘（视情况而定，如果主推扫码则不弹键盘）。
2. **触觉反馈：** 选中“始终”或点击“发送”时，触发轻微震动。
3. **成功反馈：** 发送成功后，面板自动收起，并弹出一个 `Snackbar`：“邀请已发送给 Gargamel”。

这样修改后，用户只需 **“点+号 -> 扫一扫 -> 确认”**，三步完成添加，体验会极其流畅。