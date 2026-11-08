package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import fi.dy.masa.malilib.gui.GuiConfigsBase;

import java.util.ArrayList;
import java.util.List;

public class GuiSaveGitConfigs extends GuiConfigsBase {
    public GuiSaveGitConfigs() {
        super(10, 50, SaveGitMod.MOD_ID, null, "savegit.gui.title.configs");
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<ConfigOptionWrapper> wrappers =
                new ArrayList<>(ConfigOptionWrapper.createFor(SaveGitConfigs.OPTIONS));
        wrappers.add(0, new ConfigOptionWrapper(
                "按键绑定: 选项 → 控制 → 按键绑定 → SaveGit（打开主界面）"));
        return wrappers;
    }
}
