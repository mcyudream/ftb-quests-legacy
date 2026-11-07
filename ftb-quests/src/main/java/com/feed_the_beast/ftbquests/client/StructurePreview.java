package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.net.*;
import com.feed_the_beast.ftbquests.quest.task.StructureTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.util.math.*;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid=FTBQuests.MOD_ID,value=Side.CLIENT)
public final class StructurePreview
{
    private static StructureTask active; private static BlockPos locked;
    public static void clear() { active=null;locked=null; }
    public static void select(StructureTask task)
    {
        if(active!=null && active.id==task.id && locked!=null) {new MessageStructurePosition(task.id,locked).sendToServer();new MessageSubmitTask(task.id).sendToServer();active=null;locked=null;}
        else {active=task;locked=null;}
        Minecraft.getMinecraft().displayGuiScreen(null);
    }
    @SubscribeEvent public static void anchor(PlayerInteractEvent.RightClickBlock event)
    {
        if(!event.getWorld().isRemote || active==null || locked!=null) return;
        locked=event.getPos().offset(event.getFace());new MessageStructurePosition(active.id,locked).sendToServer();event.setCanceled(true);
    }
    @SubscribeEvent public static void render(RenderWorldLastEvent event)
    {
        Minecraft mc=Minecraft.getMinecraft();if(active==null || mc.world==null || mc.player==null) return;
        BlockPos origin=locked;
        if(origin==null) {RayTraceResult ray=mc.objectMouseOver;if(ray==null || ray.typeOfHit!=RayTraceResult.Type.BLOCK)return;origin=ray.getBlockPos().offset(ray.sideHit);}
        double t=event.getPartialTicks(),x=mc.player.lastTickPosX+(mc.player.posX-mc.player.lastTickPosX)*t,y=mc.player.lastTickPosY+(mc.player.posY-mc.player.lastTickPosY)*t,z=mc.player.lastTickPosZ+(mc.player.posZ-mc.player.lastTickPosZ)*t;
        GlStateManager.pushMatrix();GlStateManager.translate(-x,-y,-z);GlStateManager.disableLighting();GlStateManager.enableBlend();GlStateManager.depthMask(false);
        try
        {
            for(var entry:active.blocks().entrySet())
            {
                BlockPos pos=origin.add(entry.getKey());if(pos.distanceSq(mc.player.getPosition())>64*64)continue;
                boolean match=mc.world.getBlockState(pos).equals(entry.getValue());
                if (!match && !entry.getValue().getBlock().isAir(entry.getValue(), mc.world, pos))
                {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(pos.getX()+0.05,pos.getY()+0.05,pos.getZ()+0.05);GlStateManager.scale(0.9,0.9,0.9);
                    mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
                    GlStateManager.color(1,1,1,0.45F);
                    mc.getBlockRendererDispatcher().renderBlockBrightness(entry.getValue(),1);
                    GlStateManager.popMatrix();
                }
                GlStateManager.disableTexture2D();
                RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(pos).grow(0.002),match?0.2F:1F,match?1F:0.2F,0.2F,0.65F);
                GlStateManager.enableTexture2D();
            }
        }
        finally {GlStateManager.depthMask(true);GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();}
    }
}
