package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import net.minecraft.client.MinecraftClient;

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
                "按键绑定请在 选项 - 控制 - 按键绑定 中修改 SaveGit 主界面热键"));

        for (IConfigBase config : SaveGitConfigs.OPTIONS) {
            // 定时快照关闭时不显示间隔设置
            if (config == SaveGitConfigs.Generic.AUTO_SNAPSHOT_INTERVAL_MINUTES
                    && !SaveGitConfigs.isScheduledSnapshotEnabled()) {
                continue;
            }
            wrappers.add(new ConfigOptionWrapper(config));
        }
        return wrappers;
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
