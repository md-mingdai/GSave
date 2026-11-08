package com.mingdai.savegit.input;

import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiSaveGitConfigs;
import com.mingdai.savegit.gui.GuiSnapshotList;
import com.mingdai.savegit.snapshot.SaveGitSnapshotManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.KeyAction;

public final class SaveGitKeyCallbacks {
    private SaveGitKeyCallbacks() {
    }

    public static void init() {
        SaveGitConfigs.Hotkeys.MANUAL_SNAPSHOT.getKeybind().setCallback((action, key) -> {
            if (action == KeyAction.PRESS) {
                SaveGitSnapshotManager.manualSnapshot();
                return true;
            }
            return false;
        });

        SaveGitConfigs.Hotkeys.OPEN_LIST.getKeybind().setCallback((action, key) -> {
            if (action == KeyAction.PRESS) {
                GuiBase.openGui(new GuiSnapshotList());
                return true;
            }
            return false;
        });

        SaveGitConfigs.Hotkeys.OPEN_SETTINGS.getKeybind().setCallback((action, key) -> {
            if (action == KeyAction.PRESS) {
                GuiBase.openGui(new GuiSaveGitConfigs());
                return true;
            }
            return false;
        });
    }
}
