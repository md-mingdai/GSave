package com.mingdai.savegit;

import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiSaveGitConfigs;
import com.mingdai.savegit.hud.SnapshotHud;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

public class SaveGitInitHandler implements IInitializationHandler {
    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(SaveGitMod.MOD_ID, new SaveGitConfigs());
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(SaveGitMod.MOD_ID, SaveGitMod.MOD_NAME, GuiSaveGitConfigs::new));

        RenderEventHandler.getInstance().registerGameOverlayRenderer(new SnapshotHud());
        SaveGitConfigs.Generic.SCHEDULED_SNAPSHOT_ENABLED.setValueChangeCallback(
                config -> GuiSaveGitConfigs.refreshIfOpen());
        SaveGitConfigs.Generic.UNLIMITED_SNAPSHOTS.setValueChangeCallback(
                config -> GuiSaveGitConfigs.refreshIfOpen());
    }
}
