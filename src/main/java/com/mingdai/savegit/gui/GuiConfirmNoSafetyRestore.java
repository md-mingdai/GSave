package com.mingdai.savegit.gui;

import com.mingdai.savegit.snapshot.Commit;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * 取消“恢复前安全快照”后的二级确认。
 */
public class GuiConfirmNoSafetyRestore extends GuiSaveGitScreen {
    private final Commit commit;
    private final GuiSnapshotList parent;

    public GuiConfirmNoSafetyRestore(Commit commit, GuiSnapshotList parent) {
        this.commit = commit;
        this.parent = parent;
        this.setTitle("警告：不创建恢复前快照");
    }

    @Override
    public void initGui() {
        super.initGui();

        int buttonWidth = 160;
        int y = this.height / 2 + 40;
        this.addButton(new ButtonGeneric(this.width / 2 - buttonWidth - 4, y, buttonWidth, 20, "我知道我在干什么"),
                (button, mouseButton) -> {
                    this.mc.setScreen(null);
                    SaveGitSnapshotManager.requestRestore(this.mc, this.commit, false);
                });
        this.addButton(new ButtonGeneric(this.width / 2 + 4, y, buttonWidth, 20, "取消"),
                (button, mouseButton) -> GuiBase.openGui(new GuiConfirmRestore(this.commit, this.parent)));
    }

    @Override
    protected void drawContents(DrawContext drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        List<String> lines = List.of(
                "你选择不创建恢复前安全快照。",
                "恢复后，当前世界状态将无法通过本模组回退。",
                "",
                "如果确定要继续，请点击「我知道我在干什么」。"
        );

        int startY = this.height / 2 - lines.size() * 6 - 30;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = i == 0 ? 0xFFFF6060 : 0xFFFFDD55;
            this.drawString(drawContext, line,
                    this.width / 2 - this.getStringWidth(line) / 2, startY + i * 12, color);
        }
    }
}
