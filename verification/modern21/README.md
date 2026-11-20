# modern.21：章节名称显示

修复关闭的章节浮层因禁用而停止 tick，导致侧栏悬停无法显示名称的问题。普通浏览、编辑模式和关闭动画时，父章节、子章节及点击选择均正常。

正式 **Cleanroom 0.4.0（无 -alpha）** / Java 25，非 userdev，使用实际安装 JAR：

| 组合 | 验证 |
| --- | --- |
| Ponder 1.7.2 + Quests modern.21 客户端 | 27 项检查通过，涵盖聊天命令和章节名称 |
| 未安装 Ponder 的 Quests 客户端 | 15 项章节检查通过 |
| 安装／未安装 Ponder 的专服 | 启动、任务定义保存重读通过 |
| 旧公开包 Ponder 1.7.1 + Quests modern.20 | 同一套用例复现两个问题，保留预期失败对照 |

Quests 89 项、Library 15 项、专服补丁 2 项单元测试通过。任务模型、网络和保存工具的 231 个 class 与 modern.20 逐字节一致。继续使用 Library `5.5.4-modern.8`、Utilities `5.5.1-modern.2` 和 ItemFilters `1.0.4.2`。

运行报告、生产包哈希和截图见 [summary.json](summary.json)。仅本地 Linux x86_64 / llvmpipe 游戏验收；鼠标坐标通过实际 GuiWrapper 注入，保留父控件 tick、布局、渲染和点击处理。Windows CI 原生文件锁／IME 检查另由发布流水线执行，不代表完整 Windows 游戏或输入法引擎测试。

## CI 安装包复验

主分支流水线 `44706952979` 的构建、Windows 文件锁、Windows IME 检查通过。下载产物逐个校验后，使用其原始安装 JAR 在正式 CR 0.4.0 上完成有／无 Ponder 的客户端和专服四组合复验，全部通过；联合客户端 27 项、无 Ponder 客户端 15 项检查通过。生产 Quests SHA-256：`a2d92a0e3e52f5193daf8a9d1ab8c5c06b1c1e5b3e7b27a1d8dedcd0ffba4a66`。

CI 与本地构建仅五个匿名 TileEntity class 字节不同，忽略常量池索引后声明和指令一致；231 个任务模型／网络／保存工具 class 字节一致。最终兼容结论依据下载的 CI 安装 JAR 的实际四组合运行结果，详见 [main-ci.json](main-ci.json)。
