package com.mingdai.savegit.input;

import com.mingdai.savegit.config.SaveGitConfigs;
import com.mingdai.savegit.gui.GuiSaveGitMain;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.KeyAction;

public final class SaveGitKeyCallbacks {
    private SaveGitKeyCallbacks() {
    }

    public static void init() {
        SaveGitConfigs.Hotkeys.OPEN_MAIN_MENU.getKeybind().setCallback((action, key) -> {
            if (action == KeyAction.PRESS) {
                GuiBase.openGui(new GuiSaveGitMain());
                return true;
            }
            return false;
        });
    }
}
