package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.icon.ImageIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.entity.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;

public final class EntityModelIcon extends Icon
{
    private final String id, nbt;
    private net.minecraft.world.World world;
    private Entity entity; private boolean failed;
    public EntityModelIcon(String id,String nbt){this.id=id;this.nbt=nbt;}
    @Override public void draw(int x,int y,int width,int height)
    {
        Minecraft mc=Minecraft.getMinecraft();
        if(world!=mc.world) failed=false;
        if(mc.world==null || failed){new ImageIcon(ImageIcon.MISSING_IMAGE).draw(x,y,width,height);return;}
        if(world!=mc.world)
        {
            world=mc.world;
            try{NBTTagCompound tag=JsonToNBT.getTagFromJson(nbt.isEmpty()?"{}":nbt);tag.setString("id",id);entity=EntityList.createEntityFromNBT(tag,world);if(entity==null)failed=true;}
            catch(Exception ex){failed=true;}
        }
        if(entity==null)return;
        var manager=mc.getRenderManager();float previousView=manager.playerViewY;
        boolean shadow=manager.isRenderShadow(),depth=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST),lighting=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_LIGHTING);
        boolean rescale=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL),material=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_COLOR_MATERIAL);
        int texture=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        float brightnessX=OpenGlHelper.lastBrightnessX,brightnessY=OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        try
        {
            GlStateManager.enableColorMaterial();RenderHelper.enableStandardItemLighting();GlStateManager.enableDepth();
            GlStateManager.translate(x+width/2D,y+height*0.93D,60);float scale=(float)(Math.min(width/Math.max(0.7,entity.width),height/Math.max(0.7,entity.height))*0.85);
            GlStateManager.scale(scale,-scale,scale);GlStateManager.rotate(30,0,1,0);GlStateManager.rotate(-8,1,0,0);
            manager.playerViewY=180;manager.setRenderShadow(false);manager.renderEntity(entity,0,0,0,0,1,false);
        }
        catch(Exception ex){failed=true;com.feed_the_beast.ftbquests.FTBQuests.LOGGER.warn("Could not render quest entity "+id,ex);}
        finally
        {
            manager.playerViewY=previousView;manager.setRenderShadow(shadow);GlStateManager.popMatrix();RenderHelper.disableStandardItemLighting();
            if(rescale)GlStateManager.enableRescaleNormal();else GlStateManager.disableRescaleNormal();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(material)GlStateManager.enableColorMaterial();else GlStateManager.disableColorMaterial();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,brightnessX,brightnessY);GlStateManager.setActiveTexture(texture);GlStateManager.color(1,1,1,1);
        }
    }
}
