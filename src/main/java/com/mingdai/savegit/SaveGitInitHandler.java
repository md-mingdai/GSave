package com.mingdai.savegit;

import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiSaveGitConfigs;
import com.mingdai.savegit.hud.SnapshotHud;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

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

        // 在世界选择界面添加 "GSave 管理" 按钮
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof SelectWorldScreen)) {
                return;
            }
            var buttons = Screens.getButtons(screen);
            int x = scaledWidth / 2 + 158;
            int y = scaledHeight - 28;
            var button = ButtonWidget.builder(
                    Text.translatable("gsave.button.world_manager"),
                    b -> {
                        var accessor = (com.mingdai.savegit.mixin.SelectWorldScreenAccessor) screen;
                        var levelList = accessor.savegit$getLevelList();
                        levelList.getSelectedAsOptional().ifPresent(entry -> {
                            var worldEntry = (com.mingdai.savegit.mixin.WorldEntryAccessor)(Object) entry;
                            var summary = worldEntry.savegit$getLevelSummary();
                            if (summary != null && !summary.isLocked()) {
                                var worldDir = client.runDirectory.toPath()
                                        .resolve("saves").resolve(summary.getName());
                                var worldName = summary.getDisplayName();
                                com.mingdai.savegit.gui.GuiSaveGitWorldManager.open(
                                        client, worldDir, worldName);
                            }
                        });
                    }
            ).dimensions(x, y, 72, 20).build();
            button.active = false;
            buttons.add(button);

            // 未选中世界时按钮置灰
            ScreenEvents.beforeRender(screen).register((renderedScreen, drawContext, mouseX, mouseY, tickDelta) -> {
                var accessor = (com.mingdai.savegit.mixin.SelectWorldScreenAccessor) renderedScreen;
                var levelList = accessor.savegit$getLevelList();
                button.active = levelList != null && levelList.getSelectedAsOptional().isPresent();
            });
        });
    }
}
