package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftbquests.FTBQuests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.nio.charset.StandardCharsets;

/** Resource-pack supplied GLSL 1.20 backgrounds; compile failures keep the normal theme usable. */
public final class ShaderBackground
{
    private static int program;
    private static String loaded = "";
    public static void invalidate()
    { if (program != 0) GL20.glDeleteProgram(program); program = 0; loaded = ""; }

    private static int compile(int type, String source)
    {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
        {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalArgumentException(log);
        }
        return shader;
    }

    public static void draw(int x, int y, int width, int height)
    {
        String selected = com.feed_the_beast.ftbquests.quest.theme.QuestTheme.currentObject instanceof com.feed_the_beast.ftbquests.quest.Chapter ? ((com.feed_the_beast.ftbquests.quest.Chapter)com.feed_the_beast.ftbquests.quest.theme.QuestTheme.currentObject).backgroundShader : "";
        if (selected.isEmpty() && !VisualConfig.shaderBackground) return;
        String source = selected.isEmpty() ? VisualConfig.shader : selected;
        if (!loaded.equals(source))
        {
            invalidate(); loaded = source;
            int vertex = 0, fragment = 0, candidate = 0;
            try (var resource = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(loaded)))
            {
                vertex = compile(GL20.GL_VERTEX_SHADER, "#version 120\nvoid main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;}");
                fragment = compile(GL20.GL_FRAGMENT_SHADER, new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                candidate = GL20.glCreateProgram();
                GL20.glAttachShader(candidate, vertex); GL20.glAttachShader(candidate, fragment); GL20.glLinkProgram(candidate);
                if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) throw new IllegalArgumentException(GL20.glGetProgramInfoLog(candidate, 4096));
                program = candidate; candidate = 0;
            }
            catch (Exception ex) { FTBQuests.LOGGER.warn("Could not load quest background shader " + loaded, ex); }
            finally
            {
                if (vertex != 0) GL20.glDeleteShader(vertex);
                if (fragment != 0) GL20.glDeleteShader(fragment);
                if (candidate != 0) GL20.glDeleteProgram(candidate);
            }
        }
        if (program == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GlStateManager.disableTexture2D();
        GL20.glUseProgram(program);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "iTime"), (System.nanoTime() % 3_600_000_000_000L) / 1_000_000_000F);
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "iResolution"), width * scale, height * scale, 1);
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "iOffset"), x * scale, mc.displayHeight - (y + height) * scale);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y); GL11.glVertex2f(x, y + height); GL11.glVertex2f(x + width, y + height); GL11.glVertex2f(x + width, y);
        GL11.glEnd();
        GL20.glUseProgram(previous);
        GlStateManager.enableTexture2D(); GlStateManager.color(1,1,1,1);
    }
}
