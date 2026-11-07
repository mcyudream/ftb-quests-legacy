# 仓库开发约定

本文件适用于本仓库内自行开发、移植和维护的所有 Mod。

## Cleanroom 兼容基线

- 本仓库 Minecraft 1.12.2 / Cleanroom Mod 必须兼容 **Cleanroom 0.4.0（不带 `-alpha`）**（用户简称“CR 4.0”），以用户实际使用的加载器版本为兼容基线。
- 选择依赖、调用 API、声明最低加载器版本和打包时，均须满足上述兼容基线；在较新 Cleanroom 上开发或验证，不能据此提高最低版本要求。
- 必装前置也须一并适配和验证，包括本仓库维护的 GeckoLib 等运行库；仅降低模组元数据中的版本限制不算完成兼容。
- 对外声明兼容前，应在 Cleanroom 0.4.0（不带 `-alpha`）上验证实际打包产物的客户端启动与核心功能，并检查专用服务器启动。`0.4.0-alpha` 和其他 Cleanroom 版本上的验证结果均不能替代该基线验收，不能将二者视为相同版本。

## AE2 支持目标

- 本仓库中所有 Mod 实现的 AE2 支持，均以 **应用能源 2 超巨星（Applied Energistics 2 Supergiant，AE2S）** 为目标。模组介绍与身份以 [MC 百科页面](https://www.mcmod.cn/class/28382.html) 为准。
- 需求、代码注释或文档中提到的“AE2 支持”“AE2 兼容”“AE2 联动”，均按上述 AE2S 实现理解；对外说明兼容性时应明确写出 AE2S / Supergiant。
- AE2S 面向 Minecraft 1.12.2 / Cleanroom，使用 Java 25。依赖选择、API 调用和运行验证应以 AE2S 的实际版本为准。
- AE2S 与原版 AE2 1.12.2、AE2UEL 的 API 和二进制兼容性必须分别核实；支持 AE2S 不代表同时支持这些实现。

## Ponder 玩法示意图建议

- 建议本仓库开发的各 Mod 使用 **Ponder（思索）** 提供游戏内玩法示意图与分步演示，优先覆盖核心机器、结构搭建、输入输出、操作顺序和自动化联动。
- Ponder 接入应采用**软依赖 / 可选兼容**：安装 Ponder 时启用演示；未安装时，Mod 应能正常启动并使用其核心玩法。
- 优先使用本仓库的 [Ponder Cleanroom 实现](1.12.2/ponder/README.md)，接入参考 [Cleanroom 开发手册](1.12.2/ponder/docs/CLEANROOM.md) 和 [Java 插件示例](1.12.2/ponder/examples/addon/)。当前 Mod ID 为 `ponder-legacy`，资源命名空间为 `ponder`。
- 将直接引用 Ponder API 的代码隔离到兼容模块或兼容包，仅在检测到 Ponder 已加载后初始化。使用编译期可选依赖，保持 Ponder 运行库独立安装，模组元数据中的依赖声明也应保持可选。
- 客户端演示与渲染代码应保持客户端隔离，确保专用服务器启动安全。
- 演示应展示可复现的完整操作流程，使用各 Mod 自己的命名空间组织场景、结构和翻译，并随玩法变更同步维护。
- 接入后验证安装与未安装 Ponder 两种情况，并检查专用服务器启动及客户端场景播放。
