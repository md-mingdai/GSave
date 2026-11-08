package com.mingdai.savegit.hud;

import com.mingdai.savegit.snapshot.SnapshotStatus;
import fi.dy.masa.malilib.interfaces.IRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * 屏幕右下角的快照进行中提示，直到所有快照任务结束。
 */
public class SnapshotHud implements IRenderer {
    @Override
    public void onRenderGameOverlayPost(DrawContext drawContext) {
        if (!SnapshotStatus.isActive()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String label = SnapshotStatus.getLabel();
        String text = label.isEmpty()
                ? "SaveGit 正在创建快照…"
                : "SaveGit 正在创建快照…（" + label + "）";

        int width = mc.textRenderer.getWidth(text);
        int x = drawContext.getScaledWindowWidth() - width - 10;
        int y = drawContext.getScaledWindowHeight() - 18;

        drawContext.fill(x - 4, y - 4, x + width + 4, y + 10, 0xA0000000);
        drawContext.drawTextWithShadow(mc.textRenderer, Text.literal(text), x, y, 0xFFFFDD55);
    }
}
