# FTB Quests Nova 重制版

Minecraft **1.12.2** · **正式 Cleanroom 0.4.0** · **Java 25**

以 NovaEngineering 的 FTB Quests、FTB Library、FTB Utilities 为基础，回移高版本任务机制，整合任务编辑、媒体画布、扩展检测与界面动画。脚本入口使用 **CraftTweaker 4 / ZenScript**。

## 安装

modern.17 移除独立中文编辑弹窗及“中”／F4 入口，直接使用游戏输入框；同时修复 Windows 自动保存时整目录重命名被拒绝的问题，并加入保存失败后的重试与快照保留。任务数据格式不变；详见 [热修复说明](../docs/releases/1202.10.0-modern.17.md)。

客户端和服务端将 FTB Quests 更新到 modern.17，配套版本如下；已有相同版本的依赖保留一份，移走旧版同 Mod ID 的文件：

| 文件 | 用途 |
| --- | --- |
| `FTBQuests-1202.10.0-modern.17.jar` | 任务系统 |
| `FTB-Library-NovaEng-5.5.4-modern.5.jar` | 修复单人存档 UID 2 误判，保留 Nova int 团队 UID |
| `FTB-Utilities-NovaEng-5.5.1-modern.2.jar` | 配套团队、权限及工具 |
| `ItemFilters-1.0.4.2.jar` | 必装前置；已有相同版本时保留一份 |
| `MediaAPI-1.0.4.jar` | 可选客户端视频 API Mod，已内置 WATERMeDIA 1.3.43；本公开发行不重新捆绑媒体运行库，按原发布渠道独立安装 |

