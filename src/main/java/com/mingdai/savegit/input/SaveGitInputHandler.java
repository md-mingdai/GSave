package com.mingdai.savegit.input;

import com.mingdai.savegit.SaveGitMod;
import com.mingdai.savegit.config.SaveGitConfigs;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

public final class SaveGitInputHandler implements IKeybindProvider {
    private static final SaveGitInputHandler INSTANCE = new SaveGitInputHandler();

    private SaveGitInputHandler() {
    }

    public static SaveGitInputHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : SaveGitConfigs.Hotkeys.HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(SaveGitMod.MOD_NAME,
                SaveGitMod.MOD_ID + ".hotkeys.category", SaveGitConfigs.Hotkeys.HOTKEY_LIST);
    }
}
