package com.mingdai.savegit.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * 注册到原版“选项 → 控制 → 按键绑定”的热键。
 */
public final class SaveGitKeyBindings {
    public static final KeyMapping OPEN_MAIN_MENU =
            KeyMappingHelper.registerKeyMapping(createKeyBinding());

    private static KeyMapping createKeyBinding() {
        //? if >=26.3 {
        return new KeyMapping("key.gsave.open_main_menu", InputConstants.Type.KEYBOARD,
                InputConstants.KEY_H, KeyMapping.Category.MISC);
        //?} else {
        /*return new KeyMapping("key.gsave.open_main_menu", InputConstants.Type.KEYSYM,
                InputConstants.KEY_H, KeyMapping.Category.MISC);
        *///?}
    }

    private SaveGitKeyBindings() {
    }

    public static void init() {
        // 类加载时完成注册
    }
}
