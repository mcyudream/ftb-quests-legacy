# modern.22：隐藏思索按钮提示中的 ID

删除正文 `ponder` 组件悬停提示中自动追加的原始目标 ID。默认“查看思索演示”、自定义 `hover`、按钮图标和点击处理沿用原行为，编辑器仍可按教程 ID 搜索。

本地安装 JAR 在正式 **Cleanroom 0.4.0（无 `-alpha`）**、Java 25 上完成四组合回归：

| 组合 | 检查结果 |
| --- | --- |
| Ponder 1.7.2 + Quests modern.22 客户端 | 启动、聊天命令、章节悬停与点击通过 |
| 未安装 Ponder 的 Quests 客户端 | 启动、章节悬停与点击通过 |
| 安装 Ponder 的专服 | 启动、任务定义保存重读通过 |
| 未安装 Ponder 的专服 | 启动、任务定义保存重读通过 |

Quests 89 项现有单元测试通过。本地 JAR 摘要、报告与相对 modern.21 的归档差异见 [summary.json](summary.json)。生产归档只改变版本信息与思索按钮所在的匿名 class。

运行平台为 Linux x86_64 / llvmpipe，使用实际安装 JAR，非 userdev。提示删除已检查源码和生产字节码，本次未采集游戏内悬停截图或复测场景播放；Windows/macOS 完整游戏与完整用户整合包未验证。