专用服务器若没有其他模组提供正式 CR 0.4.0 的 Kirino 客户端类隔离修复，另安装 [Release](https://github.com/mcyudream/ftb-quests-legacy/releases/latest) 中的 `Cleanroom-0.4.0-Server-Compatibility-1.0.0.jar`。客户端无需此补丁；本仓库 Ponder 已提供同项修复，安装 Ponder 的服务器也无需重复加入独立补丁。

CraftTweaker、Ponder 和视频后端均为可选依赖。验收用的 `ftbquests-acceptance.jar` 不属于安装包。不要把 `-sources.jar` 或 `-mcp.jar` 放进 mods。

### modern.17 原框中文输入

- 点击原输入框后直接输入中文，标题、物品搜索、思索搜索及多行正文沿用游戏字符事件。不再打开独立系统编辑窗口，“中”按钮与 F4 弹窗入口已移除。
- UTF-16 代理对合并为完整字符提交，补充汉字的输入、删除、撤销不拆开。全拼／首字母检索保留。需配套 Library modern.5。

### modern.16 中文输入与拼音搜索（历史）

- 以下系统窗口方案已在 modern.17 退役。modern.16 曾在单行输入框及正文编辑器右侧新增“中”入口，聚焦后按 **F4** 也可打开系统输入法编辑窗口，直接调用系统中文输入法的组合与候选词界面。支持标题、搜索和多行正文；确认后回填，取消保留原文，多行编辑可整体撤销。已有可用的游戏内直接输入继续保留。
- 物品选择支持中文、英文、注册名、**全拼／首字母**，例如 `tieding`／`td` 搜索“铁锭”；保留 `@minecraft` 等模组过滤。
- 思索选择器按物品名、教程标题及教程 ID 搜索，支持全拼／首字母；选择后仍引用原有准确教程 ID。
- 拼音索引在后台生成并缓存，使用 Cleanroom 自带 ICU，无需额外前置。更新时需同时替换 **Quests modern.16 和 Library modern.4**。原任务格式、ID、进度及领奖数据不变。说明与验证边界见 [中文输入与搜索](docs/INPUT-AND-SEARCH.md)。

### modern.15 界面动画

- 任务详情默认 **320 ms**：淡入／淡出、1.5% 的轻微缩放和 4 像素位移；背景遮罩独立渐变。打开时先完成布局和首次渲染准备，再开始过渡。
- 章节浮层改为淡入淡出和 6 像素位移，默认 **350 ms**；侧栏图标用连续缩放，悬停底色和辉光一起过渡。
- 悬停与画布缩放使用按实际时间计算的临界阻尼，中途反向时保留速度；新配置的通用时长默认 **220 ms**，已有显式配置保留。
- 开箱开盖、滚动和轻晃采用平滑起止曲线，滚动物品保留亚像素位置，揭晓卡片轻微落定。原有跳过、关闭和服务器发奖流程保留。
- 详情与侧栏先按原坐标裁剪文字、物品、媒体，再合成动画；鼠标反向映射到相同坐标。无 FBO 时回退原位绘制。编辑模式继续即时打开／关闭详情及缩放画布。
- “显示设置”可分别调整通用、侧栏和详情时长。见 [动画配置与验收](docs/ANIMATIONS.md)。

### modern.14 可选 MCP 编辑接口

安装 [Minecraft MCP Server 1.0.0](https://github.com/mcyudream/ftb-quests-legacy#安装) 后注册 `ftbquests` 模块，支持章节/任务/条件/奖励创建、SNBT 属性编辑、批量坐标、网格布局和共享撤销/重做。操作在服务端线程执行，复用原有验证、同步和事务写盘；多人服请连接专服 MCP 地址。客户端另提供打开任务书的工具。MCP 前置、Ponder 均保持软依赖，未安装时不初始化对应适配。配置和参数说明见前置手册。

### modern.13 循环依赖、侧边动画与新增节点

- 修复 `FTB Quests → ZenUtils → ModularMachinery → Ponder → FTB Quests` 的加载顺序环。Ponder 链接和选择器在加载完成后按需调用，移除任务 Mod 的 `after:ponder-legacy` 排序要求，继续采用可选联动。
- 侧边导航图标、辉光与章节面板的动画时长独立为 **350 ms**，可在右下角“显示设置 → 侧边栏动画时长（毫秒）”调整。旧的通用 140 ms 设置不会覆盖新侧边栏默认值。
- 新增任务与子任务使用增量 ID 登记和画布局部更新，保留其他节点的文字、图标、按钮和 JEI 包装，不再为单个新增节点重建整本任务书的 ID 索引。任务定义仍在内存中修改，在世界自动保存和退出时写盘。
- 客户端与服务器将 Quests 升到 modern.13。Media API 保持已修复抖音实播的 1.0.4；旧任务格式、ID、团队 UID、进度和领奖数据继续兼容。处理类似加载环的方法与边界见 [加载顺序与编辑性能](docs/LOADING-AND-EDITING.md)。

### modern.12 / Media API 1.0.4 客户端视频与抖音播放修复

- 视频文件仅从客户端读取。选择本地视频后复制到客户端 `config/ftbquests/media/`，沿用 `media:<SHA256>.<扩展名>`；也可将整合包自带视频放入 `config/ftbquests/videos/`，填写 `local:教程/演示.mp4`。缺文件会提示客户端缺失，不从服务器同步视频。旧 `.media-cache` 中已有的视频可只读复用，不生成或下载新缓存；图片继续使用原同步方式。
- 视频元数据和视频流强制客户端直连，忽略 JVM、环境和系统代理；不会修改游戏的全局代理设置。在线播放使用 Java 直连输入和 VLC 解码、内存缓冲，不落盘视频。服务器只保存任务中的原始路径或分享链接。
- 新增抖音分享短链、完整视频地址、嵌入页地址与分享文本识别。描述视频提供重试和网页入口。解析公开分享页元数据，不接入第三方解析服务，不使用账号 Cookie，不修改平台返回的播放地址。
- 修复抖音分享页没有视频数据的问题：客户端取得平台匿名 `ttwid`，票据仅存内存，不读取登录 Cookie；只发给分享页，不发送到 CDN 或游戏服务器。修正路由层相同视频 ID 导致提前解析失败的问题，兼容 `playApi` 字段。用户链接 `https://v.douyin.com/IVOk7RWtfKU/` 已在正式 Cleanroom 0.4.0 中实际解码、暂停、进度跳转并重试播放，原始流总时长约 31 秒。
- Media API 更新至 **1.0.4**，API 主版本仍为 1，WATERMeDIA 1.3.43 保持原样内置。直连回调目前支持音视频合流文件；仅提供分离音轨或受限播放的视频会提示转网页，不会通过代理获取音轨。
- 以上为 modern.12 的媒体变更；modern.13 完整包继续使用 Media API 1.0.4、Library modern.3 和 Utilities modern.2。

### Library modern.3 存档加载热修复

- 修正 `Duplicate/reserved FTB team UID: 2` 崩溃。`singleplayer` 是原版 Library 正常创建的持久团队，固定 UID 为 2；加载时保留它，新团队的随机分配仍避开此编号，真正的重复 UID 仍拒绝加载。
- 已安装 Quests modern.11 / Utilities modern.2 时，只需将客户端和服务端的 Library modern.2 替换为 modern.3，直接重进原存档。热修复不迁移团队，不重置任务进度或领奖记录。
- 验收包含创建单人存档、退出、另启 JVM 重新进入、专服加载和保存重读；覆盖正式 Cleanroom 0.4.0，以及带 AE2S / Ponder 1.6.0 / AE2S Ponder 的 0.6.8-alpha。运行平台为 Linux x86_64；用户日志中的 Windows 11 环境未实机验证。详见 [Library 热修复验收](../verification/local-modern16/REPORT.md)。

### modern.11 中文与菜单本地化

- 修正显示设置中配置 ID 自动转为小写、翻译键使用驼峰导致的匹配失败；补齐中英文实际使用的键。
- 补齐右键菜单、任务/章节/奖励编辑、画布元素、批量编辑、物品说明和配套 Library / Utilities 的缺失中文。快捷键说明及隐藏任务提示改用语言文件。
- 配置中的开关显示“开启 / 关闭”，默认值、类型及数值范围提示使用本地化文字。布尔存储值、命令参数、任务 ID、团队 UID 和进度格式保持原有语义。
- 本次需一起替换上表中的 **Quests、Library、Utilities 三份 JAR**；新版 Quests 依赖配套 Library modern.2。

### modern.10 Markdown 教程选择器

- 在 Markdown 模式点击工具栏“思索演示”，直接打开教程搜索选择器，选择后在光标位置自动插入 `[教程标题](ponder:物品#教程)`，无需手填 ID。
- 先选中文字时使用选中文字作为链接标题；未选择时使用教程名。搜索框自动获得焦点，可按物品名、教程名和 ID 搜索。
- 取消保留原草稿和选区，插入可用 Ctrl+Z / Ctrl+Y 一步撤销和重做。标题中的 Markdown 特殊字符自动转义，保存与任务数据格式沿用原方式。
- 原版文本模式和“组件”菜单继续提供带搜索按钮的 JSON 组件表单。

### modern.9 任务中的 Ponder 链接

- 正文编辑器工具栏新增“思索演示”→“搜索教程”，可按物品名称、物品 ID、教程标题或教程 ID 筛选，选择物品的全部演示或具体教程。只读取已注册内容的元数据，打开时才编译场景。
- 正文使用物品图标与当前 FTBQ 主题按钮；也支持 Markdown 链接。退出演示返回原任务书，编辑器预览中点击不会执行跳转。
- 修正 Markdown 链接的文字命中范围，避免空的父文本节点遮住链接点击。
- Ponder 保持独立可选安装；未安装时仍能编辑、保存任务，点击显示中文说明。旧 `ponder` 组件的物品 ID 用法继续生效，任务、团队和进度格式不变。
- Ponder Legacy 1.5.0 支持跳转动画和静态图文；1.4.1 可打开动画。具体格式与安装说明见[思索教程链接](#思索教程链接)。

### modern.8 奖励开箱动画

- 手动领取随机奖励、战利品奖励，以及右键开启战利品箱时，展示原生 Minecraft 宝箱模型开盖、奖励滚动减速和结果页；界面沿用当前 FTBQ 主题、字体、按钮及物品槽，资源包可替换原生宝箱纹理。
- 抽取和发奖由服务器按原权重完成，动画只展示结果。点击“跳过动画”、按 Esc / Enter / 空格会直接揭晓；完成或关闭返回原界面，关闭动画不影响奖励发放。动画期间集成服务器继续运行。
- Shift 批量开箱共用一次动画并汇总同类物品，保留真实 NBT 和数量。经验、命令等奖励也能显示；空奖及 `only_one` 不产生虚假的物品结果。
- 自动领取继续使用原通知方式。若结果到达时正在操作其他 Mod 的界面或背包，会等待返回游戏或任务书后展示；断开服务器清理队列。
- 任务书右下角显示设置新增“奖励开箱动画”和“奖励开箱音效”，也可编辑 `config/ftbquests/client.cfg`。关闭动画时直接显示结果页。
- 修正旧物品奖励通知将剩余数量 `0` 当成实际发放数量的问题；不修改任务、奖励权重、任务进度或领奖数据格式。

### modern.7 视频进度条

- 正文视频下方显示当前时间、总时长和进度条。本地导入、HTTP(S) 视频直链、可定位的 B 站游客流使用相同控件。
- 点击进度条跳转；按住拖动时预览目标时间，松开鼠标后提交一次跳转，保持原来的播放或暂停状态。拖出视频区域后松开也会钳制到首尾；关闭界面取消未完成的拖动。
- 未就绪、时长未知或不支持定位时，进度条禁用，未知总时长显示 `--:--`。播放 / 暂停、重播和 B 站重试 / 网页改为图标，左侧播放、中间时间、右侧辅助操作；悬停显示中文说明，支持按下反馈与移出取消。
- 配套 Media API 1.0.2 增加当前位置、总时长与可定位状态接口。正文自动尺寸、旧任务 JSON 和媒体原路径继续沿用。

### modern.6 正文媒体自动尺寸

- 图片、Markdown 图片和视频的宽高留空时，自动占满正文或预览的可用宽度，按原始比例计算高度；仅填写宽度时也会自动计算高度。
- 横图、竖图及非 16:9 视频分别使用自己的比例。加载前临时使用封面比例或 16:9，取得真实尺寸后重排下方正文及滚动范围；较长图片可继续向下滚动。
- 编辑器预览会随宽窄布局调整。宽高字段提示自动模式；已经明确填写的尺寸继续生效，不会把自动计算出的像素值写回任务数据。
- 配套 Media API 更新至 1.0.1，新增原始视频宽高接口。

当前客户端和服务端任务系统使用 **modern.16**。Media API 主要安装在客户端，专服可省略。用法见 [Media API](https://github.com/mcyudream/ftb-quests-legacy#安装)。

### modern.5 独立媒体 API Mod

- 新增独立 `MediaAPI-1.0.0.jar`，不依赖任务系统或 FTB Library。其他 Mod 可复用视频播放器、OpenGL 纹理、播放/暂停/跳转、B 站游客源解析及诊断界面。
- WATERMeDIA 1.3.43 官方 JAR 原样内置。首次播放后台释放并校验，不需要另行下载；缺失或损坏的缓存会从内置副本恢复。
- 任务系统通过可选依赖调用媒体 API；未安装时仍可使用任务功能，视频处显示中文安装说明。缺少原生 VLC 时显示独立诊断，点击可查看说明。
- 原有任务数据及视频路径保留；已经安装在旧位置的固定版本库也可校验复用。

该阶段引入独立 Media API Mod；当前安装版本见上表。

### modern.4 媒体导入、选择器与 B 站链接

- 正文编辑器的“图片 / 视频”表单及画布媒体资源属性增加“选择文件”，在游戏内浏览文件夹或粘贴完整路径。图片复制、校验和分片传输在后台进行；视频从 modern.12 起只复制到客户端。完成后自动填入 `media:` 路径。
- 正文“任务链接”提供任务搜索；任务右键菜单提供“搜索并添加 / 移除前置任务”。可以按任务名、章节名、8 位任务 ID 搜索，原有依赖循环检查继续生效。
- 正文“物品模型”打开现有物品选择器，按本地化物品名搜索；自动保留物品 ID、Damage 与 tag NBT，模型大小可设为 8–256。
- 视频资源路径可直接粘贴普通 B 站 BV / av 链接、BV / av 号或 b23.tv 短链，支持分 P 与起播秒数。任务中保存原始链接，播放时临时解析并使用内存缓冲；B 站视频不导入本地媒体目录。
- B 站普通公开视频已在关闭代理的正式 Cleanroom 0.4.0 客户端通过实播、暂停和重试验收。描述视频提供“播放 / 暂停、重试、网页”入口；登录、付费、地区限制及无法取得游客流的视频可转网页播放。

配套依赖与当前交付版本以上表为准。详细路径、备份与视频后端要求见[图片和视频](#图片和视频)。

### modern.3 正文编辑与本地化

- 任务右键菜单的“编辑文本”直接打开游戏内编辑器，包含标题、副标题与多行正文；不再依赖游戏外 Swing 窗口。
- 原版文本与 Markdown 两种模式。旧正文默认保持原版模式；没有正文的任务默认使用 Markdown。可在顶部主动切换。
- 宽界面并排显示源码与实时预览，窄界面使用“正文源码 / 实时预览”标签。预览中的链接不会执行命令或脚本动作。
- 提供标题、加粗、斜体、列表、链接、图片、代码块和表格按钮，原有 JSON 组件也有游戏内表单。支持正文选择、剪贴板、图片粘贴与本地撤销/重做。
- `Ctrl+S` 提交并继续编辑，`Ctrl+Z/Y` 撤销/重做正文，`Ctrl+B/I` 插入加粗/斜体；“保存并关闭”返回任务书。Esc 或取消可选择保存、放弃草稿或继续。
- 补全 `ftbquests.gui.edit_lines`、依赖连线选项及编辑器的中英文标签，并补充旧界面缺失的中文标签。
- 打开、预览和取消均不修改任务；原样保存保留旧正文列表、空行、格式码及未知组件。新 Markdown 仍保存在原任务 `text` 列表内，不改任务 ID、进度与领奖格式。

### 已包含的 modern.2 操作响应修复

- 移动任务立即更新本地位置，服务端确认后校准；连续编辑时跳过过期回包，相同内容的确认包不再重复刷新界面。
- 属性编辑只清理所属对象的缓存。JEI / HEI 按任务更新索引，同一 tick 的重复修改合并处理，无关任务索引保持不变。
- 修改章节时保留当前详情、选择、画布位置和详情滚动位置，任务按钮复用原控件。
- 编辑模式采用即时缩放，详情和章节面板停止位移/缩放动画，保持点击区域与显示位置一致；浏览模式仍使用显示设置中的动画选项。

modern.2 引入编辑同步协议变更，modern.4 追加媒体传输包。任务保存格式及事务备份方式沿用上一版。

## 旧数据兼容

继续读取原有 `config/ftbquests/normal/` 任务书及世界 `data/ftb_lib/` 团队数据，保留旧版 **32 位任务 ID、8 位十六进制 ID、Nova int 团队 UID、long 进度和领奖记录**。不会重新编号旧任务。未知字段、未安装附属的任务/奖励、未解析引用和孤立进度保留到后续保存中。

这不是现代 64 位 ID 任务书的直接导入器。客户端与服务器应使用同一套重制版；新增字段和类型不能依靠旧版程序完整保存。回退时恢复升级前的完整任务书、配置及世界数据。

首次替换前停服并保留整个实例备份。测试始终使用复制品，没有直接改动现有“新星工程”实例。已验证一份旧任务书（22 章、621 个任务、772 个子任务、166 个奖励）的保存往返；未取得实际旧世界团队进度，因此该部分使用构造数据验证，不能替代特定世界的升级演练。

### 自动备份与恢复

任务书整目录保存采用暂存、读回校验、事务日志和备份；FTB Library 与任务进度文件采用原子替换及备份。坏数据、加载不完整、循环引用和 UID 冲突会拒绝加载/覆盖，避免把故障输入当空数据保存。

- 任务书备份位于 `config/ftbquests/.backups/normal-<UUID>/`。
- modern.4 文件选择器导入的媒体位于同级 `config/ftbquests/media/`。它不在 `normal/` 事务快照内；备份、搬服或发布整合包时必须一并保留。复制后不再依赖原始文件所在的位置。尚未应用的草稿也可能留下已导入文件，本版不会自动删除它们。
- 原子 NBT 文件的备份位于该文件父目录的 `.backups/<文件名>-<UUID>`。
- `.<目录名>.stage-<UUID>` 和 `.<目录名>.transaction` 用于中断恢复；启动时检查待完成事务。
- 备份不会自动删除。手动恢复须先停服，另存当前故障目录，再恢复相同时间点的任务书与世界数据。保留事务文件供故障排查，避免在运行中混合不同时间点的数据。

## 已整合的功能

完整对应及验证范围见 [特性说明](docs/FEATURES.md)。

- default / linear / flexible 进度、顺序子任务、可选/隐藏子任务、秘密任务、最少子任务数量、互斥任务、章节任务前置。
- 放置/破坏/使用方块、到达天数、计时、玩家 NBT、高级击杀、结构模板检测与结构预览。
- 全表奖励、三种重复奖励、团队领奖、命令权限等级、静默与模板字段。
- 图片/文字/辅助点/连线/实体/视频画布，背景图片、独立详情背景、PNG/GIF/APNG、剪贴板图片与大包分片。
- 多选复制、批量编辑、64 步共享撤销/重做、保留引用的重新编号副本。
- 描述中的标题、表格、图片、视频、实体、物品配方、字体、玩家数据及可点击 ZS 动作。
- 分批建立物品搜索缓存、悬停辉光、面板动画、平滑缩放和自定义 shader 背景。

### 快捷键和编辑入口

先通过原有编辑模式权限进入编辑。画布右键菜单可添加元素；元素右键打开属性。选中两个节点后使用“连接选中节点”。锁定元素不会参加移动操作。

| 操作 | 快捷键 |
| --- | --- |
| 复制 / 粘贴选中对象 | Ctrl+C / Ctrl+V |
| 粘贴系统剪贴板图片 | Ctrl+V（剪贴板包含图片时） |
| 批量编辑 | Ctrl+E |
| 撤销 / 重做 | Ctrl+Z / Ctrl+Y 或 Ctrl+Shift+Z |
| 全选当前章任务 / 清空选择 | Ctrl+A / Ctrl+D |
| 移动选中对象 | Ctrl+方向键；Shift 微调 |
| 切换章节 | Tab；Shift+Tab 反向 |
| 搜索任务 | 连按两次 Shift |
| 视图复位 / 切换网格 | Space / R |

撤销历史由服务器共享；其他编辑导致快照过期时会拒绝覆盖。创建、删除或复制等结构变更会清除已有属性撤销历史。复制使用新 ID，不复制原进度和领奖状态。

右下角“显示设置”可调整动画、缓动、辉光和 shader；设置保存在客户端 `config/ftbquests/client.cfg`。关闭缓动时动画改用线性曲线，各类动画另有独立开关。

侧边栏使用独立 `sidebarAnimationMilliseconds`，默认 **350**，范围 30～1500 ms；控制导航图标、辉光及章节浮层。详情使用独立 `detailAnimationMilliseconds=320`；通用 `smoothingMilliseconds` 控制悬停和缩放，新配置默认 220 ms，已有配置值保留。编辑模式的即时画布操作不等待侧边栏动画。

## ZenScript

安装 CraftTweaker **4.1.20.698** 后，将 `.zs` 放在实例 `scripts/`。接口说明和可复制示例见 [ZenScript API](docs/ZENSCRIPT.md)。使用服务端玩家事件读取和编辑已加载任务；在脚本顶层注册检测、奖励、完成和文字点击回调。

## 描述组件

保留原来的纯文本、翻译占位符与 vanilla text component JSON。每个 JSON 组件独占一行；`# 标题` 至 `###### 标题` 也能直接使用。

### Markdown 模式

“编辑文本”顶部切换到 Markdown 后，可直接输入以下正文并预览：

````md
# 自动化说明
使用 **加粗**、*斜体*、~~删除线~~ 和 `行内代码`。

- 准备材料
- 搭建机器
> 请先完成前置任务。

[查看相关任务](quest:12345678)
[执行已注册的 ZS 动作](zs:guide/open)
![流程图](asset:example.png)

| 输入 | 输出 |
| --- | --- |
| 铁矿石 | 铁锭 |

```zs
  print("保持缩进的代码示例");
```
````

支持六级标题、`- / + / *` 无序列表、数字列表、引用、分隔线、链接、独占一行的图片、表格、行内代码与围栏代码块；这是面向任务正文的 Markdown 子集，不包含 HTML、嵌套列表排版或完整 CommonMark。代码块不展开翻译、玩家变量或 Markdown。JSON 组件可继续独占一行混排，`\` 可转义 Markdown 标记。

Markdown 按 `{"type":"markdown","text":"正文\n第二行"}` 组件保存在原有正文列表中。重新打开会显示可编辑的 Markdown 源码，不要求手写 JSON；修改此组件时保留其未知扩展字段。单纯打开旧正文不会自动转换。原版模式的 `**`、反引号等文字继续按原有规则显示。

```json
{"type":"heading","text":"自动化说明"}
{"type":"image","source":"example:textures/quests/demo.png","width":160,"height":90}
{"type":"video","source":"local:demo.mp4","cover":"example:textures/quests/cover.png","height":90}
{"type":"entity","source":"minecraft:pig","nbt":"{}","width":64,"height":64}
{"type":"table","rows":[["输入","输出"],["铁矿石","铁锭"]]}
{"type":"item","item":"minecraft:iron_ingot","text":"查看配方"}
{"type":"item","item":"minecraft:chest","nbt":"{Damage:0s}","size":32,"text":"箱子模型"}
{"type":"font","font":"minecraft:textures/font/ascii.png","text":"Custom font","scale":1.0}
{"type":"quest","value":"12345678","text":"打开相关任务"}
{"type":"copy","value":"example","text":"复制文本"}
{"type":"link","value":"https://example.com/guide","text":"说明网页"}
{"type":"ponder","value":"minecraft:compass","text":"查看思索演示"}
{"type":"zs","value":"guide/open","text":"执行脚本动作","hover":"点击继续"}
{"type":"keybind","value":"key.jump"}
{"type":"localize","value":"item.diamond.name"}
```

普通文字可写 `${persisted:字段.子字段}` / `${player:字段}`。服务器只向当前玩家同步任务描述实际请求的字段，每次最多 32 个，每字段 512 字符，约 1 秒更新。`command` 类型的 value 是玩家要执行的命令，按其现有权限执行；需要任务奖励权限时使用命令奖励。

物品组件左键查看配方、右键查看用途，通过 JEI / HEI 适配。有 `size` 字段时绘制指定大小的游戏物品模型；旧组件继续使用原来的图标按钮。章节说明也支持这些描述组件。字体采用 1.12 bitmap 字体纹理及 Unicode 回退。

### 图片和视频

正文工具栏选择“图片 / 视频”，再点资源路径旁的“选择文件”；封面字段也能选择图片。画布媒体属性的资源路径使用同一个导入入口。文件选择框提供上级、用户目录、游戏目录、格式过滤和完整路径输入，支持中文文件名。

- 图片最多 8 MiB / 16 MP；PNG、GIF、APNG 保留原字节，JPEG / BMP 转为 PNG。动画解码有 512 帧和累计像素预算。
- 本地视频最多 256 MiB；支持 MP4 / M4V / MOV / MKV / WEBM / AVI / OGV / MPEG / MPG 容器，实际编解码支持取决于 VLC。
- 视频选择后复制到客户端 `config/ftbquests/media/`，填写 `media:<SHA256>.<扩展名>`，原文件保留。整合包必须向客户端分发这些文件；服务器不接收或发送视频。已有旧本地缓存只读复用。
- 图片仍保存在服务器 `config/ftbquests/media/`，客户端按需分片取得到 `.media-cache/`，哈希校验后发布。
- 图片只有任务编辑者可以上传；本地视频选择不上传；可取消，错误和超时显示在表单内，导入期间不能应用未完成的路径。内容哈希去重；每人最多两项传输，磁盘暂存有配额。媒体目录放在任务书事务外，避免编辑任务时重复复制大视频。
- 原有资源包路径、HTTP(S) URL、`asset:`、`local:` 继续支持。剪贴板上传的旧 `asset:` 图片仍放在 `normal/assets/`；它们不会自动迁移为 `media:`。

搬服、发布或备份需要同时保留 `normal/`、`media/` 和世界团队数据；仅复制 `normal/` 不包含新导入的媒体。图片客户端缓存可重新获取；视频必须随整合包放在每个客户端的 `media/` 或 `videos/`，缺失时不会自动同步。

视频后端改由可选 **Media API Mod** 提供，将 `MediaAPI-1.0.4.jar` 放到客户端 `mods/` 即可。它已原样内置 WATERMeDIA **1.3.43**，首次播放在后台释放到：

```text
config/media_api/libraries/watermedia-1.3.43.jar
```

运行库 SHA256：`ebe530f52e51d8a24867a98e3b22b5278c018c6b356f7209eb071673084e66e4`。该缓存可重新生成。旧 `config/ftbquests/video-libs/watermedia-1.3.43.jar` 如存在则可校验复用，不需要迁移任务文件。不要把现代 WATERMeDIA Forge JAR 单独装进 1.12.2 `mods/`。

仍需可加载的原生 VLC / libVLC；Linux 使用与 Java 架构匹配的系统原生包。Mod 列表 → Media API → 配置，以及视频区域的错误入口，提供中文诊断和重新检测。正式 0.4.0 的实际解码验收平台为 Linux x64；其他操作系统的原生后端未验收。内置库来源与原许可见 Media API 工程的 `THIRD-PARTY.md`。

`local:demo.mp4` 对应 `config/ftbquests/videos/demo.mp4`；也支持资源包 `example:videos/demo.mp4` 和 HTTP(S)。描述视频下方显示进度条和时间，可点击或拖动跳转，暂停时跳转保持暂停；下方按钮用于播放/暂停、重播；画布视频左键播放/暂停，玩家模式下右键重播。无后端时仍能正常打开任务书，视频区域显示中文状态及封面，可点击配置视频支持。

#### B 站在线播放

在视频表单的“资源路径”直接粘贴链接，不需要点击文件导入，例如：

```json
{"type":"video","source":"https://www.bilibili.com/video/BV1B7411m7LV?p=2&t=35","width":240,"height":135}
```

普通 BV / av、裸 BV / av 号、b23.tv 短链和带 `p` / `t` 的外链播放器地址可以解析。`p` 从 1 开始，`t` 为整数秒；直播和番剧链接暂不支持。参数说明参考 [B 站官方外链播放器文档](https://player.bilibili.com/)。

每个客户端在播放时请求 B 站元数据和临时流地址，任务文件只保留用户填写的原始链接。客户端使用带 Referer 的直连 HTTP 输入、VLC 解码和内存缓冲，不创建 B 站视频文件、不写入 `.media-cache` 或 `.video-cache`；仍需上述可选视频后端。普通 HTTP(S) 视频直链也可在线播放，抖音分享链接另有公开页面解析，其他网站的普通网页不会自动解析为视频。

目前只请求无需登录的游客流，不读取账号或 Cookie。接口策略、地域、登录和付费限制均可能使解析失败；多段旧流不会仅播放首段，而是提示转网页。描述控件的“重试”会重新获取临时地址；“网页”打开系统浏览器中的 B 站播放器或短链。

在继承代理的测试进程中曾出现 TLS EOF；显式清空代理环境、关闭 JVM 系统代理后，API、CDN 和游戏内视频解码均已通过，实际暂停与重试可用。播放前后任务媒体目录与缓存文件一致。测试仅覆盖一个普通公开视频、Linux x64 和固定 WATERMeDIA 1.3.43 / libVLC 组合，不代表所有视频、地区、账号或代理路径均可用。该段为旧版验收历史。modern.12 的元数据与视频内容使用显式直连输入，不再依赖清空全局代理。详情见[验收报告](verification/REPORT.md)。

#### 抖音在线播放

```json
{"type":"video","source":"https://v.douyin.com/IVOk7RWtfKU/"}
```

可粘贴 `v.douyin.com` 短链、`www.douyin.com/video/<视频号>`、`iesdouyin.com/share/video/<视频号>` 或包含这些链接的分享文本。任务保存原始来源，播放时只解析临时流地址；宽高留空沿用正文等比自动铺满。公开网页策略、地域、TLS 或风控均可能阻止解析；私密、付费、图集、直播和需要登录的内容不作为可播放视频处理。

Media API 1.0.4 通过平台公开接口取得匿名访问票据，再从分享页读取视频数据；路由对象和统计对象即使包含相同视频 ID，也不会遮蔽实际视频。用户短链 `7704984500145390912` 的实际视频帧、31 秒原始流时间轴、正文暂停、跳转与重试播放已在正式 Cleanroom 0.4.0 / Linux x86_64 中通过。匿名注册、元数据和视频流均客户端直连，不经过游戏服务器，不生成视频缓存，平台返回的播放地址保持原样。

票据只保存在内存中，30 分钟后自动重新申请；不会读取浏览器或账号 Cookie，也不依赖第三方解析服务。任务文件继续保存原始短链。平台接口、地域或公开权限改变时仍可能失败，保留重试和“网页”入口。公开嵌入地址格式参考[抖音官方嵌入播放文档](https://developer.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/iframe-player/get-iframe-by-video)。本模组未申请或内置开放平台密钥。

### 思索教程链接

独立安装本仓库 **Ponder Legacy**（Mod ID `ponder-legacy`）。客户端要有对应的教程与结构；仅显示本地教程时服务器可不安装 Ponder。若服务器也安装，建议两端使用相同版本。Ponder 1.5.0 的 JSON、Java 和 Markdown 教程无需 CraftTweaker，只有 ZS 教程需要 CraftTweaker。

在任务的“编辑文本”中切换为 **Markdown 模式**，点击工具栏“思索演示”即可打开搜索选择器，点击教程后自动插入链接。先选中文字可指定链接标题；未选择时使用教程名称。取消不改草稿；Ctrl+Z / Ctrl+Y 撤销、重做插入。

原版文本模式，或“组件”菜单中的“思索演示”，会打开组件表单，再点击“搜索教程”。列表提供“全部演示”及具体场景／图文页，选中后填入目标与标题，也可以手动填写：

```json
{"type":"ponder","value":"minecraft:compass","text":"查看思索演示"}
{"type":"ponder","value":"minecraft:compass#mypack:first_machine","text":"搭建第一台机器"}
```

Markdown 模式同样可以写：

```markdown
[全部演示](ponder:minecraft:compass)
[搭建第一台机器](ponder:minecraft:compass#mypack:first_machine)
```

`#` 前为入口物品的完整注册名，后为属于该物品的场景／教程 ID。旧链接中省略物品命名空间的写法继续按 `minecraft` 处理。未指定教程时保留全部场景的导航；指定后定位到对应内容。1.5.0 遵循玩家的动画／图文默认展示，只有一种展示的教程使用其可用形式；关闭按钮或 Esc 返回任务书。

1.4.1 没有公开的定位接口，指定教程时单独打开该动画；选择“全部演示”可浏览该物品的完整场景列表。

这些例子只演示链接格式，不会自动创建、下载教程。教程需先通过 Ponder 注册，例如分发 `scripts/ponder/guides/`、`packs/` 和 `structures/`；可参考 Ponder 自带文档。缺少模组、教程或目标格式有误时显示本地化提示。旧正文中的其他行、未知组件及额外字段继续保留。

### 结构任务

使用 1.12 结构方块的压缩 NBT 模板，放在 `config/ftbquests/normal/structures/<名称>.nbt`，或兼容目录 `Quests_structures/<名称>.nbt`。模板可直接存入任务的 `template` 字段。上限 4096 个方块，支持空气检查和方块实体 NBT 子集匹配。

点击结构任务开启预览，右键锁定原点，再次提交完成检测。服务器会重新检查维度、加载区块、128 格距离和实际方块，客户端预览不能代替服务端判定。`custom_picture` 使用资源包 `questsadditions:textures/structure_icons/<名称>.png`。

## 构建与验证

依赖锁记录了 17 个可选编译依赖的 URL 和 SHA256；编译依赖不会全部成为运行时必装项。旧 Nova 集成需要的编译接口保留。

Ponder 适配层另外使用 `ponder-compat.lock.json` 固定的 MCP SDK：将 `Ponder-Legacy-1.5.0-mcp.jar` 放在仓库 `.dev/ftb-quests-deps/`（或 `-PftbDepsDir` 指定目录），构建会核对其 SHA256。SDK 来自相邻 Ponder 工程对应版本的构建，不是运行时前置，也不会内置进任务 JAR。

```bash
python3 tools/fetch_dependencies.py
JAVA_HOME=/path/to/jdk-25 ./gradlew :media-api:test :media-api:build :media-api:reobfAcceptanceJar test build reobfAcceptanceJar --console=plain
```

上述命令同时构建相邻 `ftb-library`、`ftb-utilities` 和 `media-api` 工程。安装产物位于各自 `build/libs/`。SDK 用于编译；正式 0.4.0 的运行验收使用仓库 `tools/verify_ae2_addons.py` 安装器环境。验收源代码在 `verification/`，独立打包，不混入产品 JAR。

最终结果、SHA256 和未覆盖范围见 [验收报告](verification/REPORT.md)。

## 上游与归属

精确基线见 [upstream-lock.json](upstream-lock.json)：

- [Nova FTB Quests](https://github.com/NovaEngineering-Source/FTB-Quests-NovaEng)
- [Nova FTB Library](https://github.com/NovaEngineering-Source/FTB-Library-NovaEng)
- [Nova FTB Utilities](https://github.com/NovaEngineering-Source/FTB-Utilities-NovaEng)
- [FTB Quests 官方高版本](https://github.com/FTBTeam/FTB-Quests)

附属对应：[Quest Enhance](https://www.mcmod.cn/class/28696.html)、[Freeze Fix](https://www.mcmod.cn/class/21635.html)、[Quests Additions](https://www.mcmod.cn/class/5709.html)、[Certain Questing Additions](https://www.mcmod.cn/class/22780.html)。保留原作者信息；本工程未为上游代码擅自追加新的许可证。Certain Questing Additions 的 ARR shader 资源未复制，本版提供自行实现的 GLSL 1.20 背景。
