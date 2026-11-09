package com.mingdai.savegit.compat;

import com.mingdai.savegit.gui.GuiSaveGitMain;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * 向 Mod Menu 注册 GSave 主界面作为配置界面。
 * Mod Menu 未安装时该类不会被加载。
 */
public class SaveGitModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new GuiSaveGitMain(parent);
    }
}
