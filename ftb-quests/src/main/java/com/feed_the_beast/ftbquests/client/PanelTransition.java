package com.feed_the_beast.ftbquests.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.nio.IntBuffer;

/** Composite a panel after its normal layout and scissoring, so text/media clip correctly. */
public final class PanelTransition
{
    private Framebuffer buffer;
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);

    public static boolean supported()
    { return OpenGlHelper.isFramebufferEnabled() && GLContext.getCapabilities().OpenGL20; }

    public void draw(Runnable content, double opacity, double scale, double centerX, double centerY, double dx, double dy)
    {
        if (!supported() || opacity >= .9995 && scale == 1 && dx == 0 && dy == 0)
        {
            content.run();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int target = GL11.glGetInteger(0x8CA6); // GL_FRAMEBUFFER_BINDING, including ARB/EXT implementations.
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int equationRGB = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        try
        {
            if (buffer == null || buffer.framebufferWidth != mc.displayWidth || buffer.framebufferHeight != mc.displayHeight)
            {
                close();
                buffer = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
                buffer.setFramebufferColor(0, 0, 0, 0);
                buffer.setFramebufferFilter(GL11.GL_LINEAR);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            buffer.framebufferClear();
            buffer.bindFramebuffer(false);
            GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            // Vanilla font/item draws replace destination alpha. Preserve the opaque panel
            // foundation under those draws to prevent holes around antialiased glyphs.
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_MAX);
            content.run();
        }
        finally
        {
            GL20.glBlendEquationSeparate(equationRGB, equationAlpha);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target);
            GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        GlStateManager.pushMatrix();
        try
        {
            GlStateManager.translate(centerX + dx, centerY + dy, 0);
            GlStateManager.scale(scale, scale, 1);
            GlStateManager.translate(-centerX, -centerY, 0);
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float a = (float)Math.max(0, Math.min(1, opacity));
            GlStateManager.color(a, a, a, a); // Captured RGB is premultiplied.
            buffer.bindFramebufferTexture();
            double w = resolution.getScaledWidth_double(), h = resolution.getScaledHeight_double();
            double u = buffer.framebufferWidth / (double)buffer.framebufferTextureWidth;
            double v = buffer.framebufferHeight / (double)buffer.framebufferTextureHeight;
            BufferBuilder vertices = Tessellator.getInstance().getBuffer();
            vertices.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            vertices.pos(0, h, 0).tex(0, 0).endVertex();
            vertices.pos(w, h, 0).tex(u, 0).endVertex();
            vertices.pos(w, 0, 0).tex(u, v).endVertex();
            vertices.pos(0, 0, 0).tex(0, v).endVertex();
            Tessellator.getInstance().draw();
            buffer.unbindFramebufferTexture();
        }
        finally
        {
            GlStateManager.popMatrix();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        }
    }

    public void close()
    {
        if (buffer != null) { buffer.deleteFramebuffer(); buffer = null; }
    }
}
