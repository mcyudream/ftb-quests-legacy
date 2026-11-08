# FTB Quests Legacy · Nova 重制版

[![CI](https://github.com/mcyudream/ftb-quests-legacy/actions/workflows/ci.yml/badge.svg)](https://github.com/mcyudream/ftb-quests-legacy/actions/workflows/ci.yml)

Minecraft **1.12.2** · **Cleanroom 0.4.0 正式版（无 alpha）** · **Java 25**

基于 NovaEngineering 的 FTB Quests、FTB Library 与 FTB Utilities，由 **yudream** 维护。保留旧任务格式、整数任务／团队 ID、未知扩展字段、进度与领奖数据；整合现代任务编辑、Markdown 正文、媒体入口、可选 Ponder、ZenScript、动画及中文／拼音搜索。

**[下载正式版](https://github.com/mcyudream/ftb-quests-legacy/releases/latest)** · [完整功能和使用说明](ftb-quests/README.md) · [来源与版权说明](NOTICE.md) · [问题反馈](https://github.com/mcyudream/ftb-quests-legacy/issues)

## 安装

客户端和服务端同时安装以下文件，替换旧版同 Mod ID 的 JAR：

| 文件 | 版本 |
| --- | --- |
| FTB Quests | `FTBQuests-1202.10.0-modern.19.jar` |
| FTB Library | `FTB-Library-NovaEng-5.5.4-modern.7.jar` |
| FTB Utilities（原整合包使用时保留） | `FTB-Utilities-NovaEng-5.5.1-modern.2.jar` |
| ItemFilters（必装，另从原发布页下载） | `1.0.4.2` |

使用 Utilities 的整合包请一并替换本次发布的三个 FTB JAR。Utilities 版本号仍为 modern.2，但已使用新 Library 重新编译。

只有 `-sources.jar` 后缀的文件为源码，不放进 mods。此仓库的 `media-api` / `mcp-server` 项目是编译接口，不能装进游戏。

正式 CR 0.4.0 专用服务器若未安装 Ponder 或同等 Kirino 客户端隔离修复，请额外安装 Release 中的 `Cleanroom-0.4.0-Server-Compatibility-1.0.0.jar`。这是本次专服验收实际使用的加载器补丁；只在版本精确为 0.4.0 的专服启用。客户端无需安装。源码与说明见 [server-compat](server-compat/README.md)。

Ponder、CraftTweaker／ZenScript、Media API 与 Minecraft MCP Server 保持可选，分别安装对应运行 Mod；[Ponder Legacy](https://github.com/mcyudream/ponder-legacy/releases) 无需随任务系统重复捆绑。公开发行的三个 FTB JAR 不内嵌媒体运行库，已有 Media API 的视频接入继续保留。

## 本版要点

**Quests modern.19 / Library modern.7**：自绘输入框通知 Cleanroom 启用系统输入法，补齐 Windows 输入法激活路径；章节侧栏支持直接右键编辑，无需等待悬停展开。保留 modern.18 的点击坐标、互斥焦点和 Unicode 边界修复。详见 [modern.19 验收与参考实现](verification/modern19/README.md)。

- **modern.17 Windows 保存热修复**：保持任务根目录不动，完整备份与事务回滚后逐文件原子提交；I/O 失败保留脏状态并间隔 5 秒重试。兼容旧事务记录和任务数据。详见 [发行说明](docs/releases/1202.10.0-modern.17.md)。

- **在原输入框直接编辑中文**：移除“中”按钮、F4 及独立系统编辑窗口；标题、搜索和多行正文接收游戏的 Unicode 字符事件，补充汉字作为完整字符插入与撤销。物品／思索拼音检索保持可用。
- 物品和思索选择器支持中文、英文、注册名／教程 ID、全拼和首字母；`tieding`／`td` 可查询铁锭。索引使用 Cleanroom 自带 ICU，在后台生成与缓存。
- 详情淡入淡出、轻微缩放；侧栏、悬停、画布缩放和开箱过渡更柔和；编辑模式仍即时响应。
- 保留原任务格式；复用原服务端校验与同步。可选思索的搜索链接返回准确场景 ID。

[中文输入与搜索](ftb-quests/docs/INPUT-AND-SEARCH.md) · [动画配置](ftb-quests/docs/ANIMATIONS.md) · [依赖环与编辑性能](ftb-quests/docs/LOADING-AND-EDITING.md)

## 独立构建与 CI

```bash
python3 tools/bootstrap.py
./gradlew --no-daemon :build --console=plain
python3 tools/prepare_release.py
```

使用 Java 25 和 Python 3.11+。依赖按锁文件下载并校验 SHA-256，缓存位于 `.build-deps/`；不依赖维护者的同级工作目录。Cleanroom 编译 SDK 与最低运行基线分别声明；较新的 SDK 不提高最低加载器版本。

CI 在 main、PR、手动运行时构建三个 FTB 模块及独立专服补丁、执行 Quests／Library／补丁单元测试、重混淆、检查产物并保存 JAR／源码包／测试报告。推送与 `release.json` 一致的 `v*` 标签后，在 Linux 构建和 Windows 原生文件锁检查均通过后自动发布 **非预览** GitHub Release。`SHA256SUMS` 校验下载文件，`BUILD-INFO.json` 记录提交与内容摘要。

## 验证范围

modern.18 的云端 CI 生产 JAR 已逐一下载校验，与正式 Cleanroom 0.4.0（非 userdev）客户端／专服实际验收的文件一致。Quests 89 项、Library 13 项、专服补丁 2 项单元测试通过；Windows 原生目录占用、文件共享锁、回滚与解锁重试检查通过：[modern.18 输入回归](verification/modern18/README.md)。[modern.17 发行记录](verification/modern17/README.md) 保留供查阅。

modern.16 历史记录保留在 [原本地版报告](verification/local-modern16/REPORT.md) 和 [原公开构建验收](verification/release/README.md)。

独立系统编辑窗口已移除，中文按游戏键盘字符队列送入原输入框并验证保存；**系统输入法引擎的真实候选词按键流程未自动化实测**。Linux x86_64 已验证，Windows/macOS 和完整整合包未实测。拼音采用当前显示名及 ICU 的读音，未枚举所有多音字别名。详见使用说明。
