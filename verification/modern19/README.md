# modern.19 Windows 输入法与章节操作

## 输入法根因与参考实现

FTB Library 的 `TextBox` 与正文多行输入框均为自绘控件。此前只设置内部焦点及键盘重复，没有调用原版输入框的平台焦点通知，因此无法启用 Cleanroom 已关闭的 Windows IME。modern.18 的真实 JAR 在正式 CR 0.4.0 的观察结果为：正文已选中，`IMEHandler` 最后收到的状态仍为 `false`。

参考了 [HEI 搜索框](https://github.com/CleanroomMC/HadEnoughItems/blob/cleanroom/src/main/java/mezz/jei/input/GuiTextFieldFilter.java)：继承 `GuiTextField`，在 `setFocused` 中先调用 `super.setFocused`。同时检查正式 CR 0.4.0 的 `IMEHandler` / `WindowsIMEHandler` 和加载器提供的原版 `GuiTextField` 焦点处理。

修复让自绘输入控件通过只负责焦点通知的原版 `GuiTextField` 委托走相同入口。该委托不绘制、不出现在控件列表、不持有玩家文本；正文、搜索和标题仍在原框编辑。使用原版入口可跟随不同 Cleanroom 版本的 IMM／SDL 实现，无需硬编码某个新版加载器的内部 API 或新建外部编辑窗口。

## 章节点击

侧栏 `ButtonChapter.onClicked` 原本是空回调，仅悬停后生成的展开按钮包含操作。真实点击回归进一步发现，画布的右键分支没有检查鼠标是否位于画布，先于侧栏消费事件并弹出添加任务菜单。现画布限制鼠标区域，侧栏与展开章节共用点击处理：左键切换，右键在有编辑权限时打开移动、编辑和删除菜单。未开启编辑模式的玩家不获得章节编辑入口。

## 验收边界

使用正式 Minecraft 1.12.2 / Cleanroom **0.4.0（不带 alpha）** / Java 25 的客户端和专服，独立副本运行，用户原实例未被修改。

客户端回归观察并委托给**真实** Cleanroom 原生 IME 处理器，检查标题／副标题／正文的焦点变化是否通知平台；使用真实鼠标分发、游戏字符队列、组件表单、物品／任务／思索选择器，检查保存与未知字段保留。

Windows CI 使用下载后校验 SHA-256 的正式 CR 0.4.0、JNA 和 JNA Platform，创建真实 Win32 窗口，把其 IME 上下文解除后调用加载器的真实 `WindowsIMEHandler`，检查关联、解除、重新启用。测试不使用虚假的 IMM 实现。[检查已通过](https://github.com/mcyudream/ftb-quests-legacy/actions/runs/42464079509)：[IME 输出](windows-ime.txt)、[文件锁输出](windows-storage.txt)。

**Windows 原生上下文检查不包含 Minecraft 的 OpenGL 窗口和微软拼音候选词按键流程。** 不能据此声称 Windows 完整整合包或实际中文选词验收通过。

## 真实产物回归

- [modern.18 失败结果](baseline/result.json)：原版 Library modern.6 正文内部已经获得焦点，但实际 Cleanroom IME 处理器最后状态仍为 `false`；此检查在 Linux 正式运行环境观察平台通知，不声称复现了 Windows 候选词界面。
- [客户端结果](client/result.json) 与 [逐项检查](client/focus-checks.json)：实际 `GuiWrapper` 右键章节图标并点击编辑，进入章节配置页；展开章节菜单及非编辑权限检查通过。正文／标题／副标题、重排、预览、组件表单、任务／物品／思索搜索与返回、上下文菜单均检查平台输入法状态或焦点归属；中文及补充汉字网络保存与磁盘重读保留未知字段。
- [专服结果](server/result.json)：无 Ponder、Media API、MCP，使用独立 CR 0.4.0 专服兼容补丁，启动和任务数据完整读写重载通过，退出码 0。
- [构建记录](build.json)：104 项单元测试通过，零失败、零跳过；[客户端](client/runtime.json) 和 [专服](server/runtime.json) 记录实际运行 JAR 的 SHA-256。

使用用户任务书的独立副本（22 个章节、621 个任务），未修改原实例。客户端自动化完成后正常退出。

## 云端产物

[源码 CI](https://github.com/mcyudream/ftb-quests-legacy/actions/runs/42464868801) 的构建、104 项测试、Windows 原生文件锁和 IME 检查均通过。已下载并校验云端四个生产 JAR，再使用这些实际文件在正式 CR 0.4.0 客户端和专服重跑上述回归，均为 `FUNCTIONAL_PASS`、退出码 0；上方运行文件与结果记录的就是这批云端产物。[下载校验记录](cloud-build.json) 记录其 SHA-256。
