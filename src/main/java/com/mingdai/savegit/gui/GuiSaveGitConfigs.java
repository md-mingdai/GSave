package com.mingdai.savegit.gui;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import fi.dy.masa.malilib.gui.GuiConfigsBase;

import java.util.List;

public class GuiSaveGitConfigs extends GuiConfigsBase {
    public GuiSaveGitConfigs() {
        super(10, 50, SaveGitMod.MOD_ID, null, "savegit.gui.title.configs");
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return ConfigOptionWrapper.createFor(SaveGitConfigs.OPTIONS);
    }
}
