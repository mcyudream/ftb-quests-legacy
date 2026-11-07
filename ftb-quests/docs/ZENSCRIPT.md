# CraftTweaker / ZenScript API

可选依赖：CraftTweaker 4.1.20.698。脚本放在实例 `scripts/*.zs`，使用默认 `crafttweaker` loader。

```zenscript
import mods.ftbquests.Quests;
import crafttweaker.player.IPlayer;
```

所有对象 ID 都是 **8 位 hex 字符串**；团队 UID 返回 Java int。`tag:标签` 用于注册回调和批量查询，具体读取/修改接口使用对象 ID。

## 查询、创建与编辑

这些方法应在服务端的玩家/世界事件内调用。启动脚本顶层执行时任务书可能尚未加载；`exists` 在客户端或未加载时返回 false。

| 方法 | 返回值 / 说明 |
| --- | --- |
| `exists(IWorld world, string id)` | bool，检查对象是否存在 |
| `findObjects(IWorld world, string selector)` | string[]，按 ID 或 `tag:name` 查询，结果按 ID 排序 |
| `createQuest(IWorld world, string chapterID, double x, double y, IData definition)` | 新任务 ID；内含子任务、奖励均分配新 ID |
| `editObject(IWorld world, string id, IData patch)` | 合并属性补丁，保存并向在线客户端同步 |
| `getObjectData(IWorld world, string id)` | IData，定义快照 |
| `isComplete(IPlayer player, string id)` | bool，该玩家团队是否完成对象 |
| `getProgress(IPlayer player, string taskID)` | long，子任务绝对进度 |
| `setProgress(IPlayer player, string taskID, long value)` | 设置绝对进度，仍受前置和顺序任务限制 |
| `getTeamUID(IPlayer player)` | int，Nova 团队 UID |

属性名使用原 SNBT 字段名。`editObject` 不接受 `uid`、`type`、`tasks`、`rewards` 的结构替换；新增子任务/奖励通过 `createQuest` 完整定义。引用仍按旧 32 位 ID 表示。定义变更会持久化；重复加载脚本前需查询标签，防止再次创建。

```zenscript
events.onPlayerTick(function(event as crafttweaker.event.PlayerTickEvent) {
    val world = event.player.world;
    // 换成现有章节的真实 8 位 ID。
    if (!Quests.exists(world, "12345678")) return;
    if (Quests.findObjects(world, "tag:script_created").length != 0) return;
    val id = Quests.createQuest(world, "12345678", 4.0, 2.0, {
        title: "脚本创建的任务", tags: ["script_created"],
        tasks: [{type: "custom", tags: ["sample_check"]}],
        rewards: [{type: "custom", tags: ["sample_reward"]}]
    });
    Quests.editObject(world, id, {title: "新的标题"});
});
```

## 自定义检测、奖励与完成

脚本顶层注册处理器，任务书加载或重新读取后自动重绑。精确 ID 的处理器优先；多个标签都命中时按标签字母顺序使用第一个。相同 selector 再次注册会替换之前的处理器。

```zenscript
// 每 20 tick 检查一次；返回绝对进度，最大值 7。
Quests.customTask("tag:sample_check", function(player as IPlayer, progress as long, maximum as long) as long {
    return progress + 1;
}, 20, 7);

Quests.customReward("tag:sample_reward", function(player as IPlayer) {
    player.give(<minecraft:gold_ingot> * 2);
});

Quests.onCompleted("tag:script_created", function(player as IPlayer, id as string, teamUID as int) {
    print(player.name + " completed " + id);
});
```

- `customTask(selector, IQuestTaskCheck check, int intervalTicks, long maximum)`：只绑定 `custom` 子任务；间隔 1..1200 tick，最大值须大于 0。未绑定时不自动完成。错误回调记录一次，失败调用不推进任务。
- `customReward(selector, IQuestReward callback)`：绑定 `custom` 奖励；领取仍走服务端既有领取记录和重复包检查。
- `onCompleted(selector, IQuestCompleted callback)`：每次对象完成事件，向该团队每个在线成员调用一次。重复任务重新完成会再次触发；离线成员不补发该回调。
- `clearHandlers()`：清除所有 ZS 处理器。默认 loader 重新开始加载时也会清空，避免 reload 叠加；加载完成后重绑现有自定义任务。

回调运行在服务端线程，不应做磁盘下载或阻塞等待。

## 文字点击事件

modern.3 的 Markdown 模式也可写 `[打开说明](zs:guide/open)`。注册方式与下方 JSON 组件相同。服务端只允许原始正文中声明并实际显示为链接的动作；代码块和行内代码中的链接不构成声明。编辑器预览不会执行动作，保存后在任务详情中点击。

在任务描述或章节说明中加入独立 JSON 行：

```json
{"type":"zs","text":"打开说明","value":"guide/open","hover":"运行 ZenScript 动作"}
```

画布元素的 `click` 也可填写 `zs:guide/open`。注册：

```zenscript
Quests.onTextClick("guide/open", function(player as IPlayer, action as string, sourceID as string, teamUID as int) {
    print(player.name + " clicked " + sourceID + ": " + action);
});
```

动作名允许 1..128 个字母、数字、`_ . : / -`。服务端核对对象可见性、描述隐藏条件，以及源对象确实声明此动作；客户端不能仅发送任意名称调用未声明的动作。每玩家最多每 250 ms 接受一次有效点击。

点击回调可以再次触发；一次性物品奖励应放在 `customReward` 中，由任务领奖记录控制。文本动作中的业务权限由脚本依据玩家和 `sourceID` 决定。

本工程实际运行的完整示例：[ftbquests_acceptance.zs](../verification/scripts/ftbquests_acceptance.zs)。该脚本包含验收专用章节 ID，不直接用于现有任务书。
