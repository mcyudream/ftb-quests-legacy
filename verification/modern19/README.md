# modern.19 Windows 输入法与章节操作

## 输入法根因与参考实现

FTB Library 的 `TextBox` 与正文多行输入框均为自绘控件。此前只设置内部焦点及键盘重复，没有调用原版输入框的平台焦点通知，因此无法启用 Cleanroom 已关闭的 Windows IME。modern.18 的真实 JAR 在正式 CR 0.4.0 的观察结果为：正文已选中，`IMEHandler` 最后收到的状态仍为 `false`。

参考了 [HEI 搜索框](https://github.com/CleanroomMC/HadEnoughItems/blob/cleanroom/src/main/java/mezz/jei/input/GuiTextFieldFilter.java)：继承 `GuiTextField`，在 `setFocused` 中先调用 `super.setFocused`。同时检查正式 CR 0.4.0 的 `IMEHandler` / `WindowsIMEHandler` 和加载器提供的原版 `GuiTextField` 焦点处理。

修复让自绘输入控件通过只负责焦点通知的原版 `GuiTextField` 委托走相同入口。该委托不绘制、不出现在控件列表、不持有玩家文本；正文、搜索和标题仍在原框编辑。使用原版入口可跟随不同 Cleanroom 版本的 IMM／SDL 实现，无需硬编码某个新版加载器的内部 API 或新建外部编辑窗口。

## 章节点击

侧栏 `ButtonChapter.onClicked` 原本是空回调，仅悬停后生成的展开按钮包含操作。现侧栏与展开章节共用点击处理：左键切换，右键在有编辑权限时打开移动、编辑和删除菜单。未开启编辑模式的玩家不获得章节编辑入口。

## 验收边界

使用正式 Minecraft 1.12.2 / Cleanroom **0.4.0（不带 alpha）** / Java 25 的客户端和专服，独立副本运行，用户原实例未被修改。

客户端回归观察并委托给**真实** Cleanroom 原生 IME 处理器，检查标题／副标题／正文的焦点变化是否通知平台；使用真实鼠标分发、游戏字符队列、组件表单、物品／任务／思索选择器，检查保存与未知字段保留。

Windows CI 使用下载后校验 SHA-256 的正式 CR 0.4.0、JNA 和 JNA Platform，创建真实 Win32 窗口，把其 IME 上下文解除后调用加载器的真实 `WindowsIMEHandler`，检查关联、解除、重新启用。测试不使用虚假的 IMM 实现。

**Windows 原生上下文检查不包含 Minecraft 的 OpenGL 窗口和微软拼音候选词按键流程。** 不能据此声称 Windows 完整整合包或实际中文选词验收通过。
