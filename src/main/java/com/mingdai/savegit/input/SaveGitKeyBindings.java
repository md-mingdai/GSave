package com.mingdai.savegit.input;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 注册到原版“选项 → 控制 → 按键绑定”的热键。
 */
public final class SaveGitKeyBindings {
    public static final KeyBinding OPEN_MAIN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.savegit.open_main_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.savegit"
    ));

    private SaveGitKeyBindings() {
    }

    public static void init() {
        // 类加载时完成注册
    }
}
