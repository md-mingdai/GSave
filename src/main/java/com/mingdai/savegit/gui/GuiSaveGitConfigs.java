package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.util.KeyCodes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class GuiSaveGitConfigs extends GuiConfigsBase {
    public GuiSaveGitConfigs() {
        super(10, 50, SaveGitMod.MOD_ID, null, "savegit.gui.title.configs");
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<ConfigOptionWrapper> wrappers = new ArrayList<>();
        wrappers.add(new ConfigOptionWrapper(
                "按键绑定请在 选项 - 控制 - 按键绑定 中修改 GSave 主界面热键"));

        for (IConfigBase config : SaveGitConfigs.OPTIONS) {
            // 定时快照关闭时不显示间隔与自动停用阈值
            if ((config == SaveGitConfigs.Generic.AUTO_SNAPSHOT_INTERVAL_MINUTES
                    || config == SaveGitConfigs.Generic.SCHEDULED_SNAPSHOT_STOP_COUNT)
                    && !SaveGitConfigs.isScheduledSnapshotEnabled()) {
                continue;
            }
            // 无限保留快照时不显示数量上限
            if (config == SaveGitConfigs.Generic.MAX_SNAPSHOTS
                    && SaveGitConfigs.isUnlimitedSnapshots()) {
                continue;
            }
            wrappers.add(new ConfigOptionWrapper(config));
        }
        return wrappers;
    }

    @Override
    protected void drawScreenBackground(DrawContext drawContext, int mouseX, int mouseY) {
        // 未加载世界时（标题界面 / Mod Menu）使用原版滚动全景背景
        if (this.mc.world == null) {
            this.renderPanoramaBackground(drawContext, 0.0F);
            drawContext.fill(0, 0, this.width, this.height, 0x60000000);
        } else {
            super.drawScreenBackground(drawContext, mouseX, mouseY);
        }
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        // 按 ESC 返回 GSave 主界面（正在编辑热键时保留 MaLiLib 的取消逻辑）
        if (keyCode == KeyCodes.KEY_ESCAPE && this.activeKeybindButton == null) {
            GuiBase.openGui(new GuiSaveGitMain());
            return true;
        }
        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }

    /** 定时快照开关变化时，立即重建选项列表。 */
    public static void refreshIfOpen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof GuiSaveGitConfigs gui) {
            mc.execute(() -> {
                if (mc.currentScreen == gui) {
                    gui.rebuildOptions();
                }
            });
        }
    }

    private void rebuildOptions() {
        SaveGitConfigs.saveToFile();
        this.reCreateListWidget();
        this.initGui();
    }
}
