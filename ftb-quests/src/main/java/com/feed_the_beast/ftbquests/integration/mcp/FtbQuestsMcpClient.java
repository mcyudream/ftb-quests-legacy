package com.feed_the_beast.ftbquests.integration.mcp;

import com.google.gson.JsonObject;
import dev.yudream.mcp.api.*;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import net.minecraft.client.Minecraft;

/** Client-only book opening; no client symbols are referenced by the common adapter. */
final class FtbQuestsMcpClient {
    static void addTools(McpModule.Builder module) {
        module.tool(new McpTool("open", "Open the client's quest book. Authoring operations use the local server module and normal editor sync.", McpSchema.object().build(), McpTool.Thread.CLIENT, false, a -> {
            if (ClientQuestFile.INSTANCE == null || Minecraft.getMinecraft().player == null) throw new IllegalStateException("No quest book is loaded");
            ClientQuestFile.INSTANCE.openQuestGui(Minecraft.getMinecraft().player); JsonObject result = new JsonObject(); result.addProperty("opened", true); return McpResult.json(result);
        }));
    }
}
