# 公开构建发行验收

公开多模块源码独立构建使用哈希固定的 Ponder 1.5.0 公开发行 JAR，并在编译时反混淆；Media API／MCP 仅使用编译接口，三个 FTB 产物均不包含接口 SDK、验收代码或媒体后端。

正式 **Cleanroom 0.4.0（无 alpha）**、Java 25、Linux x86_64、非 userdev 的实际安装 JAR 验证：

- 客户端（Ponder 1.6.3，未安装 Media API）：中文字符事件、原生 AWT 编辑确认／取消、生命周期、正文撤销、物品全拼／首字母选择、思索精确 ID 选择、中文标题／多行正文网络保存、未知字段及整本保存重读均通过。
- 专用服务器（未安装 Ponder／MCP／Media API）：启动和旧任务保存重读通过；使用正式 CR 0.4.0 已有客户端类隔离补丁。
- Quests 78 项、Library 10 项、专服补丁 2 项单元测试通过。独立公开构建的专服补丁与本目录运行验收所用补丁 SHA-256 完全一致。运行哈希和原始结果见本目录 runtime.json 及子目录。

最终 GitHub main CI 产物已下载并校验，并以下载 JAR 重新完成上述正式 CR 0.4.0 客户端与专服验收：原始结果见 [ci-input-client](ci-input-client/) 和 [ci-dedicated-server](ci-dedicated-server/)。云端与本地构建的代码条目完全相同；两份西班牙语翻译资源经 Git 规范为 LF 换行，因此最终 Quests／Library JAR 哈希与前一轮本地构建不同。最终验收直接使用云端文件。

发布 CI、正式版状态、公开下载附件的完整 SHA-256 与逐文件复核结果见 [published.json](published.json)。Release 中的 JAR 与最后一次实际运行验收文件完全一致。

媒体、MCP、Windows/macOS、用户完整整合包，以及系统输入法引擎真实候选词按键未在本次公开构建上实测。既有本地版详细边界保留在 ../local-modern16/。
