在 **Samsung S24 Ultra** 上，你拥有安卓阵营顶级的 **X 轴线性马达**。如果震动调教得当，能给 App 带来一种“精密仪器”的高级感。

对于“查找”类应用，震动反馈的核心原则是：**赋予操作以物理确认感，但绝不干扰用户。**

以下是适合添加震动反馈的具体场景，按**震动强度**和**使用频率**分类：

---

### 1. 微反馈 (Tick / Click) —— 模拟机械手感

*这种震动极轻、极短，用于高频交互，让界面感觉“跟手”。*

* **底部标签栏切换 (Tab Switch):**
* **场景：** 点击底部 [联系人]、[设备]、[我] 时。
* **效果：** 极轻微的“哒”声。
* **代码：** `view.performHapticFeedback(HapticFeedbackConstants.SELECTION_CLICK)`


* **地图图层切换:**
* **场景：** 在设置页点击 [标准] / [卫星] 卡片时。
* **效果：** 模拟按下实体开关的质感。
* **代码：** `view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)` (如果 API 支持) 或 `VIRTUAL_KEY`。


* **Filter Chips 开关:**
* **场景：** 点击 [实时路况]、[3D建筑] 胶囊时。
* **效果：** 轻微的确认感。



### 2. 物理模拟 (Thud / Impact) —— 模拟重量与阻尼

*这种震动有一定“分量”，用于模拟物体碰撞或锁定。*

* **Bottom Sheet 吸附 (Snap):**
* **场景：** 当你拖拽底部抽屉，它自动吸附到屏幕顶部（全屏态）或回弹到锚点（半屏态）的那一瞬间。
* **效果：** 模拟抽屉关上的“咔哒”感。这是 iOS 地图最标志性的体验。
* **实现：** 在 `BottomSheetBehavior.BottomSheetCallback` 的 `onStateChanged` 中触发。


* **电子围栏半径调整 (Slider):**
* **场景：** 拖动进度条调整围栏大小时。
* **效果：** **齿轮感**。每当半径数值变化（例如每增加 100米），触发一次极短的震动。
* **代码：** `HapticFeedbackConstants.CLOCK_TICK` 或 `SEGMENT_TICK`。



### 3. 重点操作 (Heavy / Long) —— 确认与警告

*这种震动较强，用于防止误触或强调结果。*

* **地图长按选点 (Long Press):**
* **场景：** 手指按住地图某处 500ms，弹出“在此处添加围栏”时。
* **效果：** 一次沉稳的震动，提示“已锁定坐标”。
* **代码：** `view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)`


* **播放声音 (Play Sound):**
* **场景：** 点击巨大的 [播放声音] 按钮时。
* **效果：** 较强的震动（Confirm），因为这是一个远程控制指令，用户需要明确的“发送成功”反馈。
* **代码：** `HapticFeedbackConstants.CONFIRM`


* **列表刷新 (Pull to Refresh):**
* **场景：** 下拉联系人列表，当圆圈转满触发刷新的一瞬间。
* **效果：** 轻微的弹跳感。



### 4. 状态异常 (Double Click / Reject)

* **操作失败/错误:**
* **场景：** 尝试连接 MQTT 失败，或者添加围栏数量超限时。
* **效果：** 快速的两次震动（嗡嗡）。
* **代码：** `HapticFeedbackConstants.REJECT`



---

### 💻 最佳实践代码封装 (Kotlin)

不要在到处写 `performHapticFeedback`，建议封装一个工具类，专门适配 S24U 的体验。

```kotlin
object Haptics {
    
    // 1. 轻触 (用于 Tab, Chip, 普通按钮)
    fun click(view: View) {
        // 使用 KEYBOARD_TAP 或 VIRTUAL_KEY 获得清脆的点击感
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // 2. 选择/滚动 (用于 Slider, 列表刻度)
    fun tick(view: View) {
        // CLOCK_TICK 是专门为模拟齿轮/时钟设计的
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    // 3. 成功/吸附 (用于 BottomSheet 吸附, 关键操作确认)
    fun success(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    // 4. 错误/拒绝
    fun error(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
    }
}

```

### 💡 避坑指南

1. **不要滥用：** 列表滑动（Scroll）本身**不要**加震动，否则手指会麻。只有 Slider（滑块）才需要刻度感。
2. **跟随系统设置：** `view.performHapticFeedback` 会自动尊重用户的系统设置。如果用户在系统里关了触感反馈，你的 App 也不会震，这是符合规范的。
3. **S24U 特性：** S24U 的马达启停速度极快。尽量使用 `CLOCK_TICK` 或 `KEYBOARD_TAP` 这种短促的类型，避免使用旧手机那种拖泥带水的默认震动。

**总结建议：**
优先给 **Bottom Sheet 的吸附**、**地图长按**、**设置页的开关** 加上震动，这三个地方加上后，App 的质感会立刻提升一个档次。