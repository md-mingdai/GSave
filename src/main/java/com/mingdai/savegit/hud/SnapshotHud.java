package com.mingdai.savegit.hud;

import com.mingdai.savegit.snapshot.SnapshotStatus;
import com.mingdai.savegit.util.L10n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 屏幕右下角的快照进行中提示，直到所有快照任务结束。
 */
public final class SnapshotHud {

    public static void render(GuiGraphicsExtractor graphics) {
        if (!SnapshotStatus.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        String label = L10n.commitMessage(SnapshotStatus.getLabel());
        String text = label.isEmpty()
                ? L10n.tr("gsave.hud.snapshot_active")
                : L10n.tr("gsave.hud.snapshot_active_reason", label);

        int width = mc.font.width(text);
        int x = graphics.guiWidth() - width - 10;
        int y = graphics.guiHeight() - 18;

        graphics.fill(x - 4, y - 4, x + width + 4, y + 10, 0xA0000000);
        graphics.text(mc.font, text, x, y, 0xFFFFDD55, true);
    }
}
