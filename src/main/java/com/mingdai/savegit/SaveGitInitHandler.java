package com.mingdai.savegit;

import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiSaveGitConfigs;
import com.mingdai.savegit.input.SaveGitInputHandler;
import com.mingdai.savegit.input.SaveGitKeyCallbacks;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

public class SaveGitInitHandler implements IInitializationHandler {
    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(SaveGitMod.MOD_ID, new SaveGitConfigs());
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(SaveGitMod.MOD_ID, SaveGitMod.MOD_NAME, GuiSaveGitConfigs::new));

        InputEventHandler.getKeybindManager().registerKeybindProvider(SaveGitInputHandler.getInstance());
        SaveGitKeyCallbacks.init();
    }
}
